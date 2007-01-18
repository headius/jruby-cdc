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
 * Copyright (C) 2006 Charles O Nutter <headius@headius.com>
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Stack;
import org.jruby.IRuby;
import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyBoolean;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyModule;
import org.jruby.RubyString;
import org.jruby.ast.Node;
import org.jruby.ast.executable.Script;
import org.jruby.common.RubyWarnings;
import org.jruby.compiler.*;
import org.jruby.compiler.Compiler;
import org.jruby.exceptions.RaiseException;
import org.jruby.internal.runtime.GlobalVariables;
import org.jruby.lexer.yacc.ISourcePosition;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.CallType;
import org.jruby.runtime.DynamicMethod;
import org.jruby.runtime.MethodFactory;
import org.jruby.runtime.MethodIndex;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.JRubyClassLoader;
import org.jruby.util.collections.SinglyLinkedList;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 *
 * @author headius
 */
public class StandardASMCompiler implements Compiler {
    private static final String THREADCONTEXT = p(ThreadContext.class);
    private static final String IRUBY = p(IRuby.class);
    private static final String IRUBYOBJECT = p(IRubyObject.class);
    
    private static final String METHOD_SIGNATURE =
            sig(IRubyObject.class, new Class[] { ThreadContext.class, IRubyObject.class, IRubyObject[].class, Block.class });
    private static final String CLOSURE_SIGNATURE =
            sig(IRubyObject.class, new Class[] { ThreadContext.class, IRubyObject.class, IRubyObject[].class });
    
    private static final int THREADCONTEXT_INDEX = 0;
    private static final int SELF_INDEX = 1;
    private static final int ARGS_INDEX = 2;
    private static final int CLOSURE_INDEX = 3;
    private static final int RUNTIME_INDEX = 4;
    private static final int LOCAL_VARS_INDEX = 5;
    
    //private Stack classVisitors = new Stack();
    private Stack methodVisitors = new Stack();
    private Stack arities = new Stack();
    private Stack scopeStarts = new Stack();
    
    private String classname;
    private String sourcename;
    
    //Map classWriters = new HashMap();
    private ClassWriter classWriter;
    ClassWriter currentMultiStub = null;
    int methodIndex = -1;
    int multiStubCount = -1;
    int innerIndex = 1;
    
    int lastLine = -1;
    
    /** Creates a new instance of StandardCompilerContext */
    public StandardASMCompiler(String classname, String sourcename) {
        this.classname = classname;
        this.sourcename = sourcename;
    }
    
    public StandardASMCompiler(Node node) {
        // determine new class name based on filename of incoming node
        ISourcePosition position = node.getPosition();
        
        if (position != null) {
            classname = position.getFile();
            if (classname.endsWith(".rb")) {
                classname = classname.substring(0, classname.length() - 3);
            }
            sourcename = position.getFile();
        } else {
            // must generate unique classnames for evals, since they could be generated many times in a given run
            classname = "EVAL" + hashCode();
            sourcename = "EVAL" + hashCode();
        }
    }
    
    /**
     * Creates a dotted class name from a path/package name
     */
    private static String c(String p) {
        return p.replace("/", ".");
    }

    /**
     * Creates a class path name, from a Class.
     */
    private static String p(Class n) {
        return n.getName().replace('.','/');
    }

    /**
     * Creates a class identifier of form Labc/abc;, from a Class.
     */
    private static String ci(Class n) {
        if (n.isArray()) {
            n = n.getComponentType();
            if (n.isPrimitive()) {
                if (n == Byte.TYPE) {
                    return "[B";
                } else if (n == Boolean.TYPE) {
                    return "[Z";
                } else if (n == Integer.TYPE) {
                    return "[I";
                } else if (n == Double.TYPE) {
                    return "[D";
                } else if (n == Long.TYPE) {
                    return "[J";
                } else {
                    throw new RuntimeException("Unrecognized type in compiler: " + n.getName());
                }
            } else {
                return "[L" + p(n) + ";";
            }
        } else {
            if (n.isPrimitive()) {
                if (n == Byte.TYPE) {
                    return "B";
                } else if (n == Boolean.TYPE) {
                    return "Z";
                } else if (n == Integer.TYPE) {
                    return "I";
                } else if (n == Double.TYPE) {
                    return "D";
                } else if (n == Long.TYPE) {
                    return "J";
                } else if (n == Void.TYPE) {
                    return "V";
                } else {
                    throw new RuntimeException("Unrecognized type in compiler: " + n.getName());
                }
            } else {
                return "L" + p(n) + ";";
            }
        }
    }
    
    /**
     * Create a method signature from the given param types and return values
     */
    private static String sig(Class retval, Class[] params) {
        StringBuffer signature = new StringBuffer("(");
        
        for (int i = 0; i < params.length; i++) {
            signature.append(ci(params[i]));
        }
        
        signature.append(")").append(ci(retval));
        
        return signature.toString();
    }
    
