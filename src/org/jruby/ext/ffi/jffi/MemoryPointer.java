
package org.jruby.ext.ffi.jffi;

import org.jruby.ext.ffi.*;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyModule;
import org.jruby.RubyNumeric;
import org.jruby.RubyString;
import org.jruby.RubySymbol;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.Block;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

@JRubyClass(name = "FFI::MemoryPointer", parent = FFIProvider.MODULE_NAME + "::" + AbstractMemoryPointer.className)
public class MemoryPointer extends BasePointer {
    
    public static RubyClass createMemoryPointerClass(Ruby runtime, RubyModule module) {
        RubyClass result = module.defineClassUnder("MemoryPointer",
                module.getClass(BasePointer.BASE_POINTER_NAME),
                ObjectAllocator.NOT_ALLOCATABLE_ALLOCATOR);
        result.defineAnnotatedMethods(MemoryPointer.class);
        result.defineAnnotatedConstants(MemoryPointer.class);

        return result;
    }

    private MemoryPointer(Ruby runtime, IRubyObject klass, DirectMemoryIO io, long offset, long size) {
        super(runtime, (RubyClass) klass, io, offset, size);
    }
    
    private static final IRubyObject allocate(ThreadContext context, IRubyObject recv,
            IRubyObject sizeArg, int count, boolean clear, Block block) {
        int size = calculateSize(context, sizeArg);
        int total = size * count;
        if (total < 0) {
            throw context.getRuntime().newArgumentError(String.format("Negative size (%d objects of %d size)", count, size));
        }
        DirectMemoryIO io = DirectMemoryIO.allocate(total > 0 ? total : 1, clear);
        if (io == null) {
            Ruby runtime = context.getRuntime();
            throw new RaiseException(runtime, runtime.getNoMemoryError(),
                    String.format("Failed to allocate %d objects of %d bytes", count, size), true);
        }
        MemoryPointer ptr = new MemoryPointer(context.getRuntime(), recv, io, 0, total);
        ptr.fastSetInstanceVariable("@type_size", sizeArg instanceof RubyFixnum ? sizeArg : context.getRuntime().newFixnum(size));
        if (block.isGiven()) {
            return block.yield(context, ptr);
        } else {
            return ptr;
        }
    }
    @JRubyMethod(name = { "new" }, meta = true)
    public static IRubyObject newInstance(ThreadContext context, IRubyObject recv, IRubyObject sizeArg, Block block) {
        return allocate(context, recv, sizeArg, 1, true, block);
    }
    @JRubyMethod(name = { "new" }, meta = true)
    public static IRubyObject newInstance(ThreadContext context, IRubyObject recv, IRubyObject sizeArg, IRubyObject count, Block block) {
        return allocate(context, recv, sizeArg, RubyNumeric.fix2int(count), true, block);
    }
    @JRubyMethod(name = { "new" }, meta = true)
    public static IRubyObject newInstance(ThreadContext context, IRubyObject recv,
            IRubyObject sizeArg, IRubyObject count, IRubyObject clear, Block block) {
        return allocate(context, recv, sizeArg, RubyNumeric.fix2int(count), clear.isTrue(), block);
    }

    @Override
    @JRubyMethod(name = "to_s", optional = 1)
    public IRubyObject to_s(ThreadContext context, IRubyObject[] args) {
        return RubyString.newString(context.getRuntime(),
                String.format("MemoryPointer[address=%#x size=%d]", getAddress(), size));
    }

    @Override
    @JRubyMethod(name = "inspect")
    public IRubyObject inspect(ThreadContext context) {
        return RubyString.newString(context.getRuntime(),
                String.format("#<MemoryPointer address=%#x size=%d>", getAddress(), size));
    }

    @JRubyMethod(name = "free")
    public IRubyObject free(ThreadContext context) {
        ((DirectMemoryIO) getMemoryIO()).free();
        return context.getRuntime().getNil();
    }

    @JRubyMethod(name = "autorelease=", required = 1)
    public IRubyObject autorelease(ThreadContext context, IRubyObject release) {
        ((DirectMemoryIO) getMemoryIO()).autorelease(release.isTrue());
        return context.getRuntime().getNil();
    }
}
