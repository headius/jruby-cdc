/*
 * RubyObject.java - No description
 * Created on 04. Juli 2001, 22:53
 * 
 * Copyright (C) 2001, 2002 Jan Arne Petersen, Alan Moore, Benoit Cerrina, Chad Fowler
 * Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Alan Moore <alan_moore@gmx.net>
 * Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Chad Fowler <chadfowler@chadfowler.com>
 * 
 * JRuby - http://jruby.sourceforge.net
 * 
 * This file is part of JRuby
 * 
 * JRuby is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * JRuby is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with JRuby; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 */
package org.jruby;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import org.ablaf.ast.INode;
import org.ablaf.common.ISourcePosition;
import org.jruby.ast.ZSuperNode;
import org.jruby.evaluator.EvaluateVisitor;
import org.jruby.exceptions.ArgumentError;
import org.jruby.exceptions.NameError;


import org.jruby.exceptions.RubyFrozenException;
import org.jruby.exceptions.RubySecurityException;
import org.jruby.exceptions.TypeError;

import org.jruby.internal.runtime.methods.EvaluateMethod;
import org.jruby.runtime.marshal.MarshalStream;

import org.jruby.runtime.*;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.Asserts;
import org.jruby.util.PrintfFormat;
import org.jruby.util.RubyHashMap;
import org.jruby.util.RubyMap;
import org.jruby.util.RubyMapMethod;

/**
 *
 * @author  jpetersen
 */
public class RubyObject implements Cloneable, IRubyObject {

    // A reference to the JRuby runtime.
    protected transient Ruby runtime;

    // The class of this object
    private RubyClass internalClass;

    // The instance variables of this object.
    private RubyMap instanceVariables;

    // The two properties frozen and taint
    private boolean frozen;
    private boolean taint;

    public RubyObject(Ruby ruby) {
        this(ruby, null, false);
    }

    public RubyObject(Ruby ruby, RubyClass rubyClass) {
        this(ruby, rubyClass, true);
    }

    public RubyObject(Ruby ruby, RubyClass rubyClass, boolean useObjectSpace) {
        this.runtime = ruby;
        this.internalClass = rubyClass;
        this.frozen = false;
        this.taint = false;

        if (useObjectSpace) {
            ruby.objectSpace.add(this);
        }
    }

    public static IRubyObject nilObject(Ruby ruby) {
        if (ruby.getNil() != null) {
            return ruby.getNil();
        } else {
            return new RubyObject(ruby) {
                public boolean isNil() {
                    return true;
                }
            };
        }
    }

    /**
     * Create a new meta class.
     *
     * This method is used by a lot of other methods.
     *
     * @since Ruby 1.6.7
     */
    public RubyClass makeMetaClass(RubyClass type) {
        setInternalClass(type.newSingletonClass());
        getInternalClass().attachSingletonClass(this);
        return getInternalClass();
    }

    public Class getJavaClass() {
        return IRubyObject.class;
    }

    /**
     * This method is just a wrapper around the Ruby "==" method,
     * provided so that RubyObjects can be used as keys in the Java
     * HashMap object underlying RubyHash.
     */
    public boolean equals(Object other) {
        return other == this || (other instanceof IRubyObject) && callMethod("==", (IRubyObject) other).isTrue();
    }

    public String toString() {
        return ((RubyString) callMethod("to_s")).getValue();
    }

    /** Getter for property ruby.
     * @return Value of property ruby.
     */
    public Ruby getRuntime() {
        return this.runtime;
    }

    public RubyMap getInstanceVariables() {
        return instanceVariables;
    }

    public void setInstanceVariables(RubyMap instanceVariables) {
        this.instanceVariables = instanceVariables;
    }

    /**
     * Gets the rubyClass.
     * @return Returns a RubyClass
     */
    public RubyClass getInternalClass() {
        if (isNil()) {
            return getRuntime().getClasses().getNilClass();
        }
        return internalClass;
    }

    /**
     * Sets the rubyClass.
     * @param rubyClass The rubyClass to set
     */
    public void setInternalClass(RubyClass rubyClass) {
        this.internalClass = rubyClass;
    }

    /**
     * Gets the frozen.
     * @return Returns a boolean
     */
    public boolean isFrozen() {
        return frozen;
    }

