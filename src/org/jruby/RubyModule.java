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
 * Copyright (C) 2001 Chad Fowler <chadfowler@chadfowler.com>
 * Copyright (C) 2001 Alan Moore <alan_moore@gmx.net>
 * Copyright (C) 2001-2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2001-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004-2005 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jruby.internal.runtime.methods.AliasMethod;
import org.jruby.internal.runtime.methods.CallbackMethod;
import org.jruby.internal.runtime.methods.MethodMethod;
import org.jruby.internal.runtime.methods.ProcMethod;
import org.jruby.internal.runtime.methods.UndefinedMethod;
import org.jruby.internal.runtime.methods.WrapperCallable;
import org.jruby.runtime.Arity;
import org.jruby.runtime.CallType;
import org.jruby.runtime.Frame;
import org.jruby.runtime.ICallable;
import org.jruby.runtime.Iter;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;
import org.jruby.runtime.marshal.MarshalStream;
import org.jruby.runtime.marshal.UnmarshalStream;
import org.jruby.util.IdUtil;

/**
 *
 * @author  jpetersen
 */
public class RubyModule extends RubyObject {
    private static final String CVAR_TAINT_ERROR =
        "Insecure: can't modify class variable";
    private static final String CVAR_FREEZE_ERROR = "class/module";
    
    // superClass may be null.
    private RubyClass superClass;
    
    // Containing class...The parent of Object is null. Object should always be last in chain.
    private RubyModule parentModule;

    // ClassId is the name of the class/module sans where it is located.
    // If it is null, then it an anonymous class.
    private String classId;

    // All methods and all CACHED methods for the module.  The cached methods will be removed
    // when appropriate (e.g. when method is removed by source class or a new method is added
    // with same name by one of its subclasses).
    private Map methods = new HashMap();

    protected RubyModule(Ruby runtime, RubyClass metaClass, RubyClass superClass, RubyModule parentModule, String name) {
        super(runtime, metaClass);
        
        this.superClass = superClass;
        this.parentModule = parentModule;
		
        setBaseName(name);

        // If no parent is passed in, it is safe to assume Object.
        if (this.parentModule == null) {
            this.parentModule = runtime.getObject();
        }
    }
    
    /** Getter for property superClass.
     * @return Value of property superClass.
     */
    public RubyClass getSuperClass() {
        return superClass;
    }

    private void setSuperClass(RubyClass superClass) {
        this.superClass = superClass;
    }
    
    public RubyModule getParent() {
    	return parentModule;
    }

    public Map getMethods() {
        return methods;
    }

    public boolean isModule() {
        return true;
    }

    public boolean isClass() {
        return false;
    }

    public boolean isSingleton() {
        return false;
    }

    /**
     * Is this module one that in an included one (e.g. an IncludedModuleWrapper). 
     */
    public boolean isIncluded() {
        return false;
    }
 
    public String getBaseName() {
        return classId;
    }
    
    public void setBaseName(String name) {
        classId = name;
    }
    
    /**
     * Generate a fully-qualified class name or a #-style name for anonymous and singleton classes.
     * 
     * Ruby C equivalent = "classname"
     * 
     * @return The generated class name
     */
    public String getName() {
        if (getBaseName() == null) {
        	if (isClass()) {
                return "#<" + "Class" + ":01x" + Integer.toHexString(System.identityHashCode(this)) + ">";
        	} else {
                return "#<" + "Module" + ":01x" + Integer.toHexString(System.identityHashCode(this)) + ">";
        	}
        }
        
        StringBuffer result = new StringBuffer(getBaseName());
        RubyClass objectClass = getRuntime().getObject();
        
        for (RubyModule p = this.getParent(); p != null && p != objectClass; p = p.getParent()) {
            result.insert(0, "::").insert(0, p.getBaseName());
        }

        return result.toString();
    }

    /** 
     * Create a wrapper to use for including the specified module into this one.
     * 
     * Ruby C equivalent = "include_class_new"
     * 
     * @return The module wrapper
     */
    public IncludedModuleWrapper newIncludeClass(RubyClass superClazz) {
        return new IncludedModuleWrapper(getRuntime(), superClazz, this);
    }
    
    /**
     * Search this and parent modules for the named variable.
     * 
     * @param name The variable to search for
     * @return The module in which that variable is found, or null if not found
     */
    private RubyModule getModuleWithInstanceVar(String name) {
        for (RubyModule p = this; p != null; p = p.getSuperClass()) {
            if (p.getInstanceVariable(name) != null) {
                return p;
            }
        }
        return null;
    }

    /** 
     * Set the named class variable to the given value, provided taint and freeze allow setting it.
     * 
     * Ruby C equivalent = "rb_cvar_set"
     * 
     * @param name The variable name to set
     * @param value The value to set it to
     */
    public void setClassVar(String name, IRubyObject value) {
        RubyModule module = getModuleWithInstanceVar(name);
        
        if (module == null) {
            module = this;
        }

        module.setInstanceVariable(name, value, CVAR_TAINT_ERROR, CVAR_FREEZE_ERROR);
    }

    /**
     * Retrieve the specified class variable, searching through this module, included modules, and supermodules.
     * 
     * Ruby C equivalent = "rb_cvar_get"
     * 
     * @param name The name of the variable to retrieve
     * @return The variable's value, or throws NameError if not found
     */
    public IRubyObject getClassVar(String name) {
        RubyModule module = getModuleWithInstanceVar(name);
        
        if (module != null) {
        	IRubyObject variable = module.getInstanceVariable(name); 
        	
            return variable == null ? getRuntime().getNil() : variable;
        }
        
        throw getRuntime().newNameError("uninitialized class variable " + name + " in " + getName());
    }

