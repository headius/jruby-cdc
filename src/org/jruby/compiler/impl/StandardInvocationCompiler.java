/*
 ***** BEGIN LICENSE BLOCK *****
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

package org.jruby.compiler.impl;

import org.jruby.compiler.ArgumentsCallback;
import org.jruby.compiler.CompilerCallback;
import org.jruby.compiler.InvocationCompiler;
import org.jruby.compiler.NotCompilableException;
import org.jruby.runtime.Block;
import org.jruby.runtime.CallSite;
import org.jruby.runtime.CallType;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import static org.jruby.util.CodegenUtils.*;
import org.objectweb.asm.Label;

/**
 *
 * @author headius
 */
public class StandardInvocationCompiler implements InvocationCompiler {
    private StandardASMCompiler.AbstractMethodCompiler methodCompiler;
    private SkinnyMethodAdapter method;
    
    private static final int THIS = 0;

    public StandardInvocationCompiler(StandardASMCompiler.AbstractMethodCompiler methodCompiler, SkinnyMethodAdapter method) {
        this.methodCompiler = methodCompiler;
        this.method = method;
    }

    public SkinnyMethodAdapter getMethodAdapter() {
        return this.method;
    }

    public void setMethodAdapter(SkinnyMethodAdapter sma) {
        this.method = sma;
    }

    public void invokeAttrAssignMasgn(String name, CompilerCallback receiverCallback, ArgumentsCallback argsCallback) {
        // value is already on stack, evaluate receiver and args and call helper
        receiverCallback.call(methodCompiler);

        String signature;
        if (argsCallback == null) {
            signature = sig(IRubyObject.class,
                    IRubyObject.class /*value*/,
                    IRubyObject.class /*receiver*/,
                    ThreadContext.class,
                    CallSite.class);
        } else {
            switch (argsCallback.getArity()) {
                case 0:
                    signature = sig(IRubyObject.class,
                            IRubyObject.class /*value*/,
                            IRubyObject.class /*receiver*/,
                            ThreadContext.class,
                            CallSite.class);
                    break;
                case 1:
                    argsCallback.call(methodCompiler);
                    signature = sig(IRubyObject.class,
                            IRubyObject.class /*value*/,
                            IRubyObject.class /*receiver*/,
                            IRubyObject.class /*arg0*/,
                            ThreadContext.class,
                            CallSite.class);
                    break;
                case 2:
                    argsCallback.call(methodCompiler);
                    signature = sig(IRubyObject.class,
                            IRubyObject.class /*value*/,
                            IRubyObject.class /*receiver*/,
                            IRubyObject.class /*arg0*/,
                            IRubyObject.class /*arg1*/,
                            ThreadContext.class,
                            CallSite.class);
                    break;
                case 3:
                    argsCallback.call(methodCompiler);
                    signature = sig(IRubyObject.class,
                            IRubyObject.class /*value*/,
                            IRubyObject.class /*receiver*/,
                            IRubyObject.class /*arg0*/,
                            IRubyObject.class /*arg1*/,
                            IRubyObject.class /*arg2*/,
                            ThreadContext.class,
                            CallSite.class);
                    break;
                default:
                    argsCallback.call(methodCompiler);
                    signature = sig(IRubyObject.class,
                            IRubyObject.class /*value*/,
                            IRubyObject.class /*receiver*/,
                            IRubyObject[].class /*args*/,
                            ThreadContext.class,
                            CallSite.class);
                    break;
            }
        }
        
        methodCompiler.loadThreadContext();
        methodCompiler.getScriptCompiler().getCacheCompiler().cacheCallSite(methodCompiler, name, CallType.NORMAL);

        methodCompiler.invokeUtilityMethod("doAttrAsgn", signature);
    }

