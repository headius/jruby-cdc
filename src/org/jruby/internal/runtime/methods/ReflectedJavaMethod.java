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
 * Copyright (c) 2007 Peter Brant <peter.brant@gmail.com>
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
package org.jruby.internal.runtime.methods;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.jruby.Ruby;
import org.jruby.RubyModule;
import org.jruby.anno.JRubyMethod;
import org.jruby.exceptions.JumpException;
import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.EventHook;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

public class ReflectedJavaMethod extends JavaMethod {
    private final Method method;
    
    private final boolean needsBlock;
    private final boolean isStatic;
    private final int required;
    private final int optional;
    private final boolean rest;
    private final int max;
    
    private final boolean argsAsIs;

    private final boolean needsThreadContext;

    public ReflectedJavaMethod(
            RubyModule implementationClass, Method method, JRubyMethod annotation) {
        super(implementationClass, annotation.visibility());
        
        this.method = method;
        
        Class<?>[] params = method.getParameterTypes();
        this.needsBlock = params.length > 0 && params[params.length - 1] == Block.class;
        this.isStatic = Modifier.isStatic(method.getModifiers());
        
        Arity arity = Arity.fromAnnotation(annotation, params, this.isStatic);
        setArity(arity);

        this.required = arity.getValue() >= 0 ? arity.getValue() : Math.abs(arity.getValue())-1;
        this.optional = annotation.optional();
        this.rest = annotation.rest();
        
        this.needsThreadContext = params.length > 0 && params[0] == ThreadContext.class;
        this.argsAsIs = ! isStatic && optional == 0 && !rest && !needsBlock && !needsThreadContext;
        
        if (rest) {
            max = -1;
        } else {
            max = required + optional;
        }
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name,
            IRubyObject[] args, Block block) {
        Ruby runtime = context.getRuntime();
        Arity.checkArgumentCount(runtime, args, required, max);
        
        callConfig.pre(context, self, getImplementationClass(), name, block, null, this);
        
        try {
            if (! isStatic && ! method.getDeclaringClass().isAssignableFrom(self.getClass())) {
                throw new ClassCastException(
                        self.getClass().getName() + " cannot be converted to " +
                        method.getDeclaringClass().getName());
            }
            
            if (argsAsIs) {
                boolean isTrace = runtime.hasEventHooks();
                try {
                    if (isTrace) {
                        runtime.callEventHooks(context, EventHook.RUBY_EVENT_C_CALL, context.getFile(), context.getLine(), name, getImplementationClass());
                    }  
                    return (IRubyObject)method.invoke(self, (Object[])args);
                } finally {
                    if (isTrace) {
                        runtime.callEventHooks(context, EventHook.RUBY_EVENT_C_RETURN, context.getFile(), context.getLine(), name, getImplementationClass());
                    }
                }                    
            } else {
                int argsLength = calcArgsLength();
                
                Object[] params = new Object[argsLength];
                int offset = 0;
                if (needsThreadContext) {
                    params[offset++] = context;
                }
                if (isStatic) {
                    params[offset++] = self;
                }
                if (optional == 0 && !rest) {
                    for (int i = 0; i < args.length; i++) {
                        params[offset++] = args[i];
                    }
                } else {
                    params[offset++] = args;
                }
                if (needsBlock) {
                    params[offset++] = block;
                }
                
                boolean isTrace = runtime.hasEventHooks();
                try {
                    if (isTrace) {
                        runtime.callEventHooks(context, EventHook.RUBY_EVENT_C_CALL, context.getFile(), context.getLine(), name, getImplementationClass());
                    }  
                    if (isStatic) {
                        return (IRubyObject)method.invoke(null, params);
                    } else {
                        return (IRubyObject)method.invoke(self, params);
                    }
                } finally {
                    if (isTrace) {
                        runtime.callEventHooks(context, EventHook.RUBY_EVENT_C_RETURN, context.getFile(), context.getLine(), name, getImplementationClass());
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            throw RaiseException.createNativeRaiseException(runtime, e);
        } catch (IllegalAccessException e) {
            throw RaiseException.createNativeRaiseException(runtime, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof JumpException.ReturnJump) {
                JumpException.FlowControlException f = (JumpException.FlowControlException)cause;
                if (f.getTarget() == this) {
                    return (IRubyObject)f.getValue();
                } else {
                    throw f;
                }
            } else if (cause instanceof JumpException.RedoJump) {
                throw runtime.newLocalJumpError("redo", runtime.getNil(), "unexpected redo");
            } else if (cause instanceof RuntimeException) {
                throw (RuntimeException)cause;
            } else if (cause instanceof Error) {
                throw (Error)cause;
            } else {
                throw RaiseException.createNativeRaiseException(runtime, cause);
            }
        } finally {
            callConfig.post(context);
        }
    }

    private int calcArgsLength() {
        int argsLength = 0;
        
        if (needsThreadContext) {
            argsLength++;
        }

        if (isStatic) {
            argsLength++;
        }
        if (optional == 0 && !rest) {
            argsLength += required;
        } else {
            argsLength++;
        }
        if (needsBlock) {
            argsLength++;
        }
        return argsLength;
    }
}
