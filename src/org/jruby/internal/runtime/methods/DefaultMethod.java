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
 * Copyright (C) 2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2002-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2004-2005 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2006 Miguel Covarrubias <mlcovarrubias@gmail.com>
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

import java.util.ArrayList;
import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyModule;
import org.jruby.ast.ArgsNode;
import org.jruby.ast.ListNode;
import org.jruby.ast.Node;
import org.jruby.ast.executable.Script;
import org.jruby.compiler.ASTInspector;
import org.jruby.compiler.ClosureCallback;
import org.jruby.compiler.MethodCompiler;
import org.jruby.compiler.NodeCompilerFactory;
import org.jruby.compiler.impl.StandardASMCompiler;
import org.jruby.evaluator.AssignmentVisitor;
import org.jruby.evaluator.EvaluationState;
import org.jruby.exceptions.JumpException;
import org.jruby.internal.runtime.JumpTarget;
import org.jruby.javasupport.util.CompilerHelpers;
import org.jruby.lexer.yacc.ISourcePosition;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.EventHook;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.CodegenUtils;
import org.jruby.util.JRubyClassLoader;

/**
 *
 */
public final class DefaultMethod extends DynamicMethod implements JumpTarget {
    
    private StaticScope staticScope;
    private Node body;
    private ArgsNode argsNode;
    private int callCount = 0;
    private Script jitCompiledScript;
    private int expectedArgsCount;
    private int restArg;
    private boolean hasOptArgs;
    private boolean needsImplementer;
    private CallConfiguration jitCallConfig;

    public DefaultMethod(RubyModule implementationClass, StaticScope staticScope, Node body, 
            ArgsNode argsNode, Visibility visibility) {
        super(implementationClass, visibility, CallConfiguration.RUBY_FULL);
        this.body = body;
        this.staticScope = staticScope;
        this.argsNode = argsNode;
        this.expectedArgsCount = argsNode.getArgsCount();
        this.restArg = argsNode.getRestArg();
        this.hasOptArgs = argsNode.getOptArgs() != null;
		
        assert argsNode != null;
    
        if (implementationClass != null) {
            // Classes don't need the implementer search because they're not included
            // Kernel doesn't need the implementer class because it's always at the top of the hierarchy
            needsImplementer = !implementationClass.isClass() && implementationClass != implementationClass.getRuntime().getModule("Kernel");
        }
    }