    /** 
     * Is class var defined?
     * 
     * Ruby C equivalent = "rb_cvar_defined"
     * 
     * @param name The class var to determine "is defined?"
     * @return true if true, false if false
     */
    public boolean isClassVarDefined(String name) {
        return getModuleWithInstanceVar(name) != null;
    }

    /**
     * Set the named constant on this module. Also, if the value provided is another Module and
     * that module has not yet been named, assign it the specified name.
     * 
     * @param name The name to assign
     * @param value The value to assign to it; if an unnamed Module, also set its basename to name
     * @return The result of setting the variable.
     * @see RubyObject#setInstanceVariable(String, IRubyObject, String, String)
     */
    public IRubyObject setConstant(String name, IRubyObject value) {
        IRubyObject result = setInstanceVariable(name, value, "Insecure: can't set constant", 
                "class/module");
        
        // if adding a module under a constant name, set that module's basename to the constant name
        if (value instanceof RubyModule) {
            RubyModule module = (RubyModule)value;
            if (module.getBaseName() == null) {
                module.setBaseName(name);
            }
        }
        return result;
    }

    /**
     * Retrieve the named constant, invoking 'const_missing' should that be appropriate.
     * 
     * @param name The constant to retrieve
     * @return The value for the constant, or null if not found
     * @see RubyModule#getConstant(String, boolean)
     */
    public IRubyObject getConstant(String name) {
    	return getConstant(name, true);
    }
    
    /**
     * Retrieve the named constant, invoking 'const_missing' if invokeConstMissing == true and
     * it is appropriate to do so.
     * 
     * @param name The constant to retrieve
     * @param invokeConstMissing Whether or not to invoke const_missing as appropriate
     * @return The retrieved value, or null if not found
     */
    public IRubyObject getConstant(String name, boolean invokeConstMissing) {
    	// First look for constants in module hierachy
    	for (RubyModule p = this; p != null; p = p.parentModule) {
            IRubyObject var = p.getInstanceVariable(name);
            if (var != null) {
                return var;
            }
        }

        // Second look for constants in the inheritance hierachy
        for (RubyModule p = this; p != null; p = p.getSuperClass()) {
        	IRubyObject var = p.getInstanceVariable(name);
            if (var != null) {
                return var;
            }
        }

        if (invokeConstMissing) {
        	return callMethod("const_missing", RubySymbol.newSymbol(getRuntime(), name));
        }
		
        return null;
    }
    
    /**
     * Finds a class that is within the current module (or class).
     * 
     * @param name to be found in this module (or class)
     * @return the class or null if no such class
     */
    public RubyClass getClass(String name) {
    	IRubyObject module = getConstant(name, false);
    	
    	return  (module instanceof RubyClass) ? (RubyClass) module : null;
    }

    /**
     * Base implementation of Module#const_missing, throws NameError for specific missing constant.
     * 
     * @param name The constant name which was found to be missing
     * @return Nothing! Absolutely nothing! (though subclasses might choose to return something)
     */
    public IRubyObject const_missing(IRubyObject name) {
        /* Uninitialized constant */
        if (this != getRuntime().getObject()) {
            throw getRuntime().newNameError("uninitialized constant " + name.asSymbol() + " at " + getName());
        } 

        throw getRuntime().newNameError("uninitialized constant " + name.asSymbol());
    }

    /** 
     * Include a new module in this module or class.
     * 
     * @param arg The module to include
     */
    public synchronized void includeModule(IRubyObject arg) {
        testFrozen("module");
        if (!isTaint()) {
            getRuntime().secure(4);
        }

        if (!(arg instanceof RubyModule)) {
            throw getRuntime().newTypeError("Wrong argument type " + arg.getMetaClass().getName() + " (expected Module).");
        }

        RubyModule module = (RubyModule) arg;
        Map moduleMethods = module.getMethods();

        // Make sure the module we include does not already exist 
        for (RubyModule p = this; p != null; p = p.getSuperClass()) {
        	// XXXEnebo - Lame equality check (cause: IncludedModule?)
        	if (p.getMethods() == moduleMethods) {
        		return;
        	}
        }
        
        // Invalidate cache for all methods in the new included module in case a base class method
        // of the same name has already been cached.
        for (Iterator iter = moduleMethods.keySet().iterator(); iter.hasNext();) {
        	String methodName = (String) iter.next();
            getRuntime().getCacheMap().remove(methodName, searchMethod(methodName));
        }
        
        // Include new module
        setSuperClass(module.newIncludeClass(getSuperClass()));
        
        // Try to include all included modules from module just added 
        for (RubyModule p = module.getSuperClass(); p != null; 
        	p = p.getSuperClass()) {
        	includeModule(p);
        }
        
        module.callMethod("included", this);
    }

    public void defineMethod(String name, Callback method) {
        Visibility visibility = name.equals("initialize") ? 
        		Visibility.PRIVATE : Visibility.PUBLIC;

        addMethod(name, new CallbackMethod(this, method, visibility));
    }

    public void definePrivateMethod(String name, Callback method) {
        addMethod(name, new CallbackMethod(this, method, Visibility.PRIVATE));
    }

    public void undefineMethod(String name) {
        addMethod(name, UndefinedMethod.getInstance());
    }