    public void invokeAttrAssign(String name, CompilerCallback receiverCallback, ArgumentsCallback argsCallback) {
        Label variableCallType = new Label();
        Label readyForCall = new Label();
        
        // receiver first, so we know which call site to use
        receiverCallback.call(methodCompiler);
        
        // select appropriate call site
        method.dup(); // dup receiver
        methodCompiler.loadSelf(); // load self
        method.if_acmpeq(variableCallType); // compare
        
        methodCompiler.getScriptCompiler().getCacheCompiler().cacheCallSite(methodCompiler, name, CallType.NORMAL);
        method.go_to(readyForCall);
        method.label(variableCallType);
        methodCompiler.getScriptCompiler().getCacheCompiler().cacheCallSite(methodCompiler, name, CallType.VARIABLE);
        method.label(readyForCall);
        
        String signature = null;
        switch (argsCallback.getArity()) {
        case 1:
            signature = sig(IRubyObject.class,
                    IRubyObject.class, /*receiver*/
                    CallSite.class,
                    IRubyObject.class, /*value*/
                    ThreadContext.class);
            break;
        case 2:
            signature = sig(IRubyObject.class,
                    IRubyObject.class, /*receiver*/
                    CallSite.class,
                    IRubyObject.class, /*arg0*/
                    IRubyObject.class, /*value*/
                    ThreadContext.class);
            break;
        case 3:
            signature = sig(IRubyObject.class,
                    IRubyObject.class, /*receiver*/
                    CallSite.class,
                    IRubyObject.class, /*arg0*/
                    IRubyObject.class, /*arg1*/
                    IRubyObject.class, /*value*/
                    ThreadContext.class);
            break;
        default:
            signature = sig(IRubyObject.class,
                    IRubyObject.class, /*receiver*/
                    CallSite.class,
                    IRubyObject[].class, /*args*/
                    ThreadContext.class);
        }
        
        argsCallback.call(methodCompiler);
        methodCompiler.loadThreadContext();
        
        methodCompiler.invokeUtilityMethod("doAttrAsgn", signature);
    }
    
    public void opElementAsgnWithOr(CompilerCallback receiver, ArgumentsCallback args, CompilerCallback valueCallback) {
        // get call site and thread context
        methodCompiler.getScriptCompiler().getCacheCompiler().cacheCallSite(methodCompiler, "[]", CallType.FUNCTIONAL);
        methodCompiler.loadThreadContext();
        methodCompiler.loadSelf();
        
        // evaluate and save receiver and args
        receiver.call(methodCompiler);
        args.call(methodCompiler);
        method.dup2();
        int argsLocal = methodCompiler.getVariableCompiler().grabTempLocal();
        methodCompiler.getVariableCompiler().setTempLocal(argsLocal);
        int receiverLocal = methodCompiler.getVariableCompiler().grabTempLocal();
        methodCompiler.getVariableCompiler().setTempLocal(receiverLocal);
        
        // invoke
        switch (args.getArity()) {
        case 1:
            method.invokevirtual(p(CallSite.class), "callFrom", sig(IRubyObject.class, ThreadContext.class, IRubyObject.class, IRubyObject.class, IRubyObject.class));
            break;
        default:
            method.invokevirtual(p(CallSite.class), "callFrom", sig(IRubyObject.class, ThreadContext.class, IRubyObject.class, IRubyObject.class, IRubyObject[].class));
        }
        
        // check if it's true, ending if so
        method.dup();
        methodCompiler.invokeIRubyObject("isTrue", sig(boolean.class));
        Label done = new Label();
        method.ifne(done);
        
        // not true, eval value and assign
        method.pop();
        // thread context, receiver and original args
        methodCompiler.loadThreadContext();
        methodCompiler.getVariableCompiler().getTempLocal(receiverLocal);
        methodCompiler.getVariableCompiler().getTempLocal(argsLocal);
        
        // eval value for assignment
        valueCallback.call(methodCompiler);
        
        // call site
        methodCompiler.getScriptCompiler().getCacheCompiler().cacheCallSite(methodCompiler, "[]=", CallType.FUNCTIONAL);
        
        // depending on size of original args, call appropriate utility method
        switch (args.getArity()) {
        case 0:
            throw new NotCompilableException("Op Element Asgn with zero-arity args");
        case 1:
            methodCompiler.invokeUtilityMethod("opElementAsgnWithOrPartTwoOneArg", 
                    sig(IRubyObject.class, ThreadContext.class, IRubyObject.class, IRubyObject.class, IRubyObject.class, CallSite.class));
            break;
        case 2:
            methodCompiler.invokeUtilityMethod("opElementAsgnWithOrPartTwoTwoArgs", 
                    sig(IRubyObject.class, ThreadContext.class, IRubyObject.class, IRubyObject[].class, IRubyObject.class, CallSite.class));
            break;
        case 3:
            methodCompiler.invokeUtilityMethod("opElementAsgnWithOrPartTwoThreeArgs", 
                    sig(IRubyObject.class, ThreadContext.class, IRubyObject.class, IRubyObject[].class, IRubyObject.class, CallSite.class));
            break;
        default:
            methodCompiler.invokeUtilityMethod("opElementAsgnWithOrPartTwoNArgs", 
                    sig(IRubyObject.class, ThreadContext.class, IRubyObject.class, IRubyObject[].class, IRubyObject.class, CallSite.class));
            break;
        }
        
        method.label(done);
        
        methodCompiler.getVariableCompiler().releaseTempLocal();
        methodCompiler.getVariableCompiler().releaseTempLocal();
    }
    