    /**
     * Sets the frozen.
     * @param frozen The frozen to set
     */
    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    /**
     * Gets the taint.
     * @return Returns a boolean
     */
    public boolean isTaint() {
        return taint;
    }

    /**
     * Sets the taint.
     * @param taint The taint to set
     */
    public void setTaint(boolean taint) {
        this.taint = taint;
    }

    public boolean isNil() {
        return false;
    }

    public boolean isTrue() {
        return !isNil();
    }

    public boolean isFalse() {
        return isNil();
    }

    public boolean respondsTo(String name) {
        return getInternalClass().isMethodBound(name, false);
    }

    public static void createObjectClass(RubyModule objectClass) {
        objectClass.defineMethod("==", CallbackFactory.getMethod(RubyObject.class, "equal", IRubyObject.class));
        objectClass.defineMethod("=~", CallbackFactory.getFalseMethod(1));
        objectClass.defineMethod("clone", CallbackFactory.getMethod(RubyObject.class, "rbClone"));
        objectClass.defineMethod("dup", CallbackFactory.getMethod(RubyObject.class, "dup"));
        objectClass.defineMethod("eql?", CallbackFactory.getMethod(RubyObject.class, "equal", IRubyObject.class));
        objectClass.defineMethod("respond_to?", CallbackFactory.getOptMethod(RubyObject.class, "respond_to"));
        objectClass.defineMethod("extend", CallbackFactory.getOptMethod(RubyObject.class, "extend"));
        objectClass.defineMethod("freeze", CallbackFactory.getMethod(RubyObject.class, "freeze"));
        objectClass.defineMethod("frozen?", CallbackFactory.getMethod(RubyObject.class, "frozen"));
        objectClass.defineMethod("id", CallbackFactory.getMethod(RubyObject.class, "id"));
        objectClass.defineMethod("hash", CallbackFactory.getMethod(RubyObject.class, "hash"));
        objectClass.defineMethod("__id__", CallbackFactory.getMethod(RubyObject.class, "id"));
        objectClass.defineMethod("inspect", CallbackFactory.getMethod(RubyObject.class, "inspect"));
        objectClass.defineMethod("instance_eval", CallbackFactory.getOptMethod(RubyObject.class, "instance_eval"));
        objectClass.defineMethod(
            "instance_of?",
            CallbackFactory.getMethod(RubyObject.class, "instance_of", RubyModule.class));
        objectClass.defineMethod(
            "instance_variables",
            CallbackFactory.getMethod(RubyObject.class, "instance_variables"));
        objectClass.defineMethod("is_a?", CallbackFactory.getMethod(RubyObject.class, "kind_of", RubyModule.class));
        objectClass.defineMethod("kind_of?", CallbackFactory.getMethod(RubyObject.class, "kind_of", RubyModule.class));
        objectClass.defineMethod("method", CallbackFactory.getMethod(RubyObject.class, "method", IRubyObject.class));
        objectClass.defineMethod("methods", CallbackFactory.getMethod(RubyObject.class, "methods"));
        objectClass.defineMethod("nil?", CallbackFactory.getFalseMethod(0));
        objectClass.defineMethod("private_methods", CallbackFactory.getMethod(RubyObject.class, "private_methods"));
        objectClass.defineMethod("protected_methods", CallbackFactory.getMethod(RubyObject.class, "protected_methods"));
        objectClass.defineMethod("public_methods", CallbackFactory.getMethod(RubyObject.class, "methods"));
        objectClass.defineMethod("send", CallbackFactory.getOptMethod(RubyObject.class, "send"));
        objectClass.defineMethod("__send__", CallbackFactory.getOptMethod(RubyObject.class, "send"));
        objectClass.defineMethod("taint", CallbackFactory.getMethod(RubyObject.class, "taint"));
        objectClass.defineMethod("tainted?", CallbackFactory.getMethod(RubyObject.class, "tainted"));
        objectClass.defineMethod("to_a", CallbackFactory.getMethod(RubyObject.class, "to_a"));
        objectClass.defineMethod("to_s", CallbackFactory.getMethod(RubyObject.class, "to_s"));
        objectClass.defineMethod("type", CallbackFactory.getMethod(RubyObject.class, "type"));
        objectClass.defineMethod("untaint", CallbackFactory.getMethod(RubyObject.class, "untaint"));
        objectClass.defineMethod("method_missing", CallbackFactory.getOptMethod(RubyObject.class, "method_missing"));

        objectClass.defineAlias("===", "==");
        objectClass.defineAlias("class", "type");
        objectClass.defineAlias("equal?", "==");
    }

