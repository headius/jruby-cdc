/*
 ***** BEGIN LICENSE BLOCK *****
 * Version: CPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Common Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/cpl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2001 Chad Fowler <chadfowler@chadfowler.com>
 * Copyright (C) 2001 Alan Moore <alan_moore@gmx.net>
 * Copyright (C) 2001-2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2001-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004-2006 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004-2005 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2006 Ola Bini <ola.bini@ki.se>
 * Copyright (C) 2006 Miguel Covarrubias <mlcovarrubias@gmail.com>
 * Copyright (C) 2007 MenTaLguY <mental@rydia.net>
 * Copyright (C) 2007 William N Dortch <bill.dortch@gmail.com>
 * 
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the CPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the CPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jruby;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jruby.common.IRubyWarnings.ID;
import org.jruby.evaluator.ASTInterpreter;
import org.jruby.exceptions.JumpException;
import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.runtime.Block;
import org.jruby.runtime.CallType;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.builtin.Variable;
import org.jruby.runtime.component.VariableEntry;
import org.jruby.util.IdUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.javasupport.util.RuntimeHelpers;
import org.jruby.runtime.ClassIndex;
import org.jruby.runtime.MethodIndex;
import org.jruby.runtime.builtin.InstanceVariables;
import org.jruby.runtime.builtin.InternalVariables;
import org.jruby.runtime.marshal.CoreObjectType;
import org.jruby.util.TypeConverter;

/**
 * RubyObject is the only implementation of the
 * {@link org.jruby.runtime.builtin.IRubyObject}. Every Ruby object in JRuby
 * is represented by something that is an instance of RubyObject. In
 * some of the core class implementations, this means doing a subclass
 * that extends RubyObject, in other cases it means using a simple
 * RubyObject instance and the data field to store specific
 * information about the Ruby object.
 *
 * Some care has been taken to make the implementation be as
 * monomorphic as possible, so that the Java Hotspot engine can
 * improve performance of it. That is the reason for several patterns
 * that might seem odd in this class.
 *
 * The IRubyObject interface used to have lots of methods for
 * different things, but these have now mostly been refactored into
 * several interfaces that gives access to that specific part of the
 * object. This gives us the possibility to switch out that subsystem
 * without changing interfaces again. For example, instance variable
 * and internal variables are handled this way, but the implementation
 * in RubyObject only returns "this" in {@link #getInstanceVariables()} and
 * {@link #getInternalVariables()}.
 * 
 * @author  jpetersen
 */
@JRubyClass(name="Object", include="Kernel")
public class RubyObject implements Cloneable, IRubyObject, Serializable, CoreObjectType, InstanceVariables, InternalVariables {
    
    /**
     * It's not valid to create a totally empty RubyObject. Since the
     * RubyObject is always defined in relation to a runtime, that
     * means that creating RubyObjects from outside the class might
     * cause problems.
     */
    private RubyObject(){};

    /** 
     *  A value that is used as a null sentinel in among other places
     *  the RubyArray implementation. It will cause large problems to
     *  call any methods on this object.
     */
    public static final IRubyObject NEVER = new RubyObject();

    /**
     * A value that specifies an undefined value. This value is used
     * as a sentinel for undefined constant values, and other places
     * where neither null nor NEVER makes sense.
     */
    public static final IRubyObject UNDEF = new RubyObject();
    
    // The class of this object
    protected transient RubyClass metaClass;
    protected String metaClassName;

    /**
     * The variableTable contains variables for an object, defined as:
     * <ul>
     * <li> instance variables
     * <li> class variables (for classes/modules)
     * <li> internal variables (such as those used when marshaling RubyRange and RubyException)
     * </ul>
     * 
     * Constants are stored separately, see {@link RubyModule}. 
     * 
     */
    protected transient volatile VariableTableEntry[] variableTable;
    protected transient int variableTableSize;
    protected transient int variableTableThreshold;

    // The dataStruct is a place where custom information can be
    // contained for core implementations that doesn't necessarily
    // want to go to the trouble of creating a subclass of
    // RubyObject. The OpenSSL implementation uses this heavily to
    // save holder objects containing Java cryptography objects.
    private transient Object dataStruct;

    protected int flags; // zeroed by jvm
    public static final int ALL_F = -1;
    public static final int FALSE_F = 1 << 0;

    /**
     * This flag is a bit funny. It's used to denote that this value
     * is nil. It's a bit counterintuitive for a Java programmer to
     * not use subclassing to handle this case, since we have a
     * RubyNil subclass anyway. Well, the reason for it being a flag
     * is that the {@link #isNil()} method is called extremely often. So often
     * that it gives a good speed boost to make it monomorphic and
     * final. It turns out using a flag for this actually gives us
     * better performance than having a polymorphic {@link #isNil()} method.
     */
    public static final int NIL_F = 1 << 1;

    public static final int FROZEN_F = 1 << 2;
    public static final int TAINTED_F = 1 << 3;

    public static final int FL_USHIFT = 4;
    
    public static final int USER0_F = (1<<(FL_USHIFT+0));
    public static final int USER1_F = (1<<(FL_USHIFT+1));
    public static final int USER2_F = (1<<(FL_USHIFT+2));
    public static final int USER3_F = (1<<(FL_USHIFT+3));
    public static final int USER4_F = (1<<(FL_USHIFT+4));
    public static final int USER5_F = (1<<(FL_USHIFT+5));
    public static final int USER6_F = (1<<(FL_USHIFT+6));
    public static final int USER7_F = (1<<(FL_USHIFT+7));

    /**
     * Sets or unsets a flag on this object. The only flags that are
     * guaranteed to be valid to use as the first argument is:
     *
     * <ul>
     *  <li>{@link #FALSE_F}</li>
     *  <li>{@link NIL_F}</li>
     *  <li>{@link FROZEN_F}</li>
     *  <li>{@link TAINTED_F}</li>
     *  <li>{@link USER0_F}</li>
     *  <li>{@link USER1_F}</li>
     *  <li>{@link USER2_F}</li>
     *  <li>{@link USER3_F}</li>
     *  <li>{@link USER4_F}</li>
     *  <li>{@link USER5_F}</li>
     *  <li>{@link USER6_F}</li>
     *  <li>{@link USER7_F}</li>
     * </ul>
     *
     * @param flag the actual flag to set or unset.
     * @param set if true, the flag will be set, if false, the flag will be unset.
     */
    public final void setFlag(int flag, boolean set) {
        if (set) {
            flags |= flag;
        } else {
            flags &= ~flag;
        }
    }
    
    /**
     * Get the value of a custom flag on this object. The only
     * guaranteed flags that can be sent in to this method is:
     *
     * <ul>
     *  <li>{@link #FALSE_F}</li>
     *  <li>{@link NIL_F}</li>
     *  <li>{@link FROZEN_F}</li>
     *  <li>{@link TAINTED_F}</li>
     *  <li>{@link USER0_F}</li>
     *  <li>{@link USER1_F}</li>
     *  <li>{@link USER2_F}</li>
     *  <li>{@link USER3_F}</li>
     *  <li>{@link USER4_F}</li>
     *  <li>{@link USER5_F}</li>
     *  <li>{@link USER6_F}</li>
     *  <li>{@link USER7_F}</li>
     * </ul>
     *
     * @param flag the flag to get
     * @return true if the flag is set, false otherwise
     */
    public final boolean getFlag(int flag) { 
        return (flags & flag) != 0;
    }
    
    private transient Finalizer finalizer;
    
    /**
     * Class that keeps track of the finalizers for the object under
     * operation.
     */
    public class Finalizer implements Finalizable {
        private long id;
        private List<IRubyObject> finalizers;
        private AtomicBoolean finalized;
        
        public Finalizer(long id) {
            this.id = id;
            this.finalized = new AtomicBoolean(false);
        }
        
        public void addFinalizer(IRubyObject finalizer) {
            if (finalizers == null) {
                finalizers = new ArrayList<IRubyObject>();
            }
            finalizers.add(finalizer);
        }

        public void removeFinalizers() {
            finalizers = null;
        }
    
        @Override
        public void finalize() {
            if (finalized.compareAndSet(false, true)) {
                if (finalizers != null) {
                    for (int i = 0; i < finalizers.size(); i++) {
                        IRubyObject finalizer = finalizers.get(i);                        
                        RuntimeHelpers.invoke(
                                finalizer.getRuntime().getCurrentContext(),
                                finalizer, "call", RubyObject.this.id());
                    }
                }
            }
        }
    }

    /** 
     * Standard path for object creation. Objects are entered into ObjectSpace
     * only if ObjectSpace is enabled.
     */
    public RubyObject(Ruby runtime, RubyClass metaClass) {
        this.metaClass = metaClass;
        
        if (Ruby.RUNTIME_THREADLOCAL) setMetaClassName(metaClass);
        if (runtime.isObjectSpaceEnabled()) addToObjectSpace(runtime);
        if (runtime.getSafeLevel() >= 3) taint(runtime);
    }

    /**
     * Path for objects who want to decide whether they don't want to be in
     * ObjectSpace even when it is on. (notably used by objects being
     * considered immediate, they'll always pass false here)
     */
    protected RubyObject(Ruby runtime, RubyClass metaClass, boolean useObjectSpace) {
        this.metaClass = metaClass;
        
        if (Ruby.RUNTIME_THREADLOCAL) setMetaClassName(metaClass);
        if (useObjectSpace) addToObjectSpace(runtime);
        if (runtime.getSafeLevel() >= 3) taint(runtime);
    }

    private void addToObjectSpace(Ruby runtime) {
        assert runtime.isObjectSpaceEnabled();
        runtime.getObjectSpace().add(this);
    }

    private void setMetaClassName(RubyClass metaClass) {
        if (metaClass != null) {
            metaClassName = metaClass.classId;
        }
    }
    
    /**
     * Will create the Ruby class Object in the runtime
     * specified. This method needs to take the actual class as an
     * argument because of the Object class' central part in runtime
     * initialization.
     */
    public static RubyClass createObjectClass(Ruby runtime, RubyClass objectClass) {
        objectClass.index = ClassIndex.OBJECT;

        objectClass.defineAnnotatedMethods(ObjectMethods.class);

        return objectClass;
    }
    
    /**
     * Interestingly, the Object class doesn't really have that many
     * methods for itself. Instead almost all of the Object methods
     * are really defined on the Kernel module. This class is a holder
     * for all Object methods.
     *
     * @see RubyKernel
     */
    public static class ObjectMethods {
        @JRubyMethod(name = "initialize", visibility = Visibility.PRIVATE)
        public static IRubyObject intialize(IRubyObject self) {
            return self.getRuntime().getNil();
        }
    }
    
    /**
     * Default allocator instance for all Ruby objects. The only
     * reason to not use this allocator is if you actually need to
     * have all instances of something be a subclass of RubyObject.
     *
     * @see org.jruby.runtime.ObjectAllocator
     */
    public static final ObjectAllocator OBJECT_ALLOCATOR = new ObjectAllocator() {
        public IRubyObject allocate(Ruby runtime, RubyClass klass) {
            return new RubyObject(runtime, klass);
        }
    };

    /**
     * Will make sure that this object is added to the current object
     * space.
     *
     * @see org.jruby.runtime.ObjectSpace
     */
    public void attachToObjectSpace() {
        getRuntime().getObjectSpace().add(this);
    }
    
    /**
     * This is overridden in the other concrete Java builtins to provide a fast way
     * to determine what type they are.
     *
     * Will generally return a value from org.jruby.runtime.ClassIndex
     *
     * @see org.jruby.runtime.ClassInde
     */
    public int getNativeTypeIndex() {
        return ClassIndex.OBJECT;
    }

    /**
     * Specifically polymorphic method that are meant to be overridden
     * by modules to specify that they are modules in an easy way.
     */
    public boolean isModule() {
        return false;
    }
    
    /**
     * Specifically polymorphic method that are meant to be overridden
     * by classes to specify that they are classes in an easy way.
     */
    public boolean isClass() {
        return false;
    }

    /**
     *  Is object immediate (def: Fixnum, Symbol, true, false, nil?).
     */
    public boolean isImmediate() {
    	return false;
    }

