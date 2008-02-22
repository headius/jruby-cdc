package org.jruby.ext.posix;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public abstract class BaseNativePOSIX implements POSIX {
    protected LibC libc;
    protected POSIXHandler handler;
    protected JavaLibCHelper helper;
    
    public BaseNativePOSIX(LibC libc, POSIXHandler handler) {
        this.libc = libc;
        this.handler = handler;
        helper = new JavaLibCHelper(handler);
    }

    public int chmod(String filename, int mode) {
        return libc.chmod(filename, mode);
    }

    public int chown(String filename, int user, int group) {
        return libc.chown(filename, user, group);
    }

    public FileStat fstat(FileDescriptor fileDescriptor) {
        FileStat stat = allocateStat();
        int fd = helper.getfd(fileDescriptor);

        if (libc.fstat(fd, stat) < 0) handler.error(ERRORS.ENOENT, ""+fd);
        
        return stat;
    }

    public int getegid() {
        return libc.getegid();
    }

    public int geteuid() {
        return libc.geteuid();
    }

    public int getgid() {
        return libc.getgid();
    }
    
    public String getlogin() {
        return libc.getlogin();
    }

    public int getpgid() {
        return libc.getpgid();
    }

    public int getpgrp() {
        return libc.getpgrp();
    }

    public int getpid() {
        return libc.getpid();
    }

    public int getppid() {
        return libc.getppid();
    }
    
    public Passwd getpwent() {
        return libc.getpwent();
    }

    public Passwd getpwuid(int which) {
        return libc.getpwuid(which);
    }

    public Passwd getpwnam(String which) {
        return libc.getpwnam(which);
    }

    public Group getgrent() {
        return libc.getgrent();
    }
    public Group getgrgid(int which) {
        return libc.getgrgid(which);
    }
    public Group getgrnam(String which) {
        return libc.getgrnam(which);
    }

    public int setpwent() {
        return libc.setpwent();
    }

    public int endpwent() {
        return libc.endpwent();
    }

    public int setgrent() {
        return libc.setgrent();
    }

    public int endgrent() {
        return libc.endgrent();
    }

    public int getuid() {
        return libc.getuid();
    }

    public int setegid(int egid) {
        return libc.setegid(egid);
    }

    public int seteuid(int euid) {
        return libc.seteuid(euid);
    }

    public int setgid(int gid) {
        return libc.setgid(gid);
    }
    
    public int getfd(FileDescriptor descriptor) {
        return helper.getfd(descriptor);
    }

    public int getpgid(int pid) {
        return libc.getpgid(pid);
    }

    public int setpgid(int pid, int pgid) {
        return libc.setpgid(pid, pgid);
    }

    public int setpgrp(int pid, int pgrp) {
        return libc.setpgrp(pid, pgrp);
    }

    public int setsid() {
        return libc.setsid();
    }

    public int setuid(int uid) {
        return libc.setuid(uid);
    }

    public int kill(int pid, int signal) {
        return libc.kill(pid, signal);
    }

    public int lchmod(String filename, int mode) {
        return libc.lchmod(filename, mode);
    }

    public int lchown(String filename, int user, int group) {
        return libc.lchown(filename, user, group);
    }

    public int link(String oldpath, String newpath) {
        return libc.link(oldpath, newpath);
    }
    
    public FileStat lstat(String path) {
        FileStat stat = allocateStat();

        if (libc.lstat(path, stat) < 0) handler.error(ERRORS.ENOENT, path);
        
        return stat;
    }
    
    public int mkdir(String path, int mode) {
        return libc.mkdir(path, mode);
    }

    public FileStat stat(String path) {
        FileStat stat = allocateStat(); 

        if (libc.stat(path, stat) < 0) handler.error(ERRORS.ENOENT, path);
        
        return stat;
    }

    public int symlink(String oldpath, String newpath) {
        return libc.symlink(oldpath, newpath);
    }
    
    public String readlink(String oldpath) throws IOException {
        // TODO: this should not be hardcoded to 256 bytes
        ByteBuffer buffer = ByteBuffer.allocate(256);
        int result = libc.readlink(oldpath, buffer, buffer.capacity());
        
        if (result == -1) return null;
        
        buffer.position(0);
        buffer.limit(result);
        return Charset.forName("ASCII").decode(buffer).toString();
    }
    
    public int umask(int mask) {
        return libc.umask(mask);
    }
    
    public int fork() {
        return libc.fork();
    }
    
    public int waitpid(int pid, int[] status, int flags) {
        return libc.waitpid(pid, status, pid);
    }
    
    public int wait(int[] status) {
        return libc.wait(status);
    }
    
    public int getpriority(int which, int who) {
        return libc.getpriority(which, who);
    }
    
    public int setpriority(int which, int who, int prio) {
        return libc.setpriority(which, who, prio);
    }

    public boolean isatty(FileDescriptor fd) {
       return libc.isatty(helper.getfd(fd)) != 0;
    }
    
    public abstract FileStat allocateStat();
}
