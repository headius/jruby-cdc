/***** BEGIN LICENSE BLOCK *****
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
 * Copyright (C) 2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2004-2005 Thomas E Enebo <enebo@acm.org>
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
package org.jruby.runtime.builtin.meta;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.RubyObject;
import org.jruby.internal.runtime.methods.ReflectedMethod;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;

/**
 * <p>
 * The main meta class for all other meta classes.
 * </p>
 */
public class ObjectMetaClass extends RubyClass {
    private final Class builtinClass;
    
    // Only for creating ObjectMetaClass directly
    public ObjectMetaClass(Ruby runtime) {
    	this(runtime, runtime.getClasses().getClassClass(), null, null, "Object", RubyObject.class);
    }
    
    // Only for other core modules/classes
    protected ObjectMetaClass(Ruby runtime, RubyClass metaClass, RubyClass superClass, 
        RubyModule parentClass, String name, Class builtinClass) {
    	super(runtime, metaClass, superClass, parentClass, name);
    	
    	this.builtinClass = builtinClass;
    	
    	initializeClass();
    }
    
    protected ObjectMetaClass(String name, Class builtinClass, RubyClass superClass) {
        this(name, builtinClass, superClass, superClass.getRuntime().getClasses().getObjectClass(), true);
    }

    protected ObjectMetaClass(String name, Class builtinClass, RubyClass superClass, RubyModule parentModule) {
        this(name, builtinClass, superClass, parentModule, false);
    }

    protected ObjectMetaClass(String name, Class builtinClass, RubyClass superClass, RubyModule parentModule, boolean init) {
        super(superClass.getRuntime(), superClass.getRuntime().getClasses().getClassClass(), superClass, parentModule, name);

        assert name != null;
        assert builtinClass != null;
        //assert RubyObject.class.isAssignableFrom(builtinClass) || RubyObject.class == builtinClass: "builtinClass have to be a subclass of RubyObject.";
        assert superClass != null;

        this.builtinClass = builtinClass;

        makeMetaClass(superClass.getMetaClass(), superClass.getRuntime().getCurrentContext().getRubyClass());
        inheritedBy(superClass);

        getRuntime().getClasses().putClass(name, this, parentModule);

        if (init) {
            initializeClass();
        }
    }

    protected void initializeClass() {
        defineMethod("==", Arity.singleArgument(), "equal");
        defineAlias("===", "==");
        defineMethod("to_s", Arity.noArguments());
        defineFalseMethod("nil?", Arity.noArguments());
        defineMethod("to_a", Arity.noArguments());
        defineMethod("hash", Arity.noArguments());
        defineMethod("id", Arity.noArguments());
        defineAlias("__id__", "id");
        defineAlias("object_id", "id");
        defineMethod("is_a?", Arity.singleArgument(), "kind_of");
        defineAlias("kind_of?", "is_a?");
        defineMethod("dup", Arity.noArguments());
        defineAlias("eql?", "==");
        defineMethod("equal?", Arity.singleArgument(), "same");
        defineMethod("type", Arity.noArguments(), "type_deprecated");
        defineMethod("class", Arity.noArguments(), "type");
        defineMethod("inspect", Arity.noArguments());
        defineFalseMethod("=~", Arity.singleArgument());
        defineMethod("clone", Arity.noArguments(), "rbClone");
        defineMethod("display", Arity.optional(), "display");
        defineMethod("extend", Arity.optional(), "extend");
        defineMethod("freeze", Arity.noArguments());
        defineMethod("frozen?", Arity.noArguments(), "frozen");
        defineMethod("initialize_copy", Arity.singleArgument(), "initialize_copy");
        defineMethod("instance_eval", Arity.optional());
        defineMethod("instance_of?", Arity.singleArgument(), "instance_of");
        defineMethod("instance_variables", Arity.noArguments());
        defineMethod("instance_variable_get", Arity.singleArgument());
        defineMethod("instance_variable_set", Arity.twoArguments());
        defineMethod("method", Arity.singleArgument(), "method");
        defineMethod("methods", Arity.optional());
        //defineMethod("method_missing", callbackFactory.getOptMethod(RubyObject.class, "method_missing"));
        defineMethod("private_methods", Arity.noArguments());
        defineMethod("protected_methods", Arity.noArguments());
        defineMethod("public_methods", Arity.noArguments());
        defineMethod("respond_to?", Arity.optional(), "respond_to");
        defineMethod("send", Arity.optional());
        defineAlias("__send__", "send");
        defineMethod("singleton_methods", Arity.noArguments());
        defineMethod("taint", Arity.noArguments());
        defineMethod("tainted?", Arity.noArguments(), "tainted");
        defineMethod("untaint", Arity.noArguments());
	}

	protected IRubyObject allocateObject() {
        RubyObject instance = new RubyObject(getRuntime(), this);
        
		instance.setMetaClass(this);
		
		return instance;
	}
    
    public void defineMethod(String name, Arity arity) {
    	defineMethod(name, arity, name);
    }

    // TODO: Look to eliminate this (like add a false method to IRubyObject so we don't need it).
    public void defineFalseMethod(String name, final Arity arity) {
        defineMethod(name, new Callback() {
            public IRubyObject execute(IRubyObject recv, IRubyObject[] args) {
                return recv.getRuntime().getFalse();
            }

            public Arity getArity() {
                return arity;
            }
        });
    }

    public void defineMethod(String name, Arity arity, String java_name) {
        assert name != null;
        assert arity != null;
        assert java_name != null;
        
        Visibility visibility = name.equals("initialize") ? Visibility.PRIVATE : Visibility.PUBLIC;

        addMethod(name, new ReflectedMethod(builtinClass, java_name, arity, visibility));
    }

    public void defineSingletonMethod(String name, Arity arity) {
    	defineSingletonMethod(name, arity, name);
    }
    
    // TODO: Look to eliminate this (like add a self method to IRubyObject so we don't need it).
    public void defineSelfMethod(String name, final Arity arity) {
    	defineMethod(name, new Callback() {
            public IRubyObject execute(IRubyObject recv, IRubyObject[] args) {
                return recv;
            }

            public Arity getArity() {
                return arity;
            }
        });
    }

    public void defineSingletonMethod(String name, Arity arity, String java_name) {
        assert name != null;
        assert arity != null;
        assert java_name != null;

        Visibility visibility = name.equals("initialize") ? Visibility.PRIVATE : Visibility.PUBLIC;

        getSingletonClass().addMethod(name, new ReflectedMethod(getClass(), java_name, arity, visibility));
    }
}
