/*
 * Copyright (C) 2002 Anders Bengtsson <ndrsbngtssn@yahoo.se>
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

import org.jruby.RubyObject;
import org.jruby.RubyClass;
import org.jruby.Ruby;
import org.jruby.RubyModule;
import org.jruby.RubyString;
import org.jruby.RubyBoolean;
import org.jruby.RubyArray;
import org.jruby.RubyFixnum;
import org.jruby.exceptions.TypeError;
import org.jruby.exceptions.ArgumentError;
import org.jruby.exceptions.NameError;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.IndexCallable;
import org.jruby.runtime.IndexedCallback;
import org.jruby.runtime.builtin.IRubyObject;

import java.lang.reflect.Modifier;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;

public class JavaClassClass extends RubyObject implements IndexCallable {
    private Class javaClass;

    private JavaClassClass(Ruby runtime, String name) {
        super(runtime, (RubyClass) runtime.getClasses().getClassFromPath("Java::JavaClass"));
        this.javaClass = runtime.getJavaSupport().loadJavaClass(name);
    }

    private static final int PUBLIC_P = 1;
    private static final int FINAL_P = 2;
    private static final int INTERFACE_P = 3;
    private static final int NAME = 5;
    private static final int SUPERCLASS = 7;
    private static final int OP_CMP = 8;
    private static final int JAVA_INSTANCE_METHODS = 11;
    private static final int CONSTANTS = 12;
    private static final int JAVA_METHOD = 13;
    private static final int CONSTRUCTORS = 14;
    private static final int CONSTRUCTOR = 15;

    public static RubyClass createJavaClassClass(Ruby runtime, RubyModule javaModule) {
        RubyClass javaClassClass =
                javaModule.defineClassUnder("JavaClass", runtime.getClasses().getObjectClass());
        javaClassClass.includeModule(runtime.getClasses().getComparableModule());

        javaClassClass.defineSingletonMethod("for_name", CallbackFactory.getSingletonMethod(JavaClassClass.class, "for_name", IRubyObject.class));
        javaClassClass.defineMethod("public?", IndexedCallback.create(PUBLIC_P, 0));
        javaClassClass.defineMethod("final?", IndexedCallback.create(FINAL_P, 0));
        javaClassClass.defineMethod("interface?", IndexedCallback.create(INTERFACE_P, 0));
        javaClassClass.defineMethod("name", IndexedCallback.create(NAME, 0));
        javaClassClass.defineMethod("to_s", IndexedCallback.create(NAME, 0));
        javaClassClass.defineMethod("superclass", IndexedCallback.create(SUPERCLASS, 0));
        javaClassClass.defineMethod("<=>", IndexedCallback.create(OP_CMP, 1));
        javaClassClass.defineMethod("java_instance_methods", IndexedCallback.create(JAVA_INSTANCE_METHODS, 0));
        javaClassClass.defineMethod("constants", IndexedCallback.create(CONSTANTS, 0));
        javaClassClass.defineMethod("java_method", IndexedCallback.createOptional(JAVA_METHOD, 1));
        javaClassClass.defineMethod("constructors", IndexedCallback.create(CONSTRUCTORS, 0));
        javaClassClass.defineMethod("constructor", IndexedCallback.createOptional(CONSTRUCTOR));

        javaClassClass.getInternalClass().undefMethod("new");

        return javaClassClass;
    }

    public static JavaClassClass for_name(IRubyObject recv, IRubyObject name) {
        return new JavaClassClass(recv.getRuntime(), name.toId());
    }

    public RubyBoolean public_p() {
        return RubyBoolean.newBoolean(runtime, Modifier.isPublic(javaClass.getModifiers()));
    }

    public RubyBoolean final_p() {
        return RubyBoolean.newBoolean(runtime, Modifier.isFinal(javaClass.getModifiers()));
    }

    public RubyBoolean interface_p() {
        return RubyBoolean.newBoolean(runtime, javaClass.isInterface());
    }

    public RubyString name() {
        return RubyString.newString(runtime, javaClass.getName());
    }

    public IRubyObject superclass() {
        Class superclass = javaClass.getSuperclass();
        if (superclass == null) {
            return runtime.getNil();
        }
        return new JavaClassClass(runtime, superclass.getName());
    }

    public RubyFixnum op_cmp(IRubyObject other) {
        if (! (other instanceof JavaClassClass)) {
            throw new TypeError(getRuntime(), "<=> requires JavaClass (" + other.getType() + " given)");
        }
        JavaClassClass otherClass = (JavaClassClass) other;
        if (this.javaClass == otherClass.javaClass) {
            return RubyFixnum.newFixnum(getRuntime(), 0);
        }
        if (otherClass.javaClass.isAssignableFrom(this.javaClass)) {
            return RubyFixnum.newFixnum(getRuntime(), -1);
        }
        return RubyFixnum.newFixnum(getRuntime(), 1);
    }

    public RubyArray java_instance_methods() {
        Method[] methods = javaClass.getMethods();
        RubyArray result = RubyArray.newArray(runtime, methods.length);
        for (int i = 0; i < methods.length; i++) {
            result.append(RubyString.newString(runtime, methods[i].getName()));
        }
        return result;
    }

    public RubyArray constants() {
        Field[] fields = javaClass.getFields();
        RubyArray result = RubyArray.newArray(runtime);
        for (int i = 0; i < fields.length; i++) {
            if (isConstant(fields[i])) {
                result.append(RubyString.newString(runtime, fields[i].getName()));
            }
        }
        return result;
    }

    private static boolean isConstant(Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers);
    }

    public JavaMethodClass java_method(IRubyObject[] args) {
        if (args.length < 1) {
            throw new ArgumentError(getRuntime(), args.length, 1);
        }
        String methodName = args[0].toId();
        Class[] argumentTypes = new Class[args.length - 1];
        for (int i = 1; i < args.length; i++) {
            JavaClassClass type = for_name(this, args[i]);
            argumentTypes[i - 1] = type.javaClass;
        }
        return JavaMethodClass.create(runtime, javaClass, methodName, argumentTypes);
    }

    public RubyArray constructors() {
        Constructor[] constructors = javaClass.getConstructors();
        RubyArray result = RubyArray.newArray(getRuntime(), constructors.length);
        for (int i = 0; i < constructors.length; i++) {
            result.append(new JavaConstructorClass(getRuntime(), constructors[i]));
        }
        return result;
    }

    public JavaConstructorClass constructor(IRubyObject[] args) {
        Class[] parameterTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            String name = args[i].toId();
            parameterTypes[i] = getRuntime().getJavaSupport().loadJavaClass(name);
        }
        Constructor constructor;
        try {
            constructor = javaClass.getConstructor(parameterTypes);
        } catch (NoSuchMethodException nsme) {
            throw new NameError(getRuntime(), "no matching java constructor");
        }
        return new JavaConstructorClass(getRuntime(), constructor);
    }

    public IRubyObject callIndexed(int index, IRubyObject[] args) {
        switch (index) {
            case PUBLIC_P :
                return public_p();
            case FINAL_P :
                return final_p();
            case INTERFACE_P :
                return interface_p();
            case NAME :
                return name();
            case SUPERCLASS :
                return superclass();
            case OP_CMP :
                return op_cmp(args[0]);
            case JAVA_INSTANCE_METHODS :
                return java_instance_methods();
            case CONSTANTS :
                return constants();
            case JAVA_METHOD :
                return java_method(args);
            case CONSTRUCTORS :
                return constructors();
            case CONSTRUCTOR :
                return constructor(args);
            default :
                return super.callIndexed(index, args);
        }
    }
}