    /**
     * Create a method signature with just a return value
     */
    private static String sig(Class retval) {
        StringBuffer signature = new StringBuffer("()");
        
        signature.append(ci(retval));
        
        return signature.toString();
    }
    
    // TODO: Wouldn't it be nice to replace this all with a single varargs?
    private static Class[] params(Class a) {
        return new Class[] {a};
    }
    private static Class[] params(Class a, Class b) {
        return new Class[] {a,b};
    }
    private static Class[] params(Class a, Class b, Class c) {
        return new Class[] {a,b,c};
    }
    private static Class[] params(Class a, Class b, Class c, Class d) {
        return new Class[] {a,b,c,d};
    }
    private static Class[] params(Class a, Class b, Class c, Class d, Class e) {
        return new Class[] {a,b,c,d,e};
    }
    private static Class[] params(Class a, Class b, Class c, Class d, Class e, Class f) {
        return new Class[] {a,b,c,d,e,f};
    }
    
    public Class loadClass(IRuby runtime) throws ClassNotFoundException {
        JRubyClassLoader jcl = runtime.getJRubyClassLoader();
        
        jcl.defineClass(c(classname), classWriter.toByteArray());

        return jcl.loadClass(c(classname));
    }
    
    public void writeClass(File destination) throws IOException {
        writeClass(classname, destination, classWriter);
    }
    
    private void writeClass(String classname, File destination, ClassWriter writer) throws IOException {
        String fullname = classname + ".class";
        String filename = null;
        String path = null;
        if (fullname.lastIndexOf("/") == -1) {
            filename = fullname;
            path = "";
        } else {
            filename = fullname.substring(fullname.lastIndexOf("/") + 1);
            path = fullname.substring(0, fullname.lastIndexOf("/"));
        }
        // create dir if necessary
        File pathfile = new File(destination, path);
        pathfile.mkdirs();

        FileOutputStream out = new FileOutputStream(new File(pathfile, filename));

        out.write(writer.toByteArray());
    }
    
    public String getClassname() {
        return classname;
    }
    
    public String getSourcename() {
        return sourcename;
    }

    public ClassVisitor getClassVisitor() {
        return classWriter;
    }

    public MethodVisitor getMethodVisitor() {
        return (MethodVisitor)methodVisitors.peek();
    }

    public MethodVisitor popMethodVisitor() {
        return (MethodVisitor)methodVisitors.pop();
    }

    public void pushMethodVisitor(MethodVisitor mv) {
        methodVisitors.push(mv);
    }
    
    public int getArity() {
        return ((Integer)arities.peek()).intValue();
    }
    
    public void pushArity(int arity) {
        arities.push(new Integer(arity));
    }
    
    public int popArity() {
        return ((Integer)arities.pop()).intValue();
    }
    
    public void pushScopeStart(Label start) {
        scopeStarts.push(start);
    }
    
    public Label popScopeStart() {
        return (Label)scopeStarts.pop();
    }
    
    public void startScript() {
        classWriter = new ClassWriter(true);
        
        // Create the class with the appropriate class name and source file
        classWriter.visit(Opcodes.V1_2, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER, classname, null, p(Object.class), new String[] {p(Script.class)});
        classWriter.visitSource(sourcename, null);
        
        createConstructor();
    }
    