    /** rb_undef
     *
     */
    public void undef(String name) {
        Ruby runtime = getRuntime();
        if (this == runtime.getObject()) {
            runtime.secure(4);
        }
        if (runtime.getSafeLevel() >= 4 && !isTaint()) {
            throw new SecurityException("Insecure: can't undef");
        }
        testFrozen("module");
        if (name.equals("__id__") || name.equals("__send__")) {
            /*rb_warn("undefining `%s' may cause serious problem",
                     rb_id2name( id ) );*/
        }
        ICallable method = searchMethod(name);
        if (method.isUndefined()) {
            String s0 = " class";
            RubyModule c = this;

            if (c.isSingleton()) {
                IRubyObject obj = getInstanceVariable("__attached__");

                if (obj != null && obj instanceof RubyModule) {
                    c = (RubyModule) obj;
                    s0 = "";
                }
            } else if (c.isModule()) {
                s0 = " module";
            }

            throw getRuntime().newNameError("Undefined method " + name + " for" + s0 + " '" + c.getName() + "'");
        }
        addMethod(name, UndefinedMethod.getInstance());
    }

    private void addCachedMethod(String name, ICallable method) {
        // Included modules modify the original 'included' modules class.  Since multiple
    	// classes can include the same module, we cannot cache in the original included module.
        if (!isIncluded()) {
            getMethods().put(name, method);
            getRuntime().getCacheMap().add(method, this);
        }
    }
    
    // TODO: Consider a better way of synchronizing 
    public void addMethod(String name, ICallable method) {
        if (this == getRuntime().getObject()) {
            getRuntime().secure(4);
        }

        if (getRuntime().getSafeLevel() >= 4 && !isTaint()) {
            throw getRuntime().newSecurityError("Insecure: can't define method");
        }
        testFrozen("class/module");

        // We can safely reference methods here instead of doing getMethods() since if we
        // are adding we are not using a IncludedModuleWrapper.
        synchronized(getMethods()) {
            // If we add a method which already is cached in this class, then we should update the 
            // cachemap so it stays up to date.
            ICallable existingMethod = (ICallable) getMethods().remove(name);
            if (existingMethod != null) {
    	        getRuntime().getCacheMap().remove(name, existingMethod);
            }

            getMethods().put(name, method);
        }
    }

    public void removeCachedMethod(String name) {
    	getMethods().remove(name);
    }

    public void removeMethod(String name) {
        if (this == getRuntime().getObject()) {
            getRuntime().secure(4);
        }
        if (getRuntime().getSafeLevel() >= 4 && !isTaint()) {
            throw getRuntime().newSecurityError("Insecure: can't remove method");
        }
        testFrozen("class/module");

        // We can safely reference methods here instead of doing getMethods() since if we
        // are adding we are not using a IncludedModuleWrapper.
        synchronized(getMethods()) {
            ICallable method = (ICallable) getMethods().remove(name);
            if (method == null) {
                throw getRuntime().newNameError("method '" + name + "' not defined in " + getName());
            }
        
            getRuntime().getCacheMap().remove(name, method);
        }
    }
    /**
     * Search through this module and supermodules for method definitions. Cache superclass definitions in this class.
     * 
     * @param name The name of the method to search for
     * @return The method, or UndefinedMethod if not found
     */
    public ICallable searchMethod(String name) {
    	for (RubyModule searchModule = this; searchModule != null; searchModule = searchModule.getSuperClass()) {
	    	synchronized(searchModule.methods) {
	    	    // See if current class has method or if it has been cached here already
	            ICallable method = (ICallable) searchModule.getMethods().get(name);
	            if (method != null) {
	            	if (searchModule != this) {
	            		addCachedMethod(name, method);
	            	}
	            	
	                return method;
	            }
	    	}
    	}

    	return UndefinedMethod.getInstance();
    }
    
    /** rb_define_module_function
     *
     */
    public void defineModuleFunction(String name, Callback method) {
        definePrivateMethod(name, method);
        defineSingletonMethod(name, method);
    }

    public IRubyObject getConstantAtOrConstantMissing(String name) {
        IRubyObject constant = getConstantAt(name);

        if (constant != null) {
             return constant;
        }

    	for (RubyModule p = getSuperClass(); p != null; p = p.getSuperClass()) {
            constant = p.getConstantAt(name);
            if (constant != null) {
                return constant;
            }
        }
        
        return callMethod("const_missing", RubySymbol.newSymbol(getRuntime(), name));
    }

    public IRubyObject getConstantAt(String name) {
    	IRubyObject constant = getInstanceVariable(name);
    	
    	if (constant != null) {
    		return constant;
    	} 
    	
    	if (this == getRuntime().getObject()) {
    		return getConstant(name, false);
    	}
    	return null;
    }

    /** rb_call
     *
     */
    public final IRubyObject call(IRubyObject recv, String name, IRubyObject[] args, CallType callType) {
    	assert args != null;

        ICallable method = searchMethod(name);

        if (method.isUndefined()) {
            callType.registerCallStatus(getRuntime().getLastCallStatus(), name);
            return callMethodMissing(recv, name, args);
        }

        if (!name.equals("method_missing")) {
            if (method.getVisibility().isPrivate() && callType.isNormal()) {
                getRuntime().getLastCallStatus().setPrivate();
                return callMethodMissing(recv, name, args);
            } else if (method.getVisibility().isProtected()) {
                RubyModule defined = method.getImplementationClass();
                while (defined.isIncluded()) {
                    defined = defined.getMetaClass();
                }
                if (!getRuntime().getCurrentFrame().getSelf().isKindOf(defined)) {
                    getRuntime().getLastCallStatus().setProtected();
                    return callMethodMissing(recv, name, args);
                }
            }
        }
        
        String originalName = method.getOriginalName();
        if (originalName != null) {
        	name = originalName;
        }

        return method.getImplementationClass().call0(recv, name, args, method, false);
    }

