/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jruby.anno;

import org.jruby.RubyModule;
import org.jruby.internal.runtime.methods.CallConfiguration;
import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.internal.runtime.methods.JavaMethod;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Visibility;

/**
 *
 * @author headius
 */
public abstract class TypePopulator {
    public void populateMethod(JavaMethod javaMethod, int arity, String simpleName, boolean isStatic, CallConfiguration callConfig) {
        javaMethod.setArity(Arity.createArity(arity));
        javaMethod.setJavaName(simpleName);
        javaMethod.setSingleton(isStatic);
        javaMethod.setCallConfig(callConfig);
    }
    
    public DynamicMethod populateModuleMethod(RubyModule cls, JavaMethod javaMethod) {
        DynamicMethod moduleMethod = javaMethod.dup();
        moduleMethod.setImplementationClass(cls.getSingletonClass());
        moduleMethod.setVisibility(Visibility.PUBLIC);
        return moduleMethod;
    }
    
    public abstract void populate(RubyModule clsmod, Class clazz);
}
