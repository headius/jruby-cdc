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
import java.util.Iterator;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyModule;
import org.jruby.RubyProc;
import org.jruby.ast.ArgsNode;
import org.jruby.ast.ListNode;
import org.jruby.ast.Node;
import org.jruby.ast.executable.Script;
import org.jruby.compiler.NodeCompilerFactory;
import org.jruby.compiler.impl.StandardASMCompiler;
import org.jruby.evaluator.AssignmentVisitor;
import org.jruby.evaluator.CreateJumpTargetVisitor;
import org.jruby.evaluator.EvaluationState;
import org.jruby.exceptions.JumpException;
import org.jruby.lexer.yacc.ISourcePosition;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.DynamicMethod;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.collections.SinglyLinkedList;

/**
 *
 */
public final class DefaultMethod extends AbstractMethod {
    private StaticScope staticScope;
    private Node body;
    private ArgsNode argsNode;
    private SinglyLinkedList cref;
    private boolean hasBeenTargeted = false;
    private int callCount = 0;
    private static final int COMPILE_COUNT = 50;
    private Script jitCompiledScript;

    // change to true to enable JIT compilation
    private static final boolean JIT_ENABLED = Boolean.getBoolean("jruby.jit.enabled");
    
    public DefaultMethod(RubyModule implementationClass, StaticScope staticScope, Node body, 
            ArgsNode argsNode, Visibility visibility, SinglyLinkedList cref) {
        super(implementationClass, visibility);
        this.body = body;
        this.staticScope = staticScope;
        this.argsNode = argsNode;
		this.cref = cref;
		
		assert argsNode != null;
    }
    
    public void preMethod(ThreadContext context, RubyModule lastClass, IRubyObject recv, String name, IRubyObject[] args, boolean noSuper, Block block) {
        context.preDefMethodInternalCall(lastClass, recv, name, args, noSuper, cref, staticScope, block);
    }
    
    public void postMethod(ThreadContext context) {
        context.postDefMethodInternalCall();
    }

    /**
     * @see AbstractCallable#call(Ruby, IRubyObject, String, IRubyObject[], boolean)
     */
    // FIXME: This is commented out because problems were found compiling methods that call protected code.
    // because eliminating the pre/post does not change the "self" on the current frame, this caused
    // visibility to be a larger problem. We must revisit this to examine how to avoid this trap for visibility checks.
    public IRubyObject call(ThreadContext context, IRubyObject receiver, RubyModule lastClass, String name, IRubyObject[] args, boolean noSuper, Block block) {
        if (jitCompiledScript != null) {
            try {
                context.preCompiledMethod(implementationClass, cref);
                // FIXME: pass block when available
                return jitCompiledScript.run(context, receiver, args, Block.NULL_BLOCK);
            } finally {
                context.postCompiledMethod();
            }
        } else {
            return super.call(context, receiver, lastClass, name, args, noSuper, block);
        }
    }

    /**
     * @see AbstractCallable#call(Ruby, IRubyObject, String, IRubyObject[], boolean)
     */
    public IRubyObject internalCall(ThreadContext context, IRubyObject receiver, RubyModule lastClass, String name, IRubyObject[] args, boolean noSuper, Block block) {
        	assert args != null;
        if (JIT_ENABLED && jitCompiledScript != null) {
            return jitCompiledScript.run(context, receiver, args, Block.NULL_BLOCK);
        }
        
        Ruby runtime = context.getRuntime();
        
        if (!hasBeenTargeted) {
            CreateJumpTargetVisitor.setJumpTarget(this, body);
            hasBeenTargeted = true;
        }

        if (argsNode.getBlockArgNode() != null && block.isGiven()) {
            RubyProc blockArg;
            
            if (block.getProcObject() != null) {
                blockArg = (RubyProc) block.getProcObject();
            } else {
                blockArg = runtime.newProc(false, block);
                blockArg.getBlock().isLambda = block.isLambda;
            }
            // We pass depth zero since we know this only applies to newly created local scope
            context.getCurrentScope().setValue(argsNode.getBlockArgNode().getCount(), blockArg, 0);
        }

        try {
            prepareArguments(context, runtime, receiver, args);
            
            getArity().checkArity(runtime, args);

            traceCall(context, runtime, receiver, name);

            if (JIT_ENABLED) {
                runJIT(runtime, name);
            }
                    
            return EvaluationState.eval(context, body, receiver, block);
        } catch (JumpException je) {
        	if (je.getJumpType() == JumpException.JumpType.ReturnJump && je.getTarget() == this) {
	                return (IRubyObject) je.getValue();
        	}
            
       		throw je;
        } finally {
            traceReturn(context, runtime, receiver, name);
        }
    }

    private void runJIT(Ruby runtime, String name) {
        if (callCount >= 0 && getArity().isFixed()) {
            callCount++;
            if (callCount >= COMPILE_COUNT) {
                //                System.err.println("trying to compile: " + getImplementationClass().getBaseName() + "." + name);
                try {
                    String cleanName = cleanJavaIdentifier(name);
                    StandardASMCompiler compiler = new StandardASMCompiler(cleanName + hashCode(), body.getPosition().getFile());
                    compiler.startScript();
                    Object methodToken = compiler.beginMethod("__file__", getArity().getValue(), staticScope.getNumberOfVariables());
                    NodeCompilerFactory.getCompiler(body).compile(body, compiler);
                    compiler.endMethod(methodToken);
                    compiler.endScript();
                    Class sourceClass = compiler.loadClass(runtime);
                    jitCompiledScript = (Script)sourceClass.newInstance();
                    
                    String className = getImplementationClass().getBaseName();
                    if (className == null) {
                        className = "<anon class>";
                    }
                    System.out.println("compiled: " + className + "." + name);
                } catch (Exception e) {
                    //                    e.printStackTrace();
                } finally {
                    callCount = -1;
                }
            }
        }
    }