    private IRubyObject callMethodMissing(IRubyObject receiver, String name, IRubyObject[] args) {
    	Ruby runtime = getRuntime();
        if (name == "method_missing") {
            runtime.getFrameStack().push(new Frame(runtime.getCurrentContext()));
            try {
                return receiver.method_missing(args);
            } finally {
                runtime.getFrameStack().pop();
            }
        }

        IRubyObject[] newArgs = new IRubyObject[args.length + 1];
        System.arraycopy(args, 0, newArgs, 1, args.length);
        newArgs[0] = RubySymbol.newSymbol(runtime, name);

        return receiver.callMethod("method_missing", newArgs);
    }

    /** rb_call0
     *
     */
    public final IRubyObject call0(IRubyObject recv, String name, IRubyObject[] args,
        ICallable method, boolean noSuper) {
        ThreadContext context = getRuntime().getCurrentContext();
		RubyModule oldParent = context.setRubyClass(parentModule);

		context.getIterStack().push(context.getCurrentIter().isPre() ? Iter.ITER_CUR : Iter.ITER_NOT);
        context.getFrameStack().push(new Frame(context, recv, args, name, noSuper ? null : this));

        try {
            return method.call(getRuntime(), recv, name, args, noSuper);
        } finally {
            context.getFrameStack().pop();
            context.getIterStack().pop();
			context.setRubyClass(oldParent);
        }
    }

    /** rb_alias
     *
     */
    public synchronized void defineAlias(String name, String oldName) {
        testFrozen("module");
        if (oldName.equals(name)) {
            return;
        }
        if (this == getRuntime().getObject()) {
            getRuntime().secure(4);
        }
        ICallable method = searchMethod(oldName);
        if (method.isUndefined()) {
            if (isModule()) {
                method = getRuntime().getObject().searchMethod(oldName);
            }
            
            if (method.isUndefined()) {
                throw getRuntime().newNameError("undefined method `" + oldName + "' for " +
                    (isModule() ? "module" : "class") + " `" + getName() + "'");
            }
        }
        getRuntime().getCacheMap().remove(name, searchMethod(name));
        getMethods().put(name, new AliasMethod(method, oldName));
    }

    public RubyClass defineOrGetClassUnder(String name, RubyClass superClazz) {
        IRubyObject type = getConstantAt(name);
        
        if (type == null) {
            return (RubyClass) setConstant(name, 
            		getRuntime().defineClassUnder(name, superClazz, this)); 
        }

        if (!(type instanceof RubyClass)) {
        	throw getRuntime().newTypeError(name + " is not a class.");
        }
            
        return (RubyClass) type;
    }
    
    /** rb_define_class_under
     *
     */
    public RubyClass defineClassUnder(String name, RubyClass superClazz) {
    	IRubyObject type = getConstantAt(name);
    	
    	if (type == null) {
            return (RubyClass) setConstant(name, 
            		getRuntime().defineClassUnder(name, superClazz, this)); 
    	}

    	if (!(type instanceof RubyClass)) {
    		throw getRuntime().newTypeError(name + " is not a class.");
        } else if (((RubyClass) type).getSuperClass().getRealClass() != superClazz) {
        	throw getRuntime().newNameError(name + " is already defined.");
        } 
            
    	return (RubyClass) type;
    }

    public RubyModule defineModuleUnder(String name) {
        IRubyObject type = getConstantAt(name);
        
        if (type == null) {
            return (RubyModule) setConstant(name, 
            		getRuntime().defineModuleUnder(name, this)); 
        }

        if (!(type instanceof RubyModule)) {
        	throw getRuntime().newTypeError(name + " is not a module.");
        } 

        return (RubyModule) type;
    }

    /** rb_define_const
     *
     */
    public void defineConstant(String name, IRubyObject value) {
        assert value != null;

        if (this == getRuntime().getClass("Class")) {
            getRuntime().secure(4);
        }

        if (!IdUtil.isConstant(name)) {
            throw getRuntime().newNameError("bad constant name " + name);
        }

        setConstant(name, value);
    }

    /** rb_mod_remove_cvar
     *
     */
    public IRubyObject removeCvar(IRubyObject name) { // Wrong Parameter ?
        if (!IdUtil.isClassVariable(name.asSymbol())) {
            throw getRuntime().newNameError("wrong class variable name " + name.asSymbol());
        }

        if (!isTaint() && getRuntime().getSafeLevel() >= 4) {
            throw getRuntime().newSecurityError("Insecure: can't remove class variable");
        }
        testFrozen("class/module");

        IRubyObject value = removeInstanceVariable(name.asSymbol());

        if (value != null) {
            return value;
        }

        if (isClassVarDefined(name.asSymbol())) {
            throw getRuntime().newNameError("cannot remove " + name.asSymbol() + " for " + getName());
        }

        throw getRuntime().newNameError("class variable " + name.asSymbol() + " not defined for " + getName());
    }

