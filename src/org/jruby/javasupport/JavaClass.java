/*
 * Copyright (C) 2002 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004 David Corbin <dcorbin@users.sourceforge.net>
 *
 * JRuby - http://jruby.sourceforge.net
 *
 * This file is part of JRuby
 *
 * JRuby is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * JRuby is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with JRuby; if not, write to
 * the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA  02111-1307 USA
 */
package org.jruby.javasupport;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyBoolean;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyInteger;
import org.jruby.RubyModule;
import org.jruby.RubyString;
import org.jruby.exceptions.NameError;
import org.jruby.exceptions.TypeError;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.builtin.IRubyObject;

public class JavaClass extends JavaObject {

    private JavaClass(Ruby runtime, String name) {
        this(runtime, runtime.getJavaSupport().loadJavaClass(name));
    }

    public JavaClass(Ruby runtime, Class javaClass) {
        super(runtime, (RubyClass) runtime.getClasses().getClassFromPath("Java::JavaClass"), javaClass);
    }

    public static RubyClass createJavaClassClass(Ruby runtime, RubyModule javaModule) {
        RubyClass result = javaModule.defineClassUnder("JavaClass", runtime.getClasses().getObjectClass()); 

    	CallbackFactory callbackFactory = runtime.callbackFactory(JavaClass.class);
        
        result.includeModule(runtime.getClasses().getComparableModule());
        
        JavaObject.registerRubyMethods(runtime, result);
        
        result.defineSingletonMethod("for_name", 
                callbackFactory.getSingletonMethod("for_name", IRubyObject.class));
        result.defineMethod("public?", 
                callbackFactory.getMethod("public_p"));
        result.defineMethod("final?", 
                callbackFactory.getMethod("final_p"));
        result.defineMethod("interface?", 
                callbackFactory.getMethod("interface_p"));
        result.defineMethod("array?", 
                callbackFactory.getMethod("array_p"));
        result.defineMethod("name", 
                callbackFactory.getMethod("name"));
        result.defineMethod("to_s", 
                callbackFactory.getMethod("name"));
        result.defineMethod("superclass", 
                callbackFactory.getMethod("superclass"));
        result.defineMethod("<=>", 
                callbackFactory.getMethod("op_cmp", IRubyObject.class));
        result.defineMethod("java_instance_methods", 
                callbackFactory.getMethod("java_instance_methods"));
        result.defineMethod("java_class_methods", 
                callbackFactory.getMethod("java_class_methods"));
        result.defineMethod("java_method", 
                callbackFactory.getOptMethod("java_method"));
        result.defineMethod("constructors", 
                callbackFactory.getMethod("constructors"));
        result.defineMethod("constructor", 
                callbackFactory.getOptMethod("constructor"));
        result.defineMethod("array_class", 
                callbackFactory.getMethod("array_class"));
        result.defineMethod("new_array", 
                callbackFactory.getMethod("new_array", IRubyObject.class));
        result.defineMethod("fields", 
                callbackFactory.getMethod("fields"));
        result.defineMethod("field", 
                callbackFactory.getMethod("field", IRubyObject.class));
        result.defineMethod("interfaces", 
                callbackFactory.getMethod("interfaces"));
        result.defineMethod("primitive?", 
                callbackFactory.getMethod("primitive_p"));
        result.defineMethod("assignable_from?", 
                callbackFactory.getMethod("assignable_from_p", IRubyObject.class));
        result.defineMethod("component_type", 
                callbackFactory.getMethod("component_type"));
		result.defineMethod("declared_instance_methods", 
                callbackFactory.getMethod("declared_instance_methods"));
        result.defineMethod("declared_class_methods", 
                callbackFactory.getMethod("declared_class_methods"));
        result.defineMethod("declared_fields", 
                callbackFactory.getMethod("declared_fields"));
        result.defineMethod("declared_field", 
                callbackFactory.getMethod("declared_field", IRubyObject.class));
        result.defineMethod("declared_constructors", 
                callbackFactory.getMethod("declared_constructors"));
        result.defineMethod("declared_constructor", 
                callbackFactory.getOptMethod("declared_constructor"));
        result.defineMethod("declared_method", 
                callbackFactory.getOptMethod("declared_method"));

        result.getMetaClass().undefineMethod("new");

        return result;
    }