    /**
     * @see AbstractCallable#call(Ruby, IRubyObject, String, IRubyObject[], boolean)
     */
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject[] args, Block block) {
        assert args != null;
        
        RubyModule implementer = CompilerHelpers.findImplementerIfNecessary(needsImplementer, clazz, getImplementationClass());
        
        Ruby runtime = context.getRuntime();

        if (runtime.getInstanceConfig().isJitEnabled()) {
            runJIT(runtime, context, name);
        }
        
        if (jitCompiledScript != null && !runtime.hasEventHooks()) {
            try {
                // FIXME: For some reason this wants (and works with) clazz instead of implementer,
                // and needed it for compiled module method_function's called from outside the module. Why?
                jitCallConfig.pre(context, self, clazz, getArity(), name, args, block, staticScope, this);

                return jitCompiledScript.run(context, self, args, block);
            } catch (JumpException.ReturnJump rj) {
                if (rj.getTarget() == this) {
                    return (IRubyObject) rj.getValue();
                }
                throw rj;
            } catch (JumpException.RedoJump rj) {
                    throw runtime.newLocalJumpError("redo", runtime.getNil(), "unexpected redo");
            } finally {
                if (runtime.hasEventHooks()) {
                    traceReturn(context, runtime, name);
                }
                jitCallConfig.post(context);
            }
        } else {
            try {
                callConfig.pre(context, self, implementer, getArity(), name, args, block, staticScope, this);
                if (argsNode.getBlockArgNode() != null) {
                    CompilerHelpers.processBlockArgument(runtime, context, block, argsNode.getBlockArgNode().getCount());
                }

                getArity().checkArity(runtime, args);

                prepareArguments(context, runtime, args);

                if (runtime.hasEventHooks()) {
                    traceCall(context, runtime, name);
                }

                return EvaluationState.eval(runtime, context, body, self, block);
            } catch (JumpException.ReturnJump rj) {
                if (rj.getTarget() == this) {
                    return (IRubyObject) rj.getValue();
                }
                throw rj;
            } catch (JumpException.RedoJump rj) {
                throw runtime.newLocalJumpError("redo", runtime.getNil(), "unexpected redo");
            } finally {
                if (runtime.hasEventHooks()) {
                    traceReturn(context, runtime, name);
                }
                callConfig.post(context);
            }
        }
    }

    private void runJIT(Ruby runtime, ThreadContext context, String name) {
        if (callCount >= 0) {
            String className = null;
            if (runtime.getInstanceConfig().isJitLogging()) {
                className = getImplementationClass().getBaseName();
                if (className == null) {
                    className = "<anon class>";
                }
            }
            
            try {
                callCount++;

                if (callCount >= runtime.getInstanceConfig().getJitThreshold()) {
                    String cleanName = CodegenUtils.cg.cleanJavaIdentifier(name);
                    // FIXME: not handling empty bodies correctly...
                    StandardASMCompiler compiler = new StandardASMCompiler(cleanName + hashCode() + "_" + context.hashCode(), body.getPosition().getFile());
                    compiler.startScript();
                    
        
                    ClosureCallback args = new ClosureCallback() {
                        public void compile(MethodCompiler context) {
                            NodeCompilerFactory.compileArgs(argsNode, context);
                        }
                    };
        
                    ASTInspector inspector = new ASTInspector();
                    inspector.inspect(body);
                    inspector.inspect(argsNode);
                    
                    MethodCompiler methodCompiler = compiler.startMethod("__file__", args, staticScope, inspector);
                    NodeCompilerFactory.compile(body, methodCompiler);
                    methodCompiler.endMethod();
                    compiler.endScript();
                    Class sourceClass = compiler.loadClass(new JRubyClassLoader(runtime.getJRubyClassLoader()));
                    
                    // if we're not doing any of the operations that still need a scope, use the scopeless config
                    if (!(inspector.hasClosure() || inspector.hasScopeAwareMethods() || inspector.hasBlockArg() || inspector.hasOptArgs() || inspector.hasRestArg())) {
                        // switch to a slightly faster call config
                        jitCallConfig = CallConfiguration.JAVA_FULL;
                    } else {
                        jitCallConfig = CallConfiguration.RUBY_FULL;
                    }
                    
                    // finally, grab the script
                    jitCompiledScript = (Script)sourceClass.newInstance();
                    
                    if (runtime.getInstanceConfig().isJitLogging()) System.err.println("compiled: " + className + "." + name);
                    callCount = -1;
                }
            } catch (Exception e) {
                if (runtime.getInstanceConfig().isJitLoggingVerbose()) System.err.println("could not compile: " + className + "." + name + " because of: \"" + e.getMessage() + '"');
                callCount = -1;
             }
        }
    }

    private void prepareArguments(ThreadContext context, Ruby runtime, IRubyObject[] args) {
        if (expectedArgsCount > args.length) {
            throw runtime.newArgumentError("Wrong # of arguments(" + args.length + " for " + expectedArgsCount + ")");
        }

        // Bind 'normal' parameter values to the local scope for this method.
        if (expectedArgsCount > 0) {
            context.getCurrentScope().setArgValues(args, expectedArgsCount);
        }

        // optArgs and restArgs require more work, so isolate them and ArrayList creation here
        if (hasOptArgs || restArg != -1) {
            args = prepareOptOrRestArgs(context, runtime, args);
        }
        
        context.setFrameArgs(args);
    }

    private IRubyObject[] prepareOptOrRestArgs(ThreadContext context, Ruby runtime, IRubyObject[] args) {
        int localExpectedArgsCount = expectedArgsCount;
        if (restArg == -1 && hasOptArgs) {
            int opt = expectedArgsCount + argsNode.getOptArgs().size();

            if (opt < args.length) {
                throw runtime.newArgumentError("wrong # of arguments(" + args.length + " for " + opt + ")");
            }
        }
        
        int count = expectedArgsCount;
        if (argsNode.getOptArgs() != null) {
            count += argsNode.getOptArgs().size();
        }

        ArrayList allArgs = new ArrayList();
        
        // Combine static and optional args into a single list allArgs
        for (int i = 0; i < count && i < args.length; i++) {
            allArgs.add(args[i]);
        }
        
        if (hasOptArgs) {
            ListNode optArgs = argsNode.getOptArgs();
   
            int j = 0;
            for (int i = expectedArgsCount; i < args.length && j < optArgs.size(); i++, j++) {
                // in-frame EvalState should already have receiver set as self, continue to use it
                AssignmentVisitor.assign(runtime, context, context.getFrameSelf(), optArgs.get(j), args[i], Block.NULL_BLOCK, true);
                localExpectedArgsCount++;
            }
   
            // assign the default values, adding to the end of allArgs
            while (j < optArgs.size()) {
                // in-frame EvalState should already have receiver set as self, continue to use it
                allArgs.add(EvaluationState.eval(runtime, context, optArgs.get(j++), context.getFrameSelf(), Block.NULL_BLOCK));
            }
        }
        
        // build an array from *rest type args, also adding to allArgs
        
        // ENEBO: Does this next comment still need to be done since I killed hasLocalVars:
        // move this out of the scope.hasLocalVariables() condition to deal
        // with anonymous restargs (* versus *rest)
        
        
        // none present ==> -1
        // named restarg ==> >=0
        // anonymous restarg ==> -2
        if (restArg != -1) {
            for (int i = localExpectedArgsCount; i < args.length; i++) {
                allArgs.add(args[i]);
            }

            // only set in scope if named
            if (restArg >= 0) {
                RubyArray array = runtime.newArray(args.length - localExpectedArgsCount);
                for (int i = localExpectedArgsCount; i < args.length; i++) {
                    array.append(args[i]);
                }

                context.getCurrentScope().setValue(restArg, array, 0);
            }
        }
        
        args = (IRubyObject[])allArgs.toArray(new IRubyObject[allArgs.size()]);
        return args;
    }

    private void traceReturn(ThreadContext context, Ruby runtime, String name) {
        ISourcePosition position = context.getPreviousFramePosition();
        runtime.callEventHooks(context, EventHook.RUBY_EVENT_RETURN, position.getFile(), position.getStartLine(), name, getImplementationClass());
    }
    
    private void traceCall(ThreadContext context, Ruby runtime, String name) {
        ISourcePosition position = body != null ? body.getPosition() : context.getPosition();
        
        runtime.callEventHooks(context, EventHook.RUBY_EVENT_CALL, position.getFile(), position.getStartLine(), name, getImplementationClass());
    }

    public Arity getArity() {
        return argsNode.getArity();
    }
    
    public DynamicMethod dup() {
        return new DefaultMethod(getImplementationClass(), staticScope, body, argsNode, getVisibility());
    }
}