    public void opElementAsgnWithAnd(CompilerCallback receiver, ArgumentsCallback args, CompilerCallback valueCallback) {
        // get call site and thread context
        methodCompiler.getScriptCompiler().getCacheCompiler().cacheCallSite(methodCompiler, "[]", CallType.FUNCTIONAL);
        methodCompiler.loadThreadContext();
        methodCompiler.loadSelf();
        
        // evaluate and save receiver and args
        receiver.call(methodCompiler);
        args.call(methodCompiler);
        method.dup2();
        int argsLocal = methodCompiler.getVariableCompiler().grabTempLocal();
        methodCompiler.getVariableCompiler().setTempLocal(argsLocal);
        int receiverLocal = methodCompiler.getVariableCompiler().grabTempLocal();
        methodCompiler.getVariableCompiler().setTempLocal(receiverLocal);
        
        // invoke
        switch (args.getArity()) {
        case 1:
            method.invokevirtual(p(CallSite.class), "callFrom", sig(IRubyObject.class, ThreadContext.class, IRubyObject.class, IRubyObject.class, IRubyObject.class));
            break;
        default:
            method.invokevirtual(p(CallSite.class), "callFrom", sig(IRubyObject.class, ThreadContext.class, IRubyObject.class, IRubyObject.class, IRubyObject[].class));
        }
        
        // check if it's true, ending if not
        method.dup();
        methodCompiler.invokeIRubyObject("isTrue", sig(boolean.class));
        Label done = new Label();
        method.ifeq(done);
        
        // not true, eval value and assign
        method.pop();
        // thread context, receiver and original args
        methodCompiler.loadThreadContext();
        methodCompiler.getVariableCompiler().getTempLocal(receiverLocal);
        methodCompiler.getVariableCompiler().getTempLocal(argsLocal);
        
        // eval value and save it
        valueCallback.call(methodCompiler);
        
        // call site
        methodCompiler.getScriptCompiler().getCacheCompiler().cacheCallSite(methodCompiler, "[]=", CallType.FUNCTIONAL);
        
        // depending on size of original args, call appropriate utility method
        switch (args.getArity()) {
        case 0:
            throw new NotCompilableException("Op Element Asgn with zero-arity args");
        case 1:
            methodCompiler.invokeUtilityMethod("opElementAsgnWithOrPartTwoOneArg", 
                    sig(IRubyObject.class, ThreadContext.class, IRubyObject.class, IRubyObject.class, IRubyObject.class, CallSite.class));
            break;
        case 2:
            methodCompiler.invokeUtilityMethod("opElementAsgnWithOrPartTwoTwoArgs", 
                    sig(IRubyObject.class, ThreadContext.class, IRubyObject.class, IRubyObject[].class, IRubyObject.class, CallSite.class));
            break;
        case 3:
            methodCompiler.invokeUtilityMethod("opElementAsgnWithOrPartTwoThreeArgs", 
                    sig(IRubyObject.class, ThreadContext.class, IRubyObject.class, IRubyObject[].class, IRubyObject.class, CallSite.class));
            break;
        default:
            methodCompiler.invokeUtilityMethod("opElementAsgnWithOrPartTwoNArgs", 
                    sig(IRubyObject.class, ThreadContext.class, IRubyObject.class, IRubyObject[].class, IRubyObject.class, CallSite.class));
            break;
        }
        
        method.label(done);
        
        methodCompiler.getVariableCompiler().releaseTempLocal();
        methodCompiler.getVariableCompiler().releaseTempLocal();
    }
    