    // Some helper functions:

    public int argCount(IRubyObject[] args, int min, int max) {
        int len = args.length;
        if (len < min || (max > -1 && len > max)) {
            throw new ArgumentError(
                getRuntime(),
                "Wrong # of arguments for method. " + args.length + " is not in Range " + min + ".." + max);
        }
        return len;
    }

    public boolean isKindOf(RubyModule type) {
        return getInternalClass().ancestors().includes(type);
    }

    /** SPECIAL_SINGLETON(x,c)
     *
     */
    private RubyClass getNilSingletonClass() {
        RubyClass rubyClass = getInternalClass();

        if (!rubyClass.isSingleton()) {
            rubyClass = rubyClass.newSingletonClass();
            rubyClass.attachSingletonClass(this);
        }

        return rubyClass;
    }

    /** rb_singleton_class
     *
     */
    public RubyClass getSingletonClass() {
        if (isNil()) {
            return getNilSingletonClass();
        }

        RubyClass type = getInternalClass().isSingleton() ?
            getInternalClass() : makeMetaClass(getInternalClass());

        type.setTaint(isTaint());
        type.setFrozen(isFrozen());

        return type;
    }

    /** rb_define_singleton_method
     *
     */
    public void defineSingletonMethod(String name, Callback method) {
        getSingletonClass().defineMethod(name, method);
    }

    /** CLONESETUP
     *
     */
    public void setupClone(IRubyObject obj) {
        setInternalClass(obj.getInternalClass().getSingletonClassClone());
        getInternalClass().attachSingletonClass(this);
        frozen = obj.isFrozen();
        taint = obj.isTaint();
    }

    /** OBJ_INFECT
     *
     */
    protected void infectBy(IRubyObject obj) {
        setTaint(isTaint() || obj.isTaint());
    }

    /** rb_funcall2
     *
     */
    public IRubyObject callMethod(String name, IRubyObject[] args) {
        return getInternalClass().call(this, name, args, CallType.FUNCTIONAL);
    }

    public IRubyObject callMethod(String name) {
        return callMethod(name, new IRubyObject[0]);
    }

    /** rb_funcall3
     *
     */
    public IRubyObject funcall3(String name, IRubyObject[] args) {
        return getInternalClass().call(this, name, args, CallType.NORMAL);
    }

    /** rb_funcall
     *
     */
    public IRubyObject callMethod(String name, IRubyObject arg) {
        return callMethod(name, new IRubyObject[] { arg });
    }

    /** rb_iv_get / rb_ivar_get
     *
     */
    public IRubyObject getInstanceVariable(String name) {
        if (getInstanceVariables() != null) {
            IRubyObject value = (IRubyObject) getInstanceVariables().get(name);
            if (value != null) {
                return value;
            }
        }
        // todo: add warn if verbose
        return getRuntime().getNil();
    }

    /** rb_iv_set / rb_ivar_set
     *
     */
    public IRubyObject setInstanceVariable(String name, IRubyObject value) {
        if (isTaint() && getRuntime().getSafeLevel() >= 4) {
            throw new RubySecurityException(getRuntime(), "Insecure: can't modify instance variable");
        }
        if (isFrozen()) {
            throw new RubyFrozenException(getRuntime(), "");
        }
        if (getInstanceVariables() == null) {
            setInstanceVariables(new RubyHashMap());
        }
        getInstanceVariables().put(name, value);

        return value;
    }

    public boolean isInstanceVarDefined(String name) {
        if (getInstanceVariables() != null) {
            if (getInstanceVariables().get(name) != null) {
                return true;
            }
        }
        return false;
    }

    /** rb_cvar_singleton
     *
     *@deprecated  since Ruby 1.6.7
     */
    public RubyModule getClassVarSingleton() {
        return getInternalClass();
    }

    /** rb_eval
     *
     */
    public IRubyObject eval(INode n) {
        return EvaluateVisitor.createVisitor(this).eval(n);
    }

    public void callInit(IRubyObject[] args) {
        runtime.getIterStack().push(runtime.isBlockGiven() ? Iter.ITER_PRE : Iter.ITER_NOT);
        callMethod("initialize", args);
        runtime.getIterStack().pop();
    }

