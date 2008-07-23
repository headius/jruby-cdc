package org.jruby.javasupport.methods;

import java.lang.reflect.Field;
import org.jruby.Ruby;
import org.jruby.RubyModule;
import org.jruby.internal.runtime.methods.JavaMethod.JavaMethodOne;
import org.jruby.runtime.Visibility;

public abstract class FieldMethodOne extends JavaMethodOne {
    Field field;
    String name;

    FieldMethodOne(String name, RubyModule host, Field field) {
        super(host, Visibility.PUBLIC);
        if (!Ruby.isSecurityRestricted()) {
            field.setAccessible(true);
        }
        this.field = field;
        this.name = name;
    }
}
