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

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyModule;
import org.jruby.ast.ArgsNode;
import org.jruby.ast.ListNode;
import org.jruby.ast.Node;
import org.jruby.ast.executable.Script;
import org.jruby.exceptions.JumpException;
import org.jruby.exceptions.RaiseException;
import org.jruby.internal.runtime.JumpTarget;
import org.jruby.javasupport.util.RuntimeHelpers;
import org.jruby.lexer.yacc.ISourcePosition;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.EventHook;
import org.jruby.runtime.Frame;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;

/**
 *
 */
public final class DefaultMethod extends DynamicMethod implements JumpTarget {
    
    private StaticScope staticScope;
    private Node body;
    private ArgsNode argsNode;
    private int callCount = 0;
    private Script jitCompiledScript;
    private int requiredArgsCount;
    private int restArg;
    private boolean hasOptArgs;
    private CallConfiguration jitCallConfig;
    private ISourcePosition position;

    public DefaultMethod(RubyModule implementationClass, StaticScope staticScope, Node body, 
            ArgsNode argsNode, Visibility visibility, ISourcePosition position) {
        super(implementationClass, visibility, CallConfiguration.FRAME_AND_SCOPE);
        this.body = body;
        this.staticScope = staticScope;
        this.argsNode = argsNode;
        this.requiredArgsCount = argsNode.getRequiredArgsCount();
        this.restArg = argsNode.getRestArg();
        this.hasOptArgs = argsNode.getOptArgs() != null;
        this.position = position;
		
        assert argsNode != null;
    }
    
    public int getCallCount() {
        return callCount;
    }
    
    public void setCallCount(int callCount) {
        this.callCount = callCount;
    }
    
    public Script getJITCompilerScript() {
        return jitCompiledScript;
    }
    
    public void setJITCompiledScript(Script jitCompiledScript) {
        this.jitCompiledScript = jitCompiledScript;
    }
    
    public CallConfiguration getJITCallConfig() {
        return jitCallConfig;
    }
    
    public void setJITCallConfig(CallConfiguration jitCallConfig) {
        this.jitCallConfig = jitCallConfig;
    }
    
    public Node getBodyNode() {
        return body;
    }
    
    public ArgsNode getArgsNode() {
        return argsNode;
    }
    
    public StaticScope getStaticScope() {
        return staticScope;
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject[] args, Block block) {
        assert args != null;
        
        Ruby runtime = context.getRuntime();

        if (runtime.getInstanceConfig().getCompileMode().shouldJIT()) {
            runtime.getJITCompiler().runJIT(this, context, name);
        }
        
        if (jitCompiledScript != null && !runtime.hasEventHooks()) {
            try {
                jitPre(context, self, name, block);

                getArity().checkArity(runtime, args);

                return jitCompiledScript.__file__(context, self, args, block);
            } catch (JumpException.ReturnJump rj) {
                return handleReturn(context, rj);
            } catch (JumpException.RedoJump rj) {
                return handleRedo(runtime);
            } finally {
                jitPost(runtime, context, name);
            }
        } else {
            return interpretedCall(context, runtime, self, clazz, name, args, block);
        }
    }