    private void addAccessor(String name, boolean readable, boolean writeable) {
        Visibility attributeScope = getRuntime().getCurrentVisibility();
        if (attributeScope.isPrivate()) {
            //FIXME warning
        } else if (attributeScope.isModuleFunction()) {
            attributeScope = Visibility.PRIVATE;
            // FIXME warning
        }
        final String variableName = "@" + name;
		final Ruby runtime = getRuntime();
        if (readable) {
            defineMethod(name, new Callback() {
                public IRubyObject execute(IRubyObject self, IRubyObject[] args) {
		    	    IRubyObject variable = self.getInstanceVariable(variableName);
		    	
		            return variable == null ? runtime.getNil() : variable;
                }

                public Arity getArity() {
                    return Arity.noArguments();
                }
            });
            callMethod("method_added", RubySymbol.newSymbol(getRuntime(), name));
        }
        if (writeable) {
            name = name + "=";
            defineMethod(name, new Callback() {
                public IRubyObject execute(IRubyObject self, IRubyObject[] args) {
					IRubyObject[] fargs = runtime.getCurrentFrame().getArgs();
					
			        if (fargs.length != 1) {
			            throw runtime.newArgumentError("wrong # of arguments(" + fargs.length + "for 1)");
			        }

			        return self.setInstanceVariable(variableName, fargs[0]);
                }

                public Arity getArity() {
                    return Arity.singleArgument();
                }
            });
            callMethod("method_added", RubySymbol.newSymbol(getRuntime(), name));
        }
    }

    /** set_method_visibility
     *
     */
    public void setMethodVisibility(IRubyObject[] methods, Visibility visibility) {
        if (getRuntime().getSafeLevel() >= 4 && !isTaint()) {
            throw getRuntime().newSecurityError("Insecure: can't change method visibility");
        }

        for (int i = 0; i < methods.length; i++) {
            exportMethod(methods[i].asSymbol(), visibility);
        }
    }

    /** rb_export_method
     *
     */
    public void exportMethod(String name, Visibility visibility) {
        if (this == getRuntime().getObject()) {
            getRuntime().secure(4);
        }

        ICallable method = searchMethod(name);

        if (method.isUndefined()) {
            throw getRuntime().newNameError("undefined method '" + name + "' for " + 
                                (isModule() ? "module" : "class") + " '" + getName() + "'");
        }

        if (method.getVisibility() != visibility) {
            if (this == method.getImplementationClass()) {
                method.setVisibility(visibility);
            } else {
                final ThreadContext context = getRuntime().getCurrentContext();
                addMethod(name, new CallbackMethod(this, new Callback() {
	                public IRubyObject execute(IRubyObject self, IRubyObject[] args) {
				        return context.callSuper(context.getCurrentFrame().getArgs());
	                }

	                public Arity getArity() {
	                    return Arity.optional();
	                }
	            }, visibility));
            }
        }
    }

    /**
     * MRI: rb_method_boundp
     *
     */
    public boolean isMethodBound(String name, boolean checkVisibility) {
        ICallable method = searchMethod(name);
        if (!method.isUndefined()) {
            return !(checkVisibility && method.getVisibility().isPrivate());
        }
        return false;
    }

    public IRubyObject newMethod(IRubyObject receiver, String name, boolean bound) {
        ICallable method = searchMethod(name);
        if (method.isUndefined()) {
            throw getRuntime().newNameError("undefined method `" + name + 
                "' for class `" + this.getName() + "'");
        }

        RubyMethod newMethod = null;
        if (bound) {
            newMethod = RubyMethod.newMethod(method.getImplementationClass(), name, this, name, method, receiver);
        } else {
            newMethod = RubyUnboundMethod.newUnboundMethod(method.getImplementationClass(), name, this, name, method);
        }
        newMethod.infectBy(this);

        return newMethod;
    }

    // What is argument 1 for in this method?
    public IRubyObject define_method(IRubyObject[] args) {
        if (args.length < 1 || args.length > 2) {
            throw getRuntime().newArgumentError("wrong # of arguments(" + args.length + " for 1)");
        }

        String name = args[0].asSymbol();
        IRubyObject body;
        ICallable newMethod;
        Visibility visibility = getRuntime().getCurrentVisibility();

        if (visibility.isModuleFunction()) {
            visibility = Visibility.PRIVATE;
        }
        
        if (args.length == 1) {
            body = getRuntime().newProc();
            newMethod = new ProcMethod(this, (RubyProc)body, visibility);
        } else if (args[1].isKindOf(getRuntime().getClass("Method"))) {
            body = args[1];
            newMethod = new MethodMethod(this, ((RubyMethod)body).unbind(), visibility);
        } else if (args[1].isKindOf(getRuntime().getClass("Proc"))) {
            body = args[1];
            newMethod = new ProcMethod(this, (RubyProc)body, visibility);
        } else {
            throw getRuntime().newTypeError("wrong argument type " + args[0].getType().getName() + " (expected Proc/RubyMethod)");
        }

        addMethod(name, newMethod);

        RubySymbol symbol = RubySymbol.newSymbol(getRuntime(), name);
        if (getRuntime().getCurrentVisibility().isModuleFunction()) {
            getSingletonClass().addMethod(name, new WrapperCallable(getSingletonClass(), newMethod, Visibility.PUBLIC));
            callMethod("singleton_method_added", symbol);
        }

        callMethod("method_added", symbol);

        return body;
    }

    public IRubyObject executeUnder(Callback method, IRubyObject[] args) {
        ThreadContext context = getRuntime().getCurrentContext();
		RubyModule oldParent = context.setRubyClass(this);

        Frame frame = context.getCurrentFrame();
        context.getFrameStack().push(new Frame(context, null, frame.getArgs(), 
            frame.getLastFunc(), frame.getLastClass()));

        try {
            return method.execute(this, args);
        } finally {
            context.getFrameStack().pop();
			context.setRubyClass(oldParent);
        }
    }

