/*
 * RubyObject.java - No description
 * Created on 04. Juli 2001, 22:53
 * 
 * Copyright (C) 2001 Jan Arne Petersen, Stefan Matthias Aust
 * Jan Arne Petersen <japetersen@web.de>
 * Stefan Matthias Aust <sma@3plus4.de>
 * 
 * JRuby - http://jruby.sourceforge.net
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 */

package org.jruby;

import java.util.*;

import org.jruby.core.*;
import org.jruby.exceptions.*;
import org.jruby.interpreter.nodes.*;
import org.jruby.original.*;
import org.jruby.util.*;

/**
 *
 * @author  jpetersen
 */
public class RubyObject implements VALUE {
    private Ruby ruby;
    
    private RubyModule rubyClass;
    private RubyMap instanceVariables;
    
    private boolean frozen = false;
    private boolean taint = false;
    
    // deprecated ???
    private boolean immediate = false;
    
    public RubyObject(Ruby ruby) {
        this(ruby, null);
    }
    
    public RubyObject(Ruby ruby, RubyModule rubyClass) {
        this.ruby = ruby;
        this.rubyClass = rubyClass;
    }
    
    
    /** Getter for property frozen.
     * @return Value of property frozen.
     */
    public boolean isFrozen() {
        return this.frozen;
    }
    