    public IRubyObject interpretedCall(ThreadContext context, Ruby runtime, IRubyObject self, RubyModule clazz, String name, IRubyObject[] args, Block block) {
        try {
            preInterpret(context, name,self, block, runtime, args.length);

            prepareArguments(context, runtime, self, args);

            return body.interpret(runtime, context, self, block);
        } catch (JumpException.ReturnJump rj) {
            return handleReturn(context, rj);
        } catch (JumpException.RedoJump rj) {
            return handleRedo(runtime);
        } catch (StackOverflowError sfe) {
            throw runtime.newSystemStackError("stack level too deep");
        } finally {
            postInterpret(runtime, context, name);
        }
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject[] args) {
        Ruby runtime = context.getRuntime();
        
        if (jitCompiledScript != null && !runtime.hasEventHooks()) {
            try {
                jitPre(context, self, name, Block.NULL_BLOCK);
                
                return jitCompiledScript.__file__(context, self, args, Block.NULL_BLOCK);
            } catch (JumpException.ReturnJump rj) {
                return handleReturn(context, rj);
            } catch (JumpException.RedoJump rj) {
                return handleRedo(runtime);
            } finally {
                jitPost(runtime,context, name);
            }
        } else {
            return call(context, self, clazz, name, args, Block.NULL_BLOCK);
        }
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name) {
        Ruby runtime = context.getRuntime();
        
        if (jitCompiledScript != null && !runtime.hasEventHooks()) {
            try {
                jitPre(context, self, name, Block.NULL_BLOCK);

                return jitCompiledScript.__file__(context, self, Block.NULL_BLOCK);
            } catch (JumpException.ReturnJump rj) {
                return handleReturn(context, rj);
            } catch (JumpException.RedoJump rj) {
                return handleRedo(runtime);
            } finally {
                jitPost(runtime, context, name);
            }
        } else {
            return call(context, self, clazz, name, IRubyObject.NULL_ARRAY, Block.NULL_BLOCK);
        }
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, Block block) {
        Ruby runtime = context.getRuntime();
        
        if (jitCompiledScript != null && !runtime.hasEventHooks()) {
            try {
                jitPre(context, self, name, block);

                return jitCompiledScript.__file__(context, self, block);
            } catch (JumpException.ReturnJump rj) {
                return handleReturn(context, rj);
            } catch (JumpException.RedoJump rj) {
                return handleRedo(runtime);
            } finally {
                jitPost(runtime, context, name);
            }
        } else {
            return call(context, self, clazz, name, IRubyObject.NULL_ARRAY, block);
        }
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject arg0) {
        Ruby runtime = context.getRuntime();
        
        if (jitCompiledScript != null && !runtime.hasEventHooks()) {
            try {
                jitPre(context, self, name, Block.NULL_BLOCK);

                return jitCompiledScript.__file__(context, self, arg0, Block.NULL_BLOCK);
            } catch (JumpException.ReturnJump rj) {
                return handleReturn(context, rj);
            } catch (JumpException.RedoJump rj) {
                return handleRedo(runtime);
            } finally {
                jitPost(runtime, context, name);
            }
        } else {
            return call(context, self, clazz, name, new IRubyObject[] {arg0}, Block.NULL_BLOCK);
        }
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject arg0, Block block) {
        Ruby runtime = context.getRuntime();
        
        if (jitCompiledScript != null && !runtime.hasEventHooks()) {
            try {
                jitPre(context, self, name, block);

                return jitCompiledScript.__file__(context, self, arg0, block);
            } catch (JumpException.ReturnJump rj) {
                return handleReturn(context, rj);
            } catch (JumpException.RedoJump rj) {
                return handleRedo(runtime);
            } finally {
                jitPost(runtime, context, name);
            }
        } else {
            return call(context, self, clazz, name, new IRubyObject[] {arg0}, block);
        }
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject arg0, IRubyObject arg1) {
        Ruby runtime = context.getRuntime();
        
        if (jitCompiledScript != null && !runtime.hasEventHooks()) {
            try {
                jitPre(context, self, name, Block.NULL_BLOCK);

                return jitCompiledScript.__file__(context, self, arg0, arg1, Block.NULL_BLOCK);
            } catch (JumpException.ReturnJump rj) {
                return handleReturn(context, rj);
            } catch (JumpException.RedoJump rj) {
                return handleRedo(runtime);
            } finally {
                jitPost(runtime, context, name);
            }
        } else {
            return call(context, self, clazz, name, new IRubyObject[] {arg0, arg1}, Block.NULL_BLOCK);
        }
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject arg0, IRubyObject arg1, Block block) {
        Ruby runtime = context.getRuntime();
        
        if (jitCompiledScript != null && !runtime.hasEventHooks()) {
            try {
                jitPre(context, self, name, block);

                return jitCompiledScript.__file__(context, self, arg0, arg1, block);
            } catch (JumpException.ReturnJump rj) {
                return handleReturn(context, rj);
            } catch (JumpException.RedoJump rj) {
                return handleRedo(runtime);
            } finally {
                jitPost(runtime, context, name);
            }
        } else {
            return call(context, self, clazz, name, new IRubyObject[] {arg0, arg1}, block);
        }
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject arg0, IRubyObject arg1, IRubyObject arg2) {
        Ruby runtime = context.getRuntime();
        
        if (jitCompiledScript != null && !runtime.hasEventHooks()) {
            try {
                jitPre(context, self, name, Block.NULL_BLOCK);

                return jitCompiledScript.__file__(context, self, arg0, arg1, arg2, Block.NULL_BLOCK);
            } catch (JumpException.ReturnJump rj) {
                return handleReturn(context, rj);
            } catch (JumpException.RedoJump rj) {
                return handleRedo(runtime);
            } finally {
                jitPost(runtime, context, name);
            }
        } else {
            return call(context, self, clazz, name, new IRubyObject[] {arg0, arg1, arg2}, Block.NULL_BLOCK);
        }
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject arg0, IRubyObject arg1, IRubyObject arg2, Block block) {
        Ruby runtime = context.getRuntime();
        
        if (jitCompiledScript != null && !runtime.hasEventHooks()) {
            try {
                jitPre(context, self, name, block);

                return jitCompiledScript.__file__(context, self, arg0, arg1, arg2, block);
            } catch (JumpException.ReturnJump rj) {
                return handleReturn(context, rj);
            } catch (JumpException.RedoJump rj) {
                return handleRedo(runtime);
            } finally {
                jitPost(runtime, context, name);
            }
        } else {
            return call(context, self, clazz, name, new IRubyObject[] {arg0, arg1, arg2}, block);
        }
    }