    private void prepareArguments(ThreadContext context, Ruby runtime, IRubyObject receiver, IRubyObject[] args) {
        int expectedArgsCount = argsNode.getArgsCount();

        int restArg = argsNode.getRestArg();
        boolean hasOptArgs = argsNode.getOptArgs() != null;

        // FIXME: This seems redundant with the arity check in internalCall...is it actually different?
        if (expectedArgsCount > args.length) {
            throw runtime.newArgumentError("Wrong # of arguments(" + args.length + " for " + expectedArgsCount + ")");
        }

        // Bind 'normal' parameter values to the local scope for this method.
        if (expectedArgsCount > 0) {
            context.getCurrentScope().setArgValues(args, expectedArgsCount);
        }

        // optArgs and restArgs require more work, so isolate them and ArrayList creation here
        if (hasOptArgs || restArg != -1) {
            args = prepareOptOrRestArgs(context, runtime, args, expectedArgsCount, restArg, hasOptArgs);
        }
        
        context.setFrameArgs(args);
    }

    private IRubyObject[] prepareOptOrRestArgs(ThreadContext context, Ruby runtime, IRubyObject[] args, int expectedArgsCount, int restArg, boolean hasOptArgs) {
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
   
            Iterator iter = optArgs.iterator();
            for (int i = expectedArgsCount; i < args.length && iter.hasNext(); i++) {
                //new AssignmentVisitor(new EvaluationState(runtime, receiver)).assign((Node)iter.next(), args[i], true);
                // in-frame EvalState should already have receiver set as self, continue to use it
                AssignmentVisitor.assign(context, context.getFrameSelf(), (Node)iter.next(), args[i], Block.NULL_BLOCK, true);
                expectedArgsCount++;
            }
   
            // assign the default values, adding to the end of allArgs
            while (iter.hasNext()) {
                //new EvaluationState(runtime, receiver).begin((Node)iter.next());
                //EvaluateVisitor.getInstance().eval(receiver.getRuntime(), receiver, (Node)iter.next());
                // in-frame EvalState should already have receiver set as self, continue to use it
                allArgs.add(EvaluationState.eval(context, (Node) iter.next(), context.getFrameSelf(), Block.NULL_BLOCK));
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
            for (int i = expectedArgsCount; i < args.length; i++) {
                allArgs.add(args[i]);
            }

            // only set in scope if named
            if (restArg >= 0) {
                RubyArray array = runtime.newArray(args.length - expectedArgsCount);
                for (int i = expectedArgsCount; i < args.length; i++) {
                    array.append(args[i]);
                }

                context.getCurrentScope().setValue(restArg, array, 0);
            }
        }
        
        args = (IRubyObject[])allArgs.toArray(new IRubyObject[allArgs.size()]);
        return args;
    }

    private void traceReturn(ThreadContext context, Ruby runtime, IRubyObject receiver, String name) {
        if (runtime.getTraceFunction() == null) {
            return;
        }

        ISourcePosition position = context.getPreviousFramePosition();
        runtime.callTraceFunction(context, "return", position, receiver, name, getImplementationClass());
    }

    private void traceCall(ThreadContext context, Ruby runtime, IRubyObject receiver, String name) {
        if (runtime.getTraceFunction() == null) {
            return;
        }

		ISourcePosition position = body != null ? 
                body.getPosition() : context.getPosition(); 

		runtime.callTraceFunction(context, "call", position, receiver, name, getImplementationClass());
    }

    public Arity getArity() {
        return argsNode.getArity();
    }
    
    public DynamicMethod dup() {
        return new DefaultMethod(getImplementationClass(), staticScope, body, argsNode, getVisibility(), cref);
    }	
    
    private String cleanJavaIdentifier(String name) {
        char[] characters = name.toCharArray();
        StringBuffer cleanBuffer = new StringBuffer();
        boolean prevWasReplaced = false;
        for (int i = 0; i < characters.length; i++) {
            if (Character.isJavaIdentifierStart(characters[i])) {
                cleanBuffer.append(characters[i]);
                prevWasReplaced = false;
            } else {
                if (!prevWasReplaced) {
                    cleanBuffer.append("_");
                }
                prevWasReplaced = true;
                switch (characters[i]) {
                case '?':
                    cleanBuffer.append("p_");
                    continue;
                case '!':
                    cleanBuffer.append("b_");
                    continue;
                case '<':
                    cleanBuffer.append("lt_");
                    continue;
                case '>':
                    cleanBuffer.append("gt_");
                    continue;
                case '=':
                    cleanBuffer.append("equal_");
                    continue;
                case '[':
                    if ((i + 1) < characters.length && characters[i + 1] == ']') {
                        cleanBuffer.append("aref_");
                        i++;
                    } else {
                        // can this ever happen?
                        cleanBuffer.append("lbracket_");
                    }
                    continue;
                case ']':
                    // given [ logic above, can this ever happen?
                    cleanBuffer.append("rbracket_");
                    continue;
                case '+':
                    cleanBuffer.append("plus_");
                    continue;
                case '-':
                    cleanBuffer.append("minus_");
                    continue;
                case '*':
                    cleanBuffer.append("times_");
                    continue;
                case '/':
                    cleanBuffer.append("div_");
                    continue;
                case '&':
                    cleanBuffer.append("and_");
                    continue;
                default:
                    cleanBuffer.append(Integer.toHexString(characters[i])).append("_");
                }
            }
        }
        return cleanBuffer.toString();
    }
}