    public void opElementAsgnWithMethod(CompilerCallback receiver, ArgumentsCallback args, CompilerCallback valueCallback, String operator) {
        methodCompiler.loadThreadContext();
        receiver.call(methodCompiler);
        args.call(methodCompiler);
        valueCallback.call(methodCompiler); // receiver, args, result, value
        methodCompiler.getScriptCompiler().getCacheCompiler().cacheCallSite(methodCompiler, "[]", CallType.FUNCTIONAL);
        methodCompiler.getScriptCompiler().getCacheCompiler().cacheCallSite(methodCompiler, operator, CallType.NORMAL);
        methodCompiler.getScriptCompiler().getCacheCompiler().cacheCallSite(methodCompiler, "[]=", CallType.FUNCTIONAL);
        
        switch (args.getArity()) {
        case 0:
            methodCompiler.invokeUtilityMethod("opElementAsgnWithMethod",
                    sig(IRubyObject.class, ThreadContext.class, IRubyObject.class, IRubyObject.class, CallSite.class, CallSite.class, CallSite.class));
            break;
        case 1:
            methodCompiler.invokeUtilityMethod("opElementAsgnWithMethod",
                    sig(IRubyObject.class, ThreadContext.class, IRubyObject.class, IRubyObject.class, IRubyObject.class, CallSite.class, CallSite.class, CallSite.class));
            break;
        case 2:
            methodCompiler.invokeUtilityMethod("opElementAsgnWithMethod",
                    sig(IRubyObject.class, ThreadContext.class, IRubyObject.class, IRubyObject.class, IRubyObject.class, IRubyObject.class, CallSite.class, CallSite.class, CallSite.class));
            break;
        case 3:
            methodCompiler.invokeUtilityMethod("opElementAsgnWithMethod",
                    sig(IRubyObject.class, ThreadContext.class, IRubyObject.class, IRubyObject.class, IRubyObject.class, IRubyObject.class, IRubyObject.class, CallSite.class, CallSite.class, CallSite.class));
            break;
        default:
            methodCompiler.invokeUtilityMethod("opElementAsgnWithMethod",
                    sig(IRubyObject.class, ThreadContext.class, IRubyObject.class, IRubyObject[].class, IRubyObject.class, CallSite.class, CallSite.class, CallSite.class));
            break;
        }
    }

    public void invokeSuper(CompilerCallback argsCallback, CompilerCallback closureArg) {
        methodCompiler.loadThreadContext();
        methodCompiler.invokeUtilityMethod("checkSuperDisabledOrOutOfMethod", sig(void.class, ThreadContext.class));
        
        methodCompiler.loadSelf();

        methodCompiler.loadThreadContext(); // [self, tc]
        
        // args
        if (argsCallback == null) {
            method.getstatic(p(IRubyObject.class), "NULL_ARRAY", ci(IRubyObject[].class));
            // block
            if (closureArg == null) {
                // no args, no block
                methodCompiler.loadBlock();
            } else {
                // no args, with block
                closureArg.call(methodCompiler);
            }
        } else {
            argsCallback.call(methodCompiler);
            // block
            if (closureArg == null) {
                // with args, no block
                methodCompiler.loadBlock();
            } else {
                // with args, with block
                closureArg.call(methodCompiler);
            }
        }
        
        method.invokeinterface(p(IRubyObject.class), "callSuper", sig(IRubyObject.class, ThreadContext.class, IRubyObject[].class, Block.class));
    }

