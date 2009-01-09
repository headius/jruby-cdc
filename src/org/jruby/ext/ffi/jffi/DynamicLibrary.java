
package org.jruby.ext.ffi.jffi;

import com.kenai.jffi.Address;
import com.kenai.jffi.Library;
import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.RubyNumeric;
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
    
    private final Library library;
    private final String name;
    public static RubyClass createDynamicLibraryClass(Ruby runtime, RubyModule module) {
        RubyClass result = module.defineClassUnder("DynamicLibrary",
                runtime.getObject(),
                ObjectAllocator.NOT_ALLOCATABLE_ALLOCATOR);

        RubyClass symClass = result.defineClassUnder("Symbol",
                module.fastGetClass("BasePointer"), ObjectAllocator.NOT_ALLOCATABLE_ALLOCATOR);
        symClass.defineAnnotatedMethods(Symbol.class);
        result.defineAnnotatedMethods(DynamicLibrary.class);
        result.defineAnnotatedConstants(DynamicLibrary.class);

        return result;
    }
    private static final int getNativeLibraryFlags(IRubyObject rbFlags) {
        int f = 0, flags = RubyNumeric.fix2int(rbFlags);
        f |= (flags & LAZY) != 0 ? Library.LAZY : 0;
        f |= (flags & NOW) != 0 ? Library.NOW : 0;
        f |= (flags & LOCAL) != 0 ? Library.LOCAL : 0;
        f |= (flags & GLOBAL) != 0 ? Library.GLOBAL : 0;
        return f;
    }
    public DynamicLibrary(Ruby runtime, RubyClass klass, String name, Library library) {
        super(runtime, klass);
        this.name = name;
        this.library = library;
    }
    final Library getNativeLibrary() {
        return library;
    }
    @JRubyMethod(name = {  "open" }, meta = true)
    public static final  IRubyObject open(ThreadContext context, IRubyObject recv, IRubyObject libraryName, IRubyObject libraryFlags) {
        final String libName = libraryName.toString();
        try {
            return new DynamicLibrary(context.getRuntime(), (RubyClass) recv, 
                    libName, LibraryCache.open(libName, getNativeLibraryFlags(libraryFlags)));
        } catch (UnsatisfiedLinkError ex) {
            throw context.getRuntime().newLoadError(String.format("Could not open library '%s' : %s",
                    libName, Library.lastError()));
        }
    }
    @JRubyMethod(name = {  "find_symbol" })
    public IRubyObject findSymbol(ThreadContext context, IRubyObject symbolName) {
        final String sym = symbolName.toString();
        final Address address = library.findSymbol(sym);
        if (address == null || address.isNull()) {
            return context.getRuntime().getNil();
        }
        return new Symbol(context.getRuntime(), this, sym, address.nativeAddress());
    }
    static final class Symbol extends BasePointer {
        private final DynamicLibrary library;
        private final String name;
        public Symbol(Ruby runtime, DynamicLibrary library, String name, long address) {
            super(runtime, FFIProvider.getModule(runtime).fastGetClass("DynamicLibrary").fastGetClass("Symbol"),
                    new DirectMemoryIO(address), 0, Long.MAX_VALUE);
            this.library = library;
            this.name = name;
        }
        @Override
        @JRubyMethod(name = "inspect")
        public IRubyObject inspect(ThreadContext context) {
            return RubyString.newString(context.getRuntime(),
                    String.format("#<Library Symbol library=%s symbol=%s address=%#x>", library.name, name, getAddress()));
        }
    }
}
