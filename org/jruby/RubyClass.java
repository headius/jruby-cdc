/*
 * RubyClass.java - No description
 * Created on 04. Juli 2001, 22:53
 * 
 * Copyright (C) 2001 Jan Arne Petersen, Stefan Matthias Aust, Alan Moore, Benoit Cerrina
 * Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Stefan Matthias Aust <sma@3plus4.de>
 * Alan Moore <alan_moore@gmx.net>
 * Benoit Cerrina <b.cerrina@wanadoo.fr>
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

import java.util.Iterator;
import java.util.Map;

import org.jruby.exceptions.RubyFrozenException;
import org.jruby.exceptions.TypeError;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.marshal.MarshalStream;
import org.jruby.runtime.marshal.UnmarshalStream;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.ICallable;

/**
 *
 * @author  jpetersen
 */
public class RubyClass extends RubyModule {
    // Flags
    private boolean singleton = false;

    private RubyClass(Ruby ruby) {
        this(ruby, null, null);
    }

    protected RubyClass(Ruby ruby, RubyClass superClass) {
        this(ruby, null, superClass);
    }

    public RubyClass(Ruby ruby, RubyClass rubyClass, RubyClass superClass) {
        super(ruby, rubyClass, superClass);
    }

    public static RubyClass nilClass(Ruby ruby) {
        return new RubyClass(ruby) {
            public boolean isNil() {
                return true;
            }
        };
    }

    protected void testFrozen() {
        if (isFrozen()) {
            if (isSingleton()) {
                throw new RubyFrozenException(getRuntime(), "object");
            } else {
                throw new RubyFrozenException(getRuntime(), "class");
            }
        }
    }

    public boolean isModule() {
        return false;
    }

    public boolean isClass() {
        return true;
    }

    public void setSingleton(boolean singleton) {
        this.singleton = singleton;
    }

    public static void createClassClass(RubyClass classClass) {
        classClass.defineSingletonMethod("new", CallbackFactory.getOptSingletonMethod(RubyClass.class, "newInstance"));

        classClass.defineMethod("new", CallbackFactory.getOptMethod(RubyClass.class, "newInstance"));
        classClass.defineMethod("superclass", CallbackFactory.getMethod(RubyClass.class, "superclass"));

        classClass.defineSingletonMethod("inherited", CallbackFactory.getNilMethod(1));

        classClass.undefMethod("module_function");
    }
    
    /** Invokes if  a class is inherited from an other  class.
     * 
     * MRI: rb_class_inherited
     * 
     * @since Ruby 1.6.7
     * 
     */
    public void inheritedBy(RubyClass superType) {
        if (superType == null) {
            superType = runtime.getClasses().getObjectClass();
        }
        superType.callMethod("inherited", this);
    }

    /** rb_singleton_class_clone
     *
     */
    public RubyClass getSingletonClassClone() {
        if (!isSingleton()) {
            return (RubyClass) this;
        }

        RubyClass clone = newClass(getRuntime(), getInternalClass(), getSuperClass());
        clone.setupClone(this);
        clone.setInstanceVariables(getInstanceVariables().cloneRubyMap());
        
        
        Iterator iter = getMethods().entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            
            ICallable value = (ICallable)entry.getValue();
            
            clone.getMethods().put(entry.getKey(), value);
        }      

        //clone.setMethods();

        // st_foreach(RCLASS(klass)->m_tbl, clone_method, clone->m_tbl);

        clone.setSingleton(true);

        return clone;
    }

    public boolean isSingleton() {
        return this.singleton;
    }

    public RubyClass getInternalClass() {
        RubyClass type = super.getInternalClass();

        return type != null ? type : getRuntime().getClasses().getClassClass();
    }
    
	public RubyClass getRealClass() {
        if (isSingleton() || isIncluded()) {
            return getSuperClass().getRealClass();
        }
        return this;
    }

    /** rb_singleton_class_attached
     *
     */
    public void attachSingletonClass(IRubyObject object) {
        if (isSingleton()) {
            setInstanceVariable("__atached__", object);
        } else {
            getRuntime().getRuntime().printBug("attachSingletonClass called on a non singleton class.");
        }
    }

    /** rb_singleton_class_new
     *
     */
    public RubyClass newSingletonClass() {
        RubyClass newClass = RubyClass.newClass(getRuntime(), this);
        newClass.setSingleton(true);

        return newClass;
    }

    // Methods of the Class class (rb_class_*):

    /** rb_class_new
     *
     */
    public static RubyClass newClass(Ruby ruby, RubyClass superClass) {
        return new RubyClass(ruby, ruby.getClasses().getClassClass(), superClass);
    }

    public static RubyClass newClass(Ruby ruby, RubyClass rubyClass, RubyClass superClass) {
        return new RubyClass(ruby, rubyClass, superClass);
    }

    /** rb_class_new_instance
     *
     */
    public IRubyObject newInstance(IRubyObject[] args) {
        if (isSingleton()) {
            throw new TypeError(getRuntime(), "can't create instance of virtual class");
        }

        IRubyObject obj = getRuntime().getFactory().newObject(this);

        obj.callInit(args);

        return obj;
    }

    /** rb_class_s_new
     *
     */
    public static RubyModule newInstance(IRubyObject recv, IRubyObject[] args) {
        RubyClass superClass = recv.getRuntime().getClasses().getObjectClass();

        if (args.length >= 1) {
            superClass = (RubyClass) args[0];
        }

        if (superClass.isSingleton()) {
            throw new TypeError(recv.getRuntime(), "Can't make subclass of virtual class.");
        }

        RubyClass newClass = newClass(recv.getRuntime(), superClass);

        newClass.makeMetaClass(superClass.getInternalClass());

        // call "initialize" method
        newClass.callInit(args);

        // call "inherited" method of the superclass
        newClass.inheritedBy(superClass);

        return newClass;
    }

    /** Return the real super class of this class.
     * 
     * rb_class_superclass
     *
     */
    public RubyClass superclass() {
        RubyClass superClass = getSuperClass();
        while (superClass != null && superClass.isIncluded()) {
            superClass = superClass.getSuperClass();
        }

        return superClass != null ? superClass : nilClass(getRuntime());
    }

    /** rb_class_s_inherited
     *
     */
    public static IRubyObject inherited(RubyClass recv) {
        throw new TypeError(recv.getRuntime(), "can't make subclass of Class");
    }


    public void marshalTo(MarshalStream output) throws java.io.IOException {
        output.write('c');
        output.dumpString(getClassname().toString());
    }

    public static RubyModule unmarshalFrom(UnmarshalStream output) throws java.io.IOException {
        return (RubyClass) RubyModule.unmarshalFrom(output);
    }
}
