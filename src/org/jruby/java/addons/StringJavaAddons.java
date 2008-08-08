package org.jruby.java.addons;

import org.jruby.javasupport.*;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

public class StringJavaAddons {
    @JRubyMethod
    public static IRubyObject to_java_bytes(ThreadContext context, IRubyObject self) {
        return JavaArrayUtilities.ruby_string_to_bytes(self, self);
    }
    
    @JRubyMethod(meta = true)
    public static IRubyObject from_java_bytes(ThreadContext context, IRubyObject self, IRubyObject bytes) {
        return JavaArrayUtilities.bytes_to_ruby_string(bytes, bytes);
    }
}
