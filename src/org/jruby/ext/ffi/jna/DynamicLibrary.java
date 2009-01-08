
package org.jruby.ext.ffi.jna;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.RubyObject;
import org.jruby.RubyString;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyConstant;
import org.jruby.anno.JRubyMethod;
import org.jruby.ext.ffi.FFIProvider;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

@JRubyClass(name = "FFI::DynamicLibrary", parent = "Object")
public class DynamicLibrary extends RubyObject {
    
    @JRubyConstant public static final int LAZY   = 0x00001;
    @JRubyConstant public static final int NOW    = 0x00002;
    @JRubyConstant public static final int LOCAL  = 0x00004;
    @JRubyConstant public static final int GLOBAL = 0x00008;
    
    private final com.sun.jna.NativeLibrary library;
    private final String name;
    public static RubyClass createDynamicLibraryClass(Ruby runtime, RubyModule module) {
        RubyClass result = module.defineClassUnder("DynamicLibrary",
                runtime.getObject(),
                ObjectAllocator.NOT_ALLOCATABLE_ALLOCATOR);

        RubyClass symClass = result.defineClassUnder("Symbol",
                module.fastGetClass(JNABasePointer.JNA_POINTER_NAME), ObjectAllocator.NOT_ALLOCATABLE_ALLOCATOR);
        symClass.defineAnnotatedMethods(Symbol.class);
        result.defineAnnotatedMethods(DynamicLibrary.class);
        result.defineAnnotatedConstants(DynamicLibrary.class);

        return result;
    }
    public DynamicLibrary(Ruby runtime, RubyClass klass, String name, com.sun.jna.NativeLibrary library) {
        super(runtime, klass);
        this.name = name;
        this.library = library;
    }
    @JRubyMethod(name = {  "open" }, meta = true)
    public static final  IRubyObject open(ThreadContext context, IRubyObject recv, IRubyObject libraryName, IRubyObject libraryFlags) {
        final String libName = libraryName.toString();
        try {
            return new DynamicLibrary(context.getRuntime(), (RubyClass) recv, 
                    libName, com.sun.jna.NativeLibrary.getInstance(libName));
        } catch (UnsatisfiedLinkError ex) {
            throw context.getRuntime().newLoadError(ex.getMessage());
        }
    }
    @JRubyMethod(name = {  "find_symbol" })
    public IRubyObject findSymbol(ThreadContext context, IRubyObject symbolName) {
        final String sym = symbolName.toString();
        com.sun.jna.Pointer ptr = library.getGlobalVariableAddress(sym);
        return new Symbol(context.getRuntime(), this, sym, ptr);
    }
    static final class Symbol extends JNABasePointer {
        private final DynamicLibrary library;
        private final String name;
        public Symbol(Ruby runtime, DynamicLibrary library, String name, com.sun.jna.Pointer address) {
            super(runtime, FFIProvider.getModule(runtime).fastGetClass("DynamicLibrary").fastGetClass("Symbol"),
                    JNAMemoryIO.wrap(address), 0, Long.MAX_VALUE);
            this.library = library;
            this.name = name;
        }
        @Override
        @JRubyMethod(name = "inspect")
        public IRubyObject inspect(ThreadContext context) {
            return RubyString.newString(context.getRuntime(),
                    String.format("#<Library Symbol library=%s symbol=%s address=%#x>", 
                    library.name, name, ptr2long(getAddress())));
        }
    }
}