    private int assignOptArgs(IRubyObject[] args, Ruby runtime, ThreadContext context, IRubyObject self, int givenArgsCount) {
        ListNode optArgs = argsNode.getOptArgs();

        // assign given optional arguments to their variables
        int j = 0;
        for (int i = requiredArgsCount; i < args.length && j < optArgs.size(); i++, j++) {
            // in-frame EvalState should already have receiver set as self, continue to use it
            optArgs.get(j).assign(runtime, context, self, args[i], Block.NULL_BLOCK, true);
            givenArgsCount++;
        }

        // assign the default values, adding to the end of allArgs
        for (int i = 0; j < optArgs.size(); i++, j++) {
            optArgs.get(j).interpret(runtime, context, self, Block.NULL_BLOCK);
        }
        return givenArgsCount;
    }

    private void jitPre(ThreadContext context, IRubyObject self, String name, Block block) {
        RubyModule implementer = getImplementationClass();
        // FIXME: For some reason this wants (and works with) clazz instead of implementer,
        // and needed it for compiled module method_function's called from outside the module. Why?
        jitCallConfig.pre(context, self, implementer, name, block, staticScope, this);
    }

    private void jitPost(Ruby runtime, ThreadContext context, String name) {
        if (runtime.hasEventHooks()) {
            traceReturn(context, runtime, name);
        }
        jitCallConfig.post(context);
    }

    private void postInterpret(Ruby runtime, ThreadContext context, String name) {
        if (runtime.hasEventHooks()) {
            traceReturn(context, runtime, name);
        }
        context.postMethodFrameAndScope();
    }

    private void preInterpret(ThreadContext context, String name, IRubyObject self, Block block, Ruby runtime, int argsLength) {
        RubyModule implementer = getImplementationClass();

        context.preMethodFrameAndScope(implementer, name, self, block, staticScope);
        if (argsNode.getBlockArgNode() != null) {
            context.getCurrentScope().setValue(argsNode.getBlockArgNode().getCount(), RuntimeHelpers.processBlockArgument(runtime, block), 0);
        }

        getArity().checkArity(runtime, argsLength);

        if (runtime.hasEventHooks()) {
            traceCall(context, runtime, name);
        }
    }

    private void prepareArguments(ThreadContext context, Ruby runtime, IRubyObject self, IRubyObject[] args) {
        // Bind 'normal' parameter values to the local scope for this method.
        if (requiredArgsCount > 0) {
            context.getCurrentScope().setArgValues(args, requiredArgsCount);
        }

        // optArgs and restArgs require more work, so isolate them and ArrayList creation here
        if (hasOptArgs || restArg != -1) {
            prepareOptOrRestArgs(context, runtime, self, args);
        }
    }

    private void prepareOptOrRestArgs(ThreadContext context, Ruby runtime, IRubyObject self, IRubyObject[] args) {
        // we know we've at least got the required count at this point, so start with that
        int givenArgsCount = requiredArgsCount;
        
        if (hasOptArgs) {
            givenArgsCount = assignOptArgs(args, runtime, context, self, givenArgsCount);
        }
        
        if (restArg >= 0) {
            RubyArray array = RubyArray.newArrayNoCopy(runtime, args, givenArgsCount);
            context.getCurrentScope().setValue(restArg, array, 0);
        }
    }
    
    public ISourcePosition getPosition() {
        return position;
    }

    private void traceReturn(ThreadContext context, Ruby runtime, String name) {
        runtime.callEventHooks(context, EventHook.RUBY_EVENT_RETURN, context.getFile(), context.getLine(), name, getImplementationClass());
    }
    
    private void traceCall(ThreadContext context, Ruby runtime, String name) {
        runtime.callEventHooks(context, EventHook.RUBY_EVENT_CALL, position.getFile(), position.getStartLine(), name, getImplementationClass());
    }

    @Override
    public Arity getArity() {
        return argsNode.getArity();
    }
    
    public DynamicMethod dup() {
        return new DefaultMethod(getImplementationClass(), staticScope, body, argsNode, getVisibility(), position);
    }
}
