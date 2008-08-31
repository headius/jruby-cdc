package org.jruby.javasupport;

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
 * Copyright (C) 2006 Thomas E Enebo <enebo@acm.org>
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

import java.util.List;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyInstanceConfig;
import org.jruby.RubyInteger;
import org.jruby.RubyModule;
import org.jruby.RubyObjectAdapter;
import org.jruby.RubyRuntimeAdapter;
import org.jruby.RubyString;
import org.jruby.javasupport.util.RuntimeHelpers;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ClassCache;

/**
 * Utility functions to help embedders out.   These function consolidate logic that is
 * used between BSF and JSR 223.  People who are embedding JRuby 'raw' should use these
 * as well.  If at a later date, we discover a flaw or change how we do things, this
 * utility class should provide some insulation.
 */
public class JavaEmbedUtils {
    /**
     * Get an instance of a JRuby runtime.  Provide any loadpaths you want used at startup.
     * 
     * @param loadPaths to specify where to look for Ruby modules. 
     * @return an instance
     */
    public static Ruby initialize(List loadPaths) {
        return initialize(loadPaths, new RubyInstanceConfig());
    }
    
    /**
     * Get an instance of a JRuby runtime.  Provide any loadpaths you want used at startup.
     * 
     * @param loadPaths to specify where to look for Ruby modules.
     * @param classCache to use as a common repository for cached classes 
     * @return an instance
     */
    public static Ruby initialize(List loadPaths, ClassCache classCache) {
        RubyInstanceConfig config = new RubyInstanceConfig();
        if (classCache != null) {
            config.setClassCache(classCache);
        }
        return initialize(loadPaths, config);
    }

    /**
     * Get an instance of a JRuby runtime.
     * @param loadPaths additional load paths you wish to add
     * @param config a runtime configuration instance
     * @return an instance
     */
    public static Ruby initialize(List loadPaths, RubyInstanceConfig config) {
        Ruby runtime = Ruby.newInstance(config);
        runtime.getLoadService().init(loadPaths);
        runtime.getLoadService().require("java");

        return runtime;
    }
    
    /**
     * Generate a class cache.  This will end up setting max cache size per JRuby preferences
     * (e.g. jruby.jit.max).
     * 
     * @param loader use the provided classloader to create the cache
     * @return
     */
    public static ClassCache createClassCache(ClassLoader loader) {
        return new ClassCache(loader, new RubyInstanceConfig().getJitMax()); 
    }

    public static RubyObjectAdapter newObjectAdapter() {
        return new RubyObjectAdapter() {
            public boolean isKindOf(IRubyObject value, RubyModule rubyModule) {
                return rubyModule.isInstance(value);
            }

            public IRubyObject setInstanceVariable(IRubyObject obj, String variableName, IRubyObject value) {
                return obj.getInstanceVariables().setInstanceVariable(variableName, value);
            }

            public IRubyObject[] convertToJavaArray(IRubyObject array) {
                return ((RubyArray) array).toJavaArray();
            }

            public RubyInteger convertToRubyInteger(IRubyObject obj) {
                return obj.convertToInteger();
            }

            public IRubyObject getInstanceVariable(IRubyObject obj, String variableName) {
                return obj.getInstanceVariables().getInstanceVariable(variableName);
            }

            public RubyString convertToRubyString(IRubyObject obj) {
                return obj.convertToString();
            }

            public IRubyObject callMethod(IRubyObject receiver, String methodName) {
                return receiver.callMethod(receiver.getRuntime().getCurrentContext(), methodName);
            }

            public IRubyObject callMethod(IRubyObject receiver, String methodName, IRubyObject singleArg) {
                return receiver.callMethod(receiver.getRuntime().getCurrentContext(), methodName, singleArg);
            }

            public IRubyObject callMethod(IRubyObject receiver, String methodName, IRubyObject[] args) {
                return receiver.callMethod(receiver.getRuntime().getCurrentContext(), methodName, args);
            }

            public IRubyObject callMethod(IRubyObject receiver, String methodName, IRubyObject[] args, Block block) {
                return receiver.callMethod(receiver.getRuntime().getCurrentContext(), methodName, args, block);
            }

            public IRubyObject callSuper(IRubyObject receiver, IRubyObject[] args) {
                return RuntimeHelpers.invokeSuper(receiver.getRuntime().getCurrentContext(), receiver, args, Block.NULL_BLOCK);
            }

            public IRubyObject callSuper(IRubyObject receiver, IRubyObject[] args, Block block) {
                return RuntimeHelpers.invokeSuper(receiver.getRuntime().getCurrentContext(), receiver, args, block);
            }
        };
    }

