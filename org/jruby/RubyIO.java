package org.jruby;

import java.io.*;

import org.jruby.exceptions.*;
import org.jruby.runtime.*;
import org.jruby.util.*;

public class RubyIO extends RubyObject {
    private RubyInputStream inStream = null;
    private OutputStream outStream = null;

    private boolean sync = false;

    private boolean readable = false;
    private boolean writeable = false;

    private int lineNumber = 0;

    private String path;

    public RubyIO(Ruby ruby) {
        super(ruby, ruby.getClasses().getIoClass());
    }

    public RubyIO(Ruby ruby, RubyClass type) {
        super(ruby, type);
    }

    public static RubyClass createIOClass(Ruby ruby) {
        RubyClass ioClass = ruby.defineClass("IO", ruby.getClasses().getObjectClass());
        ioClass.includeModule(ruby.getClasses().getEnumerableModule());

        ioClass.defineSingletonMethod("new", CallbackFactory.getOptSingletonMethod(RubyIO.class, "newInstance"));
        ioClass.defineMethod("initialize", CallbackFactory.getOptMethod(RubyIO.class, "initialize"));

        ioClass.defineMethod("write", CallbackFactory.getMethod(RubyIO.class, "write", RubyObject.class));

        ioClass.defineMethod("<<", CallbackFactory.getMethod(RubyIO.class, "addString", RubyObject.class));

        ioClass.defineMethod("gets", CallbackFactory.getOptMethod(RubyIO.class, "gets"));

        ioClass.defineMethod("lineno", CallbackFactory.getMethod(RubyIO.class, "lineno"));
        ioClass.defineMethod("lineno=", CallbackFactory.getMethod(RubyIO.class, "lineno_set", RubyFixnum.class));
        ioClass.defineMethod("sync", CallbackFactory.getMethod(RubyIO.class, "sync"));
        ioClass.defineMethod("sync=", CallbackFactory.getMethod(RubyIO.class, "sync_set", RubyBoolean.class));

        ruby.defineHookedVariable("$stdin", stdin(ruby, ioClass), null, new StdInSetter());
        ruby.defineHookedVariable("$stdout", stdout(ruby, ioClass), null, new StdOutSetter());

        return ioClass;
    }

    private static RubyObject stdin(Ruby ruby, RubyClass rubyClass) {
        RubyIO io = new RubyIO(ruby, rubyClass);

        io.inStream = new RubyInputStream(ruby.getRuntime().getInputStream());
        io.readable = true;

        return io;
    }

    private static RubyObject stdout(Ruby ruby, RubyClass rubyClass) {
        RubyIO io = new RubyIO(ruby, rubyClass);

        io.outStream = ruby.getRuntime().getOutputStream();
        io.writeable = true;

        return io;
    }

    protected void checkWriteable() {
        if (!writeable || outStream == null) {
            throw new IOError(getRuby(), "not opened for writing");
        }
    }

    protected void checkReadable() {
        if (!readable || inStream == null) {
            throw new IOError(getRuby(), "not opened for reading");
        }
    }

    protected boolean isReadable() {
        return readable;
    }

    protected boolean isWriteable() {
        return writeable;
    }

    protected void closeStreams() {
        if (inStream != null) {
            try {
                inStream.close();
            } catch (IOException ioExcptn) {
            }
        }

        if (outStream != null) {
            try {
                outStream.close();
            } catch (IOException ioExcptn) {
            }
        }

        inStream = null;
        outStream = null;
    }

    /** rb_io_write
     * 
     */
    protected RubyObject callWrite(RubyObject anObject) {
        return funcall("write", anObject);
    }

    /** rb_io_mode_flags
     * 
     */
    protected void setMode(String mode) {
        if (mode.length() == 0) {
            throw new RubyArgumentException(getRuby(), "illegal access mode");
        }

        switch (mode.charAt(0)) {
            case 'r' :
                readable = true;
                break;
            case 'w' :
            case 'a' :
                writeable = true;
                break;
            default :
                throw new RubyArgumentException(getRuby(), "illegal access mode " + mode);
        }

        if (mode.length() > 1) {
            int i = mode.charAt(1) == 'b' ? 2 : 1;

            if (mode.length() > i) {
                if (mode.charAt(i) == '+') {
                    readable = true;
                    writeable = true;
                } else {
                    throw new RubyArgumentException(getRuby(), "illegal access mode " + mode);
                }
            }
        }
    }