    /** rb_make_metaclass
     *
     * Will create a new meta class, insert this in the chain of
     * classes for this specific object, and return the generated meta
     * class.
     */
    public RubyClass makeMetaClass(RubyClass superClass) {
        MetaClass klass = new MetaClass(getRuntime(), superClass); // rb_class_boot
        setMetaClass(klass);

        klass.setAttached(this);
        klass.setMetaClass(superClass.getRealClass().getMetaClass());

        return klass;
    }

    /**
     * Will return the Java interface that most closely can represent
     * this object, when working through JAva integration
     * translations.
     */
    public Class getJavaClass() {
        return IRubyObject.class;
    }
    
    /**
     * Simple helper to print any objects.
     */
    public static void puts(Object obj) {
        System.out.println(obj.toString());
    }

    /**
     * This method is just a wrapper around the Ruby "==" method,
     * provided so that RubyObjects can be used as keys in the Java
     * HashMap object underlying RubyHash.
     */
    @Override
    public boolean equals(Object other) {
        return other == this || 
                other instanceof IRubyObject && 
                callMethod(getRuntime().getCurrentContext(), MethodIndex.EQUALEQUAL, "==", (IRubyObject) other).isTrue();
    }

    /**
     * The default toString method is just a wrapper that calls the
     * Ruby "to_s" method.
     */
    @Override
    public String toString() {
        return RuntimeHelpers.invoke(getRuntime().getCurrentContext(), this, MethodIndex.TO_S, "to_s", IRubyObject.NULL_ARRAY).toString();
    }

    /** 
     * Will return the runtime that this object is associated with.
     *
     * @return current runtime
     */
    public final Ruby getRuntime() {
        return getMetaClass().getClassRuntime();
    }

    /**
     * if exist return the meta-class else return the type of the object.
     *
     */
    public final RubyClass getMetaClass() {
        if (Ruby.RUNTIME_THREADLOCAL) {
            return lazyMetaClass();
        }
        return metaClass;
    }
    
    private final RubyClass lazyMetaClass() {
        RubyClass mc;
        
        if ((mc = metaClass) != null) {
            return mc;
        }

        if (metaClassName != null) {
            // this should only happen when we're persisting objects, so go after getCurrentInstance directly
            metaClass = Ruby.getCurrentInstance().getClass(metaClassName);
        }
        
        return metaClass;
    }

    /** 
     * Makes it possible to change the metaclass of an object. In
     * practice, this is a simple version of Smalltalks Become, except
     * that it doesn't work when we're dealing with subclasses. In
     * practice it's used to change the singleton/meta class used,
     * without changing the "real" inheritance chain.
     */
    public void setMetaClass(RubyClass metaClass) {
        this.metaClass = metaClass;
        if (Ruby.RUNTIME_THREADLOCAL) {
            if (metaClass != null) {
                metaClassName = metaClass.classId;
            }
        }
    }

    /**
     * Is this value frozen or not? Shortcut for doing
     * getFlag(FROZEN_F).
     *
     * @return true if this object is frozen, false otherwise
     */
    public boolean isFrozen() {
        return (flags & FROZEN_F) != 0;
    }

    /**
     * Sets whether this object is frozen or not. Shortcut for doing
     * setFlag(FROZEN_F, frozen).
     *
     * @param frozen should this object be frozen?
     */
    public void setFrozen(boolean frozen) {
        if (frozen) {
            flags |= FROZEN_F;
        } else {
            flags &= ~FROZEN_F;
        }
    }

    /** rb_frozen_class_p
     *
     * Helper to test whether this object is frozen, and if it is will
     * throw an exception based on the message.
     */
   protected final void testFrozen(String message) {
       if (isFrozen()) {
           throw getRuntime().newFrozenError(message + " " + getMetaClass().getName());
       }
   }

    /**
     * The actual method that checks frozen with the default frozen message from MRI.
     * If possible, call this instead of {@link #testFrozen}.
     */
   protected void checkFrozen() {
       testFrozen("can't modify frozen ");
   }

    /**
     * Gets the taint. Shortcut for getFlag(TAINTED_F).
     * 
     * @return true if this object is tainted
     */
    public boolean isTaint() {
        return (flags & TAINTED_F) != 0; 
    }

    /**
     * Sets the taint flag. Shortcut for setFlag(TAINTED_F, taint)
     *
     * @param taint should this object be tainted or not?
     */
    public void setTaint(boolean taint) {
        if (taint) {
            flags |= TAINTED_F;
        } else {
            flags &= ~TAINTED_F;
        }
    }

    /**
     * Does this object represent nil? See the docs for the {@link
     * #NIL_F} flag for more information.
     */
    public final boolean isNil() {
        return (flags & NIL_F) != 0;
    }

    /**
     * Is this value a true value or not? Based on the {@link #FALSE_F} flag.
     */
    public final boolean isTrue() {
        return (flags & FALSE_F) == 0;
    }

    /**
     * Is this value a false value or not? Based on the {@link #FALSE_F} flag.
     */
    public final boolean isFalse() {
        return (flags & FALSE_F) != 0;
    }

    /**
     * Does this object respond to the specified message? Uses a
     * shortcut if it can be proved that respond_to? haven't been
     * overridden.
     */
    public final boolean respondsTo(String name) {
        if(getMetaClass().searchMethod("respond_to?") == getRuntime().getRespondToMethod()) {
            return getMetaClass().isMethodBound(name, false);
        } else {
            return callMethod(getRuntime().getCurrentContext(),"respond_to?",getRuntime().newSymbol(name)).isTrue();
        }
    }

    /** rb_singleton_class
     * 
     * Note: this method is specialized for RubyFixnum, RubySymbol,
     * RubyNil and RubyBoolean
     *
     * Will either return the existing singleton class for this
     * object, or create a new one and return that.
     */    
    public RubyClass getSingletonClass() {
        RubyClass klass;
        
        if (getMetaClass().isSingleton() && ((MetaClass)getMetaClass()).getAttached() == this) {
            klass = getMetaClass();            
        } else {
            klass = makeMetaClass(getMetaClass());
        }
        
        klass.setTaint(isTaint());
        if (isFrozen()) klass.setFrozen(true);
        
        return klass;
    }
    
    /** rb_singleton_class_clone
     *
     * Will make sure that if the current objects class is a
     * singleton, it will get cloned.
     *
     * @return either a real class, or a clone of the current singleton class
     */
    protected RubyClass getSingletonClassClone() {
       RubyClass klass = getMetaClass();

       if (!klass.isSingleton()) return klass;

       MetaClass clone = new MetaClass(getRuntime());
       clone.flags = flags;

       if (this instanceof RubyClass) {
           clone.setMetaClass(clone);
       } else {
           clone.setMetaClass(klass.getSingletonClassClone());
       }

       clone.setSuperClass(klass.getSuperClass());

       if (klass.hasVariables()) {
           clone.syncVariables(klass.getVariableList());
       }

       klass.cloneMethods(clone);

       ((MetaClass)clone.getMetaClass()).setAttached(clone);

       ((MetaClass)clone).setAttached(((MetaClass)klass).getAttached());

       return clone;
    }

    /** init_copy
     * 
     * Initializes a copy with variable and special instance variable
     * information, and then call the initialize_copy Ruby method.
     */
    private static void initCopy(IRubyObject clone, RubyObject original) {
        assert !clone.isFrozen() : "frozen object (" + clone.getMetaClass().getName() + ") allocated";

        original.copySpecialInstanceVariables(clone);

        if (original.hasVariables()) {
            clone.syncVariables(original.getVariableList());
        }

        /* FIXME: finalizer should be dupped here */
        clone.callMethod(clone.getRuntime().getCurrentContext(), "initialize_copy", original);
    }

    /** OBJ_INFECT
     *
     * Infects this object with traits from the argument obj. In real
     * terms this currently means that if obj is tainted, this object
     * will get tainted too. It's possible to hijack this method to do
     * other infections if that would be interesting.
     */
    public IRubyObject infectBy(IRubyObject obj) {
        if (obj.isTaint()) setTaint(true);
        return this;
    }

    /**
     * The protocol for super method invocation is a bit complicated
     * in Ruby. In real terms it involves first finding the real
     * implementation class (the super class), getting the name of the
     * method to call from the frame, and then invoke that on the
     * super class with the current self as the actual object
     * invoking.
     */
    public IRubyObject callSuper(ThreadContext context, IRubyObject[] args, Block block) {
        RubyModule klazz = context.getFrameKlazz();

        RubyClass superClass = RuntimeHelpers.findImplementerIfNecessary(getMetaClass(), klazz).getSuperClass();
        
        if (superClass == null) {
            String name = context.getFrameName(); 
            return RuntimeHelpers.callMethodMissing(context, this, klazz.searchMethod(name), name, args, this, CallType.SUPER, block);
        }
        return RuntimeHelpers.invokeAs(context, superClass, this, context.getFrameName(), args, CallType.SUPER, block);
    }    

    /**
     * Will invoke a named method with no arguments and no block.
     */
    public final IRubyObject callMethod(ThreadContext context, String name) {
        return RuntimeHelpers.invoke(context, this, name, IRubyObject.NULL_ARRAY, null, Block.NULL_BLOCK);
    }

    /**
     * Will invoke a named method with one argument and no block with
     * functional invocation.
     */
     public final IRubyObject callMethod(ThreadContext context, String name, IRubyObject arg) {
        return RuntimeHelpers.invoke(context, this, name, arg, CallType.FUNCTIONAL, Block.NULL_BLOCK);
    }

    /**
     * Will invoke a named method with the supplied arguments and no
     * block with functional invocation.
     */
    public final IRubyObject callMethod(ThreadContext context, String name, IRubyObject[] args) {
        return RuntimeHelpers.invoke(context, this, name, args, CallType.FUNCTIONAL, Block.NULL_BLOCK);
    }

    /**
     * Will invoke a named method with the supplied arguments and
     * supplied block with functional invocation.
     */
    public final IRubyObject callMethod(ThreadContext context, String name, IRubyObject[] args, Block block) {
        return RuntimeHelpers.invoke(context, this, name, args, CallType.FUNCTIONAL, block);
    }

    /**
     * Will invoke an indexed method with the no arguments and no
     * block.
     */
    public final IRubyObject callMethod(ThreadContext context, int methodIndex, String name) {
        return RuntimeHelpers.invoke(context, this, methodIndex, name, IRubyObject.NULL_ARRAY, null, Block.NULL_BLOCK);
    }

    /**
     * Will invoke an indexed method with the one argument and no
     * block with a functional invocation.
     */
    public final IRubyObject callMethod(ThreadContext context, int methodIndex, String name, IRubyObject arg) {
        return RuntimeHelpers.invoke(context, this, methodIndex,name,new IRubyObject[]{arg},CallType.FUNCTIONAL, Block.NULL_BLOCK);
    }

    /**
     * Call the Ruby initialize method with the supplied arguments and block.
     */
    public final void callInit(IRubyObject[] args, Block block) {
        callMethod(getRuntime().getCurrentContext(), "initialize", args, block);
    }

    /** rb_to_id
     *
     * Will try to convert this object to a String using the Ruby
     * "to_str" if the object isn't already a String. If this still
     * doesn't work, will throw a Ruby TypeError.
     *
     */
    public String asJavaString() {
        IRubyObject asString = checkStringType();
        if(!asString.isNil()) return ((RubyString)asString).asJavaString();
        throw getRuntime().newTypeError(inspect().toString() + " is not a symbol");
    }

    /**
     * Tries to convert this object to a Ruby Array using the "to_ary"
     * method.
     */
    public RubyArray convertToArray() {
        return (RubyArray) TypeConverter.convertToType(this, getRuntime().getArray(), MethodIndex.TO_ARY, "to_ary");
    }

    /**
     * Tries to convert this object to a Ruby Hash using the "to_hash"
     * method.
     */
    public RubyHash convertToHash() {
        return (RubyHash)TypeConverter.convertToType(this, getRuntime().getHash(), MethodIndex.TO_HASH, "to_hash");
    }
    
