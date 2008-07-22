package org.jruby.javasupport.methods;

import org.jruby.javasupport.*;
import java.lang.reflect.Field;
import org.jruby.Ruby;
import org.jruby.RubyModule;
import org.jruby.internal.runtime.methods.JavaMethod.JavaMethodZero;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;

public abstract class FieldMethodZero extends JavaMethodZero {

    Field field;
    String name;

    FieldMethodZero(String name, RubyModule host, Field field) {
        super(host, Visibility.PUBLIC);
        if (!Ruby.isSecurityRestricted()) {
            field.setAccessible(true);
        }
        this.field = field;
        this.name = name;
    }

    protected Object safeConvert(IRubyObject value) {
        Object newValue = JavaUtil.convertRubyToJava(value);
        if (newValue == null) {
            if (field.getType().isPrimitive()) {
                throw value.getRuntime().newTypeError("wrong type for " + field.getType().getName() + ": null");
            }
        } else if (!field.getType().isInstance(newValue)) {
            throw value.getRuntime().newTypeError("wrong type for " + field.getType().getName() + ": " + newValue.getClass().getName());
        }
        return newValue;
    }
}