    public void endScript() {
        // add Script#run impl, used for running this script with a specified threadcontext and self
        // root method of a script is always in __load__ method
        String methodName = "__file__";
        MethodVisitor mv = getClassVisitor().visitMethod(Opcodes.ACC_PUBLIC, "run", METHOD_SIGNATURE, null, null);
        mv.visitCode();
        
        // invoke __file__ with threadcontext, self, args (null), and block (null)
        // These are all +1 because run is an instance method where others are static
        mv.visitVarInsn(Opcodes.ALOAD, THREADCONTEXT_INDEX + 1);
        mv.visitVarInsn(Opcodes.ALOAD, SELF_INDEX + 1);
        mv.visitVarInsn(Opcodes.ALOAD, ARGS_INDEX + 1);
        mv.visitVarInsn(Opcodes.ALOAD, CLOSURE_INDEX + 1);
        
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, classname, methodName, METHOD_SIGNATURE);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        // add main impl, used for detached or command-line execution of this script with a new runtime
        // root method of a script is always in stub0, method0
        mv = getClassVisitor().visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "main", sig(Void.TYPE, params(String[].class)), null, null);
        mv.visitCode();
        
        // new instance to invoke run against
        mv.visitTypeInsn(Opcodes.NEW, classname);
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, classname, "<init>", sig(Void.TYPE));
        
        // invoke run with threadcontext and topself
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, p(Ruby.class), "getDefaultInstance", sig(IRuby.class));
        mv.visitInsn(Opcodes.DUP);
        
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, IRUBY, "getCurrentContext", sig(ThreadContext.class));
        mv.visitInsn(Opcodes.SWAP);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, IRUBY, "getTopSelf", sig(IRubyObject.class));
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitInsn(Opcodes.ACONST_NULL);
        
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, classname, "run", METHOD_SIGNATURE);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }
    
    private void createConstructor() {
        ClassVisitor cv = getClassVisitor();
        
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, p(Object.class), "<init>",
                sig(Void.TYPE));
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    public Object beginMethod(String friendlyName, int arity, int localVarCount) {
        MethodVisitor newMethod = getClassVisitor().visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, friendlyName, METHOD_SIGNATURE, null, null);
        pushMethodVisitor(newMethod);
        
        newMethod.visitCode();
        
        // logic to start off the root node's code with local var slots and all
        newMethod.visitLdcInsn(new Integer(localVarCount));
        newMethod.visitTypeInsn(Opcodes.ANEWARRAY, p(IRubyObject.class));
        
        // store the local vars in a local variable
        newMethod.visitVarInsn(Opcodes.ASTORE, LOCAL_VARS_INDEX);
        
        // set up a local IRuby variable
        newMethod.visitVarInsn(Opcodes.ALOAD, THREADCONTEXT_INDEX);
        invokeThreadContext("getRuntime", sig(IRuby.class));
        newMethod.visitVarInsn(Opcodes.ASTORE, RUNTIME_INDEX);
        
        // visit a label to start scoping for local vars in this method
        Label start = new Label();
        newMethod.visitLabel(start);
        pushScopeStart(start);
        
        // push down the argument count of this method
        pushArity(arity);
        
        return newMethod;
    }
    
    public void endMethod(Object token) {
        assert token instanceof MethodVisitor;
        
        MethodVisitor mv = (MethodVisitor)token;
        // return last value from execution
        mv.visitInsn(Opcodes.ARETURN);
        
        // end of variable scope
        Label end = new Label();
        mv.visitLabel(end);
        
        // local variable for lvars array
        mv.visitLocalVariable("lvars", ci(IRubyObject[].class), null, popScopeStart(), end, LOCAL_VARS_INDEX);
        
        mv.visitMaxs(1, 1); // automatically calculated by ASM
        mv.visitEnd();
        
        popMethodVisitor();
        popArity();
    }
    
    public void lineNumber(ISourcePosition position) {
        if (lastLine == (lastLine = position.getEndLine())) return; // did not change lines for this node, don't bother relabeling
        
        Label l = new Label();
        MethodVisitor mv = getMethodVisitor();
        mv.visitLabel(l);
        mv.visitLineNumber(position.getEndLine(), l);
    }
    
    public void invokeDynamic(String name, boolean hasReceiver, boolean hasArgs) {
        MethodVisitor mv = getMethodVisitor();
        String callType = null;
        String callSig = sig(IRubyObject.class, params(ThreadContext.class, String.class, IRubyObject[].class, CallType.class));
        String callSigIndexed = sig(IRubyObject.class, params(ThreadContext.class, Byte.TYPE, String.class, IRubyObject[].class, CallType.class));
        
        byte index = MethodIndex.getIndex(name);
        
        if (hasArgs) {
            if (hasReceiver) {
                // Call with args
                // receiver already present

                loadThreadContext();
                // put under args
                mv.visitInsn(Opcodes.SWAP);
                
                callType = "NORMAL";
            } else {
                // FCall
                // no receiver present, use self
                loadSelf();
                // put self under args
                mv.visitInsn(Opcodes.SWAP);
                
                loadThreadContext();
                // put under args
                mv.visitInsn(Opcodes.SWAP);
                
                callType = "FUNCTIONAL";
            }

            if (index != 0) {
                // load method index
                mv.visitLdcInsn(new Byte(index));
                // put under args
                mv.visitInsn(Opcodes.SWAP);
            }

            mv.visitLdcInsn(name);
            // put under args
            mv.visitInsn(Opcodes.SWAP);
        } else {
            if (hasReceiver) {
                // Call with no args
                // receiver already present
                
                loadThreadContext();
                
                callType = "FUNCTIONAL";
            } else {
                // VCall
                // no receiver present, use self
                loadSelf();
                
                loadThreadContext();
                
                callType = "VARIABLE";
            }

            
            if (index != 0) {
                // load method index
                mv.visitLdcInsn(new Byte(index));
            }

            mv.visitLdcInsn(name);
                
            // empty args list
            mv.visitFieldInsn(Opcodes.GETSTATIC, p(IRubyObject.class), "NULL_ARRAY", ci(IRubyObject[].class));
        }

        mv.visitFieldInsn(Opcodes.GETSTATIC, p(CallType.class), callType, ci(CallType.class));

        if (index != 0) {
            invokeIRubyObject("callMethod", callSigIndexed);
        } else {
            invokeIRubyObject("callMethod", callSig);
        }
    }

    private void invokeIRubyObject(String methodName, String signature) {
        getMethodVisitor().visitMethodInsn(Opcodes.INVOKEINTERFACE, IRUBYOBJECT, methodName, signature);
    }
    
    public void loadThreadContext() {
        getMethodVisitor().visitVarInsn(Opcodes.ALOAD, THREADCONTEXT_INDEX);
    }
    
    public void loadSelf() {
        getMethodVisitor().visitVarInsn(Opcodes.ALOAD, SELF_INDEX);
    }
    
    public void loadRuntime() {
        getMethodVisitor().visitVarInsn(Opcodes.ALOAD, RUNTIME_INDEX);
    }
    
    public void loadNil() {
        loadRuntime();
        invokeIRuby("getNil", sig(IRubyObject.class));
    }
    
    public void consumeCurrentValue() {
        getMethodVisitor().visitInsn(Opcodes.POP);
    }
    
    public void retrieveSelf() {
        loadSelf();
    }
    
    public void assignLocalVariable(int index) {
        MethodVisitor mv = getMethodVisitor();
        mv.visitInsn(Opcodes.DUP);
        if ((index - 2) < Math.abs(getArity())) {
            // load from the incoming params
            // index is 2-based, and our zero is runtime
            
            // load args array
            mv.visitVarInsn(Opcodes.ALOAD, ARGS_INDEX);
            index = index - 2;
        } else {
            mv.visitVarInsn(Opcodes.ALOAD, LOCAL_VARS_INDEX);
        }
        
        mv.visitInsn(Opcodes.SWAP);
        mv.visitLdcInsn(new Integer(index));
        mv.visitInsn(Opcodes.SWAP);
        mv.visitInsn(Opcodes.AASTORE);
    }
    
    public void retrieveLocalVariable(int index) {
        MethodVisitor mv = getMethodVisitor();
        
    
        // check if it's an argument
        if ((index - 2) < Math.abs(getArity())) {
            // load from the incoming params
            // index is 2-based, and our zero is runtime
            
            // load args array
            mv.visitVarInsn(Opcodes.ALOAD, ARGS_INDEX);
            mv.visitLdcInsn(new Integer(index - 2));
            mv.visitInsn(Opcodes.AALOAD);
        } else {
            mv.visitVarInsn(Opcodes.ALOAD, LOCAL_VARS_INDEX);
            mv.visitLdcInsn(new Integer(index));
            mv.visitInsn(Opcodes.AALOAD);
        }
    }
    
    public void retrieveConstant(String name) {
        MethodVisitor mv = getMethodVisitor();
    
        // FIXME this doesn't work right yet since TC.getConstant depends on TC state
        loadThreadContext();
        mv.visitLdcInsn(name);
        invokeThreadContext("getConstant", sig(IRubyObject.class, params(String.class)));
    }
    
    public void createNewFixnum(long value) {
        MethodVisitor mv = getMethodVisitor();
        
        loadRuntime();
        mv.visitLdcInsn(new Long(value));
        
        invokeIRuby("newFixnum", sig(RubyFixnum.class, params(Long.TYPE)));
    }
    
    public void createNewString(String value) {
        MethodVisitor mv = getMethodVisitor();
        
        loadRuntime();
        mv.visitLdcInsn(value);
        
        invokeIRuby("newString", sig(RubyString.class, params(String.class)));
    }
    
    public void createNewArray() {
        MethodVisitor mv = getMethodVisitor();
        
        loadRuntime();
        // put under object array already present
        mv.visitInsn(Opcodes.SWAP);
        
        invokeIRuby("newArray", sig(RubyArray.class, params(IRubyObject[].class)));
    }
    
    public void createObjectArray(Object[] sourceArray, ArrayCallback callback) {
        buildObjectArray(IRUBYOBJECT, sourceArray, callback);
    }
    
    private void buildObjectArray(String type, Object[] sourceArray, ArrayCallback callback) {
        MethodVisitor mv = getMethodVisitor();
        
        mv.visitLdcInsn(new Integer(sourceArray.length));
        mv.visitTypeInsn(Opcodes.ANEWARRAY, type);
        
        for (int i = 0; i < sourceArray.length; i++) {
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn(new Integer(i));
            
            callback.nextValue(this, sourceArray, i);
            
            mv.visitInsn(Opcodes.AASTORE);
        }
    }
    /**
     * Invoke IRubyObject.isTrue
     */
    private void isTrue() {
        invokeIRubyObject("isTrue", sig(Boolean.TYPE));
    }
    
    public void performBooleanBranch(BranchCallback trueBranch, BranchCallback falseBranch) {
        Label afterJmp = new Label();
        Label falseJmp = new Label();
        
        MethodVisitor mv = getMethodVisitor();
        
        // call isTrue on the result
        isTrue();
        
        mv.visitJumpInsn(Opcodes.IFEQ, falseJmp); // EQ == 0 (i.e. false)
        trueBranch.branch(this);
        mv.visitJumpInsn(Opcodes.GOTO, afterJmp);

        // FIXME: optimize for cases where we have no false branch
        mv.visitLabel(falseJmp);
        falseBranch.branch(this);

        mv.visitLabel(afterJmp);
    }
    
    public void performLogicalAnd(BranchCallback longBranch) {
        Label afterJmp = new Label();
        Label falseJmp = new Label();
        
        MethodVisitor mv = getMethodVisitor();
        
        // dup it since we need to return appropriately if it's false
        mv.visitInsn(Opcodes.DUP);
        
        // call isTrue on the result
        isTrue();
        
        mv.visitJumpInsn(Opcodes.IFEQ, falseJmp); // EQ == 0 (i.e. false)
        // pop the extra result and replace with the send part of the AND
        mv.visitInsn(Opcodes.POP);
        longBranch.branch(this);
        mv.visitLabel(falseJmp);
    }
    
    public void performLogicalOr(BranchCallback longBranch) {
        Label afterJmp = new Label();
        Label falseJmp = new Label();
        
        MethodVisitor mv = getMethodVisitor();
        
        // dup it since we need to return appropriately if it's false
        mv.visitInsn(Opcodes.DUP);
        
        // call isTrue on the result
        isTrue();
        
        mv.visitJumpInsn(Opcodes.IFNE, falseJmp); // EQ == 0 (i.e. false)
        // pop the extra result and replace with the send part of the AND
        mv.visitInsn(Opcodes.POP);
        longBranch.branch(this);
        mv.visitLabel(falseJmp);
    }
    
    public void performBooleanLoop(BranchCallback condition, BranchCallback body, boolean checkFirst) {
        // FIXME: handle next/continue, break, etc
        MethodVisitor mv = getMethodVisitor();
        
        Label endJmp = new Label();
        if (checkFirst) {
            // calculate condition
            condition.branch(this);
            // call isTrue on the result
            isTrue();
        
            mv.visitJumpInsn(Opcodes.IFEQ, endJmp); // EQ == 0 (i.e. false)
        }

        Label topJmp = new Label();

        mv.visitLabel(topJmp);
            
        body.branch(this);
        
        // clear result after each loop
        mv.visitInsn(Opcodes.POP);

        // calculate condition
        condition.branch(this);
        // call isTrue on the result
        isTrue();

        mv.visitJumpInsn(Opcodes.IFNE, topJmp); // NE == nonzero (i.e. true)
        
        if (checkFirst) {
            mv.visitLabel(endJmp);
        }
        
        loadNil();
    }
    
    public void createNewClosure(StaticScope scope, ClosureCallback body) {
        // FIXME: This isn't quite done yet; waiting to have full support for passing closures so we can test it
        ClassVisitor closureVisitor = new ClassWriter(true);
        FieldVisitor fv;
        MethodVisitor method;

        String closureMethodName = "closure" + innerIndex;
        
        ////////////////////////////
        // closure implementation
        method = closureVisitor.visitMethod(Opcodes.ACC_PUBLIC, closureMethodName, CLOSURE_SIGNATURE, null, null);
        pushMethodVisitor(method);
        
        method.visitCode();
        
        // logic to start off the closure with dvar slots
        method.visitLdcInsn(new Integer(scope.getNumberOfVariables()));
        method.visitTypeInsn(Opcodes.ANEWARRAY, p(IRubyObject.class));
        
        // store the dvars in a local variable
        method.visitVarInsn(Opcodes.ASTORE, LOCAL_VARS_INDEX);
        
        // set up local variables for containing scopes
        // containing scope's local vars are stored in LOCAL_VARS_INDEX + <depth>
        // where <depth> is the number of scopes we are away from that.
        // Eventually this will want to handle multiple nested scopes
        //method.visitFieldInsn(Opcodes.GETFIELD, closureClassName, "val$variables", "[L" + IRUBYOBJECT +";");
        method.visitVarInsn(Opcodes.ASTORE, LOCAL_VARS_INDEX + 1);
        
        // set up a local IRuby variable
        method.visitVarInsn(Opcodes.ALOAD, THREADCONTEXT_INDEX);
        invokeThreadContext("getRuntime", sig(IRuby.class));
        method.visitVarInsn(Opcodes.ASTORE, RUNTIME_INDEX);
        
        // start of scoping for closure's vars
        Label start = new Label();
        method.visitLabel(start);
        
        // visit the body of the closure
        body.compile(this);
        
        method.visitInsn(Opcodes.ARETURN);
        
        // end of scoping for closure's vars
        Label end = new Label();
        method.visitLabel(end);
        method.visitLocalVariable("this", "Lorg/jruby/FooBar$1$Figlet;", null, start, end, 0);
        //method.visitLocalVariable("dvars" + closureClassShortName, "[L" + IRUBYOBJECT + ";", null, start, end, LOCAL_VARS_INDEX);
        // in the future this should be indexed by depth and have no distinction
        // between lvars and dvars
        //method.visitLocalVariable("lvars" + closureClassShortName, "[L" + IRUBYOBJECT + ";", null, start, end, LOCAL_VARS_INDEX + 1);
        method.visitMaxs(1, 1);
        method.visitEnd();
        
        popMethodVisitor();
    }

    private void invokeThreadContext(String methodName, String signature) {
        MethodVisitor mv = getMethodVisitor();
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, THREADCONTEXT, methodName, signature);
    }

    private void invokeIRuby(String methodName, String signature) {
        MethodVisitor mv = getMethodVisitor();
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, IRUBY, methodName, signature);
    }
    
    private void getRubyClass() {
        loadSelf();
        // FIXME: This doesn't seem *quite* right. If actually within a class...end, is self.getMetaClass the correct class? should be self, no?
        invokeIRubyObject("getMetaClass", sig(RubyClass.class));
    }
    
    private void getCRef() {
        loadThreadContext();
        // FIXME: This doesn't seem *quite* right. If actually within a class...end, is self.getMetaClass the correct class? should be self, no?
        invokeThreadContext("peekCRef", sig(SinglyLinkedList.class));
    }

    private void newTypeError(String error) {
        loadRuntime();
        getMethodVisitor().visitLdcInsn(error);
        invokeIRuby("newTypeError", sig(RaiseException.class, params(String.class)));
    }

    private void getCurrentVisibility() {
        loadThreadContext();
        invokeThreadContext("getCurrentVisibility", sig(Visibility.class));
    }
    
    private void println() {
        MethodVisitor mv = getMethodVisitor();
        
        mv.visitInsn(Opcodes.DUP);
        mv.visitFieldInsn(Opcodes.GETSTATIC, p(System.class), "out", ci(PrintStream.class));
        mv.visitInsn(Opcodes.SWAP);
        
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, p(PrintStream.class), "println", sig(Void.TYPE, params(Object.class)));
    }
    
    public void defineNewMethod(String name, int arity, int localVarCount, ClosureCallback body) {
        // TODO: build arg list based on number of args, optionals, etc
        ++methodIndex;
        String methodName = name + "__" + methodIndex;
        beginMethod(methodName, arity, localVarCount);
        
        MethodVisitor mv = getMethodVisitor();

        mv.visitCode();
        
        // arraycopy arguments into local vars array
        mv.visitVarInsn(Opcodes.ALOAD, ARGS_INDEX);
        mv.visitLdcInsn(new Integer(0));
        mv.visitVarInsn(Opcodes.ALOAD, LOCAL_VARS_INDEX);
        mv.visitLdcInsn(new Integer(0));
        mv.visitLdcInsn(new Integer(arity));
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, p(System.class), "arraycopy", sig(Void.TYPE, params(Object.class, Integer.TYPE, Object.class, Integer.TYPE, Integer.TYPE)));
        
        // callback to fill in method body
        body.compile(this);

        endMethod(mv);
        
        // return to previous method
        mv = getMethodVisitor();
        
        // method compiled, add to class
        getRubyClass();
        
        Label classAvailable = new Label();
        // if class is null, throw error
        mv.visitJumpInsn(Opcodes.IFNONNULL, classAvailable);
        newTypeError("No class to add method.");
        mv.visitInsn(Opcodes.ATHROW);
        
        mv.visitLabel(classAvailable);
        
        // only do Object#initialize check if necessary
        // this warns the user if they try to redefine initialize on Object, which would be bad
        if (name.equals("initialize")) {
            Label notObjectClass = new Label();
    
            // get class, compare to Object
            getRubyClass();
            loadRuntime();
            invokeIRuby("getObject", sig(RubyClass.class));
            
            // if class == Object
            mv.visitJumpInsn(Opcodes.IF_ACMPNE, notObjectClass);
            loadRuntime();
            
            // display warning about redefining Object#initialize
            invokeIRuby("getWarnings", sig(RubyWarnings.class));
            mv.visitLdcInsn("redefining Object#initialize may cause infinite loop");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, p(RubyWarnings.class), "warn", sig(Void.TYPE, params(String.class)));
        
            mv.visitLabel(notObjectClass);
        }
        
        // TODO: fix this section for initialize visibility