    /**
     * Tries to convert this object to a Ruby Float using the "to_f"
     * method.
     */
    public RubyFloat convertToFloat() {
        return (RubyFloat) TypeConverter.convertToType(this, getRuntime().getFloat(), MethodIndex.TO_F, "to_f");
    }

    /**
     * Tries to convert this object to a Ruby Integer using the "to_int"
     * method.
     */
    public RubyInteger convertToInteger() {
        return convertToInteger(MethodIndex.TO_INT, "to_int");
    }

    /**
     * Tries to convert this object to a Ruby Integer using the
     * supplied conversion method.
     */
    public RubyInteger convertToInteger(int convertMethodIndex, String convertMethod) {
        IRubyObject val = TypeConverter.convertToType(this, getRuntime().getInteger(), convertMethodIndex, convertMethod, true);
        if (!(val instanceof RubyInteger)) throw getRuntime().newTypeError(getMetaClass().getName() + "#" + convertMethod + " should return Integer");
        return (RubyInteger)val;
    }

    /**
     * Tries to convert this object to a Ruby String using the
     * "to_str" method.
     */
    public RubyString convertToString() {
        return (RubyString) TypeConverter.convertToType(this, getRuntime().getString(), MethodIndex.TO_STR, "to_str");
    }
    
    /**
     * Tries to convert this object to the specified Ruby type, using
     * a specific conversion method.
     */
    public final IRubyObject convertToType(RubyClass target, int convertMethodIndex) {
        return TypeConverter.convertToType(this, target, convertMethodIndex, (String)MethodIndex.NAMES.get(convertMethodIndex));
    }

    /** rb_obj_as_string
     *
     * First converts this object into a String using the "to_s"
     * method, infects it with the current taint and returns it. If
     * to_s doesn't return a Ruby String, {@link #anyToString} is used
     * instead.
     */
    public RubyString asString() {
        IRubyObject str = RuntimeHelpers.invoke(getRuntime().getCurrentContext(), this, MethodIndex.TO_S, "to_s", IRubyObject.NULL_ARRAY);
        
        if (!(str instanceof RubyString)) return (RubyString)anyToString();
        if (isTaint()) str.setTaint(true);
        return (RubyString) str;
    }
    
    /** rb_check_string_type
     *
     * Tries to return a coerced string representation of this object,
     * using "to_str". If that returns something other than a String
     * or nil, an empty String will be returned.
     *
     */
    public IRubyObject checkStringType() {
        IRubyObject str = TypeConverter.convertToTypeWithCheck(this, getRuntime().getString(), MethodIndex.TO_STR, "to_str");
        if(!str.isNil() && !(str instanceof RubyString)) {
            str = RubyString.newEmptyString(getRuntime());
        }
        return str;
    }

    /** rb_check_array_type
    *
    * Returns the result of trying to convert this object to an Array
    * with "to_ary".
    */    
    public IRubyObject checkArrayType() {
        return TypeConverter.convertToTypeWithCheck(this, getRuntime().getArray(), MethodIndex.TO_ARY, "to_ary");
    }

    /** specific_eval
     *
     * Evaluates the block or string inside of the context of this
     * object, using the supplied arguments. If a block is given, this
     * will be yielded in the specific context of this object. If no
     * block is given then a String-like object needs to be the first
     * argument, and this string will be evaluated. Second and third
     * arguments in the args-array is optional, but can contain the
     * filename and line of the string under evaluation.
     */
    @Deprecated
    public IRubyObject specificEval(ThreadContext context, RubyModule mod, IRubyObject[] args, Block block) {
        if (block.isGiven()) {
            if (args.length > 0) throw getRuntime().newArgumentError(args.length, 0);

            return yieldUnder(context, mod, block);
        }

        if (args.length == 0) {
            throw getRuntime().newArgumentError("block not supplied");
        } else if (args.length > 3) {
            String lastFuncName = context.getFrameName();
            throw getRuntime().newArgumentError(
                "wrong # of arguments: " + lastFuncName + "(src) or " + lastFuncName + "{..}");
        }
        
        // We just want the TypeError if the argument doesn't convert to a String (JRUBY-386)
        RubyString evalStr;
        if (args[0] instanceof RubyString) {
            evalStr = (RubyString)args[0];
        } else {
            evalStr = args[0].convertToString();
        }

        String file;
        int line;
        if (args.length > 1) {
            file = args[1].convertToString().asJavaString();
            if (args.length > 2) {
                line = (int)(args[2].convertToInteger().getLongValue() - 1);
            } else {
                line = 0;
            }
        } else {
            file = "(eval)";
            line = 0;
        }

        return evalUnder(context, mod, evalStr, file, line);
    }

    /** specific_eval
     *
     * Evaluates the block or string inside of the context of this
     * object, using the supplied arguments. If a block is given, this
     * will be yielded in the specific context of this object. If no
     * block is given then a String-like object needs to be the first
     * argument, and this string will be evaluated. Second and third
     * arguments in the args-array is optional, but can contain the
     * filename and line of the string under evaluation.
     */
    public IRubyObject specificEval(ThreadContext context, RubyModule mod, Block block) {
        if (block.isGiven()) {
            return yieldUnder(context, mod, block);
        } else {
            throw getRuntime().newArgumentError("block not supplied");
        }
    }

    /** specific_eval
     *
     * Evaluates the block or string inside of the context of this
     * object, using the supplied arguments. If a block is given, this
     * will be yielded in the specific context of this object. If no
     * block is given then a String-like object needs to be the first
     * argument, and this string will be evaluated. Second and third
     * arguments in the args-array is optional, but can contain the
     * filename and line of the string under evaluation.
     */
    public IRubyObject specificEval(ThreadContext context, RubyModule mod, IRubyObject arg, Block block) {
        if (block.isGiven()) {
            throw getRuntime().newArgumentError(1, 0);
        }
        
        // We just want the TypeError if the argument doesn't convert to a String (JRUBY-386)
        RubyString evalStr;
        if (arg instanceof RubyString) {
            evalStr = (RubyString)arg;
        } else {
            evalStr = arg.convertToString();
        }

        String file = "(eval)";
        int line = 0;

        return evalUnder(context, mod, evalStr, file, line);
    }

    /** specific_eval
     *
     * Evaluates the block or string inside of the context of this
     * object, using the supplied arguments. If a block is given, this
     * will be yielded in the specific context of this object. If no
     * block is given then a String-like object needs to be the first
     * argument, and this string will be evaluated. Second and third
     * arguments in the args-array is optional, but can contain the
     * filename and line of the string under evaluation.
     */
    public IRubyObject specificEval(ThreadContext context, RubyModule mod, IRubyObject arg0, IRubyObject arg1, Block block) {
        if (block.isGiven()) {
            throw getRuntime().newArgumentError(2, 0);
        }
        
        // We just want the TypeError if the argument doesn't convert to a String (JRUBY-386)
        RubyString evalStr;
        if (arg0 instanceof RubyString) {
            evalStr = (RubyString)arg0;
        } else {
            evalStr = arg0.convertToString();
        }

        String file = arg1.convertToString().asJavaString();
        int line = 0;

        return evalUnder(context, mod, evalStr, file, line);
    }

    /** specific_eval
     *
     * Evaluates the block or string inside of the context of this
     * object, using the supplied arguments. If a block is given, this
     * will be yielded in the specific context of this object. If no
     * block is given then a String-like object needs to be the first
     * argument, and this string will be evaluated. Second and third
     * arguments in the args-array is optional, but can contain the
     * filename and line of the string under evaluation.
     */
    public IRubyObject specificEval(ThreadContext context, RubyModule mod, IRubyObject arg0, IRubyObject arg1, IRubyObject arg2, Block block) {
        if (block.isGiven()) {
            throw getRuntime().newArgumentError(2, 0);
        }
        
        // We just want the TypeError if the argument doesn't convert to a String (JRUBY-386)
        RubyString evalStr;
        if (arg0 instanceof RubyString) {
            evalStr = (RubyString)arg0;
        } else {
            evalStr = arg0.convertToString();
        }

        String file = arg1.convertToString().asJavaString();
        int line = (int)(arg2.convertToInteger().getLongValue() - 1);

        return evalUnder(context, mod, evalStr, file, line);
    }

    /**
     * Evaluates the string src with self set to the current object,
     * using the module under as the context.
     * @deprecated Call with an int line number and String file
     */
    public IRubyObject evalUnder(final ThreadContext context, RubyModule under, IRubyObject src, IRubyObject file, IRubyObject line) {
        return evalUnder(context, under, src.convertToString(), file.convertToString().toString(), (int) (line.convertToInteger().getLongValue() - 1));
    }

    /**
     * Evaluates the string src with self set to the current object,
     * using the module under as the context.
     */
    public IRubyObject evalUnder(final ThreadContext context, RubyModule under, RubyString src, String file, int line) {
        Visibility savedVisibility = context.getCurrentVisibility();
        context.setCurrentVisibility(Visibility.PUBLIC);
        context.preExecuteUnder(under, Block.NULL_BLOCK);
        try {
            return ASTInterpreter.evalSimple(context, this, src, 
                    file, line);
        } finally {
            context.postExecuteUnder();
            context.setCurrentVisibility(savedVisibility);
        }
    }

    /**
     * Will yield to the specific block changing the self to be the
     * current object instead of the self that is part of the frame
     * saved in the block frame. This method is the basis for the Ruby
     * instance_eval and module_eval methods. The arguments sent in to
     * it in the args array will be yielded to the block. This makes
     * it possible to emulate both instance_eval and instance_exec
     * with this implementation.
     */
    private IRubyObject yieldUnder(final ThreadContext context, RubyModule under, IRubyObject[] args, Block block) {
        context.preExecuteUnder(under, block);
        
        Visibility savedVisibility = block.getBinding().getVisibility();
        block.getBinding().setVisibility(Visibility.PUBLIC);
        
        try {
            IRubyObject valueInYield;
            boolean aValue;
            if (args.length == 1) {
                valueInYield = args[0];
                aValue = false;
            } else {
                valueInYield = RubyArray.newArrayNoCopy(getRuntime(), args);
                aValue = true;
            }

            // FIXME: This is an ugly hack to resolve JRUBY-1381; I'm not proud of it
            block = block.cloneBlock();
            block.getBinding().setSelf(RubyObject.this);
            block.getBinding().getFrame().setSelf(RubyObject.this);
            // end hack

            return block.yield(context, valueInYield, RubyObject.this, context.getRubyClass(), aValue);
            //TODO: Should next and return also catch here?
        } catch (JumpException.BreakJump bj) {
            return (IRubyObject) bj.getValue();
        } finally {
            block.getBinding().setVisibility(savedVisibility);
            
            context.postExecuteUnder();
        }
    }

    /**
     * Will yield to the specific block changing the self to be the
     * current object instead of the self that is part of the frame
     * saved in the block frame. This method is the basis for the Ruby
     * instance_eval and module_eval methods. The arguments sent in to
     * it in the args array will be yielded to the block. This makes
     * it possible to emulate both instance_eval and instance_exec
     * with this implementation.
     */
    private IRubyObject yieldUnder(final ThreadContext context, RubyModule under, Block block) {
        context.preExecuteUnder(under, block);
        
        Visibility savedVisibility = block.getBinding().getVisibility();
        block.getBinding().setVisibility(Visibility.PUBLIC);
        
        try {
            // FIXME: This is an ugly hack to resolve JRUBY-1381; I'm not proud of it
            block = block.cloneBlock();
            block.getBinding().setSelf(RubyObject.this);
            block.getBinding().getFrame().setSelf(RubyObject.this);
            // end hack

            return block.yield(context, this, this, context.getRubyClass(), false);
            //TODO: Should next and return also catch here?
        } catch (JumpException.BreakJump bj) {
            return (IRubyObject) bj.getValue();
        } finally {
            block.getBinding().setVisibility(savedVisibility);
            
            context.postExecuteUnder();
        }
    }

    // Methods of the Object class (rb_obj_*):

    /** rb_obj_equal
     *
     * Will by default use identity equality to compare objects. This
     * follows the Ruby semantics.
     */
    @JRubyMethod(name = "==", required = 1)
    public IRubyObject op_equal(ThreadContext context, IRubyObject obj) {
        return this == obj ? getRuntime().getTrue() : getRuntime().getFalse();
    }
    