    // Methods of the Module Class (rb_mod_*):

    public static RubyModule newModule(Ruby runtime, String name) {
        return newModule(runtime, name, null);
    }

    public static RubyModule newModule(Ruby runtime, String name, RubyModule parentModule) {
        // Modules do not directly define Object as their superClass even though in theory they
    	// should.  The C version of Ruby may also do this (special checks in rb_alias for Module
    	// makes me think this).
        return new RubyModule(runtime, runtime.getClass("Module"), null, parentModule, name);
    }
    
    /** rb_mod_name
     *
     */
    public RubyString name() {
        return getRuntime().newString(getName());
    }

    /** rb_mod_class_variables
     *
     */
    public RubyArray class_variables() {
        RubyArray ary = getRuntime().newArray();

        for (RubyModule p = this; p != null; p = p.getSuperClass()) {
            for (Iterator iter = p.instanceVariableNames(); iter.hasNext();) {
                String id = (String) iter.next();
                if (IdUtil.isClassVariable(id)) {
                    RubyString kval = getRuntime().newString(id);
                    if (!ary.includes(kval)) {
                        ary.append(kval);
                    }
                }
            }
        }
        return ary;
    }

    /** rb_mod_clone
     *
     */
    public IRubyObject rbClone() {
        return cloneMethods((RubyModule) super.rbClone());
    }
    
    protected IRubyObject cloneMethods(RubyModule clone) {
    	RubyModule realType = isIncluded() ? ((IncludedModuleWrapper) this).getDelegate() : this;
        for (Iterator iter = getMethods().entrySet().iterator(); iter.hasNext(); ) {
            Map.Entry entry = (Map.Entry) iter.next();
            ICallable method = (ICallable) entry.getValue();

            // Do not clone cached methods
            if (method.getImplementationClass() == realType) {            
                // A cloned method now belongs to a new class.  Set it.
                // TODO: Make ICallable immutable
                ICallable clonedMethod = method.dup();
                clonedMethod.setImplementationClass(clone);
                clone.getMethods().put(entry.getKey(), clonedMethod);
            }
        }
        
        return clone;
    }
    
    protected IRubyObject doClone() {
    	return RubyModule.newModule(getRuntime(), getBaseName(), parentModule);
    }

    /** rb_mod_dup
     *
     */
    public IRubyObject dup() {
        RubyModule dup = (RubyModule) rbClone();
        dup.setMetaClass(getMetaClass());
        dup.setFrozen(false);
        // +++ jpetersen
        // dup.setSingleton(isSingleton());
        // --- jpetersen

        return dup;
    }

    /** rb_mod_included_modules
     *
     */
    public RubyArray included_modules() {
        RubyArray ary = getRuntime().newArray();

        for (RubyModule p = getSuperClass(); p != null; p = p.getSuperClass()) {
            if (p.isIncluded()) {
                ary.append(((IncludedModuleWrapper) p).getDelegate());
            }
        }

        return ary;
    }

    /** rb_mod_ancestors
     *
     */
    public RubyArray ancestors() {
        RubyArray ary = getRuntime().newArray();

        for (RubyModule p = this; p != null; p = p.getSuperClass()) {
            if (p.isSingleton()) {
                continue;
            }

            if (p.isIncluded()) {
                ary.append(((IncludedModuleWrapper) p).getDelegate());
            } else {
                ary.append(p);
            }
        }

        return ary;
    }

    /** rb_mod_to_s
     *
     */
    public RubyString to_s() {
        return getRuntime().newString(getName());
    }

    /** rb_mod_eqq
     *
     */
    public RubyBoolean op_eqq(IRubyObject obj) {
        return getRuntime().newBoolean(obj.isKindOf(this));
    }

    /** rb_mod_le
     *
     */
    public RubyBoolean op_le(IRubyObject obj) {
        if (!(obj instanceof RubyModule)) {
            throw getRuntime().newTypeError("compared with non class/module");
        }

        for (RubyModule p = this; p != null; p = p.getSuperClass()) { 
            if (p.getMethods() == ((RubyModule) obj).getMethods()) {
                return getRuntime().getTrue();
            }
        }

        return getRuntime().getFalse();
    }

    /** rb_mod_lt
     *
     */
    public RubyBoolean op_lt(IRubyObject obj) {
    	return obj == this ? getRuntime().getFalse() : op_le(obj); 
    }

    /** rb_mod_ge
     *
     */
    public RubyBoolean op_ge(IRubyObject obj) {
        if (!(obj instanceof RubyModule)) {
            throw getRuntime().newTypeError("compared with non class/module");
        }

        return ((RubyModule) obj).op_le(this);
    }

    /** rb_mod_gt
     *
     */
    public RubyBoolean op_gt(IRubyObject obj) {
        return this == obj ? getRuntime().getFalse() : op_ge(obj);
    }

    /** rb_mod_cmp
     *
     */
    public RubyFixnum op_cmp(IRubyObject obj) {
        if (this == obj) {
            return getRuntime().newFixnum(0);
        }

        if (!(obj instanceof RubyModule)) {
            throw getRuntime().newTypeError(
                "<=> requires Class or Module (" + getMetaClass().getName() + " given)");
        }

        return getRuntime().newFixnum(
                op_le(obj).isTrue() ? -1 : 1);
    }

