package org.jruby;

import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.builtin.IRubyObject;

public class RubyJava {
    public static RubyModule createJavaModule(Ruby ruby) {
        RubyModule javaModule = ruby.defineModule("Java");

        javaModule.defineModuleFunction("import", CallbackFactory.getSingletonMethod(RubyJava.class, "rbImport", RubyString.class));
        javaModule.defineModuleFunction("name", CallbackFactory.getSingletonMethod(RubyJava.class, "name", RubyString.class, RubyString.class));
        javaModule.defineModuleFunction("define_exception_handler", CallbackFactory.getOptSingletonMethod(RubyJava.class, "define_exception_handler"));

        return javaModule;
    }

	// Java methods
    public static IRubyObject rbImport(IRubyObject recv, RubyString packageName) {
		recv.getRuntime().getJavaSupport().addImportPackage(packageName.getValue());
        return recv;
    }

    public static IRubyObject name(IRubyObject recv, RubyString javaName, RubyString rubyName) {
		recv.getRuntime().getJavaSupport().rename(rubyName.getValue(), javaName.getValue());
        return recv;
    }

    public static IRubyObject define_exception_handler(IRubyObject recv, IRubyObject[] args) {
        String name = args[0].toString();
        RubyProc handler = null;
        if (args.length > 1) {
            handler = (RubyProc)args[1];
        } else {
            handler = RubyProc.newProc(recv.getRuntime());
        }
        recv.getRuntime().getJavaSupport().defineExceptionHandler(name, handler);

        return recv;
    }
}