    public void invokeDynamic(String name, CompilerCallback receiverCallback, ArgumentsCallback argsCallback, CallType callType, CompilerCallback closureArg, boolean iterator) {
        methodCompiler.getScriptCompiler().getCacheCompiler().cacheCallSite(methodCompiler, name, callType);

        methodCompiler.loadThreadContext(); // [adapter, tc]

        // for visibility checking without requiring frame self
        // TODO: don't bother passing when fcall or vcall, and adjust callsite appropriately
        methodCompiler.loadSelf();
        
        if (receiverCallback != null) {
            receiverCallback.call(methodCompiler);
        } else {
            methodCompiler.loadSelf();
        }
        
        String signature;
        String callSiteMethod = "callFrom";
        // args
        if (argsCallback == null) {
            // block
            if (closureArg == null) {
                // no args, no block
                signature = sig(IRubyObject.class, params(ThreadContext.class, IRubyObject.class, IRubyObject.class));
            } else {
                // no args, with block
                if (iterator) callSiteMethod = "callIterFrom";
                closureArg.call(methodCompiler);
                signature = sig(IRubyObject.class, params(ThreadContext.class, IRubyObject.class, IRubyObject.class, Block.class));
            }
        } else {
            argsCallback.call(methodCompiler);
            // block
            if (closureArg == null) {
                // with args, no block
                switch (argsCallback.getArity()) {
                case 1:
                    signature = sig(IRubyObject.class, params(ThreadContext.class, IRubyObject.class, IRubyObject.class, IRubyObject.class));
                    break;
                case 2:
                    signature = sig(IRubyObject.class, params(ThreadContext.class, IRubyObject.class, IRubyObject.class, IRubyObject.class, IRubyObject.class));
                    break;
                case 3:
                    signature = sig(IRubyObject.class, params(ThreadContext.class, IRubyObject.class, IRubyObject.class, IRubyObject.class, IRubyObject.class, IRubyObject.class));
                    break;
                default:
                    signature = sig(IRubyObject.class, params(ThreadContext.class, IRubyObject.class, IRubyObject.class, IRubyObject[].class));
                }
            } else {
                // with args, with block
                if (iterator) callSiteMethod = "callIterFrom";
                closureArg.call(methodCompiler);
                
                switch (argsCallback.getArity()) {
                case 1:
                    signature = sig(IRubyObject.class, params(ThreadContext.class, IRubyObject.class, IRubyObject.class, IRubyObject.class, Block.class));
                    break;
                case 2:
                    signature = sig(IRubyObject.class, params(ThreadContext.class, IRubyObject.class, IRubyObject.class, IRubyObject.class, IRubyObject.class, Block.class));
                    break;
                case 3:
                    signature = sig(IRubyObject.class, params(ThreadContext.class, IRubyObject.class, IRubyObject.class, IRubyObject.class, IRubyObject.class, IRubyObject.class, Block.class));
                    break;
                default:
                    signature = sig(IRubyObject.class, params(ThreadContext.class, IRubyObject.class, IRubyObject.class, IRubyObject[].class, Block.class));
                }
            }
        }
        
        // adapter, tc, recv, args{0,1}, block{0,1}]

        method.invokevirtual(p(CallSite.class), callSiteMethod, signature);
    }

    public void invokeOpAsgnWithOr(String attrName, String attrAsgnName, CompilerCallback receiverCallback, ArgumentsCallback argsCallback) {
        receiverCallback.call(methodCompiler);
        method.dup();
        methodCompiler.loadThreadContext();
        methodCompiler.getScriptCompiler().getCacheCompiler().cacheCallSite(methodCompiler, attrName, CallType.FUNCTIONAL);
        
        methodCompiler.invokeUtilityMethod("preOpAsgnWithOrAnd", sig(IRubyObject.class, IRubyObject.class, ThreadContext.class, CallSite.class));
        
        Label done = new Label();
        Label isTrue = new Label();
        
        method.dup();
        methodCompiler.invokeIRubyObject("isTrue", sig(boolean.class));
        method.ifne(isTrue);
        
        method.pop(); // pop extra attr value
        argsCallback.call(methodCompiler);
        methodCompiler.loadThreadContext();
        methodCompiler.getScriptCompiler().getCacheCompiler().cacheCallSite(methodCompiler, attrAsgnName, CallType.NORMAL);
        
        methodCompiler.invokeUtilityMethod("postOpAsgnWithOrAnd",
                sig(IRubyObject.class, IRubyObject.class, IRubyObject.class, ThreadContext.class, CallSite.class));
        method.go_to(done);
        
        method.label(isTrue);
        method.swap();
        method.pop();
        
        method.label(done);
    }

