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
import org.jruby.exceptions.TypeError;
import org.jruby.exceptions.ArgumentError;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.IndexCallable;
import org.jruby.runtime.IndexedCallback;
import org.jruby.runtime.builtin.IRubyObject;

import java.lang.reflect.Modifier;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

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
    private static final int OP_GT = 8;
    private static final int OP_LT = 9;
    private static final int INSTANCE_METHODS = 10;
    private static final int CONSTANTS = 11;
    private static final int JAVA_METHOD = 12;

    public static RubyClass createJavaClassClass(Ruby runtime, RubyModule javaModule) {
        RubyClass javaClassClass =
                javaModule.defineClassUnder("JavaClass", runtime.getClasses().getObjectClass());

        javaClassClass.defineSingletonMethod("for_name", CallbackFactory.getSingletonMethod(JavaClassClass.class, "for_name", IRubyObject.class));
        javaClassClass.defineMethod("public?", IndexedCallback.create(PUBLIC_P, 0));
        javaClassClass.defineMethod("final?", IndexedCallback.create(FINAL_P, 0));
        javaClassClass.defineMethod("interface?", IndexedCallback.create(INTERFACE_P, 0));
        javaClassClass.defineMethod("name", IndexedCallback.create(NAME, 0));
        javaClassClass.defineMethod("to_s", IndexedCallback.create(NAME, 0));
        javaClassClass.defineMethod("superclass", IndexedCallback.create(SUPERCLASS, 0));
        javaClassClass.defineMethod(">", IndexedCallback.create(OP_GT, 1));
        javaClassClass.defineMethod("<", IndexedCallback.create(OP_LT, 1));
        javaClassClass.defineMethod("instance_methods", IndexedCallback.create(INSTANCE_METHODS, 0));
        javaClassClass.defineMethod("constants", IndexedCallback.create(CONSTANTS, 0));
        javaClassClass.defineMethod("java_method", IndexedCallback.createOptional(JAVA_METHOD, 1));

        javaClassClass.getInternalClass().undefMethod("new");

        return javaClassClass;
    }

    public static JavaClassClass for_name(IRubyObject recv, IRubyObject name) {
        return new JavaClassClass(recv.getRuntime(), name.convertToString().toString());
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

    public RubyBoolean op_gt(IRubyObject other) {
        if (! (other instanceof JavaClassClass)) {
            throw new TypeError(runtime, "compared with non-javaclass");
        }
        boolean result = javaClass.isAssignableFrom(((JavaClassClass) other).javaClass);
        return RubyBoolean.newBoolean(runtime, result);
    }

    public RubyBoolean op_lt(IRubyObject other) {
        if (! (other instanceof JavaClassClass)) {
            throw new TypeError(runtime, "compared with non-javaclass");
        }
        boolean result = ((JavaClassClass) other).javaClass.isAssignableFrom(javaClass);
        return RubyBoolean.newBoolean(runtime, result);
    }

    public RubyArray instance_methods() {
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
        String methodName = ((RubyString) args[0].convertToString()).getValue();
        Class[] argumentTypes = new Class[args.length - 1];
        for (int i = 1; i < args.length; i++) {
            JavaClassClass type = for_name(this, args[i]);
            argumentTypes[i - 1] = type.javaClass;
        }
        return JavaMethodClass.create(runtime, javaClass, methodName, argumentTypes);
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
            case OP_GT :
                return op_gt(args[0]);
            case OP_LT :
                return op_lt(args[0]);
            case INSTANCE_METHODS :
                return instance_methods();
            case CONSTANTS :
                return constants();
            case JAVA_METHOD :
                return java_method(args);
        }
        return super.callIndexed(index, args);
    }
}