    /** rb_obj_equal
     *
     * Will use Java identity equality.
     */
    @JRubyMethod(name = "equal?", required = 1)
    public IRubyObject equal_p(IRubyObject obj) {
        return this == obj ? getRuntime().getTrue() : getRuntime().getFalse();
    }

    /** method used for Hash key comparison (specialized for String, Symbol and Fixnum)
     * 
     * Will by default just call the Ruby method "eql?"
     */
    public boolean eql(IRubyObject other) {
        return callMethod(getRuntime().getCurrentContext(), MethodIndex.EQL_P, "eql?", other).isTrue();
    }

    /** rb_obj_equal
     * 
     * Just like "==" and "equal?", "eql?" will use identity equality for Object.
     */    
    @JRubyMethod(name = "eql?", required = 1)
    public IRubyObject eql_p(IRubyObject obj) {
        return this == obj ? getRuntime().getTrue() : getRuntime().getFalse();
    }
    
    /** rb_equal
     * 
     * The Ruby "===" method is used by default in case/when
     * statements. The Object implementation first checks Java identity
     * equality and then calls the "==" method too.
     */
    @JRubyMethod(name = "===", required = 1)
    public IRubyObject op_eqq(ThreadContext context, IRubyObject other) {
        return getRuntime().newBoolean(equalInternal(context, this, other));
    }

    /**
     * Helper method for checking equality, first using Java identity
     * equality, and then calling the "==" method.
     */
    protected static boolean equalInternal(final ThreadContext context, final IRubyObject that, final IRubyObject other){
        return that == other || that.callMethod(context, MethodIndex.EQUALEQUAL, "==", other).isTrue();
    }

    /**
     * Helper method for checking equality, first using Java identity
     * equality, and then calling the "eql?" method.
     */
    protected static boolean eqlInternal(final ThreadContext context, final IRubyObject that, final IRubyObject other){
        return that == other || that.callMethod(context, MethodIndex.EQL_P, "eql?", other).isTrue();
    }

    /** rb_obj_init_copy
     * 
     * Initializes this object as a copy of the original, that is the
     * parameter to this object. Will make sure that the argument
     * actually has the same real class as this object. It shouldn't
     * be possible to initialize an object with something totally
     * different.
     */
    @JRubyMethod(name = "initialize_copy", required = 1, visibility = Visibility.PRIVATE)
    public IRubyObject initialize_copy(IRubyObject original) {
	    if (this == original) return this;
	    checkFrozen();

        if (getMetaClass().getRealClass() != original.getMetaClass().getRealClass()) {
            throw getRuntime().newTypeError("initialize_copy should take same class object");
	    }

	    return this;
	}

    /** obj_respond_to
     *
     * respond_to?( aSymbol, includePriv=false ) -> true or false
     *
     * Returns true if this object responds to the given method. Private
     * methods are included in the search only if the optional second
     * parameter evaluates to true.
     *
     * @return true if this responds to the given method
     *
     * !!! For some reason MRI shows the arity of respond_to? as -1, when it should be -2; that's why this is rest instead of required, optional = 1
     *
     * Going back to splitting according to method arity. MRI is wrong
     * about most of these anyway, and since we have arity splitting
     * in both the compiler and the interpreter, the performance
     * benefit is important for this method.
     */
    @JRubyMethod(name = "respond_to?")
    public RubyBoolean respond_to_p(IRubyObject mname) {
        String name = mname.asJavaString();
        return getRuntime().newBoolean(getMetaClass().isMethodBound(name, true));
    }

    /** obj_respond_to
     *
     * respond_to?( aSymbol, includePriv=false ) -> true or false
     *
     * Returns true if this object responds to the given method. Private
     * methods are included in the search only if the optional second
     * parameter evaluates to true.
     *
     * @return true if this responds to the given method
     *
     * !!! For some reason MRI shows the arity of respond_to? as -1, when it should be -2; that's why this is rest instead of required, optional = 1
     *
     * Going back to splitting according to method arity. MRI is wrong
     * about most of these anyway, and since we have arity splitting
     * in both the compiler and the interpreter, the performance
     * benefit is important for this method.
     */
    @JRubyMethod(name = "respond_to?")
    public RubyBoolean respond_to_p(IRubyObject mname, IRubyObject includePrivate) {
        String name = mname.asJavaString();
        return getRuntime().newBoolean(getMetaClass().isMethodBound(name, !includePrivate.isTrue()));
    }

    /** rb_obj_id 
     *
     * Return the internal id of an object.
     *
     * FIXME: Should this be renamed to match its ruby name?
     */
    @JRubyMethod(name = {"object_id", "__id__"})
    public synchronized IRubyObject id() {
        return getRuntime().newFixnum(getRuntime().getObjectSpace().idOf(this));
    }

    /** rb_obj_id_obsolete
     * 
     * Old id version. This one is bound to the "id" name and will emit a deprecation warning.
     */
    @JRubyMethod(name = "id")
    public synchronized IRubyObject id_deprecated() {
        getRuntime().getWarnings().warn(ID.DEPRECATED_METHOD, "Object#id will be deprecated; use Object#object_id", "Object#id", "Object#object_id");
        return id();
    }

    /** rb_obj_id
     *
     * Will return the hash code of this object. In comparison to MRI,
     * this method will use the Java identity hash code instead of
     * using rb_obj_id, since the usage of id in JRuby will incur the
     * cost of some. ObjectSpace maintenance.
     */    
    @JRubyMethod(name = "hash")
    public RubyFixnum hash() {
        return getRuntime().newFixnum(super.hashCode());
    }

    /**
     * Override the Object#hashCode method to make sure that the Ruby
     * hash is actually used as the hashcode for Ruby objects. If the
     * Ruby "hash" method doesn't return a number, the Object#hashCode
     * implementation will be used instead.
     */
    @Override
    public int hashCode() {
        IRubyObject hashValue = callMethod(getRuntime().getCurrentContext(), MethodIndex.HASH, "hash");
        
        if (hashValue instanceof RubyFixnum) return (int) RubyNumeric.fix2long(hashValue); 
        
        return super.hashCode();
    }

    /** rb_obj_class
     *
     * Returns the real class of this object, excluding any
     * singleton/meta class in the inheritance chain.
     */
    @JRubyMethod(name = "class")
    public RubyClass type() {
        return getMetaClass().getRealClass();
    }

    /** rb_obj_type
     *
     * The deprecated version of type, that emits a deprecation
     * warning.
     */
    @JRubyMethod(name = "type")
    public RubyClass type_deprecated() {
        getRuntime().getWarnings().warn(ID.DEPRECATED_METHOD, "Object#type is deprecated; use Object#class", "Object#type", "Object#class");
        return type();
    }

    /** rb_obj_clone
     *
     * This method should be overridden only by: Proc, Method,
     * UnboundedMethod, Binding. It will use the defined allocated of
     * the object, then clone the singleton class, taint the object,
     * call initCopy and then copy frozen state.
     */
    @JRubyMethod(name = "clone", frame = true)
    public IRubyObject rbClone() {
        if (isImmediate()) throw getRuntime().newTypeError("can't clone " + getMetaClass().getName());
        
        // We're cloning ourselves, so we know the result should be a RubyObject
        RubyObject clone = (RubyObject)getMetaClass().getRealClass().allocate();
        clone.setMetaClass(getSingletonClassClone());
        if (isTaint()) clone.setTaint(true);

        initCopy(clone, this);

        if (isFrozen()) clone.setFrozen(true);
        return clone;
    }

    /** rb_obj_dup
     *
     * This method should be overridden only by: Proc
     *
     * Will allocate a new instance of the real class of this object,
     * and then initialize that copy. It's different from {@link
     * #rbClone} in that it doesn't copy the singleton class.
     */
    @JRubyMethod(name = "dup")
    public IRubyObject dup() {
        if (isImmediate()) throw getRuntime().newTypeError("can't dup " + getMetaClass().getName());

        IRubyObject dup = getMetaClass().getRealClass().allocate();
        if (isTaint()) dup.setTaint(true);

        initCopy(dup, this);

        return dup;
    }
    
    /** 
     * Lots of MRI objects keep their state in non-lookupable ivars
     * (e:g. Range, Struct, etc). This method is responsible for
     * dupping our java field equivalents
     */
    protected void copySpecialInstanceVariables(IRubyObject clone) {
    }    

    /** rb_obj_display
     *
     *  call-seq:
     *     obj.display(port=$>)    => nil
     *  
     *  Prints <i>obj</i> on the given port (default <code>$></code>).
     *  Equivalent to:
     *     
     *     def display(port=$>)
     *       port.write self
     *     end
     *     
     *  For example:
     *     
     *     1.display
     *     "cat".display
     *     [ 4, 5, 6 ].display
     *     puts
     *     
     *  <em>produces:</em>
     *     
     *     1cat456
     * 
     */
    @JRubyMethod(name = "display", optional = 1)
    public IRubyObject display(ThreadContext context, IRubyObject[] args) {
        IRubyObject port = args.length == 0
            ? getRuntime().getGlobalVariables().get("$>") : args[0];

        port.callMethod(context, "write", this);

        return getRuntime().getNil();
    }

    /** rb_obj_tainted
     *
     *  call-seq:
     *     obj.tainted?    => true or false
     *  
     *  Returns <code>true</code> if the object is tainted.
     *
     */
    @JRubyMethod(name = "tainted?")
    public RubyBoolean tainted_p() {
        return getRuntime().newBoolean(isTaint());
    }

    /** rb_obj_taint
     *
     *  call-seq:
     *     obj.taint -> obj
     *  
     *  Marks <i>obj</i> as tainted---if the <code>$SAFE</code> level is
     *  set appropriately, many method calls which might alter the running
     *  programs environment will refuse to accept tainted strings.
     */
    @JRubyMethod(name = "taint")
    public IRubyObject taint() {
        taint(getRuntime());
        return this;
    }
    
    private void taint(Ruby runtime) {
        runtime.secure(4);
        if (!isTaint()) {
        	testFrozen("object");
            setTaint(true);
        }
    }

    /** rb_obj_untaint
     *
     *  call-seq:
     *     obj.untaint    => obj
     *  
     *  Removes the taint from <i>obj</i>.
     * 
     *  Only callable in if more secure than 3.
     */
    @JRubyMethod(name = "untaint")
    public IRubyObject untaint() {
        getRuntime().secure(3);
        if (isTaint()) {
        	testFrozen("object");
            setTaint(false);
        }
        return this;
    }

    /** rb_obj_freeze
     *
     *  call-seq:
     *     obj.freeze    => obj
     *  
     *  Prevents further modifications to <i>obj</i>. A
     *  <code>TypeError</code> will be raised if modification is attempted.
     *  There is no way to unfreeze a frozen object. See also
     *  <code>Object#frozen?</code>.
     *     
     *     a = [ "a", "b", "c" ]
     *     a.freeze
     *     a << "z"
     *     
     *  <em>produces:</em>
     *     
     *     prog.rb:3:in `<<': can't modify frozen array (TypeError)
     *     	from prog.rb:3
     */
    @JRubyMethod(name = "freeze")
    public IRubyObject freeze() {
        if ((flags & FROZEN_F) == 0) {
            if (getRuntime().getSafeLevel() >= 4 && isTaint()) {
                throw getRuntime().newSecurityError("Insecure: can't freeze object");
            }
            flags |= FROZEN_F;
        }
        return this;
    }

    /** rb_obj_frozen_p
     *
     *  call-seq:
     *     obj.frozen?    => true or false
     *  
     *  Returns the freeze status of <i>obj</i>.
     *     
     *     a = [ "a", "b", "c" ]
     *     a.freeze    #=> ["a", "b", "c"]
     *     a.frozen?   #=> true
     */    
    @JRubyMethod(name = "frozen?")
    public RubyBoolean frozen_p() {
        return getRuntime().newBoolean(isFrozen());
    }

