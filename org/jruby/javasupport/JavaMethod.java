/*
 * JavaMethod.java - No description
 * Created on 21. September 2001, 15:03
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

package org.jruby.javasupport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Iterator;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import org.jruby.Ruby;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.Callback;
import org.jruby.runtime.Arity;
import org.jruby.RubyJavaObject;
import org.jruby.util.Asserts;
import org.jruby.exceptions.ArgumentError;
import org.jruby.exceptions.IOError;
import org.jruby.exceptions.RaiseException;

/**
 *
 * @author  jpetersen
 * @version $Revision$
 */
public class JavaMethod implements Callback {
    private Method[] methods = null;
    private boolean callSuper = false;
    private boolean singleton = false;

    public JavaMethod(Method[] methods, boolean callSuper) {
        this(methods, callSuper, false);
    }

    public JavaMethod(Method[] methods, boolean callSuper, boolean singleton) {
        this.methods = methods;
        this.callSuper = callSuper;
        this.singleton = singleton;
    }

    public Arity getArity() {
        return Arity.optional();
    }

    public IRubyObject execute(IRubyObject recv, IRubyObject[] args) {
        Ruby runtime = recv.getRuntime();

        Method method = findMatchingMethod(args);
        if (method == null) {
            if (callSuper) {
            	return runtime.callSuper(args);
            } else {
            	throw new ArgumentError(runtime, "wrong argument count or types.");
            }
        }

        int argsLength = args != null ? args.length : 0;
        Object[] newArgs = new Object[argsLength];
        for (int i = 0; i < argsLength; i++) {
            newArgs[i] = JavaUtil.convertRubyToJava(runtime, args[i], method.getParameterTypes()[i]);
        }

        try {
            Object receiver = null;
            if (! singleton) {
                receiver = ((RubyJavaObject) recv).getValue();
            }
            Object javaResult = method.invoke(receiver, newArgs);
            Class returnType = returnedObjectType(javaResult, method);
            return JavaUtil.convertJavaToRuby(runtime, javaResult, returnType);
        } catch (InvocationTargetException itException) {
            convertException(runtime, (Exception)itException.getTargetException());
            Asserts.notReached();
            return null;
        } catch (IllegalAccessException iaException) {
            convertException(runtime, iaException);
            Asserts.notReached();
            return null;
        }
    }

    private Class returnedObjectType(Object javaResult, Method returningMethod) {
        Class narrowestClass = javaResult.getClass();
        Class widestClass = returningMethod.getReturnType();

        if (narrowestClass.isPrimitive()) {
            return narrowestClass;
        }
        if (widestClass.isPrimitive()) {
            return widestClass;
        }

        Asserts.assertExpression(widestClass.isAssignableFrom(narrowestClass));

        if (Modifier.isPublic(narrowestClass.getModifiers())) {
            return narrowestClass;
        }
        if (widestClass.isInterface()) {
            return widestClass;
        }

        // If there is any interface on the narrowest that isn't on the widest,
        // then we should use that. The theory is that this should minimize information
        // loss. (This theory is open to discussion ;-)
        Iterator narrowestClassInterfaces = Arrays.asList(narrowestClass.getInterfaces()).iterator();
        List widestClassInterfaces = Arrays.asList(widestClass.getInterfaces());
        while (narrowestClassInterfaces.hasNext()) {
            Class iface = (Class) narrowestClassInterfaces.next();
            if (! widestClassInterfaces.contains(iface)) {
                return iface;
            }
        }

        while (true) {
            narrowestClass = narrowestClass.getSuperclass();
            Asserts.assertExpression(narrowestClass != null);
            if (Modifier.isPublic(narrowestClass.getModifiers())) {
                return narrowestClass;
            }
        }
    }

    private static void convertException(Ruby ruby, Exception e) {
        if (e instanceof RaiseException) {
            throw (RaiseException) e;
        } else if (e instanceof IOException) {
            throw IOError.fromException(ruby, (IOException) e);
        } else {
            ruby.getJavaSupport().handleNativeException(e);
        }
    }


    private Method findMatchingMethod(IRubyObject[] args) {
        ArrayList executeMethods = new ArrayList(methods.length);

        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (hasMatchingArguments(method, args)) {
                executeMethods.add(method);
            }
        }

        if (executeMethods.isEmpty()) {
            return null;
        }
        return (Method) executeMethods.get(0);
    }

    private static boolean hasMatchingArgumentCount(Method method, int expected) {
        return (method.getParameterTypes().length == expected);
    }

    private static boolean hasMatchingArguments(Method method, IRubyObject[] args) {
        int expectedLength = (args != null ? args.length : 0);
        if (! hasMatchingArgumentCount(method, expectedLength)) {
            return false;
        }
        Class[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (! JavaUtil.isCompatible(args[i], parameterTypes[i])) {
                return false;
            }
        }
        return true;
    }
}
