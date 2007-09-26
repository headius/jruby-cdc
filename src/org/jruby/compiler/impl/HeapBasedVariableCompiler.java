/*
 * HeapBasedVariableCompiler.java
 * 
 * Created on Jul 13, 2007, 11:23:05 PM
 * 
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jruby.compiler.impl;

import java.util.Arrays;
import org.jruby.Ruby;
import org.jruby.compiler.ArrayCallback;
import org.jruby.compiler.ClosureCallback;
import org.jruby.compiler.NotCompilableException;
import org.jruby.compiler.VariableCompiler;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.Frame;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.CodegenUtils;
import org.objectweb.asm.Label;

/**
 *
 * @author headius
 */
public class HeapBasedVariableCompiler implements VariableCompiler {
    private static final CodegenUtils cg = CodegenUtils.cg;
    private SkinnyMethodAdapter method;
    private StandardASMCompiler.AbstractMethodCompiler methodCompiler;
    private int scopeIndex; // the index of the DynamicScope in the local Java scope to use for depth > 0 variable accesses
    private int varsIndex; // the index of the IRubyObject[] in the local Java scope to use for depth 0 variable accesses
    private int argsIndex; // the index where an IRubyObject[] representing incoming arguments can be found
    private int closureIndex; // the index of the block parameter
    private Arity arity;

    public HeapBasedVariableCompiler(StandardASMCompiler.AbstractMethodCompiler methodCompiler, SkinnyMethodAdapter method, int scopeIndex, int varsIndex, int argsIndex, int closureIndex) {
        this.methodCompiler = methodCompiler;
        this.method = method;
        
        this.scopeIndex = scopeIndex;
        this.varsIndex = varsIndex;
        this.argsIndex = argsIndex;
        this.closureIndex = closureIndex;
    }
    
    public SkinnyMethodAdapter getMethodAdapter() {
        return this.method;
    }

    public void setMethodAdapter(SkinnyMethodAdapter sma) {
        this.method = sma;
    }

    public void beginMethod(ClosureCallback argsCallback, StaticScope scope) {
        // store the local vars in a local variable
        methodCompiler.loadThreadContext();
        methodCompiler.invokeThreadContext("getCurrentScope", cg.sig(DynamicScope.class));
        method.dup();
        method.astore(scopeIndex);
        method.invokevirtual(cg.p(DynamicScope.class), "getValues", cg.sig(IRubyObject[].class));
        method.astore(varsIndex);

        // fill local vars with nil, to avoid checking every access.
        method.aload(varsIndex);
        methodCompiler.loadNil();
        method.invokestatic(cg.p(Arrays.class), "fill", cg.sig(Void.TYPE, cg.params(Object[].class, Object.class)));
        
        if (argsCallback != null) {
            argsCallback.compile(methodCompiler);
        }
    }

    public void beginClass(ClosureCallback bodyPrep, StaticScope scope) {
        // store the local vars in a local variable for preparing the class (using previous scope)
        methodCompiler.loadThreadContext();
        methodCompiler.invokeThreadContext("getCurrentScope", cg.sig(DynamicScope.class));
        method.dup();
        method.astore(scopeIndex);
        method.invokevirtual(cg.p(DynamicScope.class), "getValues", cg.sig(IRubyObject[].class));
        method.astore(varsIndex);
        
        // class bodies prepare their own dynamic scope, so let it do that
        bodyPrep.compile(methodCompiler);
        
        // store the new local vars in a local variable
        methodCompiler.loadThreadContext();
        methodCompiler.invokeThreadContext("getCurrentScope", cg.sig(DynamicScope.class));
        method.dup();
        method.astore(scopeIndex);
        method.invokevirtual(cg.p(DynamicScope.class), "getValues", cg.sig(IRubyObject[].class));
        method.astore(varsIndex);

        // fill local vars with nil, to avoid checking every access.
        method.aload(varsIndex);
        methodCompiler.loadNil();
        method.invokestatic(cg.p(Arrays.class), "fill", cg.sig(Void.TYPE, cg.params(Object[].class, Object.class)));
    }

    public void beginClosure(ClosureCallback argsCallback, StaticScope scope) {
        // store the local vars in a local variable
        methodCompiler.loadThreadContext();
        methodCompiler.invokeThreadContext("getCurrentScope", cg.sig(DynamicScope.class));
        method.dup();
        method.astore(scopeIndex);
        method.invokevirtual(cg.p(DynamicScope.class), "getValues", cg.sig(IRubyObject[].class));
        method.astore(varsIndex);

        if (scope != null) {
            methodCompiler.loadNil();
            for (int i = 0; i < scope.getNumberOfVariables(); i++) {
                assignLocalVariable(i);
            }
            method.pop();
        }
        
        if (argsCallback != null) {
            // load args[0] which will be the IRubyObject representing block args
            method.aload(argsIndex);
            method.ldc(new Integer(0));
            method.arrayload();
            argsCallback.compile(methodCompiler);
            method.pop(); // clear remaining value on the stack
        }
    }

    public void assignLocalVariable(int index) {
        method.dup();

        method.aload(varsIndex);
        method.swap();
        method.ldc(new Integer(index));
        method.swap();
        method.arraystore();
    }