    /** inspect_obj
     * 
     * The internal helper method that takes care of the part of the
     * inspection that inspects instance variables.
     */
    private StringBuilder inspectObj(StringBuilder part) {
        ThreadContext context = getRuntime().getCurrentContext();
        String sep = "";
        
        for (Variable<IRubyObject> ivar : getInstanceVariableList()) {
            part.append(sep).append(" ").append(ivar.getName()).append("=");
            part.append(ivar.getValue().callMethod(context, "inspect"));
            sep = ",";
        }
        part.append(">");
        return part;
    }

    /** rb_inspect
     * 
     * The internal helper that ensures a RubyString instance is returned
     * so dangerous casting can be omitted
     * Prefered over callMethod(context, "inspect") 
     */
    static RubyString inspect(ThreadContext context, IRubyObject object) {
        return RubyString.objAsString(context, object.callMethod(context, "inspect"));
    }    

    /** rb_obj_inspect
     *
     *  call-seq:
     *     obj.inspect   => string
     *  
     *  Returns a string containing a human-readable representation of
     *  <i>obj</i>. If not overridden, uses the <code>to_s</code> method to
     *  generate the string.
     *     
     *     [ 1, 2, 3..4, 'five' ].inspect   #=> "[1, 2, 3..4, \"five\"]"
     *     Time.new.inspect                 #=> "Wed Apr 09 08:54:39 CDT 2003"
     */
    @JRubyMethod(name = "inspect")
    public IRubyObject inspect() {
        Ruby runtime = getRuntime();
        if ((!isImmediate()) &&
                // TYPE(obj) == T_OBJECT
                !(this instanceof RubyClass) &&
                this != runtime.getObject() &&
                this != runtime.getModule() &&
                !(this instanceof RubyModule) &&
                // TODO: should have #hasInstanceVariables method, though
                // this will work here:
                hasVariables()) {

            StringBuilder part = new StringBuilder();
            String cname = getMetaClass().getRealClass().getName();
            part.append("#<").append(cname).append(":0x");
            part.append(Integer.toHexString(System.identityHashCode(this)));

            if (runtime.isInspecting(this)) {
                /* 6:tags 16:addr 1:eos */
                part.append(" ...>");
                return runtime.newString(part.toString());
            }
            try {
                runtime.registerInspecting(this);
                return runtime.newString(inspectObj(part).toString());
            } finally {
                runtime.unregisterInspecting(this);
            }
        }

        if (isNil()) return RubyNil.inspect(this);
        return RuntimeHelpers.invoke(runtime.getCurrentContext(), this, MethodIndex.TO_S, "to_s", IRubyObject.NULL_ARRAY);
    }

    /** rb_obj_is_instance_of
     *
     *  call-seq:
     *     obj.instance_of?(class)    => true or false
     *  
     *  Returns <code>true</code> if <i>obj</i> is an instance of the given
     *  class. See also <code>Object#kind_of?</code>.
     */
    @JRubyMethod(name = "instance_of?", required = 1)
    public RubyBoolean instance_of_p(IRubyObject type) {
        if (type() == type) {
            return getRuntime().getTrue();
        } else {
            if (!(type instanceof RubyModule)) {
                throw getRuntime().newTypeError("class or module required");
            }
            return getRuntime().getFalse();
        }
    }


    /** rb_obj_is_kind_of
     *
     *  call-seq:
     *     obj.is_a?(class)       => true or false
     *     obj.kind_of?(class)    => true or false
     *  
     *  Returns <code>true</code> if <i>class</i> is the class of
     *  <i>obj</i>, or if <i>class</i> is one of the superclasses of
     *  <i>obj</i> or modules included in <i>obj</i>.
     *     
     *     module M;    end
     *     class A
     *       include M
     *     end
     *     class B < A; end
     *     class C < B; end
     *     b = B.new
     *     b.instance_of? A   #=> false
     *     b.instance_of? B   #=> true
     *     b.instance_of? C   #=> false
     *     b.instance_of? M   #=> false
     *     b.kind_of? A       #=> true
     *     b.kind_of? B       #=> true
     *     b.kind_of? C       #=> false
     *     b.kind_of? M       #=> true
     */
    @JRubyMethod(name = {"kind_of?", "is_a?"}, required = 1)
    public RubyBoolean kind_of_p(IRubyObject type) {
        // TODO: Generalize this type-checking code into IRubyObject helper.
        if (!(type instanceof RubyModule)) {
            // TODO: newTypeError does not offer enough for ruby error string...
            throw getRuntime().newTypeError("class or module required");
        }

        return getRuntime().newBoolean(((RubyModule)type).isInstance(this));
    }

    /** rb_obj_methods
     *
     *  call-seq:
     *     obj.methods    => array
     *  
     *  Returns a list of the names of methods publicly accessible in
     *  <i>obj</i>. This will include all the methods accessible in
     *  <i>obj</i>'s ancestors.
     *     
     *     class Klass
     *       def kMethod()
     *       end
     *     end
     *     k = Klass.new
     *     k.methods[0..9]    #=> ["kMethod", "freeze", "nil?", "is_a?", 
     *                             "class", "instance_variable_set",
     *                              "methods", "extend", "__send__", "instance_eval"]
     *     k.methods.length   #=> 42
     */
    @JRubyMethod(name = "methods", optional = 1)
    public IRubyObject methods(IRubyObject[] args) {
        boolean all = true;
        if (args.length == 1) {
            all = args[0].isTrue();
        }

        RubyArray singletonMethods = null;
        if (getMetaClass().isSingleton()) {
            singletonMethods =
                getMetaClass().instance_methods(new IRubyObject[] {getRuntime().getFalse()});
            if (all) {
                singletonMethods.concat(getMetaClass().getSuperClass().instance_methods(new IRubyObject[] {getRuntime().getTrue()}));
            }
        } else {
            if (all) {
                singletonMethods = getMetaClass().instance_methods(new IRubyObject[] {getRuntime().getTrue()});
            } else {
                singletonMethods = getRuntime().newEmptyArray();
            }
        }
        
        return singletonMethods;
    }

    /** rb_obj_public_methods
     *
     *  call-seq:
     *     obj.public_methods(all=true)   => array
     *  
     *  Returns the list of public methods accessible to <i>obj</i>. If
     *  the <i>all</i> parameter is set to <code>false</code>, only those methods
     *  in the receiver will be listed.
     */
    @JRubyMethod(name = "public_methods", optional = 1)
    public IRubyObject public_methods(IRubyObject[] args) {
        if (args.length == 0) {
            args = new IRubyObject[] { getRuntime().getTrue() };
        }

        return getMetaClass().public_instance_methods(args);
    }

    /** rb_obj_protected_methods
     *
     *  call-seq:
     *     obj.protected_methods(all=true)   => array
     *  
     *  Returns the list of protected methods accessible to <i>obj</i>. If
     *  the <i>all</i> parameter is set to <code>false</code>, only those methods
     *  in the receiver will be listed.
     *
     *  Internally this implementation uses the 
     *  {@link RubyModule#protected_instance_methods} method.
     */
    @JRubyMethod(name = "protected_methods", optional = 1)
    public IRubyObject protected_methods(IRubyObject[] args) {
        if (args.length == 0) {
            args = new IRubyObject[] { getRuntime().getTrue() };
        }

        return getMetaClass().protected_instance_methods(args);
    }

    /** rb_obj_private_methods
     *
     *  call-seq:
     *     obj.private_methods(all=true)   => array
     *  
     *  Returns the list of private methods accessible to <i>obj</i>. If
     *  the <i>all</i> parameter is set to <code>false</code>, only those methods
     *  in the receiver will be listed.
     *
     *  Internally this implementation uses the 
     *  {@link RubyModule#private_instance_methods} method.
     */
    @JRubyMethod(name = "private_methods", optional = 1)
    public IRubyObject private_methods(IRubyObject[] args) {
        if (args.length == 0) {
            args = new IRubyObject[] { getRuntime().getTrue() };
        }

        return getMetaClass().private_instance_methods(args);
    }

    /** rb_obj_singleton_methods
     *
     *  call-seq:
     *     obj.singleton_methods(all=true)    => array
     *  
     *  Returns an array of the names of singleton methods for <i>obj</i>.
     *  If the optional <i>all</i> parameter is true, the list will include
     *  methods in modules included in <i>obj</i>.
     *     
     *     module Other
     *       def three() end
     *     end
     *     
     *     class Single
     *       def Single.four() end
     *     end
     *     
     *     a = Single.new
     *     
     *     def a.one()
     *     end
     *     
     *     class << a
     *       include Other
     *       def two()
     *       end
     *     end
     *     
     *     Single.singleton_methods    #=> ["four"]
     *     a.singleton_methods(false)  #=> ["two", "one"]
     *     a.singleton_methods         #=> ["two", "one", "three"]
     */
    // TODO: This is almost RubyModule#instance_methods on the metaClass.  Perhaps refactor.
    @JRubyMethod(name = "singleton_methods", optional = 1)
    public RubyArray singleton_methods(IRubyObject[] args) {
        boolean all = true;
        if(args.length == 1) {
            all = args[0].isTrue();
        }

        RubyArray singletonMethods = null;
        if (getMetaClass().isSingleton()) {
            singletonMethods =
                    getMetaClass().instance_methods(new IRubyObject[] {getRuntime().getFalse()});
            if (all) {
                RubyClass superClass = getMetaClass().getSuperClass();
                while (superClass.isIncluded()) {
                    singletonMethods.concat(superClass.instance_methods(new IRubyObject[] {getRuntime().getFalse()}));
                    superClass = superClass.getSuperClass();
                }
            }
        } else {
            singletonMethods = getRuntime().newEmptyArray();
        }
        
        return singletonMethods;
    }

    /** rb_obj_method
     *
     *  call-seq:
     *     obj.method(sym)    => method
     *  
     *  Looks up the named method as a receiver in <i>obj</i>, returning a
     *  <code>Method</code> object (or raising <code>NameError</code>). The
     *  <code>Method</code> object acts as a closure in <i>obj</i>'s object
     *  instance, so instance variables and the value of <code>self</code>
     *  remain available.
     *     
     *     class Demo
     *       def initialize(n)
     *         @iv = n
     *       end
     *       def hello()
     *         "Hello, @iv = #{@iv}"
     *       end
     *     end
     *     
     *     k = Demo.new(99)
     *     m = k.method(:hello)
     *     m.call   #=> "Hello, @iv = 99"
     *     
     *     l = Demo.new('Fred')
     *     m = l.method("hello")
     *     m.call   #=> "Hello, @iv = Fred"
     */
    @JRubyMethod(name = "method", required = 1)
    public IRubyObject method(IRubyObject symbol) {
        return getMetaClass().newMethod(this, symbol.asJavaString(), true);
    }

    /**
     * Internal method that helps to convert any object into the
     * format of a class name and a hex string inside of #<>.
     */
    public IRubyObject anyToString() {
        String cname = getMetaClass().getRealClass().getName();
        /* 6:tags 16:addr 1:eos */
        RubyString str = getRuntime().newString("#<" + cname + ":0x" + Integer.toHexString(System.identityHashCode(this)) + ">");
        str.setTaint(isTaint());
        return str;
    }

    /** rb_any_to_s
     *
     *  call-seq:
     *     obj.to_s    => string
     *  
     *  Returns a string representing <i>obj</i>. The default
     *  <code>to_s</code> prints the object's class and an encoding of the
     *  object id. As a special case, the top-level object that is the
     *  initial execution context of Ruby programs returns ``main.''
     */
    @JRubyMethod(name = "to_s")
    public IRubyObject to_s() {
    	return anyToString();
    }

    /** rb_any_to_a
     *
     *  call-seq:
     *     obj.to_a -> anArray
     *  
     *  Returns an array representation of <i>obj</i>. For objects of class
     *  <code>Object</code> and others that don't explicitly override the
     *  method, the return value is an array containing <code>self</code>. 
     *  However, this latter behavior will soon be obsolete.
     *     
     *     self.to_a       #=> -:1: warning: default `to_a' will be obsolete
     *     "hello".to_a    #=> ["hello"]
     *     Time.new.to_a   #=> [39, 54, 8, 9, 4, 2003, 3, 99, true, "CDT"]
     * 
     *  The default to_a method is deprecated.
     */    
    @JRubyMethod(name = "to_a", visibility = Visibility.PUBLIC)
    public RubyArray to_a() {
        getRuntime().getWarnings().warn(ID.DEPRECATED_METHOD, "default 'to_a' will be obsolete", "to_a");
        return getRuntime().newArray(this);
    }