    /** rb_fdopen
     * 
     */
    protected void fdOpen(int fd) {
        switch (fd) {
            case 0 :
                inStream = new RubyInputStream(getRuby().getRuntime().getInputStream());
                break;
            case 1 :
                outStream = getRuby().getRuntime().getOutputStream();
                break;
            case 2 :
                outStream = getRuby().getRuntime().getErrorStream();
                break;
            default :
                throw new IOError(getRuby(), "Bad file descriptor");
        }
    }

    // IO class methods.

    /** rb_io_s_new
     * 
     */
    public static RubyObject newInstance(Ruby ruby, RubyObject recv, RubyObject[] args) {
        RubyIO newObject = new RubyIO(ruby, (RubyClass) recv);

        newObject.callInit(args);

        return newObject;
    }

    /** rb_io_initialize
     * 
     */
    public RubyObject initialize(RubyObject[] args) {
        closeStreams();

        String mode = "r";

        if (args.length > 1) {
            mode = ((RubyString) args[1]).getValue();
        }

        setMode(mode);

        fdOpen(RubyFixnum.fix2int(args[0]));

        return this;
    }

    /** io_write
     * 
     */
    public RubyObject write(RubyObject obj) {
        getRuby().secure(4);

        RubyString str = obj.to_s();

        if (str.getValue().length() == 0) {
            return RubyFixnum.zero(getRuby());
        }

        checkWriteable();

        try {
            outStream.write(str.getValue().getBytes());

            if (sync) {
                outStream.flush();
            }
        } catch (IOException ioExcptn) {
            throw new IOError(getRuby(), ioExcptn.getMessage());
        }

        return str.length();
    }

    /** rb_io_addstr
     * 
     */
    public RubyObject addString(RubyObject anObject) {
        callWrite(anObject);

        return this;
    }

    /** Returns the current line number.
     * 
     * @return the current line number.
     */
    public RubyFixnum lineno() {
        return RubyFixnum.newFixnum(getRuby(), lineNumber);
    }

    /** Sets the current line number.
     * 
     * @param newLineNumber The new line number.
     */
    public RubyObject lineno_set(RubyFixnum newLineNumber) {
        lineNumber = RubyFixnum.fix2int(newLineNumber);

        return this;
    }

    /** Returns the current sync mode.
     * 
     * @return the current sync mode.
     */
    public RubyBoolean sync() {
        return RubyBoolean.newBoolean(getRuby(), sync);
    }

    /** Sets the current sync mode.
     * 
     * @param newSync The new sync mode.
     */
    public RubyObject sync_set(RubyBoolean newSync) {
        sync = newSync.isTrue();

        return this;
    }

    /** Read a line.
     * 
     */
    public RubyString gets(RubyObject[] args) {
        RubyObject sepVal = getRuby().getGlobalEntry("$/").get();

        if (args.length > 0) {
            sepVal = args[0];
        }

        String separator = sepVal.isNil() ? null : ((RubyString) sepVal).getValue();

        if (separator == null) {
        } else if (separator.length() == 0) {
            separator = "\n\n";
        }

        try {
            String newLine = inStream.gets(separator);

            if (newLine != null) {
                lineNumber++;

                RubyString result = RubyString.newString(getRuby(), newLine);
                result.taint();

                getRuby().getParserHelper().setLastline(result);

                return result;
            }
        } catch (IOException ioExcptn) {
        }

        return RubyString.nilString(getRuby());
    }

    private static class StdInSetter implements RubyGlobalEntry.SetterMethod {
        /*
         * @see SetterMethod#set(RubyObject, String, Object, RubyGlobalEntry)
         */
        public void set(RubyObject value, String id, RubyObject data, RubyGlobalEntry entry) {
            if (value == data) {
                return;
            } else if (!(value instanceof RubyIO)) {
                entry.setData(value);
                return;
            } else {
                ((RubyIO) value).checkReadable();
                // ((RubyIO)value).fileno = 0;

                entry.setData(value);
            }
        }
    }

    private static class StdOutSetter implements RubyGlobalEntry.SetterMethod {
        /*
         * @see SetterMethod#set(RubyObject, String, Object, RubyGlobalEntry)
         */
        public void set(RubyObject value, String id, RubyObject data, RubyGlobalEntry entry) {
            if (value == data) {
                return;
            } else if (!(value instanceof RubyIO)) {
                entry.setData(value);
                return;
            } else {
                ((RubyIO) value).checkWriteable();
                // ((RubyIO)value).fileno = 0;

                entry.setData(value);
            }
        }
    }
}