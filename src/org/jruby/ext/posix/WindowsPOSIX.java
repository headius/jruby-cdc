package org.jruby.ext.posix;

import java.io.FileDescriptor;

public class WindowsPOSIX extends BaseNativePOSIX {
    // We fall back to Pure Java Posix impl when windows does not support something
    JavaLibCHelper helper;

    public WindowsPOSIX(String libraryName, LibC libc, POSIXHandler handler) {
        super(libraryName, libc, handler);

        helper = new JavaLibCHelper(handler);
    }

    @Override
    public FileStat allocateStat() {
        return new WindowsFileStat(this);
    }
    
    @Override
    public int chown(String filename, int user, int group) {
        handler.unimplementedError("chown");
        
        return -1;
    }

    @Override
    public int getegid() {
        handler.unimplementedError("egid");

        return -1;
    }

    @Override
    public int setegid(int egid) {
        handler.unimplementedError("setegid");

        return -1;
    }

    @Override
    public int geteuid() {
        handler.unimplementedError("geteuid");

        return -1;
    }

    @Override
    public int seteuid(int euid) {
        handler.unimplementedError("seteuid");

        return -1;
    }

    @Override
    public int getuid() {
        handler.unimplementedError("getuid");

        return -1;
    }

    @Override
    public int setuid(int uid) {
        handler.unimplementedError("setuid");

        return -1;
    }

    @Override
    public int getgid() {
        handler.unimplementedError("getgid");

        return -1;
    }

    @Override
    public int setgid(int gid) {
        handler.unimplementedError("setgid");

        return -1;
    }

    @Override
    public int getpgid(int pid) {
        handler.unimplementedError("getpgid");

        return -1;
    }
    
    @Override
    public int getpgid() {
        handler.unimplementedError("getpgid");

        return -1;
    }

    @Override
    public int setpgid(int pid, int pgid) {
        handler.unimplementedError("setpgid");

        return -1;
    }
    
    @Override
    public int getpriority(int which, int who) {
        handler.unimplementedError("getpriority");

        return -1;
    }
    
    @Override
    public int setpriority(int which, int who, int prio) {
        handler.unimplementedError("setpriority");

        return -1;
    }

    @Override
    public int getppid() {
        handler.unimplementedError("ppid");

        return -1;
    }
    
    @Override
    public int lchmod(String filename, int mode) {
        handler.unimplementedError("lchmod");
        
        return -1;
    }
    
    @Override
    public int lchown(String filename, int user, int group) {
        handler.unimplementedError("lchown");
        
        return -1;
    }
    
    @Override
    public FileStat lstat(String path) {
        return stat(path);
    }

    @Override
    public String readlink(String oldpath) {
        handler.unimplementedError("readlink");

        return null;
    }

    @Override
    public boolean isatty(FileDescriptor fd) {
        return (fd == FileDescriptor.in
                || fd == FileDescriptor.out
                || fd == FileDescriptor.err);
    }
}