    /** rb_obj_instance_eval
     *
     *  call-seq:
     *     obj.instance_eval(string [, filename [, lineno]] )   => obj
     *     obj.instance_eval {| | block }                       => obj
     *  
     *  Evaluates a string containing Ruby source code, or the given block,
     *  within the context of the receiver (_obj_). In order to set the
     *  context, the variable +self+ is set to _obj_ while
     *  the code is executing, giving the code access to _obj_'s
     *  instance variables. In the version of <code>instance_eval</code>
     *  that takes a +String+, the optional second and third
     *  parameters supply a filename and starting line number that are used
     *  when reporting compilation errors.
     *     
     *     class Klass
     *       def initialize
     *         @secret = 99
     *       end
     *     end
     *     k = Klass.new
     *     k.instance_eval { @secret }   #=> 99
     */
    @JRubyMethod(name = "instance_eval", frame = true)
    public IRubyObject instance_eval(ThreadContext context, Block block) {
        return specificEval(context, getInstanceEvalClass(), block);
    }
    @JRubyMethod(name = "instance_eval", frame = true)
    public IRubyObject instance_eval(ThreadContext context, IRubyObject arg0, Block block) {
        return specificEval(context, getInstanceEvalClass(), arg0, block);
    }
    @JRubyMethod(name = "instance_eval", frame = true)
    public IRubyObject instance_eval(ThreadContext context, IRubyObject arg0, IRubyObject arg1, Block block) {
        return specificEval(context, getInstanceEvalClass(), arg0, arg1, block);
    }
    @JRubyMethod(name = "instance_eval", frame = true)
    public IRubyObject instance_eval(ThreadContext context, IRubyObject arg0, IRubyObject arg1, IRubyObject arg2, Block block) {
        return specificEval(context, getInstanceEvalClass(), arg0, arg1, arg2, block);
    }
    @Deprecated
    public IRubyObject instance_eval(ThreadContext context, IRubyObject[] args, Block block) {
        RubyModule klazz;
        
        if (isImmediate()) {
            // Ruby uses Qnil here, we use "dummy" because we need a class
            klazz = getRuntime().getDummy();
        } else {
            klazz = getSingletonClass();
        }
        
        return specificEval(context, klazz, args, block);
    }
    
    private RubyModule getInstanceEvalClass() {
        if (isImmediate()) {
            // Ruby uses Qnil here, we use "dummy" because we need a class
            return getRuntime().getDummy();
        } else {
            return getSingletonClass();
        }
    }

    /** rb_obj_instance_exec
     *
     *  call-seq:
     *     obj.instance_exec(arg...) {|var...| block }                       => obj
     *
     *  Executes the given block within the context of the receiver
     *  (_obj_). In order to set the context, the variable +self+ is set
     *  to _obj_ while the code is executing, giving the code access to
     *  _obj_'s instance variables.  Arguments are passed as block parameters.
     *
     *     class Klass
     *       def initialize
     *         @secret = 99
     *       end
     *     end
     *     k = Klass.new
     *     k.instance_exec(5) {|x| @secret+x }   #=> 104
     */
    @JRubyMethod(name = "instance_exec", optional = 3, frame = true)
    public IRubyObject instance_exec(ThreadContext context, IRubyObject[] args, Block block) {
        if (!block.isGiven()) {
            throw getRuntime().newArgumentError("block not supplied");
        }

        RubyModule klazz;
        if (isImmediate()) {
            // Ruby uses Qnil here, we use "dummy" because we need a class
            klazz = getRuntime().getDummy();          
        } else {
            klazz = getSingletonClass();
        }

        return yieldUnder(context, klazz, args, block);
    }

    /** rb_obj_extend
     *
     *  call-seq:
     *     obj.extend(module, ...)    => obj
     *  
     *  Adds to _obj_ the instance methods from each module given as a
     *  parameter.
     *     
     *     module Mod
     *       def hello
     *         "Hello from Mod.\n"
     *       end
     *     end
     *     
     *     class Klass
     *       def hello
     *         "Hello from Klass.\n"
     *       end
     *     end
     *     
     *     k = Klass.new
     *     k.hello         #=> "Hello from Klass.\n"
     *     k.extend(Mod)   #=> #<Klass:0x401b3bc8>
     *     k.hello         #=> "Hello from Mod.\n"
     */
    @JRubyMethod(name = "extend", required = 1, rest = true)
    public IRubyObject extend(IRubyObject[] args) {
        // Make sure all arguments are modules before calling the callbacks
        for (int i = 0; i < args.length; i++) {
            if (!args[i].isModule()) throw getRuntime().newTypeError(args[i], getRuntime().getModule()); 
        }

        // MRI extends in order from last to first
        for (int i = args.length - 1; i >= 0; i--) {
            args[i].callMethod(getRuntime().getCurrentContext(), "extend_object", this);
            args[i].callMethod(getRuntime().getCurrentContext(), "extended", this);
        }
        return this;
    }

    /** rb_obj_dummy
     *
     * Default initialize method. This one gets defined in some other
     * place as a Ruby method.
     */
    public IRubyObject initialize() {
        return getRuntime().getNil();
    }

    /** rb_f_send
     *
     * send( aSymbol  [, args  ]*   ) -> anObject
     *
     * Invokes the method identified by aSymbol, passing it any arguments
     * specified. You can use __send__ if the name send clashes with an
     * existing method in this object.
     *
     * <pre>
     * class Klass
     *   def hello(*args)
     *     "Hello " + args.join(' ')
     *   end
     * end
     *
     * k = Klass.new
     * k.send :hello, "gentle", "readers"
     * </pre>
     *
     * @return the result of invoking the method identified by aSymbol.
     */
    @JRubyMethod(name = {"send", "__send__"}, required = 1, rest = true)
    public IRubyObject send(ThreadContext context, IRubyObject[] args, Block block) {
        String name = args[0].asJavaString();
        int newArgsLength = args.length - 1;

        IRubyObject[] newArgs;
        if (newArgsLength == 0) {
            newArgs = IRubyObject.NULL_ARRAY;
        } else {
            newArgs = new IRubyObject[newArgsLength];
            System.arraycopy(args, 1, newArgs, 0, newArgs.length);
        }

        RubyModule rubyClass = getMetaClass();
        DynamicMethod method = rubyClass.searchMethod(name);

        // send doesn't check visibility
        if (method.isUndefined()) {
            return RuntimeHelpers.callMethodMissing(context, this, method, name, newArgs, context.getFrameSelf(), CallType.FUNCTIONAL, block);
        }

        return method.call(context, this, rubyClass, name, newArgs, block);
    }

    /** rb_false
     *
     * call_seq:
     *   nil.nil?               => true
     *   <anything_else>.nil?   => false
     *
     * Only the object <i>nil</i> responds <code>true</code> to <code>nil?</code>.
     */
    @JRubyMethod(name = "nil?")
    public IRubyObject nil_p() {
    	return getRuntime().getFalse();
    }

    /** rb_obj_pattern_match
     *
     *  call-seq:
     *     obj =~ other  => false
     *  
     *  Pattern Match---Overridden by descendents (notably
     *  <code>Regexp</code> and <code>String</code>) to provide meaningful
     *  pattern-match semantics.
     */    
    @JRubyMethod(name = "=~", required = 1)
    public IRubyObject op_match(ThreadContext context, IRubyObject arg) {
    	return getRuntime().getFalse();
    }
    
    public IRubyObject to_java() {
        throw getRuntime().newTypeError(getMetaClass().getBaseName() + " cannot coerce to a Java type.");
    }

    public IRubyObject as(Class javaClass) {
        throw getRuntime().newTypeError(getMetaClass().getBaseName() + " cannot coerce to a Java type.");
    }
    
    /**
     * @see org.jruby.runtime.builtin.IRubyObject#getType()
     */
    public RubyClass getType() {
        return type();
    }

    /**
     * @see org.jruby.runtime.builtin.IRubyObject#dataWrapStruct()
     */
    public synchronized void dataWrapStruct(Object obj) {
        this.dataStruct = obj;
    }

    /**
     * @see org.jruby.runtime.builtin.IRubyObject#dataGetStruct()
     */
    public synchronized Object dataGetStruct() {
        return dataStruct;
    }

    /**
     * Adds the specified object as a finalizer for this object.
     */ 
    public void addFinalizer(IRubyObject finalizer) {
        if (this.finalizer == null) {
            this.finalizer = new Finalizer(getRuntime().getObjectSpace().idOf(this));
            getRuntime().addFinalizer(this.finalizer);
        }
        this.finalizer.addFinalizer(finalizer);
    }

    /**
     * Remove all the finalizers for this object.
     */
    public void removeFinalizers() {
        if (finalizer != null) {
            finalizer.removeFinalizers();
            finalizer = null;
            getRuntime().removeFinalizer(this.finalizer);
        }
    }


    //
    // INSTANCE VARIABLE RUBY METHODS
    //
    
    /** rb_obj_ivar_defined
     *
     *  call-seq:
     *     obj.instance_variable_defined?(symbol)    => true or false
     *
     *  Returns <code>true</code> if the given instance variable is
     *  defined in <i>obj</i>.
     *
     *     class Fred
     *       def initialize(p1, p2)
     *         @a, @b = p1, p2
     *       end
     *     end
     *     fred = Fred.new('cat', 99)
     *     fred.instance_variable_defined?(:@a)    #=> true
     *     fred.instance_variable_defined?("@b")   #=> true
     *     fred.instance_variable_defined?("@c")   #=> false
     */
    @JRubyMethod(name = "instance_variable_defined?", required = 1)
    public IRubyObject instance_variable_defined_p(IRubyObject name) {
        if (variableTableContains(validateInstanceVariable(name.asJavaString()))) {
            return getRuntime().getTrue();
        }
        return getRuntime().getFalse();
    }

    /** rb_obj_ivar_get
     *
     *  call-seq:
     *     obj.instance_variable_get(symbol)    => obj
     *
     *  Returns the value of the given instance variable, or nil if the
     *  instance variable is not set. The <code>@</code> part of the
     *  variable name should be included for regular instance
     *  variables. Throws a <code>NameError</code> exception if the
     *  supplied symbol is not valid as an instance variable name.
     *     
     *     class Fred
     *       def initialize(p1, p2)
     *         @a, @b = p1, p2
     *       end
     *     end
     *     fred = Fred.new('cat', 99)
     *     fred.instance_variable_get(:@a)    #=> "cat"
     *     fred.instance_variable_get("@b")   #=> 99
     */
    @JRubyMethod(name = "instance_variable_get", required = 1)
    public IRubyObject instance_variable_get(IRubyObject name) {
        IRubyObject value;
        if ((value = variableTableFetch(validateInstanceVariable(name.asJavaString()))) != null) {
            return value;
        }
        return getRuntime().getNil();
    }

    /** rb_obj_ivar_set
     *
     *  call-seq:
     *     obj.instance_variable_set(symbol, obj)    => obj
     *  
     *  Sets the instance variable names by <i>symbol</i> to
     *  <i>object</i>, thereby frustrating the efforts of the class's
     *  author to attempt to provide proper encapsulation. The variable
     *  did not have to exist prior to this call.
     *     
     *     class Fred
     *       def initialize(p1, p2)
     *         @a, @b = p1, p2
     *       end
     *     end
     *     fred = Fred.new('cat', 99)
     *     fred.instance_variable_set(:@a, 'dog')   #=> "dog"
     *     fred.instance_variable_set(:@c, 'cat')   #=> "cat"
     *     fred.inspect                             #=> "#<Fred:0x401b3da8 @a=\"dog\", @b=99, @c=\"cat\">"
     */
    @JRubyMethod(name = "instance_variable_set", required = 2)
    public IRubyObject instance_variable_set(IRubyObject name, IRubyObject value) {
        ensureInstanceVariablesSettable();
        return variableTableStore(validateInstanceVariable(name.asJavaString()), value);
    }

