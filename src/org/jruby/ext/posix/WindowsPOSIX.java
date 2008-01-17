package org.jruby.ext.posix;

public class WindowsPOSIX extends BaseNativePOSIX {
    // We fall back to Pure Java Posix impl when windows does not support something
    JavaLibCHelper helper;

    public WindowsPOSIX(LibC libc, POSIXHandler handler) {
        super(libc, handler);

        helper = new JavaLibCHelper(handler);
    }

    @Override
    public FileStat allocateStat() {
        return new WindowsFileStat(this);
    }

    @Override
    public int geteuid() {
        handler.unimplementedError("geteuid");
        
        return -1;
    }

    @Override
    public int getuid() {
        handler.unimplementedError("getuid");
        
        return -1;
    }

    @Override
    public FileStat lstat(String path) {
        return stat(path);
    }
}