    public void assignLocalVariable(int index, int depth) {
        if (depth == 0) {
            assignLocalVariable(index);
            return;
        }

        method.dup();

        method.aload(scopeIndex);
        method.swap();
        method.ldc(new Integer(index));
        method.swap();
        method.ldc(new Integer(depth));
        method.invokevirtual(cg.p(DynamicScope.class), "setValue", cg.sig(Void.TYPE, cg.params(Integer.TYPE, IRubyObject.class, Integer.TYPE)));
    }

    public void retrieveLocalVariable(int index) {
        method.aload(varsIndex);
        method.ldc(new Integer(index));
        method.arrayload();
    }

    public void retrieveLocalVariable(int index, int depth) {
        if (depth == 0) {
            retrieveLocalVariable(index);
            return;
        }

        method.aload(scopeIndex);
        method.ldc(new Integer(index));
        method.ldc(new Integer(depth));
        method.invokevirtual(cg.p(DynamicScope.class), "getValue", cg.sig(IRubyObject.class, cg.params(Integer.TYPE, Integer.TYPE)));
        // FIXME: This is a pretty unpleasant perf hit, and it's not required for most local var accesses. We need a better way
        methodCompiler.nullToNil();
    }

    public void assignLastLine() {
        method.dup();

        methodCompiler.loadThreadContext();
        methodCompiler.invokeThreadContext("getCurrentFrame", cg.sig(Frame.class));
        method.swap();
        method.invokevirtual(cg.p(Frame.class), "setLastLine", cg.sig(Void.TYPE, cg.params(IRubyObject.class)));
    }

    public void retrieveLastLine() {
        methodCompiler.loadThreadContext();
        methodCompiler.invokeThreadContext("getCurrentFrame", cg.sig(Frame.class));
        method.invokevirtual(cg.p(Frame.class), "getLastLine", cg.sig(IRubyObject.class));
    }

    public void retrieveBackRef() {
        methodCompiler.loadThreadContext();
        methodCompiler.invokeThreadContext("getCurrentFrame", cg.sig(Frame.class));
        method.invokevirtual(cg.p(Frame.class), "getBackRef", cg.sig(IRubyObject.class));
    }

    public void processRequiredArgs(Arity arity, int requiredArgs, int optArgs, int restArg) {
        // check arity
        methodCompiler.loadThreadContext();
        methodCompiler.loadRuntime();
        method.aload(argsIndex);
        method.arraylength();
        method.ldc(new Integer(requiredArgs));
        method.ldc(new Integer(optArgs));
        method.ldc(new Integer(restArg));
        methodCompiler.invokeUtilityMethod("handleArgumentSizes", cg.sig(Void.TYPE, ThreadContext.class, Ruby.class, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE));

        Label noArgs = new Label();

        // check if args is null
        method.aload(argsIndex);
        method.ifnull(noArgs);

        // check if args length is zero
        method.aload(argsIndex);
        method.arraylength();
        method.ifeq(noArgs);

        if (requiredArgs + optArgs == 0 && restArg == 0) {
            // only restarg, just jump to noArgs and it will be processed separately
            method.go_to(noArgs);
        } else {
            // load dynamic scope and args array
            method.aload(scopeIndex);
            method.aload(argsIndex);

            // test whether total args count or actual args given is lower, for copying to dynamic scope
            Label useArgsLength = new Label();
            Label setArgValues = new Label();
            method.aload(argsIndex);
            method.arraylength();
            method.ldc(new Integer(requiredArgs + optArgs));
            method.if_icmplt(useArgsLength);

            // total args is lower, use that
            method.ldc(new Integer(requiredArgs + optArgs));
            method.go_to(setArgValues);

            // args length is lower, use that
            method.label(useArgsLength);
            method.aload(argsIndex);
            method.arraylength();

            // do the dew
            method.label(setArgValues);
            method.invokevirtual(cg.p(DynamicScope.class), "setArgValues", cg.sig(Void.TYPE, cg.params(IRubyObject[].class, Integer.TYPE)));
        }

        method.label(noArgs);

        // push down the argument count of this method
        this.arity = arity;
    }

    public void assignOptionalArgs(Object object, int expectedArgsCount, int size, ArrayCallback optEval) {
        // NOTE: By the time we're here, arity should have already been checked. We proceed without boundschecking.
        // opt args are handled with a switch; the key is how many args we have coming in, and the cases are
        // each opt arg index. The cases fall-through, so remaining opt args are handled.
        method.aload(argsIndex);
        method.arraylength();

        Label defaultLabel = new Label();
        Label[] labels = new Label[size];

        for (int i = 0; i < size; i++) {
            labels[i] = new Label();
        }

        method.tableswitch(expectedArgsCount, expectedArgsCount + size - 1, defaultLabel, labels);

        for (int i = 0; i < size; i++) {
            method.label(labels[i]);
            optEval.nextValue(methodCompiler, object, i);
            method.pop();
        }

        method.label(defaultLabel);
    }

    public void processRestArg(int startIndex, int restArg) {
        methodCompiler.loadRuntime();
        method.aload(argsIndex);
        method.ldc(new Integer(startIndex));

        methodCompiler.invokeUtilityMethod("processRestArg", cg.sig(IRubyObject.class, cg.params(Ruby.class, IRubyObject[].class, int.class)));
        assignLocalVariable(restArg);
        method.pop();
    }

    public void processBlockArgument(int index) {
        methodCompiler.loadRuntime();
        method.aload(closureIndex);
        
        methodCompiler.invokeUtilityMethod("processBlockArgument", cg.sig(IRubyObject.class, cg.params(Ruby.class, Block.class)));
        assignLocalVariable(index);
        method.pop();
    }
}