    /** rb_obj_remove_instance_variable
     *
     *  call-seq:
     *     obj.remove_instance_variable(symbol)    => obj
     *  
     *  Removes the named instance variable from <i>obj</i>, returning that
     *  variable's value.
     *     
     *     class Dummy
     *       attr_reader :var
     *       def initialize
     *         @var = 99
     *       end
     *       def remove
     *         remove_instance_variable(:@var)
     *       end
     *     end
     *     d = Dummy.new
     *     d.var      #=> 99
     *     d.remove   #=> 99
     *     d.var      #=> nil
     */
    @JRubyMethod(name = "remove_instance_variable", required = 1, frame = true, visibility = Visibility.PRIVATE)
    public IRubyObject remove_instance_variable(IRubyObject name, Block block) {
        ensureInstanceVariablesSettable();
        IRubyObject value;
        if ((value = variableTableRemove(validateInstanceVariable(name.asJavaString()))) != null) {
            return value;
        }
        throw getRuntime().newNameError("instance variable " + name.asJavaString() + " not defined", name.asJavaString());
    }

    /** rb_obj_instance_variables
     *
     *  call-seq:
     *     obj.instance_variables    => array
     *  
     *  Returns an array of instance variable names for the receiver. Note
     *  that simply defining an accessor does not create the corresponding
     *  instance variable.
     *     
     *     class Fred
     *       attr_accessor :a1
     *       def initialize
     *         @iv = 3
     *       end
     *     end
     *     Fred.new.instance_variables   #=> ["@iv"]
     */    
    @JRubyMethod(name = "instance_variables")
    public RubyArray instance_variables() {
        Ruby runtime = getRuntime();
        List<String> nameList = getInstanceVariableNameList();

        RubyArray array = runtime.newArray(nameList.size());

        for (String name : nameList) {
            array.append(runtime.newString(name));
        }

        return array;
    }

    //
    // INSTANCE VARIABLE API METHODS
    //

    /**
     * Dummy method to avoid a cast, and to avoid polluting the
     * IRubyObject interface with all the instance variable management
     * methods.
     */    
    public InstanceVariables getInstanceVariables() {
        return this;
    }
    
    /**
     * @see org.jruby.runtime.builtin.InstanceVariables#hasInstanceVariable
     */
    public boolean hasInstanceVariable(String name) {
        assert IdUtil.isInstanceVariable(name);
        return variableTableContains(name);
    }
    
    /**
     * @see org.jruby.runtime.builtin.InstanceVariables#fastHasInstanceVariable
     */
    public boolean fastHasInstanceVariable(String internedName) {
        assert IdUtil.isInstanceVariable(internedName);
        return variableTableFastContains(internedName);
    }
    
    /**
     * @see org.jruby.runtime.builtin.InstanceVariables#getInstanceVariable
     */
    public IRubyObject getInstanceVariable(String name) {
        assert IdUtil.isInstanceVariable(name);
        return variableTableFetch(name);
    }
    
    /**
     * @see org.jruby.runtime.builtin.InstanceVariables#fastGetInstanceVariable
     */
    public IRubyObject fastGetInstanceVariable(String internedName) {
        assert IdUtil.isInstanceVariable(internedName);
        return variableTableFastFetch(internedName);
    }

    /** rb_iv_set / rb_ivar_set
    *
    * @see org.jruby.runtime.builtin.InstanceVariables#setInstanceVariable
    */
    public IRubyObject setInstanceVariable(String name, IRubyObject value) {
        assert IdUtil.isInstanceVariable(name) && value != null;
        ensureInstanceVariablesSettable();
        return variableTableStore(name, value);
    }

    /**
     * @see org.jruby.runtime.builtin.InstanceVariables#fastSetInstanceVariable
     */    
    public IRubyObject fastSetInstanceVariable(String internedName, IRubyObject value) {
        assert IdUtil.isInstanceVariable(internedName) && value != null;
        ensureInstanceVariablesSettable();
        return variableTableFastStore(internedName, value);
     }

    /**
     * @see org.jruby.runtime.builtin.InstanceVariables#removeInstanceVariable
     */    
    public IRubyObject removeInstanceVariable(String name) {
        assert IdUtil.isInstanceVariable(name);
        ensureInstanceVariablesSettable();
        return variableTableRemove(name);
    }

    /**
     * @see org.jruby.runtime.builtin.InstanceVariables#getInstanceVariableList
     */    
    public List<Variable<IRubyObject>> getInstanceVariableList() {
        VariableTableEntry[] table = variableTableGetTable();
        ArrayList<Variable<IRubyObject>> list = new ArrayList<Variable<IRubyObject>>();
        IRubyObject readValue;
        for (int i = table.length; --i >= 0; ) {
            for (VariableTableEntry e = table[i]; e != null; e = e.next) {
                if (IdUtil.isInstanceVariable(e.name)) {
                    if ((readValue = e.value) == null) readValue = variableTableReadLocked(e);
                    list.add(new VariableEntry<IRubyObject>(e.name, readValue));
                }
            }
        }
        return list;
    }

    /**
     * @see org.jruby.runtime.builtin.InstanceVariables#getInstanceVariableNameList
     */    
    public List<String> getInstanceVariableNameList() {
        VariableTableEntry[] table = variableTableGetTable();
        ArrayList<String> list = new ArrayList<String>();
        for (int i = table.length; --i >= 0; ) {
            for (VariableTableEntry e = table[i]; e != null; e = e.next) {
                if (IdUtil.isInstanceVariable(e.name)) {
                    list.add(e.name);
                }
            }
        }
        return list;
    }

    /**
     * The error message used when some one tries to modify an
     * instance variable in a high security setting.
     */
    protected static final String ERR_INSECURE_SET_INST_VAR  = "Insecure: can't modify instance variable";

    /**
     * Checks if the name parameter represents a legal instance variable name, and otherwise throws a Ruby NameError
     */
    protected String validateInstanceVariable(String name) {
        if (IdUtil.isValidInstanceVariableName(name)) {
            return name;
        }
        throw getRuntime().newNameError("`" + name + "' is not allowable as an instance variable name", name);
    }

    /**
     * Makes sure that instance variables can be set on this object,
     * including information about whether this object is frozen, or
     * tainted. Will throw a suitable exception in that case.
     */
    protected void ensureInstanceVariablesSettable() {
        if (!isFrozen() && (getRuntime().getSafeLevel() < 4 || isTaint())) {
            return;
        }
        
        if (getRuntime().getSafeLevel() >= 4 && !isTaint()) {
            throw getRuntime().newSecurityError(ERR_INSECURE_SET_INST_VAR);
        }
        if (isFrozen()) {
            if (this instanceof RubyModule) {
                throw getRuntime().newFrozenError("class/module ");
            } else {
                throw getRuntime().newFrozenError("");
            }
        }
    }

    //
    // INTERNAL VARIABLE METHODS
    //
    
    /**
     * Dummy method to avoid a cast, and to avoid polluting the
     * IRubyObject interface with all the instance variable management
     * methods.
     */    
    public InternalVariables getInternalVariables() {
        return this;
    }

    /**
     * @see org.jruby.runtime.builtin.InternalVariables#hasInternalVariable
     */    
    public boolean hasInternalVariable(String name) {
        assert !isRubyVariable(name);
        return variableTableContains(name);
    }

    /**
     * @see org.jruby.runtime.builtin.InternalVariables#fastHasInternalVariable
     */    
    public boolean fastHasInternalVariable(String internedName) {
        assert !isRubyVariable(internedName);
        return variableTableFastContains(internedName);
    }

    /**
     * @see org.jruby.runtime.builtin.InternalVariables#getInternalVariable
     */    
    public IRubyObject getInternalVariable(String name) {
        assert !isRubyVariable(name);
        return variableTableFetch(name);
    }

    /**
     * @see org.jruby.runtime.builtin.InternalVariables#fastGetInternalVariable
     */    
    public IRubyObject fastGetInternalVariable(String internedName) {
        assert !isRubyVariable(internedName);
        return variableTableFastFetch(internedName);
    }

    /**
     * @see org.jruby.runtime.builtin.InternalVariables#setInternalVariable
     */    
    public void setInternalVariable(String name, IRubyObject value) {
        assert !isRubyVariable(name);
        variableTableStore(name, value);
    }

    /**
     * @see org.jruby.runtime.builtin.InternalVariables#fastSetInternalVariable
     */    
    public void fastSetInternalVariable(String internedName, IRubyObject value) {
        assert !isRubyVariable(internedName);
        variableTableFastStore(internedName, value);
    }

    /**
     * @see org.jruby.runtime.builtin.InternalVariables#removeInternalVariable
     */    
    public IRubyObject removeInternalVariable(String name) {
        assert !isRubyVariable(name);
        return variableTableRemove(name);
    }

    /**
     * Sync one variable table with another - this is used to make
     * rbClone work correctly.
     */
    public void syncVariables(List<Variable<IRubyObject>> variables) {
        variableTableSync(variables);
    }

    /**
     * @see org.jruby.runtime.builtin.InternalVariables#getInternalVariableList
     */    
    public List<Variable<IRubyObject>> getInternalVariableList() {
        VariableTableEntry[] table = variableTableGetTable();
        ArrayList<Variable<IRubyObject>> list = new ArrayList<Variable<IRubyObject>>();
        IRubyObject readValue;
        for (int i = table.length; --i >= 0; ) {
            for (VariableTableEntry e = table[i]; e != null; e = e.next) {
                if (!isRubyVariable(e.name)) {
                    if ((readValue = e.value) == null) readValue = variableTableReadLocked(e);
                    list.add(new VariableEntry<IRubyObject>(e.name, readValue));
                }
            }
        }
        return list;
    }

    
    //
    // COMMON VARIABLE METHODS
    //

    /**
     * Returns true if object has any variables, defined as:
     * <ul>
     * <li> instance variables
     * <li> class variables
     * <li> constants
     * <li> internal variables, such as those used when marshaling Ranges and Exceptions
     * </ul>
     * @return true if object has any variables, else false
     */
    public boolean hasVariables() {
        return variableTableGetSize() > 0;
    }

    /**
     * Returns the amount of instance variables, class variables,
     * constants and internal variables this object has.
     */
    public int getVariableCount() {
        return variableTableGetSize();
    }
    
    /**
     * Gets a list of all variables in this object.
     */
    // TODO: must override in RubyModule to pick up constants
    public List<Variable<IRubyObject>> getVariableList() {
        VariableTableEntry[] table = variableTableGetTable();
        ArrayList<Variable<IRubyObject>> list = new ArrayList<Variable<IRubyObject>>();
        IRubyObject readValue;
        for (int i = table.length; --i >= 0; ) {
            for (VariableTableEntry e = table[i]; e != null; e = e.next) {
                if ((readValue = e.value) == null) readValue = variableTableReadLocked(e);
                list.add(new VariableEntry<IRubyObject>(e.name, readValue));
            }
        }
        return list;
    }

    /**
     * Gets a name list of all variables in this object.
     */
   // TODO: must override in RubyModule to pick up constants
   public List<String> getVariableNameList() {
        VariableTableEntry[] table = variableTableGetTable();
        ArrayList<String> list = new ArrayList<String>();
        for (int i = table.length; --i >= 0; ) {
            for (VariableTableEntry e = table[i]; e != null; e = e.next) {
                list.add(e.name);
            }
        }
        return list;
    }
    
    /**
     * Gets internal access to the getmap for variables.
     */
    @SuppressWarnings("unchecked")
    @Deprecated // born deprecated
    public Map getVariableMap() {
        return variableTableGetMap();
    }

    /**
     * Check the syntax of a Ruby variable, including that it's longer
     * than zero characters, and starts with either an @ or a capital
     * letter.
     */
    // FIXME: this should go somewhere more generic -- maybe IdUtil
    protected static final boolean isRubyVariable(String name) {
        char c;
        return name.length() > 0 && ((c = name.charAt(0)) == '@' || (c <= 'Z' && c >= 'A'));
    }
    