    public static RubyRuntimeAdapter newRuntimeAdapter() {
        return new RubyRuntimeAdapter() {
            public IRubyObject eval(Ruby runtime, String script) {
                return runtime.evalScriptlet(script);
            }
        };
    }

    /**
     * Dispose of the runtime you initialized.
     * 
     * @param runtime to be disposed of
     */
    public static void terminate(Ruby runtime) {
        runtime.tearDown();
    }

    /**
     * Convenience function for embedders
     * 
     * @param runtime environment where the invoke will occur
     * @param receiver is the instance that will receive the method call
     * @param method is method to be called
     * @param args are the arguments to the method
     * @param returnType is the type we want it to conform to
     * @return the result of the invocation.
     */
    public static Object invokeMethod(Ruby runtime, Object receiver, String method, Object[] args,
            Class returnType) {
        IRubyObject rubyReceiver = receiver != null ? JavaUtil.convertJavaToRuby(runtime, receiver) : runtime.getTopSelf();

        IRubyObject[] rubyArgs = JavaUtil.convertJavaArrayToRuby(runtime, args);

        // Create Ruby proxies for any input arguments that are not primitives.
        for (int i = 0; i < rubyArgs.length; i++) {
            IRubyObject obj = rubyArgs[i];

            if (obj instanceof JavaObject) rubyArgs[i] = Java.wrap(runtime, obj);
        }

        IRubyObject result = rubyReceiver.callMethod(runtime.getCurrentContext(), method, rubyArgs);

        return rubyToJava(runtime, result, returnType);
    }

    /**
     * Convert a Ruby object to a Java object.
     * 
     */
    public static Object rubyToJava(Ruby runtime, IRubyObject value, Class type) {
        return JavaUtil.convertArgument(runtime, Java.ruby_to_java(runtime.getObject(), value, Block.NULL_BLOCK), type);
    }

    /**
     *  Convert a java object to a Ruby object.
     */
    public static IRubyObject javaToRuby(Ruby runtime, Object value) {
        if (value instanceof IRubyObject) return (IRubyObject) value;

        IRubyObject result = JavaUtil.convertJavaToRuby(runtime, value);
        
        return result instanceof JavaObject ? Java.wrap(runtime, result) : result; 
    }

    public static IRubyObject javaToRuby(Ruby runtime, boolean value) {
        return javaToRuby(runtime, value ? Boolean.TRUE : Boolean.FALSE);
    }

    public static IRubyObject javaToRuby(Ruby runtime, byte value) {
        return javaToRuby(runtime, new Byte(value));
    }

    public static IRubyObject javaToRuby(Ruby runtime, char value) {
        return javaToRuby(runtime, new Character(value));
    }

    public static IRubyObject javaToRuby(Ruby runtime, double value) {
        return javaToRuby(runtime, new Double(value));
    }

    public static IRubyObject javaToRuby(Ruby runtime, float value) {
        return javaToRuby(runtime, new Float(value));
    }

    public static IRubyObject javaToRuby(Ruby runtime, int value) {
        return javaToRuby(runtime, new Integer(value));
    }

    public static IRubyObject javaToRuby(Ruby runtime, long value) {
        return javaToRuby(runtime, new Long(value));
    }

    public static IRubyObject javaToRuby(Ruby runtime, short value) {
        return javaToRuby(runtime, new Short(value));
    }
}