    /** rb_mod_initialize
     *
     */
    public IRubyObject initialize(IRubyObject[] args) {
        return getRuntime().getNil();
    }

    /** rb_mod_attr
     *
     */
    public IRubyObject attr(IRubyObject[] args) {
    	checkArgumentCount(args, 1, 2);
        boolean writeable = args.length > 1 ? args[1].isTrue() : false;
        
        addAccessor(args[0].asSymbol(), true, writeable);

        return getRuntime().getNil();
    }

    /** rb_mod_attr_reader
     *
     */
    public IRubyObject attr_reader(IRubyObject[] args) {
        for (int i = 0; i < args.length; i++) {
            addAccessor(args[i].asSymbol(), true, false);
        }

        return getRuntime().getNil();
    }

    /** rb_mod_attr_writer
     *
     */
    public IRubyObject attr_writer(IRubyObject[] args) {
        for (int i = 0; i < args.length; i++) {
            addAccessor(args[i].asSymbol(), false, true);
        }

        return getRuntime().getNil();
    }

    /** rb_mod_attr_accessor
     *
     */
    public IRubyObject attr_accessor(IRubyObject[] args) {
        for (int i = 0; i < args.length; i++) {
            addAccessor(args[i].asSymbol(), true, true);
        }

        return getRuntime().getNil();
    }

    /** rb_mod_const_get
     *
     */
    public IRubyObject const_get(IRubyObject symbol) {
        String name = symbol.asSymbol();

        if (!IdUtil.isConstant(name)) {
            throw getRuntime().newNameError("wrong constant name " + name);
        }

        return getConstant(name);
    }

    /** rb_mod_const_set
     *
     */
    public IRubyObject const_set(IRubyObject symbol, IRubyObject value) {
        String name = symbol.asSymbol();

        if (!IdUtil.isConstant(name)) {
            throw getRuntime().newNameError("wrong constant name " + name);
        }

        return setConstant(name, value); 
    }

    /** rb_mod_const_defined
     *
     */
    public RubyBoolean const_defined(IRubyObject symbol) {
        String name = symbol.asSymbol();

        if (!IdUtil.isConstant(name)) {
            throw getRuntime().newNameError("wrong constant name " + name);
        }

        return getRuntime().newBoolean(getConstant(name, false) != null);
    }

    private RubyArray instance_methods(IRubyObject[] args, final Visibility visibility) {
        boolean includeSuper = args.length > 0 ? args[0].isTrue() : true;
        RubyArray ary = getRuntime().newArray();

        for (RubyModule type = this; type != null; type = type.getSuperClass()) {
        	RubyModule realType = type.isIncluded() ? ((IncludedModuleWrapper) type).getDelegate() : type;
            for (Iterator iter = type.getMethods().entrySet().iterator(); iter.hasNext();) {
                Map.Entry entry = (Map.Entry) iter.next();
                ICallable method = (ICallable) entry.getValue();

                if (method.getImplementationClass() == realType && 
                    method.getVisibility().is(visibility) && !method.isUndefined()) {
                    RubyString name = getRuntime().newString((String) entry.getKey());

                    if (!ary.includes(name)) {
                        ary.append(name);
                    }
                } 
            }
				
            if (!includeSuper) {
                break;
            }
        }

        return ary;
    }

    public RubyArray instance_methods(IRubyObject[] args) {
        return instance_methods(args, Visibility.PUBLIC_PROTECTED);
    }

    public RubyArray public_instance_methods(IRubyObject[] args) {
    	return instance_methods(args, Visibility.PUBLIC);
    }

    public IRubyObject instance_method(IRubyObject symbol) {
        return newMethod(null, symbol.asSymbol(), false);
    }

    /** rb_class_protected_instance_methods
     *
     */
    public RubyArray protected_instance_methods(IRubyObject[] args) {
        return instance_methods(args, Visibility.PROTECTED);
    }

    /** rb_class_private_instance_methods
     *
     */
    public RubyArray private_instance_methods(IRubyObject[] args) {
        return instance_methods(args, Visibility.PRIVATE);
    }

    /** rb_mod_constants
     *
     */
    public RubyArray constants() {
        ArrayList constantNames = new ArrayList();
        RubyModule objectClass = getRuntime().getObject();
        
        if (getRuntime().getClass("Module") == this) {
            for (Iterator vars = objectClass.instanceVariableNames(); 
            	vars.hasNext();) {
                String name = (String) vars.next();
                if (IdUtil.isConstant(name)) {
                    constantNames.add(getRuntime().newString(name));
                }
            }
            
            return getRuntime().newArray(constantNames);
        } else if (getRuntime().getObject() == this) {
            for (Iterator vars = instanceVariableNames(); vars.hasNext();) {
                String name = (String) vars.next();
                if (IdUtil.isConstant(name)) {
                    constantNames.add(getRuntime().newString(name));
                }
            }

            return getRuntime().newArray(constantNames);
        }

        for (RubyModule p = this; p != null; p = p.getSuperClass()) {
            if (objectClass == p) {
                continue;
            }
            
            for (Iterator vars = p.instanceVariableNames(); vars.hasNext();) {
                String name = (String) vars.next();
                if (IdUtil.isConstant(name)) {
                    constantNames.add(getRuntime().newString(name));
                }
            }
        }
        
        return getRuntime().newArray(constantNames);
    }