    public static JavaClass for_name(IRubyObject recv, IRubyObject name) {
        return new JavaClass(recv.getRuntime(), name.asSymbol());
    }

    public RubyBoolean public_p() {
        return getRuntime().newBoolean(Modifier.isPublic(javaClass().getModifiers()));
    }

	Class javaClass() {
		return (Class) getValue();
	}

    public RubyBoolean final_p() {
        return getRuntime().newBoolean(Modifier.isFinal(javaClass().getModifiers()));
    }

    public RubyBoolean interface_p() {
        return getRuntime().newBoolean(javaClass().isInterface());
    }

    public RubyBoolean array_p() {
        return getRuntime().newBoolean(javaClass().isArray());
    }

    public RubyString name() {
        return getRuntime().newString(javaClass().getName());
    }

    public IRubyObject superclass() {
        Class superclass = javaClass().getSuperclass();
        if (superclass == null) {
            return getRuntime().getNil();
        }
        return new JavaClass(getRuntime(), superclass.getName());
    }

    public RubyFixnum op_cmp(IRubyObject other) {
        if (! (other instanceof JavaClass)) {
            throw getRuntime().newTypeError("<=> requires JavaClass (" + other.getType() + " given)");
        }
        JavaClass otherClass = (JavaClass) other;
        if (this.javaClass() == otherClass.javaClass()) {
            return getRuntime().newFixnum(0);
        }
        if (otherClass.javaClass().isAssignableFrom(this.javaClass())) {
            return getRuntime().newFixnum(-1);
        }
        return getRuntime().newFixnum(1);
    }

    public RubyArray java_instance_methods() {
        return java_methods(javaClass().getMethods(), false);
    }

    public RubyArray declared_instance_methods() {
        return java_methods(javaClass().getDeclaredMethods(), false);
    }