    //
    // VARIABLE TABLE METHODS, ETC.
    //
    
    protected static final int VARIABLE_TABLE_DEFAULT_CAPACITY = 8; // MUST be power of 2!
    protected static final int VARIABLE_TABLE_MAXIMUM_CAPACITY = 1 << 30;
    protected static final float VARIABLE_TABLE_LOAD_FACTOR = 0.75f;
    protected static final VariableTableEntry[] VARIABLE_TABLE_EMPTY_TABLE = new VariableTableEntry[0];

    /**
     * Every entry in the variable map is represented by an instance
     * of this class.
     */
    protected static final class VariableTableEntry {
        final int hash;
        final String name;
        volatile IRubyObject value;
        final VariableTableEntry next;
        
        VariableTableEntry(int hash, String name, IRubyObject value, VariableTableEntry next) {
            assert name == name.intern() : name + " is not interned";
            this.hash = hash;
            this.name = name;
            this.value = value;
            this.next = next;
        }
    }

    /**
     * Reads the value of the specified entry, locked on the current
     * object.
     */    
    protected synchronized IRubyObject variableTableReadLocked(VariableTableEntry entry) {
        return entry.value;
    }

    /**
     * Checks if the variable table contains a variable of the
     * specified name.
     */
    protected boolean variableTableContains(String name) {
        VariableTableEntry[] table;
        if ((table = variableTable) != null) {
            int hash = name.hashCode();
            for (VariableTableEntry e = table[hash & (table.length - 1)]; e != null; e = e.next) {
                if (hash == e.hash && name.equals(e.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if the variable table contains the the variable of the
     * specified name, where the precondition is that the name must be
     * an interned Java String.
     */
    protected boolean variableTableFastContains(String internedName) {
        assert internedName == internedName.intern() : internedName + " not interned";
        VariableTableEntry[] table;
        if ((table = variableTable) != null) {
            for (VariableTableEntry e = table[internedName.hashCode() & (table.length - 1)]; e != null; e = e.next) {
                if (internedName == e.name) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Fetch an object from the variable table based on the name.
     *
     * @return the object or null if not found
     */
    protected IRubyObject variableTableFetch(String name) {
        VariableTableEntry[] table;
        IRubyObject readValue;
        if ((table = variableTable) != null) {
            int hash = name.hashCode();
            for (VariableTableEntry e = table[hash & (table.length - 1)]; e != null; e = e.next) {
                if (hash == e.hash && name.equals(e.name)) {
                    if ((readValue = e.value) != null) return readValue;
                    return variableTableReadLocked(e);
                }
            }
        }
        return null;
    }

    /**
     * Fetch an object from the variable table based on the name,
     * where the name must be an interned Java String.
     *
     * @return the object or null if not found
     */
    protected IRubyObject variableTableFastFetch(String internedName) {
        assert internedName == internedName.intern() : internedName + " not interned";
        VariableTableEntry[] table;
        IRubyObject readValue;
        if ((table = variableTable) != null) {
            for (VariableTableEntry e = table[internedName.hashCode() & (table.length - 1)]; e != null; e = e.next) {
                if (internedName == e.name) {
                    if ((readValue = e.value) != null) return readValue;
                    return variableTableReadLocked(e);
                }
            }
        }
        return null;
    }

    /**
     * Store a value in the variable store under the specific name.
     */
    protected IRubyObject variableTableStore(String name, IRubyObject value) {
        int hash = name.hashCode();
        synchronized(this) {
            VariableTableEntry[] table;
            VariableTableEntry e;
            if ((table = variableTable) == null) {
                table =  new VariableTableEntry[VARIABLE_TABLE_DEFAULT_CAPACITY];
                e = new VariableTableEntry(hash, name.intern(), value, null);
                table[hash & (VARIABLE_TABLE_DEFAULT_CAPACITY - 1)] = e;
                variableTableThreshold = (int)(VARIABLE_TABLE_DEFAULT_CAPACITY * VARIABLE_TABLE_LOAD_FACTOR);
                variableTableSize = 1;
                variableTable = table;
                return value;
            }
            int potentialNewSize;
            if ((potentialNewSize = variableTableSize + 1) > variableTableThreshold) {
                table = variableTableRehash();
            }
            int index;
            for (e = table[index = hash & (table.length - 1)]; e != null; e = e.next) {
                if (hash == e.hash && name.equals(e.name)) {
                    e.value = value;
                    return value;
                }
            }
            e = new VariableTableEntry(hash, name.intern(), value, table[index]);
            table[index] = e;
            variableTableSize = potentialNewSize;
            variableTable = table; // write-volatile
        }
        return value;
    }

    /**
     * Will store the value under the specified name, where the name
     * needs to be an interned Java String.
     */
    protected IRubyObject variableTableFastStore(String internedName, IRubyObject value) {
        assert internedName == internedName.intern() : internedName + " not interned";
        int hash = internedName.hashCode();
        synchronized(this) {
            VariableTableEntry[] table;
            VariableTableEntry e;
            if ((table = variableTable) == null) {
                table =  new VariableTableEntry[VARIABLE_TABLE_DEFAULT_CAPACITY];
                e = new VariableTableEntry(hash, internedName, value, null);
                table[hash & (VARIABLE_TABLE_DEFAULT_CAPACITY - 1)] = e;
                variableTableThreshold = (int)(VARIABLE_TABLE_DEFAULT_CAPACITY * VARIABLE_TABLE_LOAD_FACTOR);
                variableTableSize = 1;
                variableTable = table;
                return value;
            }
            int potentialNewSize;
            if ((potentialNewSize = variableTableSize + 1) > variableTableThreshold) {
                table = variableTableRehash();
            }
            int index;
            for (e = table[index = hash & (table.length - 1)]; e != null; e = e.next) {
                if (internedName == e.name) {
                    e.value = value;
                    return value;
                }
            }
            e = new VariableTableEntry(hash, internedName, value, table[index]);
            table[index] = e;
            variableTableSize = potentialNewSize;
            variableTable = table; // write-volatile
        }
        return value;
    }

    /**
     * Removes the entry with the specified name from the variable
     * table, and returning the removed value.
     */
    protected IRubyObject variableTableRemove(String name) {
        synchronized(this) {
            VariableTableEntry[] table;
            if ((table = variableTable) != null) {
                int hash = name.hashCode();
                int index = hash & (table.length - 1);
                VariableTableEntry first = table[index];
                VariableTableEntry e;
                for (e = first; e != null; e = e.next) {
                    if (hash == e.hash && name.equals(e.name)) {
                        IRubyObject oldValue = e.value;
                        // All entries following removed node can stay
                        // in list, but all preceding ones need to be
                        // cloned.
                        VariableTableEntry newFirst = e.next;
                        for (VariableTableEntry p = first; p != e; p = p.next) {
                            newFirst = new VariableTableEntry(p.hash, p.name, p.value, newFirst);
                        }
                        table[index] = newFirst;
                        variableTableSize--;
                        variableTable = table; // write-volatile 
                        return oldValue;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Get the actual table used to save variable entries.
     */
    protected VariableTableEntry[] variableTableGetTable() {
        VariableTableEntry[] table;
        if ((table = variableTable) != null) {
            return table;
        }
        return VARIABLE_TABLE_EMPTY_TABLE;
    }

    /**
     * Get the size of the variable table.
     */
    protected int variableTableGetSize() {
        if (variableTable != null) {
            return variableTableSize;
        }
        return 0;
    }

    /**
     * Synchronize the variable table with the argument. In real terms
     * this means copy all entries into a newly allocated table.
     */
    protected void variableTableSync(List<Variable<IRubyObject>> vars) {
        synchronized(this) {
            variableTableSize = 0;
            variableTableThreshold = (int)(VARIABLE_TABLE_DEFAULT_CAPACITY * VARIABLE_TABLE_LOAD_FACTOR);
            variableTable =  new VariableTableEntry[VARIABLE_TABLE_DEFAULT_CAPACITY];
            for (Variable<IRubyObject> var : vars) {
                variableTableStore(var.getName(), var.getValue());
            }
        }
    }

    /**
     * Rehashes the variable table. Must be called from a synchronized
     * block.
     */
    // MUST be called from synchronized/locked block!
    // should only be called by variableTableStore/variableTableFastStore
    protected final VariableTableEntry[] variableTableRehash() {
        VariableTableEntry[] oldTable = variableTable;
        int oldCapacity;
        if ((oldCapacity = oldTable.length) >= VARIABLE_TABLE_MAXIMUM_CAPACITY) {
            return oldTable;
        }

        int newCapacity = oldCapacity << 1;
        VariableTableEntry[] newTable = new VariableTableEntry[newCapacity];
        variableTableThreshold = (int)(newCapacity * VARIABLE_TABLE_LOAD_FACTOR);
        int sizeMask = newCapacity - 1;
        VariableTableEntry e;
        for (int i = oldCapacity; --i >= 0; ) {
            // We need to guarantee that any existing reads of old Map can
            //  proceed. So we cannot yet null out each bin.
            e = oldTable[i];

            if (e != null) {
                VariableTableEntry next = e.next;
                int idx = e.hash & sizeMask;

                //  Single node on list
                if (next == null)
                    newTable[idx] = e;

                else {
                    // Reuse trailing consecutive sequence at same slot
                    VariableTableEntry lastRun = e;
                    int lastIdx = idx;
                    for (VariableTableEntry last = next;
                         last != null;
                         last = last.next) {
                        int k = last.hash & sizeMask;
                        if (k != lastIdx) {
                            lastIdx = k;
                            lastRun = last;
                        }
                    }
                    newTable[lastIdx] = lastRun;

                    // Clone all remaining nodes
                    for (VariableTableEntry p = e; p != lastRun; p = p.next) {
                        int k = p.hash & sizeMask;
                        VariableTableEntry m = new VariableTableEntry(p.hash, p.name, p.value, newTable[k]);
                        newTable[k] = m;
                    }
                }
            }
        }
        variableTable = newTable;
        return newTable;
    }

    /**
     * Method to help ease transition to new variables implementation.
     * Will likely be deprecated in the near future.
     */
    @SuppressWarnings("unchecked")
    protected Map variableTableGetMap() {
        HashMap map = new HashMap();
        VariableTableEntry[] table;
        IRubyObject readValue;
        if ((table = variableTable) != null) {
            for (int i = table.length; --i >= 0; ) {
                for (VariableTableEntry e = table[i]; e != null; e = e.next) {
                    if ((readValue = e.value) == null) readValue = variableTableReadLocked(e);
                    map.put(e.name, readValue);
                }
            }
        }
        return map;
    }

    /**
     * Method to help ease transition to new variables implementation.
     * Will likely be deprecated in the near future.
     */
    @SuppressWarnings("unchecked")
    protected Map variableTableGetMap(Map map) {
        VariableTableEntry[] table;
        IRubyObject readValue;
        if ((table = variableTable) != null) {
            for (int i = table.length; --i >= 0; ) {
                for (VariableTableEntry e = table[i]; e != null; e = e.next) {
                    if ((readValue = e.value) == null) readValue = variableTableReadLocked(e);
                    map.put(e.name, readValue);
                }
            }
        }
        return map;
    }

    /**
     * Tries to support Java serialization of Ruby objects. This is
     * still experimental and might not work.
     */
    // NOTE: Serialization is primarily supported for testing purposes, and there is no general
    // guarantee that serialization will work correctly. Specifically, instance variables pointing
    // at symbols, threads, modules, classes, and other unserializable types are not detected.
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        // write out ivar count followed by name/value pairs
        List<String> names = getInstanceVariableNameList();
        out.writeInt(names.size());
        for (String name : names) {
            out.writeObject(name);
            out.writeObject(getInstanceVariables().getInstanceVariable(name));
        }
    }

    /**
     * Tries to support Java unserialization of Ruby objects. This is
     * still experimental and might not work.
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        // rest in ivar count followed by name/value pairs
        int ivarCount = in.readInt();
        for (int i = 0; i < ivarCount; i++) {
            setInstanceVariable((String)in.readObject(), (IRubyObject)in.readObject());
        }
    }

}