    /** rb_mod_remove_cvar
     *
     */
    public IRubyObject remove_class_variable(IRubyObject name) {
        String id = name.asSymbol();

        if (!IdUtil.isClassVariable(id)) {
            throw getRuntime().newNameError("wrong class variable name " + id);
        }
        if (!isTaint() && getRuntime().getSafeLevel() >= 4) {
            throw getRuntime().newSecurityError("Insecure: can't remove class variable");
        }
        testFrozen("class/module");

        IRubyObject variable = removeInstanceVariable(id); 
        if (variable != null) {
            return variable;
        }

        if (isClassVarDefined(id)) {
            throw getRuntime().newNameError("cannot remove " + id + " for " + getName());
        }
        throw getRuntime().newNameError("class variable " + id + " not defined for " + getName());
    }
    
    public IRubyObject remove_const(IRubyObject name) {
        String id = name.asSymbol();

        if (!IdUtil.isConstant(id)) {
            throw getRuntime().newNameError("wrong constant name " + id);
        }
        if (!isTaint() && getRuntime().getSafeLevel() >= 4) {
            throw getRuntime().newSecurityError("Insecure: can't remove class variable");
        }
        testFrozen("class/module");

        IRubyObject variable = getInstanceVariable(id);
        if (variable != null) {
            return removeInstanceVariable(id);
        }

        if (isClassVarDefined(id)) {
            throw getRuntime().newNameError("cannot remove " + id + " for " + getName());
        }
        throw getRuntime().newNameError("constant " + id + " not defined for " + getName());
    }
    
    /** rb_mod_append_features
     *
     */
    // TODO: Proper argument check (conversion?)
    public RubyModule append_features(IRubyObject module) {
        ((RubyModule) module).includeModule(this);
        return this;
    }

    /** rb_mod_extend_object
     *
     */
    public IRubyObject extend_object(IRubyObject obj) {
        obj.extendObject(this);
        return obj;
    }

    /** rb_mod_include
     *
     */
    public RubyModule include(IRubyObject[] modules) {
        for (int i = 0; i < modules.length; i++) {
            modules[i].callMethod("append_features", this);
        }

        return this;
    }
    
    public IRubyObject included(IRubyObject other) {
        return getRuntime().getNil();
    }

    private void setVisibility(IRubyObject[] args, Visibility visibility) {
        if (getRuntime().getSafeLevel() >= 4 && !isTaint()) {
            throw getRuntime().newSecurityError("Insecure: can't change method visibility");
        }

        if (args.length == 0) {
            getRuntime().setCurrentVisibility(visibility);
        } else {
            setMethodVisibility(args, visibility);
        }
    }
    
    /** rb_mod_public
     *
     */
    public RubyModule rbPublic(IRubyObject[] args) {
        setVisibility(args, Visibility.PUBLIC);
        return this;
    }

    /** rb_mod_protected
     *
     */
    public RubyModule rbProtected(IRubyObject[] args) {
        setVisibility(args, Visibility.PROTECTED);
        return this;
    }

    /** rb_mod_private
     *
     */
    public RubyModule rbPrivate(IRubyObject[] args) {
        setVisibility(args, Visibility.PRIVATE);
        return this;
    }

    /** rb_mod_modfunc
     *
     */
    public RubyModule module_function(IRubyObject[] args) {
        if (getRuntime().getSafeLevel() >= 4 && !isTaint()) {
            throw getRuntime().newSecurityError("Insecure: can't change method visibility");
        }

        if (args.length == 0) {
            getRuntime().setCurrentVisibility(Visibility.MODULE_FUNCTION);
        } else {
            setMethodVisibility(args, Visibility.PRIVATE);

            for (int i = 0; i < args.length; i++) {
                String name = args[i].asSymbol();
                ICallable method = searchMethod(name);
                assert !method.isUndefined() : "undefined method '" + name + "'";
                getSingletonClass().addMethod(name, new WrapperCallable(getSingletonClass(), method, Visibility.PUBLIC));
                callMethod("singleton_method_added", RubySymbol.newSymbol(getRuntime(), name));
            }
        }
        return this;
    }
    
    public IRubyObject method_added(IRubyObject nothing) {
    	return getRuntime().getNil();
    }

    public RubyBoolean method_defined(IRubyObject symbol) {
        return isMethodBound(symbol.asSymbol(), true) ? getRuntime().getTrue() : getRuntime().getFalse();
    }

    public RubyModule public_class_method(IRubyObject[] args) {
        getMetaClass().setMethodVisibility(args, Visibility.PUBLIC);
        return this;
    }

    public RubyModule private_class_method(IRubyObject[] args) {
        getMetaClass().setMethodVisibility(args, Visibility.PRIVATE);
        return this;
    }

    public RubyModule alias_method(IRubyObject newId, IRubyObject oldId) {
        defineAlias(newId.asSymbol(), oldId.asSymbol());
        return this;
    }

    public RubyModule undef_method(IRubyObject name) {
        undef(name.asSymbol());
        return this;
    }

    public IRubyObject module_eval(IRubyObject[] args) {
        return specificEval(this, args);
    }

    public RubyModule remove_method(IRubyObject name) {
        removeMethod(name.asSymbol());
        return this;
    }

    public void marshalTo(MarshalStream output) throws java.io.IOException {
        output.write('m');
        output.dumpString(name().toString());
    }

    public static RubyModule unmarshalFrom(UnmarshalStream input) throws java.io.IOException {
        String name = input.unmarshalString();
        Ruby runtime = input.getRuntime();
        RubyModule result = runtime.getClassFromPath(name);
        if (result == null) {
            throw runtime.newNameError("uninitialized constant " + name);
        }
        input.registerLinkTarget(result);
        return result;
    }
}