    public void invokeOpAsgnWithAnd(String attrName, String attrAsgnName, CompilerCallback receiverCallback, ArgumentsCallback argsCallback) {
        receiverCallback.call(methodCompiler);
        method.dup();
        methodCompiler.loadThreadContext();
        methodCompiler.getScriptCompiler().getCacheCompiler().cacheCallSite(methodCompiler, attrName, CallType.FUNCTIONAL);
        
        methodCompiler.invokeUtilityMethod("preOpAsgnWithOrAnd", sig(IRubyObject.class, IRubyObject.class, ThreadContext.class, CallSite.class));
        
        Label done = new Label();
        Label isFalse = new Label();
        
        method.dup();
        methodCompiler.invokeIRubyObject("isTrue", sig(boolean.class));
        method.ifeq(isFalse);
        
        method.pop(); // pop extra attr value
        argsCallback.call(methodCompiler);
        methodCompiler.loadThreadContext();
        methodCompiler.getScriptCompiler().getCacheCompiler().cacheCallSite(methodCompiler, attrAsgnName, CallType.NORMAL);
        
        methodCompiler.invokeUtilityMethod("postOpAsgnWithOrAnd",
                sig(IRubyObject.class, IRubyObject.class, IRubyObject.class, ThreadContext.class, CallSite.class));
        method.go_to(done);
        
        method.label(isFalse);
        method.swap();
        method.pop();
        
        method.label(done);
    }

    public void invokeOpAsgnWithMethod(String operatorName, String attrName, String attrAsgnName, CompilerCallback receiverCallback, ArgumentsCallback argsCallback) {
        methodCompiler.loadThreadContext();
        receiverCallback.call(methodCompiler);
        argsCallback.call(methodCompiler);
        methodCompiler.getScriptCompiler().getCacheCompiler().cacheCallSite(methodCompiler, attrName, CallType.FUNCTIONAL);
        methodCompiler.getScriptCompiler().getCacheCompiler().cacheCallSite(methodCompiler, operatorName, CallType.FUNCTIONAL);
        methodCompiler.getScriptCompiler().getCacheCompiler().cacheCallSite(methodCompiler, attrAsgnName, CallType.NORMAL);
        
        methodCompiler.invokeUtilityMethod("opAsgnWithMethod",
                sig(IRubyObject.class, ThreadContext.class, IRubyObject.class, IRubyObject.class, CallSite.class, CallSite.class, CallSite.class));
    }

    public void invokeOpElementAsgnWithMethod(String operatorName, CompilerCallback receiverCallback, ArgumentsCallback argsCallback) {
        methodCompiler.loadThreadContext(); // [adapter, tc]
        receiverCallback.call(methodCompiler);
        argsCallback.call(methodCompiler);
        methodCompiler.getScriptCompiler().getCacheCompiler().cacheCallSite(methodCompiler, "[]", CallType.FUNCTIONAL);
        methodCompiler.getScriptCompiler().getCacheCompiler().cacheCallSite(methodCompiler, operatorName, CallType.FUNCTIONAL);
        methodCompiler.getScriptCompiler().getCacheCompiler().cacheCallSite(methodCompiler, "[]=", CallType.NORMAL);
        
        methodCompiler.invokeUtilityMethod("opElementAsgnWithMethod",
                sig(IRubyObject.class, ThreadContext.class, IRubyObject.class, IRubyObject.class, CallSite.class, CallSite.class, CallSite.class));
    }

    public void yield(CompilerCallback argsCallback, boolean unwrap) {
        methodCompiler.loadBlock();
        methodCompiler.loadThreadContext();

        String signature;
        if (argsCallback != null) {
            argsCallback.call(methodCompiler);
            signature = sig(IRubyObject.class, ThreadContext.class, IRubyObject.class, boolean.class);
        } else {
            signature = sig(IRubyObject.class, ThreadContext.class, boolean.class);
        }
        method.ldc(unwrap);

        method.invokevirtual(p(Block.class), "yield", signature);
    }

    public void invokeEqq() {
        // receiver and args already present on the stack
        
        methodCompiler.getScriptCompiler().getCacheCompiler().cacheCallSite(methodCompiler, "===", CallType.NORMAL);
        methodCompiler.loadThreadContext();
        
        methodCompiler.invokeUtilityMethod("invokeEqqForCaseWhen", sig(IRubyObject.class,
                IRubyObject.class, /*receiver*/
                IRubyObject.class, /*arg*/
                CallSite.class,
                ThreadContext.class));
    }
}