    /** Setter for property frozen.
     * @param frozen New value of property frozen.
     */
    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }
    
    /** Getter for property immediate.
     * @return Value of property immediate.
     */
    public boolean isImmediate() {
        return this.immediate;
    }
    
    /** Setter for property immediate.
     * @param immediate New value of property immediate.
     */
    public void setImmediate(boolean immediate) {
        this.immediate = immediate;
    }
    
    /** Getter for property ruby.
     * @return Value of property ruby.
     */
    public org.jruby.Ruby getRuby() {
        return this.ruby;
    }
    
    /** Setter for property ruby.
     * @param ruby New value of property ruby.
     */
    public void setRuby(org.jruby.Ruby ruby) {
        this.ruby = ruby;
    }
    
    /** Getter for property rubyClass.
     * @return Value of property rubyClass.
     */
    public RubyModule getRubyClass() {
        return this.rubyClass;
    }
    
    /** Setter for property rubyClass.
     * @param rubyClass New value of property rubyClass.
     */
    public void setRubyClass(RubyModule rubyClass) {
        this.rubyClass = rubyClass;
    }
    
    /** Getter for property taint.
     * @return Value of property taint.
     */
    public boolean isTaint() {
        return this.taint;
    }
    
    
    /** Setter for property taint.
     * @param taint New value of property taint.
     */
    public void setTaint(boolean taint) {
        this.taint = taint;
    }
    
    public RubyMap getInstanceVariables() {
        if (instanceVariables == null) {
            instanceVariables = new RubyHashMap();
        }
        return instanceVariables;
    }
    
    public void setInstanceVariables(RubyMap instanceVariables) {
        this.instanceVariables = instanceVariables;
    }
    
    // methods
    
    public boolean isNil() {
        return false;
    }
    
    public boolean isTrue() {
        return false;
    }
    
    public boolean isFalse() {
        return false;
    }
    
    /** rb_special_const_p
     *
     */
    public boolean isSpecialConst() {
        return (isImmediate() || isNil());
    }
    
    /** SPECIAL_SINGLETON(x,c)
     *
     */
    private RubyClass getSpecialSingleton() {
        RubyClass rubyClass = (RubyClass)getRubyClass();
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
        //        if (getType() == Type.FIXNUM || isSymbol()) {
        //            throw new RubyTypeException("can't define singleton");
        //        }
        
        if (isSpecialConst()) {
            if (isNil() || isTrue() || isFalse()) {
                return getSpecialSingleton();
            }
            throw new RubyBugException("unknown immediate " + toString());
        }
        
        //synchronize(this) {
        RubyClass rbClass = null;
        
        if (getRubyClass().isSingleton()) {
            rbClass = (RubyClass)getRubyClass();
        } else {
            rbClass = ((RubyClass)getRubyClass()).newSingletonClass();
            setRubyClass(rbClass);
            rbClass.attachSingletonClass(this);
        }
        
        rbClass.setTaint(isTaint());
        
        if (isFrozen()) {
            rbClass.setFrozen(true);
        }
        //}
        return rbClass;
    }
    
    /** rb_define_singleton_method
     *
     */
    public void defineSingletonMethod(String name, RubyCallbackMethod method) {
        getSingletonClass().defineMethod(name, method);
    }
    
    
    /** OBJSETUP
     *
     */
    protected void setupObject(RubyModule rubyClass) {
        setRubyClass(rubyClass);
    }
    
    /** CLONESETUP
     *
     */
    protected void setupClone(RubyObject obj) {
        setupObject(obj.getRubyClass().getSingletonClassClone());
        
        getRubyClass().attachSingletonClass(this);
    }
    
    /** OBJ_INFECT
     *
     */
    protected void infectObject(RubyObject obj) {
        if (obj.isTaint()) {
            setTaint(true);
        }
    }
    
    /** rb_funcall2
     *
     */
    public RubyObject funcall(RubyId mid, RubyObject[] args) {
        return getRubyClass().call(this, mid, args, 1);
    }
    
    public RubyObject funcall(RubyId mid) {
        return funcall(mid, (RubyObject[])null);
    }
    
    /** rb_funcall3
     *
     */
    public RubyObject funcall3(RubyId mid, RubyObject[] args) {
        return getRubyClass().call(this, mid, args, 0);
    }
    
    /** rb_funcall
     *
     */
    public RubyObject funcall(RubyId mid, RubyObject arg) {
        return funcall(mid, new RubyObject[] {arg});
    }
    
    /** rb_iv_get
     *
     */
    public RubyObject getIv(String name) {
        return getIvar(getRuby().intern(name));
    }
    
    /** rb_iv_set
     *
     */
    public void setIv(String name, RubyObject value) {
        setIvar(getRuby().intern(name), value);
    }
    
    public RubyObject getIvar(RubyId id) {
        return null;
    }
    
    public boolean isIvarDefined(RubyId id) {
        return false;
    }
    
    public void setIvar(RubyId id, RubyObject value) {
        //return null;
    }
    
    public RubyModule getCvarSingleton() {
        return null;
    }
    
    /** rb_eval
     *
     *  future version
     */
    public RubyObject eval(NODE n) {
        NODE node = n;
        
        while (true) {
            if (node == null) {
                return getRuby().getNil();
            }
            
            if (node instanceof ExpandNode) {
                node = ((ExpandNode)node).expand(this);
            } else if (node instanceof ResultNode) {
                return ((ResultNode)node).interpret(this);
            }
        }
    }
    
    // Methods of the Object class (rb_obj_*):
    
    /** rb_obj_equal
     *
     */
    public RubyBoolean m_equal(RubyObject obj) {
        if (this == obj) {
            return getRuby().getTrue();
        }
        return getRuby().getFalse();
    }
    
    /** rb_obj_id
     *
     */
    public RubyObject m_id() {
        //obj.hashCode();
        
        return null;
    }
    
    /** rb_obj_type
     *
     */
    public RubyModule m_type() {
        RubyModule rbClass = getRubyClass();
        
        while (rbClass.isSingleton() || rbClass.isIncluded()) {
            rbClass = rbClass.getSuperClass();
        }
        
        return rbClass;
    }
    
    /** rb_obj_clone
     *
     */
    public RubyObject m_clone() {
        RubyObject clone = new RubyObject(getRuby(), (RubyClass)getRubyClass());
        clone.setupClone(this);
        
        clone.setInstanceVariables(getInstanceVariables().cloneRubyMap());
        
        return clone;
    }
    
    /** rb_obj_dup
     *
     */
    public RubyObject m_dup() {
        RubyObject dup = funcall(getRuby().intern("clone"));
        
        if (!dup.getClass().equals(getClass())) {
            throw new RubyTypeException("duplicated object must be same type");
        }
        
        if (!dup.isSpecialConst()) {
            dup.setupObject(m_type());
            dup.infectObject(this);
        }
        
        return dup;
    }
    
    /** rb_obj_tainted
     *
     */
    public RubyBoolean m_tainted() {
        if (isTaint()) {
            return getRuby().getTrue();
        } else {
            return getRuby().getFalse();
        }
    }
    
    /** rb_obj_taint
     *
     */
    public RubyObject m_taint() {
        getRuby().secure(4);
        
        if (!isTaint()) {
            if (isFrozen()) {
                throw new RubyFrozenException("object");
            }
            setTaint(true);
        }
        
        return this;
    }
    
    /** rb_obj_untaint
     *
     */
    public RubyObject m_untaint() {
        getRuby().secure(3);
        
        if (isTaint()) {
            if (isFrozen()) {
                throw new RubyFrozenException("object");
            }
            setTaint(false);
        }
        
        return this;
    }
    
    /** rb_obj_freeze
     *
     */
    public RubyObject m_freeze() {
        if (getRuby().getSecurityLevel() >= 4 &&
        isTaint()) {
            throw new RubySecurityException("Insecure: can't freeze object");
        }
        
        // ??????????????
        
        return this;
    }
    
    /** rb_obj_frozen_p
     *
     */
    public RubyBoolean m_frozen() {
        if (isFrozen()) {
            return getRuby().getTrue();
        } else {
            return getRuby().getFalse();
        }
    }
    
    /** rb_obj_inspect
     *
     */
    public RubyString m_inspect() {
        //     if (TYPE(obj) == T_OBJECT
        // 	&& ROBJECT(obj)->iv_tbl
        // 	&& ROBJECT(obj)->iv_tbl->num_entries > 0) {
        // 	VALUE str;
        // 	char *c;
        //
        // 	c = rb_class2name(CLASS_OF(obj));
        // 	if (rb_inspecting_p(obj)) {
        // 	    str = rb_str_new(0, strlen(c)+10+16+1); /* 10:tags 16:addr 1:eos */
        // 	    sprintf(RSTRING(str)->ptr, "#<%s:0x%lx ...>", c, obj);
        // 	    RSTRING(str)->len = strlen(RSTRING(str)->ptr);
        // 	    return str;
        // 	}
        // 	str = rb_str_new(0, strlen(c)+6+16+1); /* 6:tags 16:addr 1:eos */
        // 	sprintf(RSTRING(str)->ptr, "-<%s:0x%lx ", c, obj);
        // 	RSTRING(str)->len = strlen(RSTRING(str)->ptr);
        // 	return rb_protect_inspect(inspect_obj, obj, str);
        //     }
        //     return rb_funcall(obj, rb_intern("to_s"), 0, 0);
        // }
        //return (RubyString)invokeMethod(getRuby().intern("to_s"), null, null);
        return null;
    }
    
    /** rb_obj_is_instance_of
     *
     */
    public RubyBoolean m_instance_of(RubyModule rbModule) {
        return RubyBoolean.m_newBoolean(getRuby(), m_type() == rbModule);
    }
    
    /** rb_obj_is_kind_of
     *
     */
    public RubyBoolean m_kind_of(RubyModule rbModule) {
        RubyModule rbClass = getRubyClass();
        
        while (rbClass != null) {
            if (rbClass == rbModule || rbClass.getMethods() == rbModule.getMethods()) {
                return getRuby().getTrue();
            }
            rbClass = rbClass.getSuperClass();
        }
        
        return getRuby().getFalse();
    }
    
    /** rb_obj_methods
     *
     */
    public RubyArray m_methods() {
        // return getRubyClass().m_instance_methods(getRuby().getTrue());
        return null;
    }
    
    /** rb_obj_protected_methods
     *
     */
    public RubyArray m_protected_methods() {
        // return getRubyClass().m_protected_instance_methods(getRuby().getTrue());
        return null;
    }
    
    /** rb_obj_private_methods
     *
     */
    public RubyArray m_private_methods() {
        // return getRubyClass().m_private_instance_methods(getRuby().getTrue());
        
        return null;
    }
    
    /** rb_obj_singleton_methods
     *
     */
    public RubyArray m_singleton_methods() {
        RubyArray ary = RubyArray.m_newArray(getRuby());
        RubyModule rbClass = getRubyClass();
        
        while (rbClass != null && rbClass.isSingleton()) {
            rbClass.getMethods().foreach(new RubyMapMethod() {
                public int execute(Object key, Object value, Object arg) {
                    return CONTINUE;
                }
            }, ary);
            rbClass = rbClass.getSuperClass();
        }
        //ary.removeNil();
        return ary;
    }
    
    
    public RubyString m_to_s() {
        String cname = getRubyClass().toName();
        
        RubyString str = RubyString.m_newString(getRuby(), ""); /* 6:tags 16:addr 1:eos */
        str.setString("#<" + cname + ":0x" + this + "x>");
        if (isTaint()) {
            str.setTaint(true);
        }
        
        return str;
    }
}