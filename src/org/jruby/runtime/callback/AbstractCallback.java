/*
 *  Copyright (C) 2004 Charles O Nutter
 * 
 * Charles O Nutter <headius@headius.com>
 *
 *  JRuby - http://jruby.sourceforge.net
 *
 *  This file is part of JRuby
 *
 *  JRuby is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License as
 *  published by the Free Software Foundation; either version 2 of the
 *  License, or (at your option) any later version.
 *
 *  JRuby is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License along with JRuby; if not, write to
 *  the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA  02111-1307 USA
 */
package org.jruby.runtime.callback;

import java.lang.reflect.InvocationTargetException;

import org.jruby.Ruby;
import org.jruby.exceptions.JumpException;
import org.jruby.exceptions.RaiseException;
import org.jruby.exceptions.ThreadKill;
import org.jruby.runtime.Arity;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.AssertError;
import org.jruby.util.Asserts;

/**
 * A wrapper for <code>java.lang.reflect.Method</code> objects which implement Ruby methods.
 * The public methods are {@link #execute execute()} and {@link #getArity getArity()}.
 * Before really calling the Ruby method (via {@link #invokeMethod invokeMethod()}), the arity
 * is checked and an {@link org.jruby.exceptions.ArgumentError ArgumentError} is raised if the
 * number of arguments doesn't match the number of expected arguments.  Furthermore, rest
 * arguments are collected in a single IRubyObject array.
 */
public abstract class AbstractCallback implements Callback {
    protected final Class type;
    protected final String methodName;
    protected final Class[] argumentTypes;
    protected final boolean isRestArgs;
    protected final Arity arity;
    protected final boolean isStaticMethod;

    public AbstractCallback(Class type, String methodName, Class[] argumentTypes, boolean isRestArgs, boolean isStaticMethod, Arity arity) {
        this.type = type;
        this.methodName = methodName;
        this.argumentTypes = argumentTypes != null ? argumentTypes : CallbackFactory.NULL_CLASS_ARRAY;
        this.isRestArgs = isRestArgs;
        this.arity = arity;
        this.isStaticMethod = isStaticMethod;
    }

    /**
     * Returns a string enumerating the given and the expected arguments types. 
     */
    protected String getExpectedArgsString(IRubyObject[] args) {
        StringBuffer sb = new StringBuffer();
        sb.append("Wrong arguments: ");

        if (args.length == 0) {
            sb.append("No args");
        } else {
            sb.append("(");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                String className = args[i].getType().getName();
                sb.append("a");
                if (isVowel(className.charAt(0))) {
                    sb.append("n");
                }
                sb.append(className);
            }
            sb.append(")");
        }
        sb.append(" given, ");

        if (argumentTypes.length == 0) {
            sb.append("no arguments expected.");
        } else {
            sb.append("(");
            for (int i = 0; i < argumentTypes.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                String className = argumentTypes[i].getName();
                className = className.substring(className.lastIndexOf(".Ruby") + 5);
                sb.append("a");
                if (isVowel(className.charAt(0))) {
                	sb.append("n");
                }
                sb.append(className);
            }
            if (isRestArgs) {
                sb.append(", ...");
            }
            sb.append(") expected.");
        }
        return sb.toString();
    }
    
    private boolean isVowel(char c) {
    	return c == 'A' || c == 'E' || c == 'I' || c == 'O' || c == 'U';
    }

    /**
     * Returns an object array that collects all rest arguments in its own object array which
     * is then put into the last slot of the first object array.  That is, assuming that this
     * callback expects one required argument and any number of rest arguments, an input of
     * <code>[1, 2, 3]</code> is transformed into <code>[1, [2, 3]]</code>.  
     */
    protected final Object[] packageRestArgumentsForReflection(final Object[] originalArgs) {
        IRubyObject[] restArray = new IRubyObject[originalArgs.length - (argumentTypes.length - 1)];
        Object[] result = new Object[argumentTypes.length];
        try {
            System.arraycopy(originalArgs, argumentTypes.length - 1, restArray, 0, originalArgs.length - (argumentTypes.length - 1));
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new RuntimeException(
                    "Cannot call \""
                    + methodName
                    + "\" in class \""
                    + type.getName()
                    + "\". "
                    + getExpectedArgsString((IRubyObject[]) originalArgs));
        }
        System.arraycopy(originalArgs, 0, result, 0, argumentTypes.length - 1);
        result[argumentTypes.length - 1] = restArray;
        return result;
    }

    /**
     * Checks whether 
     */
    protected void testArgsCount(Ruby runtime, IRubyObject[] methodArgs) {
        if (isRestArgs) {
            if (methodArgs.length < argumentTypes.length - 1) {
                throw runtime.newArgumentError(getExpectedArgsString(methodArgs));
            }
        } else {
            if (methodArgs.length != argumentTypes.length) {
                throw runtime.newArgumentError(getExpectedArgsString(methodArgs));
            }
        }
    }

    /**
     * Invokes the Ruby method. Actually, this methods delegates to an internal version
     * that may throw the usual Java reflection exceptions.  Ruby exceptions are rethrown, 
     * other exceptions throw an AssertError and abort the execution of the Ruby program.
     * They should never happen.
     */
    protected IRubyObject invokeMethod(IRubyObject recv, Object[] methodArgs) {
    	if (isRestArgs) {
    		methodArgs = packageRestArgumentsForReflection(methodArgs);
    	}
        try {
        	return invokeMethod0(recv, methodArgs);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof RaiseException) {
                throw (RaiseException) e.getTargetException();
            } else if (e.getTargetException() instanceof JumpException) {
                throw (JumpException) e.getTargetException();
            } else if (e.getTargetException() instanceof ThreadKill) {
            	// allow it to bubble up
            	throw (ThreadKill) e.getTargetException();
            } else if (e.getTargetException() instanceof Exception) {
                recv.getRuntime().getJavaSupport().handleNativeException(e.getTargetException());
                return recv.getRuntime().getNil();
            } else {
                throw (Error) e.getTargetException();
            }
        } catch (IllegalAccessException e) {
            StringBuffer message = new StringBuffer();
            message.append(e.getMessage());
            message.append(':');
            message.append(" methodName=").append(methodName);
            message.append(" recv=").append(recv.toString());
            message.append(" type=").append(type.getName());
            message.append(" methodArgs=[");
            for (int i = 0; i < methodArgs.length; i++) {
                message.append(methodArgs[i]);
                message.append(' ');
            }
            message.append(']');
            Asserts.notReached(message.toString());
        } catch (final IllegalArgumentException e) {
            throw new RaiseException(recv.getRuntime(), "TypeError", e.getMessage());
        }
        throw new AssertError("[BUG] Run again with Asserts.ENABLE_ASSERT=true");
    }

	protected abstract IRubyObject invokeMethod0(IRubyObject recv, Object[] methodArgs)
			throws IllegalAccessException, InvocationTargetException;

	/**
     * Calls a wrapped Ruby method for the specified receiver with the specified arguments.
     */
    public IRubyObject execute(IRubyObject recv, IRubyObject[] args) {
        args = (args != null) ? args : IRubyObject.NULL_ARRAY;
        testArgsCount(recv.getRuntime(), args);
        return invokeMethod(recv, args);
    }

    /**
     * Returns the arity of the wrapped Ruby method.
     */
    public Arity getArity() {
        return arity;
    }
}
