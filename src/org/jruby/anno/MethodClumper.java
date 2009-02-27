package org.jruby.anno;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jruby.CompatVersion;

public class MethodClumper {

    Map<String, List<JavaMethodDescriptor>> annotatedMethods = new HashMap<String, List<JavaMethodDescriptor>>();
    Map<String, List<JavaMethodDescriptor>> staticAnnotatedMethods = new HashMap<String, List<JavaMethodDescriptor>>();
    Map<String, List<JavaMethodDescriptor>> annotatedMethods1_8 = new HashMap<String, List<JavaMethodDescriptor>>();
    Map<String, List<JavaMethodDescriptor>> staticAnnotatedMethods1_8 = new HashMap<String, List<JavaMethodDescriptor>>();
    Map<String, List<JavaMethodDescriptor>> annotatedMethods1_9 = new HashMap<String, List<JavaMethodDescriptor>>();
    Map<String, List<JavaMethodDescriptor>> staticAnnotatedMethods1_9 = new HashMap<String, List<JavaMethodDescriptor>>();

    public void clump(Class cls) {
        Method[] declaredMethods = cls.getDeclaredMethods();
        for (Method method : declaredMethods) {
            JRubyMethod anno = method.getAnnotation(JRubyMethod.class);
            if (anno == null) {
                continue;
            }
            JavaMethodDescriptor desc = new JavaMethodDescriptor(method);
            String name = anno.name().length == 0 ? method.getName() : anno.name()[0];
            List<JavaMethodDescriptor> methodDescs;
            Map<String, List<JavaMethodDescriptor>> methodsHash = null;
            if (desc.isStatic) {
                if (anno.compat() == CompatVersion.RUBY1_8) {
                    methodsHash = staticAnnotatedMethods1_8;
                } else if (anno.compat() == CompatVersion.RUBY1_9) {
                    methodsHash = staticAnnotatedMethods1_9;
                } else {
                    methodsHash = staticAnnotatedMethods;
                }
            } else {
                if (anno.compat() == CompatVersion.RUBY1_8) {
                    methodsHash = annotatedMethods1_8;
                } else if (anno.compat() == CompatVersion.RUBY1_9) {
                    methodsHash = annotatedMethods1_9;
                } else {
                    methodsHash = annotatedMethods;
                }
            }
            methodDescs = methodsHash.get(name);
            if (methodDescs == null) {
                methodDescs = new ArrayList<JavaMethodDescriptor>();
                methodsHash.put(name, methodDescs);
            }
            methodDescs.add(desc);
        }
    }

    public Map<String, List<JavaMethodDescriptor>> getAnnotatedMethods() {
        return annotatedMethods;
    }

    public Map<String, List<JavaMethodDescriptor>> getAnnotatedMethods1_8() {
        return annotatedMethods1_8;
    }

    public Map<String, List<JavaMethodDescriptor>> getAnnotatedMethods1_9() {
        return annotatedMethods1_9;
    }

    public Map<String, List<JavaMethodDescriptor>> getStaticAnnotatedMethods() {
        return staticAnnotatedMethods;
    }

    public Map<String, List<JavaMethodDescriptor>> getStaticAnnotatedMethods1_8() {
        return staticAnnotatedMethods1_8;
    }

    public Map<String, List<JavaMethodDescriptor>> getStaticAnnotatedMethods1_9() {
        return staticAnnotatedMethods1_9;
    }
}