	private RubyArray java_methods(Method[] methods, boolean isStatic) {
        RubyArray result = getRuntime().newArray(methods.length);
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (isStatic == Modifier.isStatic(method.getModifiers())) {
                result.append(JavaMethod.create(getRuntime(), method));
            }
        }
        return result;
	}

	public RubyArray java_class_methods() {
	    return java_methods(javaClass().getMethods(), true);
    }

	public RubyArray declared_class_methods() {
	    return java_methods(javaClass().getDeclaredMethods(), true);
    }

	public JavaMethod java_method(IRubyObject[] args) {
        String methodName = args[0].asSymbol();
        Class[] argumentTypes = buildArgumentTypes(args);
        return JavaMethod.create(getRuntime(), javaClass(), methodName, argumentTypes);
    }

    public JavaMethod declared_method(IRubyObject[] args) {
        String methodName = args[0].asSymbol();
        Class[] argumentTypes = buildArgumentTypes(args);
        return JavaMethod.createDeclared(getRuntime(), javaClass(), methodName, argumentTypes);
    }

    private Class[] buildArgumentTypes(IRubyObject[] args) {
        if (args.length < 1) {
            throw getRuntime().newArgumentError(args.length, 1);
        }
        Class[] argumentTypes = new Class[args.length - 1];
        for (int i = 1; i < args.length; i++) {
            JavaClass type = for_name(this, args[i]);
            argumentTypes[i - 1] = type.javaClass();
        }
        return argumentTypes;
    }

    public RubyArray constructors() {
        return buildConstructors(javaClass().getConstructors());
    }

    public RubyArray declared_constructors() {
        return buildConstructors(javaClass().getDeclaredConstructors());
    }

    private RubyArray buildConstructors(Constructor[] constructors) {
        RubyArray result = getRuntime().newArray(constructors.length);
        for (int i = 0; i < constructors.length; i++) {
            result.append(new JavaConstructor(getRuntime(), constructors[i]));
        }
        return result;
    }

    public JavaConstructor constructor(IRubyObject[] args) {
        try {
            Class[] parameterTypes = buildClassArgs(args);
            Constructor constructor;
            constructor = javaClass().getConstructor(parameterTypes);
            return new JavaConstructor(getRuntime(), constructor);
        } catch (NoSuchMethodException nsme) {
            throw new NameError(getRuntime(), "no matching java constructor");
        }
    }

    public JavaConstructor declared_constructor(IRubyObject[] args) {
        try {
            Class[] parameterTypes = buildClassArgs(args);
            Constructor constructor;
            constructor = javaClass().getDeclaredConstructor (parameterTypes);
            return new JavaConstructor(getRuntime(), constructor);
        } catch (NoSuchMethodException nsme) {
            throw new NameError(getRuntime(), "no matching java constructor");
        }
    }

    private Class[] buildClassArgs(IRubyObject[] args) {
        Class[] parameterTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            String name = args[i].asSymbol();
            parameterTypes[i] = getRuntime().getJavaSupport().loadJavaClass(name);
        }
        return parameterTypes;
    }

    public JavaClass array_class() {
        return new JavaClass(getRuntime(), Array.newInstance(javaClass(), 0).getClass());
    }

    public JavaObject new_array(IRubyObject lengthArgument) {
        if (! (lengthArgument instanceof RubyInteger)) {
            throw getRuntime().newTypeError(lengthArgument, getRuntime().getClasses().getIntegerClass());
        }
        int length = (int) ((RubyInteger) lengthArgument).getLongValue();
        return new JavaArray(getRuntime(), Array.newInstance(javaClass(), length));
    }

    public RubyArray fields() {
        return buildFieldResults(javaClass().getFields());
    }

    public RubyArray declared_fields() {
        return buildFieldResults(javaClass().getDeclaredFields());
    }
    
	private RubyArray buildFieldResults(Field[] fields) {
        RubyArray result = getRuntime().newArray(fields.length);
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            result.append(getRuntime().newString(field.getName()));
        }
        return result;
	}

	public JavaField field(IRubyObject name) {
		String stringName = name.asSymbol();
        try {
            Field field = javaClass().getField(stringName);
			return new JavaField(getRuntime(),field);
        } catch (NoSuchFieldException nsfe) {
            throw new NameError(getRuntime(), undefinedFieldMessage(stringName));
        }
    }

	public JavaField declared_field(IRubyObject name) {
		String stringName = name.asSymbol();
        try {
            Field field = javaClass().getDeclaredField(stringName);
			return new JavaField(getRuntime(),field);
        } catch (NoSuchFieldException nsfe) {
            throw new NameError(getRuntime(), undefinedFieldMessage(stringName));
        }
    }

	private String undefinedFieldMessage(String stringName) {
		return "undefined field '" + stringName + "' for class '" + javaClass().getName() + "'";
	}

	public RubyArray interfaces() {
        Class[] interfaces = javaClass().getInterfaces();
        RubyArray result = getRuntime().newArray(interfaces.length);
        for (int i = 0; i < interfaces.length; i++) {
            result.append(getRuntime().newString(interfaces[i].getName()));
        }
        return result;
    }

    public RubyBoolean primitive_p() {
        return getRuntime().newBoolean(isPrimitive());
    }

    public RubyBoolean assignable_from_p(IRubyObject other) {
        if (! (other instanceof JavaClass)) {
            throw getRuntime().newTypeError("assignable_from requires JavaClass (" + other.getType() + " given)");
        }

        Class otherClass = ((JavaClass) other).javaClass();

        if (!javaClass().isPrimitive() && otherClass == Void.TYPE ||
            javaClass().isAssignableFrom(otherClass)) {
            return getRuntime().getTrue();
        }
        otherClass = JavaUtil.primitiveToWrapper(otherClass);
        Class thisJavaClass = JavaUtil.primitiveToWrapper(javaClass());
        if (thisJavaClass.isAssignableFrom(otherClass)) {
            return getRuntime().getTrue();
        }
        if (Number.class.isAssignableFrom(thisJavaClass)) {
            if (Number.class.isAssignableFrom(otherClass)) {
                return getRuntime().getTrue();
            }
            if (otherClass.equals(Character.class)) {
                return getRuntime().getTrue();
            }
        }
        if (thisJavaClass.equals(Character.class)) {
            if (Number.class.isAssignableFrom(otherClass)) {
                return getRuntime().getTrue();
            }
        }
        return getRuntime().getFalse();
    }

    private boolean isPrimitive() {
        return javaClass().isPrimitive();
    }

    public JavaClass component_type() {
        if (! javaClass().isArray()) {
            throw new TypeError(getRuntime(), "not a java array-class");
        }
        return new JavaClass(getRuntime(), javaClass().getComponentType());
    }
}