//        mv.visitLdcInsn(iVisited.getName());
//        mv.visitLdcInsn("initialize");
//        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z");
//        Label l341 = new Label();
//        mv.visitJumpInsn(Opcodes.IFNE, l341);
//        getCurrentVisibility();
//        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/jruby/runtime/Visibility", "isModuleFunction", "()Z");
//        Label l342 = new Label();
//        mv.visitJumpInsn(Opcodes.IFEQ, l342);
//        mv.visitLabel(l341);
//        mv.visitFieldInsn(Opcodes.GETSTATIC, "org/jruby/runtime/Visibility", "PRIVATE", "Lorg/jruby/runtime/Visibility;");
//        mv.visitVarInsn(ASTORE, 7);
//        mv.visitLabel(l342);
        
        // Create a new method object to call the stub, passing it a new instance of our
        // current multistub and the appropriate index to call
        
        // get the class we're binding it to (this is the receiver for the addMethod
        // call way down below...)
        getRubyClass();
        
        // method name for addMethod call
        mv.visitLdcInsn(name);
        
        { // this section sets up the parameters to the CompiledMethod constructor
            // get method factory
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, p(MethodFactory.class), "createFactory", sig(MethodFactory.class));
            getRubyClass();
            mv.visitLdcInsn(classname.replace("/", "."));

            mv.visitMethodInsn(Opcodes.INVOKESTATIC, p(Class.class), "forName", sig(Class.class, params(String.class)));
            // this is the actual name of the compiled method, for calling in the generated Method object
            mv.visitLdcInsn(methodName);
            
            // load arity
            mv.visitLdcInsn(new Integer(arity));
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, p(Arity.class), "createArity", sig(Arity.class, params(Integer.TYPE)));
            
            getCurrentVisibility();
            getCRef();
            
            // create CompiledMethod object from Factory
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, p(MethodFactory.class), "getCompiledMethod", sig(DynamicMethod.class, params(RubyModule.class, Class.class, String.class, Arity.class, Visibility.class, SinglyLinkedList.class)));

            // TODO: handle args some way? maybe unnecessary with method compiled?
    //        mv.visitVarInsn(ALOAD, 4);
    //        mv.visitMethodInsn(INVOKEVIRTUAL, "org/jruby/ast/DefnNode", "getArgsNode", "()Lorg/jruby/ast/Node;");
    //        mv.visitTypeInsn(CHECKCAST, "org/jruby/ast/ArgsNode");
        }

        // add the method to the class, and we're set!
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, p(RubyModule.class), "addMethod", sig(Void.TYPE, params(String.class, DynamicMethod.class)));

        // FIXME: this part is for invoking method_added or singleton_method_added
