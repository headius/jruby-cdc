package org.jruby.compiler.util;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.jruby.compiler.impl.SkinnyMethodAdapter;
import org.jruby.util.JRubyClassLoader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.ASMifierClassVisitor;
import static org.jruby.util.CodegenUtils.*;
import static org.objectweb.asm.Opcodes.*;

public class HandleFactory {
    private static final boolean DEBUG = false;
    
    public static class Handle {
        private Error fail() { return new AbstractMethodError("invalid call signature for target method"); }
        public Object invoke(Object receiver) { throw fail(); }
        public Object invoke(Object receiver, Object arg0) { throw fail(); }
        public Object invoke(Object receiver, Object arg0, Object arg1) { throw fail(); }
        public Object invoke(Object receiver, Object arg0, Object arg1, Object arg2) { throw fail(); }
//        public Object invoke(Object receiver, Object arg0, Object arg1, Object arg2, Object arg3) { throw fail(); }
//        public Object invoke(Object receiver, Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) { throw fail(); }
        public Object invoke(Object receiver, Object... args) { throw fail(); }
    }
    
    public static Handle createHandle(JRubyClassLoader classLoader, Method method, boolean debug) {
        ClassVisitor cv;
        if (debug) {
            cv = new ASMifierClassVisitor(new PrintWriter(System.out));
        } else {
            cv = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        }

        Class returnType = method.getReturnType();
        Class[] paramTypes = method.getParameterTypes();
        
        String name = "H" + (method.getName() + pretty(returnType, paramTypes)).hashCode();
        
        try {
            Class existing = classLoader.loadClass(name);
            return (Handle)existing.newInstance();
        } catch (Exception e) {
        }
        cv.visit(ACC_PUBLIC | ACC_FINAL | ACC_SUPER, V1_5, name, null, p(Handle.class), null);
        
        SkinnyMethodAdapter m;
        String signature;
        switch (paramTypes.length) {
        case 0:
            signature = sig(Object.class, Object.class);
            break;
        case 1:
            signature = sig(Object.class, Object.class, Object.class);
            break;
        case 2:
            signature = sig(Object.class, Object.class, Object.class, Object.class);
            break;
        case 3:
            signature = sig(Object.class, Object.class, Object.class, Object.class, Object.class);
            break;
//        case 4:
//            signature = sig(Object.class, Object.class, Object.class, Object.class, Object.class);
//            break;
//        case 5:
//            signature = sig(Object.class, Object.class, Object.class, Object.class, Object.class, Object.class);
//            break;
        default:
            signature = sig(Object.class, Object.class, Object[].class);
            break;
        }
        m = new SkinnyMethodAdapter(cv.visitMethod(ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC, "invoke", signature, null, null));
        
        m.start();
        
        // load receiver
        if (!Modifier.isStatic(method.getModifiers())) {
            m.aload(1); // receiver
            if (method.getDeclaringClass() != Object.class) {
                m.checkcast(p(method.getDeclaringClass()));
            }
        }
        
        // load arguments
        switch (paramTypes.length) {
        case 0:
        case 1:
        case 2:
        case 3:
//        case 4:
//        case 5:
            for (int i = 0; i < paramTypes.length; i++) {
                loadUnboxedArgument(m, i + 2, paramTypes[i]);
            }
            break;
        default:
            for (int i = 0; i < paramTypes.length; i++) {
                m.aload(2); // Object[] args
                m.pushInt(i);
                m.aaload();
                Class paramClass = paramTypes[i];
                if (paramClass.isPrimitive()) {
                    Class boxType = getBoxType(paramClass);
                    m.checkcast(p(boxType));
                    m.invokevirtual(p(boxType), paramClass.toString() + "Value", sig(paramClass));
                } else if (paramClass != Object.class) {
                    m.checkcast(p(paramClass));
                }
            }
            break;
        }
        
        if (Modifier.isStatic(method.getModifiers())) {
            m.invokestatic(p(method.getDeclaringClass()), method.getName(), sig(returnType, paramTypes));
        } else if (Modifier.isInterface(method.getDeclaringClass().getModifiers())) {
            m.invokeinterface(p(method.getDeclaringClass()), method.getName(), sig(returnType, paramTypes));
        } else {
            m.invokevirtual(p(method.getDeclaringClass()), method.getName(), sig(returnType, paramTypes));
        }
        
        if (returnType == void.class) {
            m.aload(1);
        } else if (returnType.isPrimitive()) {
            Class boxType = getBoxType(returnType);
            m.invokestatic(p(boxType), "valueOf", sig(boxType, returnType));
        }
        m.areturn();
        m.end();
        
        // constructor
        m = new SkinnyMethodAdapter(cv.visitMethod(ACC_PUBLIC, "<init>", sig(void.class), null, null));
        m.start();
        m.aload(0);
        m.invokespecial(p(Handle.class), "<init>", sig(void.class));
        m.voidreturn();
        m.end();
        
        cv.visitEnd();
        
        if (debug) {
            ((ASMifierClassVisitor)cv).print(new PrintWriter(System.out));
            return createHandle(classLoader, method, false);
        } else {
            byte[] bytes = ((ClassWriter)cv).toByteArray();
        
            Class handleClass = (classLoader != null ? classLoader : new JRubyClassLoader(JRubyClassLoader.class.getClassLoader())).defineClass(name, bytes);

            try {
                return (Handle)handleClass.newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    public static void loadUnboxedArgument(SkinnyMethodAdapter m, int index, Class type) {
        m.aload(index); // Object arg0
        unboxAndCast(m, type);
    }
    
    public static void unboxAndCast(SkinnyMethodAdapter m, Class paramClass) {
        if (paramClass.isPrimitive()) {
            Class boxType = getBoxType(paramClass);
            m.checkcast(p(boxType));
            m.invokevirtual(p(boxType), paramClass.toString() + "Value", sig(paramClass));
        } else if (paramClass != Object.class) {
            m.checkcast(p(paramClass));
        }
    }
    
    public static Handle createHandle(JRubyClassLoader classLoader, Method method) {
        return createHandle(classLoader, method, DEBUG);
    }
    
    protected static Class getBoxType(Class type) {
        if (type == int.class) {
            return Integer.class;
        } else if (type == byte.class) {
            return Byte.class;
        } else if (type == short.class) {
            return Short.class;
        } else if (type == char.class) {
            return Character.class;
        } else if (type == long.class) {
            return Long.class;
        } else if (type == float.class) {
            return Float.class;
        } else if (type == double.class) {
            return Double.class;
        } else if (type == boolean.class) {
            return Boolean.class;
        } else {
            throw new RuntimeException("Not a native type: " + type);
        }
    }
    
    public static void main(String[] args) {
        try {
            Method method = HandleFactory.class.getMethod("dummy", new Class[] {String.class});
            Handle handle = createHandle(null, method);
            
            Object result = null;
            String prop = "java.class.path";
            String[] callArgs = new String[] {prop};
            
            for (int i = 0; i < 10; i++) {
                long time;
                
                System.out.print("handle invocation: ");
                time = System.currentTimeMillis();
                for (int j = 0; j < 10000000; j++) {
                    result = handle.invoke(null, callArgs);
                }
                System.out.println(System.currentTimeMillis() - time);
                
                System.out.print("reflected invocation: ");
                time = System.currentTimeMillis();
                for (int j = 0; j < 10000000; j++) {
                    result = method.invoke(null, callArgs);
                }
                System.out.println(System.currentTimeMillis() - time);
                
                System.out.print("method invocation: ");
                time = System.currentTimeMillis();
                for (int j = 0; j < 10000000; j++) {
                    result = dummy(prop);
                }
                System.out.println(System.currentTimeMillis() - time);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public static String dummy(String str) {
        if (str.length() == 0) return null;
        return str;
    }
    
    public static int dummy2() {
        return 1;
    }
}