    public void extendObject(RubyModule module) {
        getSingletonClass().includeModule(module);
    }

    /** rb_to_id
     *
     */
    public String toId() {
        throw new TypeError(getRuntime(), inspect().getValue() + " is not a symbol");
    }

    /** Converts this object to type 'targetType' using 'convertMethod' method.
     *
     * MRI: convert_type
     *
     * @since Ruby 1.6.7.
     * @fixme error handling
     */
    public IRubyObject convertToType(String targetType, String convertMethod, boolean raise) {
        if (!respondsTo(convertMethod)) {
            if (raise) {
                throw new TypeError(
                    runtime,
                    "Failed to convert " + getInternalClass().toName() + " into " + targetType + ".");
                // FIXME nil, true and false instead of NilClass, TrueClass, FalseClass;
            } else {
                return runtime.getNil();
            }
        }
        return callMethod(convertMethod);
    }

    /** rb_convert_type
     *
     */
    public IRubyObject convertType(Class type, String targetType, String convertMethod) {
        if (type.isAssignableFrom(getClass())) {
            return this;
        }

        IRubyObject result = convertToType(targetType, convertMethod, true);

        if (!type.isAssignableFrom(result.getClass())) {
            throw new TypeError(
                runtime,
                getInternalClass().toName() + "#" + convertMethod + " should return " + targetType + ".");
        }

        return result;
    }

    public void checkSafeString() {
        if (runtime.getSafeLevel() > 0 && isTaint()) {
            if (runtime.getCurrentFrame().getLastFunc() != null) {
                throw new RubySecurityException(
                    runtime,
                    "Insecure operation - " + runtime.getCurrentFrame().getLastFunc());
            } else {
                throw new RubySecurityException(runtime, "Insecure operation: -r");
            }
        }
        getRuntime().secure(4);
        if (!(this instanceof RubyString)) {
            throw new TypeError(
                getRuntime(),
                "wrong argument type " + getInternalClass().toName() + " (expected String)");
        }
    }

    /** specific_eval
     *
     */
    public IRubyObject specificEval(RubyModule mod, IRubyObject[] args) {
        if (getRuntime().isBlockGiven()) {
            if (args.length > 0) {
                throw new ArgumentError(getRuntime(), "wrong # of arguments (" + args.length + " for 0)");
            }
            return yieldUnder(mod);
        } else {
            if (args.length == 0) {
                throw new ArgumentError(getRuntime(), "block not supplied");
            } else if (args.length > 3) {
                String lastFuncName = runtime.getCurrentFrame().getLastFunc();
                throw new ArgumentError(
                    getRuntime(),
                    "wrong # of arguments: " + lastFuncName + "(src) or " + lastFuncName + "{..}");
            }
            /*
            if (ruby.getSecurityLevel() >= 4) {
            	Check_Type(argv[0], T_STRING);
            } else {
            	Check_SafeStr(argv[0]);
            }
            */
            IRubyObject file = args.length > 1 ? args[1] : RubyString.newString(getRuntime(), "(eval)");
            IRubyObject line = args.length > 2 ? args[2] : RubyFixnum.one(getRuntime());
            return evalUnder(mod, args[0], file, line);
        }
    }

    public IRubyObject evalUnder(RubyModule under, IRubyObject src, IRubyObject file, IRubyObject line) {
        /*
        if (ruby_safe_level >= 4) {
        	Check_Type(src, T_STRING);
        } else {
        	Check_SafeStr(src);
        	}
        */
        return under.executeUnder(new Callback() {
            public IRubyObject execute(IRubyObject self, IRubyObject[] args) {
                return args[0].eval(
                    args[1],
                    self.getRuntime().getNil(),
                    ((RubyString) args[2]).getValue(),
                    RubyNumeric.fix2int(args[3]));
            }

            public int getArity() {
                return -1;
            }
        }, new IRubyObject[] { this, src, file, line });
    }