//        Label l345 = new Label();
//        mv.visitLabel(l345);
//        mv.visitLineNumber(538, l345);
//        loadThreadContext();
//        mv.visitMethodInsn(INVOKEVIRTUAL, "org/jruby/runtime/ThreadContext", "getCurrentVisibility", "()Lorg/jruby/runtime/Visibility;");
//        mv.visitMethodInsn(INVOKEVIRTUAL, "org/jruby/runtime/Visibility", "isModuleFunction", "()Z");
//        Label l346 = new Label();
//        mv.visitJumpInsn(IFEQ, l346);
//        Label l347 = new Label();
//        mv.visitLabel(l347);
//        mv.visitLineNumber(539, l347);
//        mv.visitVarInsn(ALOAD, 5);
//        mv.visitMethodInsn(INVOKEVIRTUAL, "org/jruby/RubyModule", "getSingletonClass", "()Lorg/jruby/MetaClass;");
//        mv.visitLdcInsn(iVisited.getName());
//        mv.visitTypeInsn(NEW, "org/jruby/internal/runtime/methods/WrapperCallable");
//        mv.visitInsn(DUP);
//        mv.visitVarInsn(ALOAD, 5);
//        mv.visitMethodInsn(INVOKEVIRTUAL, "org/jruby/RubyModule", "getSingletonClass", "()Lorg/jruby/MetaClass;");
//        mv.visitVarInsn(ALOAD, 8);
//        mv.visitFieldInsn(GETSTATIC, "org/jruby/runtime/Visibility", "PUBLIC", "Lorg/jruby/runtime/Visibility;");
//        mv.visitMethodInsn(INVOKESPECIAL, "org/jruby/internal/runtime/methods/WrapperCallable", "<init>", "(Lorg/jruby/RubyModule;Lorg/jruby/runtime/ICallable;Lorg/jruby/runtime/Visibility;)V");
//        mv.visitMethodInsn(INVOKEVIRTUAL, "org/jruby/MetaClass", "addMethod", "(Ljava/lang/String;Lorg/jruby/runtime/ICallable;)V");
//        Label l348 = new Label();
//        mv.visitLabel(l348);
//        mv.visitLineNumber(543, l348);
//        mv.visitVarInsn(ALOAD, 5);
//        mv.visitLdcInsn("singleton_method_added");
//        loadRuntime();
//        mv.visitLdcInsn(iVisited.getName());
//        invokeIRuby("newSymbol", "(Ljava/lang/String;)Lorg/jruby/RubySymbol;");
//        mv.visitMethodInsn(INVOKEVIRTUAL, "org/jruby/RubyModule", "callMethod", "(Lorg/jruby/runtime/ThreadContext;Ljava/lang/String;Lorg/jruby/runtime/builtin/IRubyObject;)Lorg/jruby/runtime/builtin/IRubyObject;");
//        mv.visitInsn(POP);
//        mv.visitLabel(l346);
//        mv.visitLineNumber(547, l346);
//        mv.visitVarInsn(ALOAD, 5);
//        mv.visitMethodInsn(INVOKEVIRTUAL, "org/jruby/RubyModule", "isSingleton", "()Z");
//        Label l349 = new Label();
//        mv.visitJumpInsn(IFEQ, l349);
//        Label l350 = new Label();
//        mv.visitLabel(l350);
//        mv.visitLineNumber(548, l350);
//        mv.visitVarInsn(ALOAD, 5);
//        mv.visitTypeInsn(CHECKCAST, "org/jruby/MetaClass");
//        mv.visitMethodInsn(INVOKEVIRTUAL, "org/jruby/MetaClass", "getAttachedObject", "()Lorg/jruby/runtime/builtin/IRubyObject;");
//        mv.visitLdcInsn("singleton_method_added");
//        loadRuntime();
//        mv.visitVarInsn(ALOAD, 4);
//        mv.visitMethodInsn(INVOKEVIRTUAL, "org/jruby/ast/DefnNode", "getName", "()Ljava/lang/String;");
//        invokeIRuby("newSymbol", "(Ljava/lang/String;)Lorg/jruby/RubySymbol;");
//        mv.visitMethodInsn(INVOKEINTERFACE, "org/jruby/runtime/builtin/IRubyObject", "callMethod", "(Lorg/jruby/runtime/ThreadContext;Ljava/lang/String;Lorg/jruby/runtime/builtin/IRubyObject;)Lorg/jruby/runtime/builtin/IRubyObject;");
//        mv.visitInsn(POP);
//        Label l351 = new Label();
//        mv.visitJumpInsn(GOTO, l351);
//        mv.visitLabel(l349);
//        mv.visitLineNumber(551, l349);
//        mv.visitVarInsn(ALOAD, 5);
//        mv.visitLdcInsn("method_added");
//        loadRuntime();
//        mv.visitLdcInsn(iVisited.getName());
//        invokeIRuby("newSymbol", "(Ljava/lang/String;)Lorg/jruby/RubySymbol;");
//        mv.visitMethodInsn(INVOKEVIRTUAL, "org/jruby/RubyModule", "callMethod", "(Lorg/jruby/runtime/ThreadContext;Ljava/lang/String;Lorg/jruby/runtime/builtin/IRubyObject;)Lorg/jruby/runtime/builtin/IRubyObject;");
//        mv.visitInsn(POP);
//        mv.visitLabel(l351);
//        mv.visitLineNumber(554, l351);
        
        // don't leave the stack hanging!
        loadRuntime();
        invokeIRuby("getNil", sig(IRubyObject.class));
    }
    
    public void loadFalse() {
        loadRuntime();
        invokeIRuby("getFalse", sig(RubyBoolean.class));
    }
    
    public void loadTrue() {
        loadRuntime();
        invokeIRuby("getTrue", sig(RubyBoolean.class));
    }
    
    public void retrieveInstanceVariable(String name) {
        loadSelf();
        
        MethodVisitor mv = getMethodVisitor();
        
        mv.visitLdcInsn(name);
        invokeIRubyObject("getInstanceVariable", sig(IRubyObject.class, params(String.class)));
        
        // check if it's null; if so, load nil
        mv.visitInsn(Opcodes.DUP);
        Label notNull = new Label();
        mv.visitJumpInsn(Opcodes.IFNONNULL, notNull);
        
        // pop the dup'ed null
        mv.visitInsn(Opcodes.POP);
        // replace it with nil
        loadNil();
        
        mv.visitLabel(notNull);
    }
    
    public void assignInstanceVariable(String name) {
        MethodVisitor mv = getMethodVisitor();
        
        loadSelf();
        mv.visitInsn(Opcodes.SWAP);
        
        mv.visitLdcInsn(name);
        mv.visitInsn(Opcodes.SWAP);
        
        invokeIRubyObject("setInstanceVariable", sig(IRubyObject.class, params(String.class, IRubyObject.class)));
    }
    
    public void retrieveGlobalVariable(String name) {
        loadRuntime();
        
        MethodVisitor mv = getMethodVisitor();
        
        invokeIRuby("getGlobalVariables", sig(GlobalVariables.class));
        mv.visitLdcInsn(name);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, p(GlobalVariables.class), "get", sig(IRubyObject.class, params(String.class)));
    }
    
    public void assignGlobalVariable(String name) {
        loadRuntime();
        
        MethodVisitor mv = getMethodVisitor();
        
        invokeIRuby("getGlobalVariables", sig(GlobalVariables.class));
        mv.visitInsn(Opcodes.SWAP);
        mv.visitLdcInsn(name);
        mv.visitInsn(Opcodes.SWAP);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, p(GlobalVariables.class), "set", sig(IRubyObject.class, params(String.class, IRubyObject.class)));
    }
    
    public void negateCurrentValue() {
        MethodVisitor mv = getMethodVisitor();
        
        isTrue();
        Label isTrue = new Label();
        Label end = new Label();
        mv.visitJumpInsn(Opcodes.IFNE, isTrue);
        loadTrue();
        mv.visitJumpInsn(Opcodes.GOTO, end);
        mv.visitLabel(isTrue);
        loadFalse();
        mv.visitLabel(end);
    }
}