    public IRubyObject yieldUnder(RubyModule under) {
        return under.executeUnder(new Callback() {
            public IRubyObject execute(IRubyObject self, IRubyObject[] args) {
                // if () {
                Block oldBlock = runtime.getBlockStack().getCurrent().cloneBlock();

                /* copy the block to avoid modifying global data. */
                runtime.getBlockStack().getCurrent().getFrame().setNamespace(runtime.getCurrentFrame().getNamespace());
                try {
                    return runtime.yield(args[0], args[0], runtime.getRubyClass(), false);
                } finally {
                    runtime.getBlockStack().setCurrent(oldBlock);
                }
                // }
                /* static block, no need to restore */
                // ruby.getBlock().frame.setNamespace(ruby.getRubyFrame().getNamespace());
                // return ruby.yield0(args[0], args[0], ruby.getRubyClass(), false);
            }

            public int getArity() {
                return -1;
            }
        }, new IRubyObject[] { this });
    }

    /**@fixme*/
    public IRubyObject eval(IRubyObject src, IRubyObject scope, String file, int line) {
        ISourcePosition savedPosition = runtime.getPosition();
        Iter iter = runtime.getCurrentFrame().getIter();
        if (file == null) {
            file = runtime.getSourceFile();
        }
        if (scope.isNil()) {
            if (runtime.getFrameStack().getPrevious() != null) {
                runtime.getCurrentFrame().setIter(runtime.getFrameStack().getPrevious().getIter());
            }
        }
        getRuntime().pushClass(runtime.getCBase());
        IRubyObject result = getRuntime().getNil();
        try {
            INode node = getRuntime().parse(src.toString(), file);
            result = eval(node);
        } finally {
            runtime.popClass();
            if (scope.isNil()) {
                runtime.getCurrentFrame().setIter(iter);
            }
            runtime.setPosition(savedPosition);
        }
        return result;
    }

    // Methods of the Object class (rb_obj_*):

    /** rb_obj_equal
     *
     */
    public RubyBoolean equal(IRubyObject obj) {
        if (isNil()) {
            return RubyBoolean.newBoolean(getRuntime(), obj.isNil());
        }
        return RubyBoolean.newBoolean(getRuntime(), this == obj);
    }

    /**
     * respond_to?( aSymbol, includePriv=false ) -> true or false
     *
     * Returns true if this object responds to the given method. Private
     * methods are included in the search only if the optional second
     * parameter evaluates to true.
     *
     * @mri rb_obj_respond_to
     * @return true if this responds to the given method
     */
    public RubyBoolean respond_to(IRubyObject[] args) {
        argCount(args, 1, 2);

        String name = args[0].toId();
        boolean includePrivate = args.length > 1 ? args[1].isTrue() : false;

        return RubyBoolean.newBoolean(runtime, getInternalClass().isMethodBound(name, !includePrivate));
    }

    /** Return the internal id of an object.
     *
     * <b>Warning:</b> In JRuby there is no guarantee that two objects have different ids.
     *
     * <i>CRuby function: rb_obj_id</i>
     *
     */
    public RubyFixnum id() {
        return RubyFixnum.newFixnum(getRuntime(), System.identityHashCode(this));
    }

    public RubyFixnum hash() {
        return RubyFixnum.newFixnum(runtime, System.identityHashCode(this));
    }

    /**
    	* hashCode() is just a wrapper around Ruby's hash() method, so that
    	* Ruby objects can be used in Java collections.
    */
    public final int hashCode() {
        return RubyNumeric.fix2int(callMethod("hash"));
    }

    /** rb_obj_type
     *
     */
    public RubyClass type() {
        return getInternalClass().getRealClass();
    }

    /** rb_obj_clone
     *
     */
    public IRubyObject rbClone() {
        try {
            IRubyObject clone = (IRubyObject) clone();
            clone.setupClone(this);
            if (getInstanceVariables() != null) {
                ((RubyObject) clone).setInstanceVariables(getInstanceVariables().cloneRubyMap());
            }
            return clone;
        } catch (CloneNotSupportedException cnsExcptn) {
            Asserts.assertNotReached(cnsExcptn.getMessage());
            return null;
        }
    }

    /** rb_obj_dup
     *
     */
    public IRubyObject dup() {
        IRubyObject dup = callMethod("clone");
        if (!dup.getClass().equals(getClass())) {
            throw new TypeError(getRuntime(), "duplicated object must be same type");
        }

        dup.setInternalClass(type());
        dup.setFrozen(false);
        return dup;
    }

    /** rb_obj_tainted
     *
     */
    public RubyBoolean tainted() {
        if (isTaint()) {
            return getRuntime().getTrue();
        } else {
            return getRuntime().getFalse();
        }
    }

    /** rb_obj_taint
     *
     */
    public IRubyObject taint() {
        getRuntime().secure(4);
        if (!isTaint()) {
            if (isFrozen()) {
                throw new RubyFrozenException(getRuntime(), "object");
            }
            setTaint(true);
        }
        return this;
    }

    /** rb_obj_untaint
     *
     */
    public IRubyObject untaint() {
        getRuntime().secure(3);
        if (isTaint()) {
            if (isFrozen()) {
                throw new RubyFrozenException(getRuntime(), "object");
            }
            setTaint(false);
        }
        return this;
    }

    /** Freeze an object.
     *
     * rb_obj_freeze
     *
     */
    public IRubyObject freeze() {
        if (getRuntime().getSafeLevel() >= 4 && isTaint()) {
            throw new RubySecurityException(getRuntime(), "Insecure: can't freeze object");
        }
        setFrozen(true);
        return this;
    }

    /** rb_obj_frozen_p
     *
     */
    public RubyBoolean frozen() {
        return RubyBoolean.newBoolean(getRuntime(), isFrozen());
    }

    /** rb_obj_inspect
     *
     */
    public RubyString inspect() {
        //     if (TYPE(obj) == T_OBJECT
        // 	&& ROBJECT(obj)->iv_tbl
        // 	&& ROBJECT(obj)->iv_tbl->num_entries > 0) {
        // 	VALUE str;
        // 	char *c;
        //
        // 	c = rb_class2name(CLASS_OF(obj));
        // 	/*if (rb_inspecting_p(obj)) {
        // 	    str = rb_str_new(0, strlen(c)+10+16+1); /* 10:tags 16:addr 1:eos */
        // 	    sprintf(RSTRING(str)->ptr, "#<%s:0x%lx ...>", c, obj);
        // 	    RSTRING(str)->len = strlen(RSTRING(str)->ptr);
        // 	    return str;
        // 	}*/
        // 	str = rb_str_new(0, strlen(c)+6+16+1); /* 6:tags 16:addr 1:eos */
        // 	sprintf(RSTRING(str)->ptr, "-<%s:0x%lx ", c, obj);
        // 	RSTRING(str)->len = strlen(RSTRING(str)->ptr);
        // 	return rb_protect_inspect(inspect_obj, obj, str);
        //     }
        //     return rb_funcall(obj, rb_intern("to_s"), 0, 0);
        // }
        return (RubyString) callMethod("to_s");
    }

    /** rb_obj_is_instance_of
     *
     */
    public RubyBoolean instance_of(RubyModule type) {
        return RubyBoolean.newBoolean(getRuntime(), type() == type);
    }

    /**
     *
     */
    public RubyArray instance_variables() {
        ArrayList names = new ArrayList();
        Iterator iter = instanceVariables.keySet().iterator();
        while (iter.hasNext()) {
            String name = (String) iter.next();
            names.add(RubyString.newString(getRuntime(), name));
        }
        return RubyArray.newArray(runtime, names);
    }

    /** rb_obj_is_kind_of
     *
     */
    public RubyBoolean kind_of(RubyModule type) {
        return RubyBoolean.newBoolean(runtime, isKindOf(type));
    }

    /** rb_obj_methods
     *
     */
    public IRubyObject methods() {
        return getInternalClass().instance_methods(new IRubyObject[] { getRuntime().getTrue()});
    }

    /** rb_obj_protected_methods
     *
     */
    public IRubyObject protected_methods() {
        return getInternalClass().protected_instance_methods(new IRubyObject[] { getRuntime().getTrue()});
    }

    /** rb_obj_private_methods
     *
     */
    public IRubyObject private_methods() {
        return getInternalClass().private_instance_methods(new IRubyObject[] { getRuntime().getTrue()});
    }

    /** rb_obj_singleton_methods
     *
     */
    public RubyArray singleton_methods() {
        RubyArray ary = RubyArray.newArray(getRuntime());
        RubyClass type = getInternalClass();
        while (type != null && type.isSingleton()) {
            type.getMethods().foreach(new RubyMapMethod() {
                public int execute(Object key, Object value, Object arg) {
                    RubyString name = RubyString.newString(getRuntime(), (String) key);
                    if (((ICallable) value).getVisibility().isPublic()) {
                        if (!((RubyArray) arg).includes(name)) {
                            if (((ICallable) value) == null) {
                                ((RubyArray) arg).append(getRuntime().getNil());
                            }
                            ((RubyArray) arg).append(name);
                        }
                    } else if (
                        value instanceof EvaluateMethod && ((EvaluateMethod) value).getNode() instanceof ZSuperNode) {
                        ((RubyArray) arg).append(getRuntime().getNil());
                        ((RubyArray) arg).append(name);
                    }
                    return CONTINUE;
                }
            }, ary);
            type = type.getSuperClass();
        }
        ary.compact_bang();
        return ary;
    }

    public IRubyObject method(IRubyObject symbol) {
        return getInternalClass().newMethod(this, symbol.toId(), getRuntime().getClasses().getMethodClass());
    }

    public RubyArray to_a() {
        return RubyArray.newArray(getRuntime(), this);
    }

    public RubyString to_s() {
        String cname = getInternalClass().toName();
        RubyString str = RubyString.newString(getRuntime(), "");
        /* 6:tags 16:addr 1:eos */
        str.setValue("#<" + cname + ":0x" + Integer.toHexString(System.identityHashCode(this)) + ">");
        str.setTaint(isTaint());
        return str;
    }

    public IRubyObject instance_eval(IRubyObject[] args) {
        return specificEval(getSingletonClass(), args);
    }

    /**
     *  @fixme: Check_Type?
     **/
    public IRubyObject extend(IRubyObject args[]) {
        if (args.length == 0) {
            throw new ArgumentError(runtime, "wrong # of arguments");
        }
        // FIXME: Check_Type?
        for (int i = 0; i < args.length; i++) {
            args[i].callMethod("extend_object", this);
        }
        return this;
    }

    public IRubyObject method_missing(IRubyObject[] args) {
        if (args.length == 0) {
            throw new ArgumentError(getRuntime(), "no id given");
        }

        String name = args[0].toId();

        String description = callMethod("inspect").toString();
        boolean noClass = description.charAt(0) == '#';
        if (isNil()) {
            noClass = true;
            description = "nil";
        } else if (this == runtime.getTrue()) {
            noClass = true;
            description = "true";
        } else if (this == runtime.getFalse()) {
            noClass = true;
            description = "false";
        }

        LastCallStatus lastCallStatus = runtime.getLastCallStatus();

        String format = lastCallStatus.errorMessageFormat(name);

        String msg =
            new PrintfFormat(format).sprintf(
                new Object[] { name, description, noClass ? "" : ":", noClass ? "" : getType().toName()});

        Frame current = runtime.getCurrentFrame();
        // runtime.getFrameStack().pop();
        try {
            throw new NameError(getRuntime(), msg);
        } finally {
            // runtime.getFrameStack().push(current);
        }
    }

    /**
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
     * @mri rb_f_send
     * @return the result of invoking the method identified by aSymbol.
     */
    public IRubyObject send(IRubyObject[] args) {
        if (args.length < 1) {
            throw new ArgumentError(runtime, "no method name given");
        }
        String name = args[0].toId();

        IRubyObject[] newArgs = new IRubyObject[args.length - 1];
        System.arraycopy(args, 1, newArgs, 0, newArgs.length);

        runtime.getIterStack().push(runtime.isBlockGiven() ? Iter.ITER_PRE : Iter.ITER_NOT);
        try {
            return getInternalClass().call(this, name, newArgs, CallType.FUNCTIONAL);
        } finally {
            getRuntime().getIterStack().pop();
        }
    }

    public void marshalTo(MarshalStream output) throws java.io.IOException {
        output.write('o');
        RubySymbol classname = RubySymbol.newSymbol(runtime, getInternalClass().getClassname());
        output.dumpObject(classname);

        if (getInstanceVariables() == null) {
            output.dumpInt(0);
        } else {
            output.dumpInt(getInstanceVariables().size());
            Iterator iter = getInstanceVariables().entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry entry = (Map.Entry) iter.next();
                String name = (String) entry.getKey();
                IRubyObject value = (IRubyObject) entry.getValue();

                output.dumpObject(RubySymbol.newSymbol(runtime, name));
                output.dumpObject(value);
            }
        }
    }
    /**
     * @see org.jruby.runtime.classes.IRubyObject#getType()
     */
    public RubyClass getType() {
        return type();
    }

}
