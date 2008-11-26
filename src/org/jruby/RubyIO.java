/*
 **** BEGIN LICENSE BLOCK *****
 * Version: CPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Common Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/cpl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2001-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2002-2006 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004-2006 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2006 Evan Buswell <ebuswell@gmail.com>
 * Copyright (C) 2007 Miguel Covarrubias <mlcovarrubias@gmail.com>
 * 
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the CPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the CPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jruby;

import java.io.EOFException;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.Pipe;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.atomic.AtomicInteger;
import org.jruby.anno.FrameField;
import org.jruby.anno.JRubyMethod;
import org.jruby.anno.JRubyClass;
import org.jruby.common.IRubyWarnings.ID;
import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.Block;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;
import org.jruby.util.io.Stream;
import org.jruby.util.io.ModeFlags;
import org.jruby.util.ShellLauncher;
import org.jruby.util.TypeConverter;
import org.jruby.util.io.BadDescriptorException;
import org.jruby.util.io.ChannelStream;
import org.jruby.util.io.InvalidValueException;
import org.jruby.util.io.PipeException;
import org.jruby.util.io.FileExistsException;
import org.jruby.util.io.STDIO;
import org.jruby.util.io.OpenFile;
import org.jruby.util.io.ChannelDescriptor;

import static org.jruby.CompatVersion.*;
import static org.jruby.RubyEnumerator.enumeratorize;

/**
 * 
 * @author jpetersen
 */
@JRubyClass(name="IO", include="Enumerable")
public class RubyIO extends RubyObject {
    protected OpenFile openFile;
    protected List<RubyThread> blockingThreads;
    
    public void registerDescriptor(ChannelDescriptor descriptor) {
        getRuntime().registerDescriptor(descriptor);
    }
    
    public void unregisterDescriptor(int aFileno) {
        getRuntime().unregisterDescriptor(aFileno);
    }
    
    public ChannelDescriptor getDescriptorByFileno(int aFileno) {
        return getRuntime().getDescriptorByFileno(aFileno);
    }
    
    // FIXME can't use static; would interfere with other runtimes in the same JVM
    protected static AtomicInteger filenoIndex = new AtomicInteger(2);
    
    public static int getNewFileno() {
        return filenoIndex.incrementAndGet();
    }

    // This should only be called by this and RubyFile.
    // It allows this object to be created without a IOHandler.
    public RubyIO(Ruby runtime, RubyClass type) {
        super(runtime, type);
        
        openFile = new OpenFile();
    }

    public RubyIO(Ruby runtime, OutputStream outputStream) {
        super(runtime, runtime.getIO());
        
        // We only want IO objects with valid streams (better to error now). 
        if (outputStream == null) {
            throw runtime.newRuntimeError("Opening null stream");
        }
        
        openFile = new OpenFile();
        
        try {
            openFile.setMainStream(new ChannelStream(runtime, new ChannelDescriptor(Channels.newChannel(outputStream), getNewFileno(), new FileDescriptor())));
        } catch (InvalidValueException e) {
            throw getRuntime().newErrnoEINVALError();
        }
        
        openFile.setMode(OpenFile.WRITABLE | OpenFile.APPEND);
        
        registerDescriptor(openFile.getMainStream().getDescriptor());
    }
    
    public RubyIO(Ruby runtime, InputStream inputStream) {
        super(runtime, runtime.getIO());
        
        if (inputStream == null) {
            throw runtime.newRuntimeError("Opening null stream");
        }
        
        openFile = new OpenFile();
        
        try {
            openFile.setMainStream(new ChannelStream(runtime, new ChannelDescriptor(Channels.newChannel(inputStream), getNewFileno(), new FileDescriptor())));
        } catch (InvalidValueException e) {
            throw getRuntime().newErrnoEINVALError();
        }
        
        openFile.setMode(OpenFile.READABLE);
        
        registerDescriptor(openFile.getMainStream().getDescriptor());
    }
    
    public RubyIO(Ruby runtime, Channel channel) {
        super(runtime, runtime.getIO());
        
        // We only want IO objects with valid streams (better to error now). 
        if (channel == null) {
            throw runtime.newRuntimeError("Opening null channelpo");
        }
        
        openFile = new OpenFile();
        
        try {
            openFile.setMainStream(new ChannelStream(runtime, new ChannelDescriptor(channel, getNewFileno(), new FileDescriptor())));
        } catch (InvalidValueException e) {
            throw getRuntime().newErrnoEINVALError();
        }
        
        openFile.setMode(openFile.getMainStream().getModes().getOpenFileFlags());
        
        registerDescriptor(openFile.getMainStream().getDescriptor());
    }

    public RubyIO(Ruby runtime, ShellLauncher.POpenProcess process, ModeFlags modes) {
    	super(runtime, runtime.getIO());
        
        openFile = new OpenFile();
        
        openFile.setMode(modes.getOpenFileFlags() | OpenFile.SYNC);
        openFile.setProcess(process);

        try {
            if (openFile.isReadable()) {
                Channel inChannel;
                if (process.getInput() != null) {
                    // NIO-based
                    inChannel = process.getInput();
                } else {
                    // Stream-based
                    inChannel = Channels.newChannel(process.getInputStream());
                }
                
                ChannelDescriptor main = new ChannelDescriptor(
                        inChannel,
                        getNewFileno(),
                        new FileDescriptor());
                main.setCanBeSeekable(false);
                
                openFile.setMainStream(new ChannelStream(getRuntime(), main));
                registerDescriptor(main);
            }
            
            if (openFile.isWritable()) {
                Channel outChannel;
                if (process.getOutput() != null) {
                    // NIO-based
                    outChannel = process.getOutput();
                } else {
                    outChannel = Channels.newChannel(process.getOutputStream());
                }

                ChannelDescriptor pipe = new ChannelDescriptor(
                        outChannel,
                        getNewFileno(),
                        new FileDescriptor());
                pipe.setCanBeSeekable(false);
                
                if (openFile.getMainStream() != null) {
                    openFile.setPipeStream(new ChannelStream(getRuntime(), pipe));
                } else {
                    openFile.setMainStream(new ChannelStream(getRuntime(), pipe));
                }
                
                registerDescriptor(pipe);
            }
        } catch (InvalidValueException e) {
            throw getRuntime().newErrnoEINVALError();
        }
    }
    
    public RubyIO(Ruby runtime, STDIO stdio) {
        super(runtime, runtime.getIO());
        
        openFile = new OpenFile();

        try {
            switch (stdio) {
            case IN:
                openFile.setMainStream(
                        new ChannelStream(
                            runtime, 
                            // special constructor that accepts stream, not channel
                            new ChannelDescriptor(runtime.getIn(), 0, new ModeFlags(ModeFlags.RDONLY), FileDescriptor.in),
                            FileDescriptor.in));
                break;
            case OUT:
                openFile.setMainStream(
                        new ChannelStream(
                            runtime, 
                            new ChannelDescriptor(Channels.newChannel(runtime.getOut()), 1, new ModeFlags(ModeFlags.WRONLY | ModeFlags.APPEND), FileDescriptor.out),
                            FileDescriptor.out));
                openFile.getMainStream().setSync(true);
                break;
            case ERR:
                openFile.setMainStream(
                        new ChannelStream(
                            runtime, 
                            new ChannelDescriptor(Channels.newChannel(runtime.getErr()), 2, new ModeFlags(ModeFlags.WRONLY | ModeFlags.APPEND), FileDescriptor.err), 
                            FileDescriptor.err));
                openFile.getMainStream().setSync(true);
                break;
            }
        } catch (InvalidValueException ex) {
            throw getRuntime().newErrnoEINVALError();
        }
        
        openFile.setMode(openFile.getMainStream().getModes().getOpenFileFlags());
        
        registerDescriptor(openFile.getMainStream().getDescriptor());        
    }
    
    public static RubyIO newIO(Ruby runtime, Channel channel) {
        return new RubyIO(runtime, channel);
    }
    
    public OpenFile getOpenFile() {
        return openFile;
    }
    
    protected OpenFile getOpenFileChecked() {
        openFile.checkClosed(getRuntime());
        return openFile;
    }
    
    private static ObjectAllocator IO_ALLOCATOR = new ObjectAllocator() {
        public IRubyObject allocate(Ruby runtime, RubyClass klass) {
            return new RubyIO(runtime, klass);
        }
    };

    public static RubyClass createIOClass(Ruby runtime) {
        RubyClass ioClass = runtime.defineClass("IO", runtime.getObject(), IO_ALLOCATOR);
        ioClass.kindOf = new RubyModule.KindOf() {
            @Override
            public boolean isKindOf(IRubyObject obj, RubyModule type) {
                return obj instanceof RubyIO;
            }
        };

        ioClass.includeModule(runtime.getEnumerable());
        
        // TODO: Implement tty? and isatty.  We have no real capability to
        // determine this from java, but if we could set tty status, then
        // we could invoke jruby differently to allow stdin to return true
        // on this.  This would allow things like cgi.rb to work properly.
        
        ioClass.defineAnnotatedMethods(RubyIO.class);

        // Constants for seek
        ioClass.fastSetConstant("SEEK_SET", runtime.newFixnum(Stream.SEEK_SET));
        ioClass.fastSetConstant("SEEK_CUR", runtime.newFixnum(Stream.SEEK_CUR));
        ioClass.fastSetConstant("SEEK_END", runtime.newFixnum(Stream.SEEK_END));

        return ioClass;
    }

    public OutputStream getOutStream() {
        return getOpenFileChecked().getMainStream().newOutputStream();
    }

    public InputStream getInStream() {
        return getOpenFileChecked().getMainStream().newInputStream();
    }

    public Channel getChannel() {
        if (getOpenFileChecked().getMainStream() instanceof ChannelStream) {
            return ((ChannelStream) openFile.getMainStream()).getDescriptor().getChannel();
        } else {
            return null;
        }
    }
    
    public Stream getHandler() {
        return getOpenFileChecked().getMainStream();
    }

    @JRubyMethod(name = "reopen", required = 1, optional = 1)
    public IRubyObject reopen(ThreadContext context, IRubyObject[] args) throws InvalidValueException {
        Ruby runtime = context.getRuntime();
        
    	if (args.length < 1) {
            throw runtime.newArgumentError("wrong number of arguments");
    	}
    	
    	IRubyObject tmp = TypeConverter.convertToTypeWithCheck(args[0], runtime.getIO(), "to_io");
        
    	if (!tmp.isNil()) {
            try {
                RubyIO ios = (RubyIO) tmp;

                if (ios.openFile == this.openFile) {
                    return this;
                }

                OpenFile originalFile = ios.getOpenFileChecked();
                OpenFile selfFile = getOpenFileChecked();

                long pos = 0;
                if (originalFile.isReadable()) {
                    pos = originalFile.getMainStream().fgetpos();
                }

                if (originalFile.getPipeStream() != null) {
                    originalFile.getPipeStream().fflush();
                } else if (originalFile.isWritable()) {
                    originalFile.getMainStream().fflush();
                }

                if (selfFile.isWritable()) {
                    selfFile.getWriteStream().fflush();
                }

                selfFile.setMode(originalFile.getMode());
                selfFile.setProcess(originalFile.getProcess());
                selfFile.setLineNumber(originalFile.getLineNumber());
                selfFile.setPath(originalFile.getPath());
                selfFile.setFinalizer(originalFile.getFinalizer());

                ChannelDescriptor selfDescriptor = selfFile.getMainStream().getDescriptor();
                ChannelDescriptor originalDescriptor = originalFile.getMainStream().getDescriptor();

                // confirm we're not reopening self's channel
                if (selfDescriptor.getChannel() != originalDescriptor.getChannel()) {
                    // check if we're a stdio IO, and ensure we're not badly mutilated
                    if (selfDescriptor.getFileno() >=0 && selfDescriptor.getFileno() <= 2) {
                        selfFile.getMainStream().clearerr();
                        
                        // dup2 new fd into self to preserve fileno and references to it
                        originalDescriptor.dup2Into(selfDescriptor);
                        
                        // re-register, since fileno points at something new now
                        registerDescriptor(selfDescriptor);
                    } else {
                        Stream pipeFile = selfFile.getPipeStream();
                        int mode = selfFile.getMode();
                        selfFile.getMainStream().fclose();
                        selfFile.setPipeStream(null);

                        // TODO: turn off readable? am I reading this right?
                        // This only seems to be used while duping below, since modes gets
                        // reset to actual modes afterward
                        //fptr->mode &= (m & FMODE_READABLE) ? ~FMODE_READABLE : ~FMODE_WRITABLE;

                        if (pipeFile != null) {
                            selfFile.setMainStream(ChannelStream.fdopen(runtime, originalDescriptor, new ModeFlags()));
                            selfFile.setPipeStream(pipeFile);
                        } else {
                            selfFile.setMainStream(
                                    new ChannelStream(
                                        runtime,
                                        originalDescriptor.dup2(selfDescriptor.getFileno())));
                            
                            // re-register the descriptor
                            registerDescriptor(selfFile.getMainStream().getDescriptor());
                            
                            // since we're not actually duping the incoming channel into our handler, we need to
                            // copy the original sync behavior from the other handler
                            selfFile.getMainStream().setSync(selfFile.getMainStream().isSync());
                        }
                        selfFile.setMode(mode);
                    }
                    
                    // TODO: anything threads attached to original fd are notified of the close...
                    // see rb_thread_fd_close
                    
                    if (originalFile.isReadable() && pos >= 0) {
                        selfFile.seek(pos, Stream.SEEK_SET);
                        originalFile.seek(pos, Stream.SEEK_SET);
                    }
                }

                if (selfFile.getPipeStream() != null && selfDescriptor.getFileno() != selfFile.getPipeStream().getDescriptor().getFileno()) {
                    int fd = selfFile.getPipeStream().getDescriptor().getFileno();
                    
                    if (originalFile.getPipeStream() == null) {
                        selfFile.getPipeStream().fclose();
                        selfFile.setPipeStream(null);
                    } else if (fd != originalFile.getPipeStream().getDescriptor().getFileno()) {
                        selfFile.getPipeStream().fclose();
                        ChannelDescriptor newFD2 = originalFile.getPipeStream().getDescriptor().dup2(fd);
                        selfFile.setPipeStream(ChannelStream.fdopen(runtime, newFD2, getIOModes(runtime, "w")));
                        
                        // re-register, since fileno points at something new now
                        registerDescriptor(newFD2);
                    }
                }
                
                // TODO: restore binary mode
    //            if (fptr->mode & FMODE_BINMODE) {
    //                rb_io_binmode(io);
    //            }
                
                // TODO: set our metaclass to target's class (i.e. scary!)

            } catch (IOException ex) { // TODO: better error handling
                throw runtime.newIOError("could not reopen: " + ex.getMessage());
            } catch (BadDescriptorException ex) {
                throw runtime.newIOError("could not reopen: " + ex.getMessage());
            } catch (PipeException ex) {
                throw runtime.newIOError("could not reopen: " + ex.getMessage());
            }
        } else {
            IRubyObject pathString = args[0].convertToString();
            
            // TODO: check safe, taint on incoming string
            
            if (openFile == null) {
                openFile = new OpenFile();
            }
            
            try {
                ModeFlags modes;
                if (args.length > 1) {
                    IRubyObject modeString = args[1].convertToString();
                    modes = getIOModes(runtime, modeString.toString());

                    openFile.setMode(modes.getOpenFileFlags());
                } else {
                    modes = getIOModes(runtime, "r");
                }

                String path = pathString.toString();
                
                // Ruby code frequently uses a platform check to choose "NUL:" on windows
                // but since that check doesn't work well on JRuby, we help it out
                
                openFile.setPath(path);
            
                if (openFile.getMainStream() == null) {
                    try {
                        openFile.setMainStream(ChannelStream.fopen(runtime, path, modes));
                    } catch (FileExistsException fee) {
                        throw runtime.newErrnoEEXISTError(path);
                    }
                    
                    registerDescriptor(openFile.getMainStream().getDescriptor());
                    if (openFile.getPipeStream() != null) {
                        openFile.getPipeStream().fclose();
                        unregisterDescriptor(openFile.getPipeStream().getDescriptor().getFileno());
                        openFile.setPipeStream(null);
                    }
                    return this;
                } else {
                    // TODO: This is an freopen in MRI, this is close, but not quite the same
                    openFile.getMainStream().freopen(path, getIOModes(runtime, openFile.getModeAsString(runtime)));

                    // re-register
                    registerDescriptor(openFile.getMainStream().getDescriptor());

                    if (openFile.getPipeStream() != null) {
                        // TODO: pipe handler to be reopened with path and "w" mode
                    }
                }
            } catch (PipeException pe) {
                throw runtime.newErrnoEPIPEError();
            } catch (IOException ex) {
                throw runtime.newIOErrorFromException(ex);
            } catch (BadDescriptorException ex) {
                throw runtime.newErrnoEBADFError();
            } catch (InvalidValueException e) {
            	throw runtime.newErrnoEINVALError();
            }
        }
        
        // A potentially previously close IO is being 'reopened'.
        return this;
    }
    
    public static ModeFlags getIOModes(Ruby runtime, String modesString) throws InvalidValueException {
        return new ModeFlags(getIOModesIntFromString(runtime, modesString));
    }
        
    public static int getIOModesIntFromString(Ruby runtime, String modesString) {
        int modes = 0;
        int length = modesString.length();

        if (length == 0) {
            throw runtime.newArgumentError("illegal access mode");
        }

        switch (modesString.charAt(0)) {
        case 'r' :
            modes |= ModeFlags.RDONLY;
            break;
        case 'a' :
            modes |= ModeFlags.APPEND | ModeFlags.WRONLY | ModeFlags.CREAT;
            break;
        case 'w' :
            modes |= ModeFlags.WRONLY | ModeFlags.TRUNC | ModeFlags.CREAT;
            break;
        default :
            throw runtime.newArgumentError("illegal access mode " + modes);
        }

        for (int n = 1; n < length; n++) {
            switch (modesString.charAt(n)) {
            case 'b':
                modes |= ModeFlags.BINARY;
                break;
            case '+':
                modes = (modes & ~ModeFlags.ACCMODE) | ModeFlags.RDWR;
                break;
            default:
                throw runtime.newArgumentError("illegal access mode " + modes);
            }
        }

        return modes;
    }

    private static ByteList getSeparatorFromArgs(Ruby runtime, IRubyObject[] args, int idx) {
        IRubyObject sepVal;

        if (args.length > idx) {
            sepVal = args[idx];
        } else {
            sepVal = runtime.getRecordSeparatorVar().get();
        }

        ByteList separator = sepVal.isNil() ? null : sepVal.convertToString().getByteList();

        if (separator != null && separator.realSize == 0) {
            separator = Stream.PARAGRAPH_DELIMETER;
        }

        return separator;
    }

    private ByteList getSeparatorForGets(Ruby runtime, IRubyObject[] args) {
        return getSeparatorFromArgs(runtime, args, 0);
    }

    public IRubyObject getline(Ruby runtime, ByteList separator) {
        try {
            OpenFile myOpenFile = getOpenFileChecked();

            myOpenFile.checkReadable(runtime);
            myOpenFile.setReadBuffered();

            boolean isParagraph = separator == Stream.PARAGRAPH_DELIMETER;
            separator = (separator == Stream.PARAGRAPH_DELIMETER) ?
                    Stream.PARAGRAPH_SEPARATOR : separator;
            
            if (isParagraph) {
                swallow('\n');
            }
            
            if (separator == null) {
                IRubyObject str = readAll(null);
                if (((RubyString)str).getByteList().length() == 0) {
                    return runtime.getNil();
                }
                incrementLineno(runtime, myOpenFile);
                return str;
            } else if (separator.length() == 1) {
                return getlineFast(runtime, separator.get(0));
            } else {
                Stream readStream = myOpenFile.getMainStream();
                int c = -1;
                int n = -1;
                int newline = separator.get(separator.length() - 1) & 0xFF;

                ByteList buf = new ByteList(0);
                boolean update = false;
                
                while (true) {
                    do {
                        readCheck(readStream);
                        readStream.clearerr();
                        
                        try {
                            n = readStream.getline(buf, (byte) newline);
                            c = buf.length() > 0 ? buf.get(buf.length() - 1) & 0xff : -1;
                        } catch (EOFException e) {
                            n = -1;
                        }

                        if (n == -1) {
                            if (!readStream.isBlocking() && (readStream instanceof ChannelStream)) {
                                if(!(waitReadable(((ChannelStream)readStream).getDescriptor()))) {
                                    throw runtime.newIOError("bad file descriptor: " + openFile.getPath());
                                }

                                continue;
                            } else {
                                break;
                            }
                        }
            
                        update = true;
                    } while (c != newline); // loop until we see the nth separator char
                    
                    // if we hit EOF, we're done
                    if (n == -1) {
                        break;
                    }
                    
                    // if we've found the last char of the separator,
                    // and we've found at least as many characters as separator length,
                    // and the last n characters of our buffer match the separator, we're done
                    if (c == newline && buf.length() >= separator.length() &&
                            0 == ByteList.memcmp(buf.unsafeBytes(), buf.begin + buf.realSize - separator.length(), separator.unsafeBytes(), separator.begin, separator.realSize)) {
                        break;
                    }
                }
                
                if (isParagraph) {
                    if (c != -1) {
                        swallow('\n');
                    }
                }
                
                if (!update) {
                    return runtime.getNil();
                } else {
                    incrementLineno(runtime, myOpenFile);
                    RubyString str = RubyString.newString(runtime, buf);
                    str.setTaint(true);

                    return str;
                }
            }
        } catch (PipeException ex) {
            throw runtime.newErrnoEPIPEError();
        } catch (InvalidValueException ex) {
            throw runtime.newErrnoEINVALError();
        } catch (EOFException e) {
            return runtime.getNil();
        } catch (BadDescriptorException e) {
            throw runtime.newErrnoEBADFError();
        } catch (IOException e) {
            throw runtime.newIOError(e.getMessage());
        }
    }

    private void incrementLineno(Ruby runtime, OpenFile myOpenFile) {
        int lineno = myOpenFile.getLineNumber() + 1;
        myOpenFile.setLineNumber(lineno);
        runtime.getGlobalVariables().set("$.", runtime.newFixnum(lineno));
        // this is for a range check, near as I can tell
        RubyNumeric.int2fix(runtime, myOpenFile.getLineNumber());
    }

    protected boolean swallow(int term) throws IOException, BadDescriptorException {
        Stream readStream = openFile.getMainStream();
        int c;
        
        do {
            readCheck(readStream);
            
            try {
                c = readStream.fgetc();
            } catch (EOFException e) {
                c = -1;
            }
            
            if (c != term) {
                readStream.ungetc(c);
                return true;
            }
        } while (c != -1);
        
        return false;
    }
    
    public IRubyObject getlineFast(Ruby runtime, int delim) throws IOException, BadDescriptorException {
        Stream readStream = openFile.getMainStream();
        int c = -1;

        ByteList buf = new ByteList(0);
        boolean update = false;
        do {
            readCheck(readStream);
            readStream.clearerr();
            int n;
            try {
                n = readStream.getline(buf, (byte) delim);
                c = buf.length() > 0 ? buf.get(buf.length() - 1) & 0xff : -1;
            } catch (EOFException e) {
                n = -1;
            }
            
            if (n == -1) {
                if (!readStream.isBlocking() && (readStream instanceof ChannelStream)) {
                    if(!(waitReadable(((ChannelStream)readStream).getDescriptor()))) {
                        throw runtime.newIOError("bad file descriptor: " + openFile.getPath());
                    }
                    continue;
                } else {
                    break;
                }
            }
            
            update = true;
        } while (c != delim);

        if (!update) {
            return runtime.getNil();
        } else {
            incrementLineno(runtime, openFile);
            RubyString str = RubyString.newString(runtime, buf);
            str.setTaint(true);
            return str;
        }
    }
    // IO class methods.

    @JRubyMethod(name = {"new", "for_fd"}, rest = true, frame = true, meta = true)
    public static IRubyObject newInstance(ThreadContext context, IRubyObject recv, IRubyObject[] args, Block block) {
        RubyClass klass = (RubyClass)recv;
        
        if (block.isGiven()) {
            String className = klass.getName();
            context.getRuntime().getWarnings().warn(
                    ID.BLOCK_NOT_ACCEPTED,
                    className + "::new() does not take block; use " + className + "::open() instead",
                    className + "::open()");
        }
        
        return klass.newInstance(context, args, block);
    }

    @JRubyMethod(name = "initialize", required = 1, optional = 1, frame = true, visibility = Visibility.PRIVATE)
    public IRubyObject initialize(IRubyObject[] args, Block unusedBlock) {
        int argCount = args.length;
        ModeFlags modes;
        
        int fileno = RubyNumeric.fix2int(args[0]);
        
        try {
            ChannelDescriptor descriptor = getDescriptorByFileno(fileno);
            
            if (descriptor == null) {
                throw getRuntime().newErrnoEBADFError();
            }
            
            descriptor.checkOpen();
            
            if (argCount == 2) {
                if (args[1] instanceof RubyFixnum) {
                    modes = new ModeFlags(RubyFixnum.fix2long(args[1]));
                } else {
                    modes = getIOModes(getRuntime(), args[1].convertToString().toString());
                }
            } else {
                // use original modes
                modes = descriptor.getOriginalModes();
            }

            openFile.setMode(modes.getOpenFileFlags());
        
            openFile.setMainStream(fdopen(descriptor, modes));
        } catch (BadDescriptorException ex) {
            throw getRuntime().newErrnoEBADFError();
        } catch (InvalidValueException ive) {
            throw getRuntime().newErrnoEINVALError();
        }
        
        return this;
    }
    
    protected Stream fdopen(ChannelDescriptor existingDescriptor, ModeFlags modes) throws InvalidValueException {
        // See if we already have this descriptor open.
        // If so then we can mostly share the handler (keep open
        // file, but possibly change the mode).
        
        if (existingDescriptor == null) {
            // redundant, done above as well
            
            // this seems unlikely to happen unless it's a totally bogus fileno
            // ...so do we even need to bother trying to create one?
            
            // IN FACT, we should probably raise an error, yes?
            throw getRuntime().newErrnoEBADFError();
            
//            if (mode == null) {
//                mode = "r";
//            }
//            
//            try {
//                openFile.setMainStream(streamForFileno(getRuntime(), fileno));
//            } catch (BadDescriptorException e) {
//                throw getRuntime().newErrnoEBADFError();
//            } catch (IOException e) {
//                throw getRuntime().newErrnoEBADFError();
//            }
//            //modes = new IOModes(getRuntime(), mode);
//            
//            registerStream(openFile.getMainStream());
        } else {
            // We are creating a new IO object that shares the same
            // IOHandler (and fileno).
            return ChannelStream.fdopen(getRuntime(), existingDescriptor, modes);
        }
    }

    @JRubyMethod(name = "open", required = 1, optional = 2, frame = true, meta = true)
    public static IRubyObject open(ThreadContext context, IRubyObject recv, IRubyObject[] args, Block block) {
        Ruby runtime = context.getRuntime();
        RubyClass klass = (RubyClass)recv;
        
        RubyIO io = (RubyIO)klass.newInstance(context, args, block);

        if (block.isGiven()) {
            try {
                return block.yield(context, io);
            } finally {
                try {
                    io.getMetaClass().finvoke(context, io, "close", IRubyObject.NULL_ARRAY, Block.NULL_BLOCK);
                } catch (RaiseException re) {
                    RubyException rubyEx = re.getException();
                    if (rubyEx.kind_of_p(context, runtime.getStandardError()).isTrue()) {
                        // MRI behavior: swallow StandardErorrs
                    } else {
                        throw re;
                    }
                }
            }
        }

        return io;
    }

    // This appears to be some windows-only mode.  On a java platform this is a no-op
    @JRubyMethod(name = "binmode")
    public IRubyObject binmode() {
            return this;
    }
    
    /** @deprecated will be removed in 1.2 */
    protected void checkInitialized() {
        if (openFile == null) {
            throw getRuntime().newIOError("uninitialized stream");
        }
    }
    
    /** @deprecated will be removed in 1.2 */
    protected void checkClosed() {
        if (openFile.getMainStream() == null && openFile.getPipeStream() == null) {
            throw getRuntime().newIOError("closed stream");
        }
    }
    
    @JRubyMethod(name = "syswrite", required = 1)
    public IRubyObject syswrite(ThreadContext context, IRubyObject obj) {
        Ruby runtime = context.getRuntime();
        
        try {
            RubyString string = obj.asString();
            OpenFile myOpenFile = getOpenFileChecked();
            
            myOpenFile.checkWritable(runtime);
            
            Stream writeStream = myOpenFile.getWriteStream();
            
            if (myOpenFile.isWriteBuffered()) {
                runtime.getWarnings().warn(ID.SYSWRITE_BUFFERED_IO, "syswrite for buffered IO");
            }
            
            if (!writeStream.getDescriptor().isWritable()) {
                myOpenFile.checkClosed(runtime);
            }
            
            int read = writeStream.getDescriptor().write(string.getByteList());
            
            if (read == -1) {
                // TODO? I think this ends up propagating from normal Java exceptions
                // sys_fail(openFile.getPath())
            }
            
            return runtime.newFixnum(read);
        } catch (InvalidValueException ex) {
            throw runtime.newErrnoEINVALError();
        } catch (PipeException ex) {
            throw runtime.newErrnoEPIPEError();
        } catch (BadDescriptorException e) {
            throw runtime.newErrnoEBADFError();
        } catch (IOException e) {
            throw runtime.newSystemCallError(e.getMessage());
        }
    }
    
    @JRubyMethod(name = "write_nonblock", required = 1)
    public IRubyObject write_nonblock(ThreadContext context, IRubyObject obj) {
        // MRI behavior: always check whether the file is writable
        // or not, even if we are to write 0 bytes.
        OpenFile myOpenFile = getOpenFileChecked();

        try {
            myOpenFile.checkWritable(context.getRuntime());
            RubyString str = obj.asString();
            if (str.getByteList().length() == 0) {
                return context.getRuntime().newFixnum(0);
            }

            if (myOpenFile.isWriteBuffered()) {
                context.getRuntime().getWarnings().warn(ID.SYSWRITE_BUFFERED_IO, "write_nonblock for buffered IO");
            }
            int written = myOpenFile.getWriteStream().getDescriptor().write(str.getByteList());
            return context.getRuntime().newFixnum(written);
        } catch (IOException ex) {
            throw context.getRuntime().newIOErrorFromException(ex);
        } catch (BadDescriptorException ex) {
            throw context.getRuntime().newErrnoEBADFError();
        } catch (InvalidValueException ex) {
            throw context.getRuntime().newErrnoEINVALError();
        }  catch (PipeException ex) {
            throw context.getRuntime().newErrnoEPIPEError();
        }
    }
    
    /** io_write
     * 
     */
    @JRubyMethod(name = "write", required = 1)
    public IRubyObject write(ThreadContext context, IRubyObject obj) {
        Ruby runtime = context.getRuntime();
        
        runtime.secure(4);
        
        RubyString str = obj.asString();

        // TODO: Ruby reuses this logic for other "write" behavior by checking if it's an IO and calling write again
        
        if (str.getByteList().length() == 0) {
            return runtime.newFixnum(0);
        }

        try {
            OpenFile myOpenFile = getOpenFileChecked();
            
            myOpenFile.checkWritable(runtime);

            int written = fwrite(str.getByteList());

            if (written == -1) {
                // TODO: sys fail
            }

            // if not sync, we switch to write buffered mode
            if (!myOpenFile.isSync()) {
                myOpenFile.setWriteBuffered();
            }

            return runtime.newFixnum(written);
        } catch (IOException ex) {
            throw runtime.newIOErrorFromException(ex);
        } catch (BadDescriptorException ex) {
            throw runtime.newErrnoEBADFError();
        } catch (InvalidValueException ex) {
            throw runtime.newErrnoEINVALError();
        } catch (PipeException ex) {
            throw runtime.newErrnoEPIPEError();
        }
    }

    protected boolean waitWritable(ChannelDescriptor descriptor) throws IOException {
        Channel channel = descriptor.getChannel();
        if (channel == null || !(channel instanceof SelectableChannel)) {
            return false;
        }
       
        Selector selector = Selector.open();

        ((SelectableChannel) channel).configureBlocking(false);
        int real_ops = ((SelectableChannel) channel).validOps() & SelectionKey.OP_WRITE;
        SelectionKey key = ((SelectableChannel) channel).keyFor(selector);
       
        if (key == null) {
            ((SelectableChannel) channel).register(selector, real_ops, descriptor);
        } else {
            key.interestOps(key.interestOps()|real_ops);
        }

        while(selector.select() == 0);

        for (Iterator i = selector.selectedKeys().iterator(); i.hasNext(); ) {
            SelectionKey skey = (SelectionKey) i.next();
            if ((skey.interestOps() & skey.readyOps() & (SelectionKey.OP_WRITE)) != 0) {
                if(skey.attachment() == descriptor) {
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean waitReadable(ChannelDescriptor descriptor) throws IOException {
        Channel channel = descriptor.getChannel();
        if (channel == null || !(channel instanceof SelectableChannel)) {
            return false;
        }
       
        Selector selector = Selector.open();

        ((SelectableChannel) channel).configureBlocking(false);
        int real_ops = ((SelectableChannel) channel).validOps() & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT);
        SelectionKey key = ((SelectableChannel) channel).keyFor(selector);
       
        if (key == null) {
            ((SelectableChannel) channel).register(selector, real_ops, descriptor);
        } else {
            key.interestOps(key.interestOps()|real_ops);
        }

        while(selector.select() == 0);

        for (Iterator i = selector.selectedKeys().iterator(); i.hasNext(); ) {
            SelectionKey skey = (SelectionKey) i.next();
            if ((skey.interestOps() & skey.readyOps() & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) != 0) {
                if(skey.attachment() == descriptor) {
                    return true;
                }
            }
        }
        return false;
    }
    
    protected int fwrite(ByteList buffer) {
        int n, r, l, offset = 0;
        boolean eagain = false;
        Stream writeStream = openFile.getWriteStream();

        int len = buffer.length();
        
        if ((n = len) <= 0) return n;

        try {
            if (openFile.isSync()) {
                openFile.fflush(writeStream);

                // TODO: why is this guarded?
    //            if (!rb_thread_fd_writable(fileno(f))) {
    //                rb_io_check_closed(fptr);
    //            }
               
                while(offset<len) {
                    l = n;

                    // TODO: Something about pipe buffer length here

                    r = writeStream.getDescriptor().write(buffer,offset,l);

                    if(r == len) {
                        return len; //Everything written
                    }

                    if (0 <= r) {
                        offset += r;
                        n -= r;
                        eagain = true;
                    }

                    if(eagain && waitWritable(writeStream.getDescriptor())) {
                        openFile.checkClosed(getRuntime());
                        if(offset >= buffer.length()) {
                            return -1;
                        }
                        eagain = false;
                    } else {
                        return -1;
                    }
                }


                // TODO: all this stuff...some pipe logic, some async thread stuff
    //          retry:
    //            l = n;
    //            if (PIPE_BUF < l &&
    //                !rb_thread_critical &&
    //                !rb_thread_alone() &&
    //                wsplit_p(fptr)) {
    //                l = PIPE_BUF;
    //            }
    //            TRAP_BEG;
    //            r = write(fileno(f), RSTRING(str)->ptr+offset, l);
    //            TRAP_END;
    //            if (r == n) return len;
    //            if (0 <= r) {
    //                offset += r;
    //                n -= r;
    //                errno = EAGAIN;
    //            }
    //            if (rb_io_wait_writable(fileno(f))) {
    //                rb_io_check_closed(fptr);
    //                if (offset < RSTRING(str)->len)
    //                    goto retry;
    //            }
    //            return -1L;
            }

            // TODO: handle errors in buffered write by retrying until finished or file is closed
            return writeStream.fwrite(buffer);
    //        while (errno = 0, offset += (r = fwrite(RSTRING(str)->ptr+offset, 1, n, f)), (n -= r) > 0) {
    //            if (ferror(f)
    //            ) {
    //                if (rb_io_wait_writable(fileno(f))) {
    //                    rb_io_check_closed(fptr);
    //                    clearerr(f);
    //                    if (offset < RSTRING(str)->len)
    //                        continue;
    //                }
    //                return -1L;
    //            }
    //        }

//            return len - n;
        } catch (IOException ex) {
            throw getRuntime().newIOErrorFromException(ex);
        } catch (BadDescriptorException ex) {
            throw getRuntime().newErrnoEBADFError();
        }
    }

    /** rb_io_addstr
     * 
     */
    @JRubyMethod(name = "<<", required = 1)
    public IRubyObject op_append(ThreadContext context, IRubyObject anObject) {
        // Claims conversion is done via 'to_s' in docs.
        callMethod(context, "write", anObject);
        
        return this; 
    }

    @JRubyMethod(name = "fileno", alias = "to_i")
    public RubyFixnum fileno(ThreadContext context) {
        return context.getRuntime().newFixnum(getOpenFileChecked().getMainStream().getDescriptor().getFileno());
    }
    
    /** Returns the current line number.
     * 
     * @return the current line number.
     */
    @JRubyMethod(name = "lineno")
    public RubyFixnum lineno(ThreadContext context) {
        return context.getRuntime().newFixnum(getOpenFileChecked().getLineNumber());
    }

    /** Sets the current line number.
     * 
     * @param newLineNumber The new line number.
     */
    @JRubyMethod(name = "lineno=", required = 1)
    public RubyFixnum lineno_set(ThreadContext context, IRubyObject newLineNumber) {
        getOpenFileChecked().setLineNumber(RubyNumeric.fix2int(newLineNumber));

        return context.getRuntime().newFixnum(getOpenFileChecked().getLineNumber());
    }

    /** Returns the current sync mode.
     * 
     * @return the current sync mode.
     */
    @JRubyMethod(name = "sync")
    public RubyBoolean sync(ThreadContext context) {
        return context.getRuntime().newBoolean(getOpenFileChecked().getMainStream().isSync());
    }
    
    /**
     * <p>Return the process id (pid) of the process this IO object
     * spawned.  If no process exists (popen was not called), then
     * nil is returned.  This is not how it appears to be defined
     * but ruby 1.8 works this way.</p>
     * 
     * @return the pid or nil
     */
    @JRubyMethod(name = "pid")
    public IRubyObject pid(ThreadContext context) {
        OpenFile myOpenFile = getOpenFileChecked();
        
        if (myOpenFile.getProcess() == null) {
            return context.getRuntime().getNil();
        }
        
        // Of course this isn't particularly useful.
        int pid = myOpenFile.getProcess().hashCode();
        
        return context.getRuntime().newFixnum(pid); 
    }
    
    /**
     * @deprecated
     * @return
     */
    public boolean writeDataBuffered() {
        return openFile.getMainStream().writeDataBuffered();
    }
    
    @JRubyMethod(name = {"pos", "tell"})
    public RubyFixnum pos(ThreadContext context) {
        try {
            return context.getRuntime().newFixnum(getOpenFileChecked().getMainStream().fgetpos());
        } catch (InvalidValueException ex) {
            throw context.getRuntime().newErrnoEINVALError();
        } catch (BadDescriptorException bde) {
            throw context.getRuntime().newErrnoEBADFError();
        } catch (PipeException e) {
            throw context.getRuntime().newErrnoESPIPEError();
        } catch (IOException e) {
            throw context.getRuntime().newIOError(e.getMessage());
        }
    }
    
    @JRubyMethod(name = "pos=", required = 1)
    public RubyFixnum pos_set(ThreadContext context, IRubyObject newPosition) {
        long offset = RubyNumeric.num2long(newPosition);

        if (offset < 0) {
            throw context.getRuntime().newSystemCallError("Negative seek offset");
        }
        
        OpenFile myOpenFile = getOpenFileChecked();
        
        try {
            myOpenFile.getMainStream().lseek(offset, Stream.SEEK_SET);
        } catch (BadDescriptorException e) {
            throw context.getRuntime().newErrnoEBADFError();
        } catch (InvalidValueException e) {
            throw context.getRuntime().newErrnoEINVALError();
        } catch (PipeException e) {
            throw context.getRuntime().newErrnoESPIPEError();
        } catch (IOException e) {
            throw context.getRuntime().newIOError(e.getMessage());
        }
        
        myOpenFile.getMainStream().clearerr();
        
        return context.getRuntime().newFixnum(offset);
    }
    
    /** Print some objects to the stream.
     * 
     */
    @JRubyMethod(name = "print", rest = true, reads = FrameField.LASTLINE)
    public IRubyObject print(ThreadContext context, IRubyObject[] args) {
        if (args.length == 0) {
            args = new IRubyObject[] { context.getCurrentFrame().getLastLine() };
        }

        Ruby runtime = context.getRuntime();
        IRubyObject fs = runtime.getGlobalVariables().get("$,");
        IRubyObject rs = runtime.getGlobalVariables().get("$\\");
        
        for (int i = 0; i < args.length; i++) {
            if (i > 0 && !fs.isNil()) {
                callMethod(context, "write", fs);
            }
            if (args[i].isNil()) {
                callMethod(context, "write", runtime.newString("nil"));
            } else {
                callMethod(context, "write", args[i]);
            }
        }
        if (!rs.isNil()) {
            callMethod(context, "write", rs);
        }

        return runtime.getNil();
    }

    @JRubyMethod(name = "printf", required = 1, rest = true)
    public IRubyObject printf(ThreadContext context, IRubyObject[] args) {
        callMethod(context, "write", RubyKernel.sprintf(context, this, args));
        return context.getRuntime().getNil();
    }

    @JRubyMethod(name = "putc", required = 1, backtrace = true)
    public IRubyObject putc(ThreadContext context, IRubyObject object) {

        try {
            OpenFile myOpenFile = getOpenFileChecked();            
            myOpenFile.checkWritable(context.getRuntime());
            Stream writeStream = myOpenFile.getWriteStream();
            writeStream.fputc(RubyNumeric.num2chr(object));
            if (myOpenFile.isSync()) myOpenFile.fflush(writeStream);
        } catch (IOException ex) {
            throw context.getRuntime().newIOErrorFromException(ex);
        } catch (BadDescriptorException e) {
            throw context.getRuntime().newErrnoEBADFError();
        } catch (InvalidValueException ex) {
            throw context.getRuntime().newErrnoEINVALError();
        } catch (PipeException ex) {
            throw context.getRuntime().newErrnoEPIPEError();
        }

        return object;
    }

    public RubyFixnum seek(ThreadContext context, IRubyObject[] args) {
        long offset = RubyNumeric.num2long(args[0]);
        int whence = Stream.SEEK_SET;
        
        if (args.length > 1) {
            whence = RubyNumeric.fix2int(args[1].convertToInteger());
        }
        
        return doSeek(context, offset, whence);
    }

    @JRubyMethod(name = "seek")
    public RubyFixnum seek(ThreadContext context, IRubyObject arg0) {
        long offset = RubyNumeric.num2long(arg0);
        int whence = Stream.SEEK_SET;
        
        return doSeek(context, offset, whence);
    }

    @JRubyMethod(name = "seek")
    public RubyFixnum seek(ThreadContext context, IRubyObject arg0, IRubyObject arg1) {
        long offset = RubyNumeric.num2long(arg0);
        int whence = RubyNumeric.fix2int(arg1.convertToInteger());
        
        return doSeek(context, offset, whence);
    }
    
    private RubyFixnum doSeek(ThreadContext context, long offset, int whence) {
        OpenFile myOpenFile = getOpenFileChecked();
        
        try {
            myOpenFile.seek(offset, whence);
        } catch (BadDescriptorException ex) {
            throw context.getRuntime().newErrnoEBADFError();
        } catch (InvalidValueException e) {
            throw context.getRuntime().newErrnoEINVALError();
        } catch (PipeException e) {
            throw context.getRuntime().newErrnoESPIPEError();
        } catch (IOException e) {
            throw context.getRuntime().newIOError(e.getMessage());
        }
        
        myOpenFile.getMainStream().clearerr();
        
        return RubyFixnum.zero(context.getRuntime());
    }
    
    // This was a getOpt with one mandatory arg, but it did not work
    // so I am parsing it for now.
    @JRubyMethod(name = "sysseek", required = 1, optional = 1)
    public RubyFixnum sysseek(ThreadContext context, IRubyObject[] args) {
        long offset = RubyNumeric.num2long(args[0]);
        long pos;
        int whence = Stream.SEEK_SET;
        
        if (args.length > 1) {
            whence = RubyNumeric.fix2int(args[1].convertToInteger());
        }
        
        OpenFile myOpenFile = getOpenFileChecked();
        
        try {
            
            if (myOpenFile.isReadable() && myOpenFile.isReadBuffered()) {
                throw context.getRuntime().newIOError("sysseek for buffered IO");
            }
            if (myOpenFile.isWritable() && myOpenFile.isWriteBuffered()) {
                context.getRuntime().getWarnings().warn(ID.SYSSEEK_BUFFERED_IO, "sysseek for buffered IO");
            }
            
            pos = myOpenFile.getMainStream().getDescriptor().lseek(offset, whence);
        } catch (BadDescriptorException ex) {
            throw context.getRuntime().newErrnoEBADFError();
        } catch (InvalidValueException e) {
            throw context.getRuntime().newErrnoEINVALError();
        } catch (PipeException e) {
            throw context.getRuntime().newErrnoESPIPEError();
        } catch (IOException e) {
            throw context.getRuntime().newIOError(e.getMessage());
        }
        
        myOpenFile.getMainStream().clearerr();
        
        return context.getRuntime().newFixnum(pos);
    }

    @JRubyMethod(name = "rewind")
    public RubyFixnum rewind(ThreadContext context) {
        OpenFile myOpenfile = getOpenFileChecked();
        
        try {
            myOpenfile.getMainStream().lseek(0L, Stream.SEEK_SET);
            myOpenfile.getMainStream().clearerr();
            
            // TODO: This is some goofy global file value from MRI..what to do?
//            if (io == current_file) {
//                gets_lineno -= fptr->lineno;
//            }
        } catch (BadDescriptorException e) {
            throw context.getRuntime().newErrnoEBADFError();
        } catch (InvalidValueException e) {
            throw context.getRuntime().newErrnoEINVALError();
        } catch (PipeException e) {
            throw context.getRuntime().newErrnoESPIPEError();
        } catch (IOException e) {
            throw context.getRuntime().newIOError(e.getMessage());
        }

        // Must be back on first line on rewind.
        myOpenfile.setLineNumber(0);
        
        return RubyFixnum.zero(context.getRuntime());
    }
    
    @JRubyMethod(name = "fsync")
    public RubyFixnum fsync(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        
        try {
            OpenFile myOpenFile = getOpenFileChecked();
            
            myOpenFile.checkWritable(runtime);
        
            myOpenFile.getWriteStream().sync();
        } catch (InvalidValueException ex) {
            throw runtime.newErrnoEINVALError();
        } catch (PipeException ex) {
            throw runtime.newErrnoEPIPEError();
        } catch (IOException e) {
            throw runtime.newIOError(e.getMessage());
        } catch (BadDescriptorException e) {
            throw runtime.newErrnoEBADFError();
        }

        return RubyFixnum.zero(runtime);
    }

    /** Sets the current sync mode.
     * 
     * @param newSync The new sync mode.
     */
    @JRubyMethod(name = "sync=", required = 1)
    public IRubyObject sync_set(IRubyObject newSync) {
        getOpenFileChecked().setSync(newSync.isTrue());
        getOpenFileChecked().getMainStream().setSync(newSync.isTrue());

        return this;
    }

    @JRubyMethod(name = {"eof?", "eof"})
    public RubyBoolean eof_p(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        
        try {
            OpenFile myOpenFile = getOpenFileChecked();

            myOpenFile.checkReadable(runtime);
            myOpenFile.setReadBuffered();

            if (myOpenFile.getMainStream().feof()) {
                return runtime.getTrue();
            }
            
            if (myOpenFile.getMainStream().readDataBuffered()) {
                return runtime.getFalse();
            }
            
            readCheck(myOpenFile.getMainStream());
            
            myOpenFile.getMainStream().clearerr();
            
            int c = myOpenFile.getMainStream().fgetc();
            
            if (c != -1) {
                myOpenFile.getMainStream().ungetc(c);
                return runtime.getFalse();
            }
            
            myOpenFile.checkClosed(runtime);
            
            myOpenFile.getMainStream().clearerr();
            
            return runtime.getTrue();
        } catch (PipeException ex) {
            throw runtime.newErrnoEPIPEError();
        } catch (InvalidValueException ex) {
            throw runtime.newErrnoEINVALError();
        } catch (BadDescriptorException e) {
            throw runtime.newErrnoEBADFError();
        } catch (IOException e) {
            throw runtime.newIOError(e.getMessage());
        }
    }

    @JRubyMethod(name = {"tty?", "isatty"})
    public RubyBoolean tty_p(ThreadContext context) {
        return context.getRuntime().newBoolean(context.getRuntime().getPosix().isatty(getOpenFileChecked().getMainStream().getDescriptor().getFileDescriptor()));
    }
    
    @JRubyMethod(name = "initialize_copy", required = 1)
    @Override
    public IRubyObject initialize_copy(IRubyObject original){
        Ruby runtime = getRuntime();
        
        if (this == original) return this;

        RubyIO originalIO = (RubyIO) TypeConverter.convertToTypeWithCheck(original, runtime.getIO(), "to_io");
        
        OpenFile originalFile = originalIO.getOpenFileChecked();
        OpenFile newFile = openFile;
        
        try {
            // TODO: I didn't see where MRI has this check, but it seems to be the right place
            originalFile.checkClosed(runtime);
            
            if (originalFile.getPipeStream() != null) {
                originalFile.getPipeStream().fflush();
                originalFile.getMainStream().lseek(0, Stream.SEEK_CUR);
            } else if (originalFile.isWritable()) {
                originalFile.getMainStream().fflush();
            } else {
                originalFile.getMainStream().lseek(0, Stream.SEEK_CUR);
            }

            newFile.setMode(originalFile.getMode());
            newFile.setProcess(originalFile.getProcess());
            newFile.setLineNumber(originalFile.getLineNumber());
            newFile.setPath(originalFile.getPath());
            newFile.setFinalizer(originalFile.getFinalizer());
            
            ModeFlags modes;
            if (newFile.isReadable()) {
                if (newFile.isWritable()) {
                    if (newFile.getPipeStream() != null) {
                        modes = new ModeFlags(ModeFlags.RDONLY);
                    } else {
                        modes = new ModeFlags(ModeFlags.RDWR);
                    }
                } else {
                    modes = new ModeFlags(ModeFlags.RDONLY);
                }
            } else {
                if (newFile.isWritable()) {
                    modes = new ModeFlags(ModeFlags.WRONLY);
                } else {
                    modes = originalFile.getMainStream().getModes();
                }
            }
            
            ChannelDescriptor descriptor = originalFile.getMainStream().getDescriptor().dup();

            newFile.setMainStream(ChannelStream.fdopen(runtime, descriptor, modes));
            
            // TODO: the rest of this...seeking to same position is unnecessary since we share a channel
            // but some of this may be needed?
            
//    fseeko(fptr->f, ftello(orig->f), SEEK_SET);
//    if (orig->f2) {
//	if (fileno(orig->f) != fileno(orig->f2)) {
//	    fd = ruby_dup(fileno(orig->f2));
//	}
//	fptr->f2 = rb_fdopen(fd, "w");
//	fseeko(fptr->f2, ftello(orig->f2), SEEK_SET);
//    }
//    if (fptr->mode & FMODE_BINMODE) {
//	rb_io_binmode(dest);
//    }
            
            // Register the new descriptor
            registerDescriptor(newFile.getMainStream().getDescriptor());
        } catch (IOException ex) {
            throw runtime.newIOError("could not init copy: " + ex);
        } catch (BadDescriptorException ex) {
            throw runtime.newIOError("could not init copy: " + ex);
        } catch (PipeException ex) {
            throw runtime.newIOError("could not init copy: " + ex);
        } catch (InvalidValueException ex) {
            throw runtime.newIOError("could not init copy: " + ex);
        }
        
        return this;
    }
    
    /** Closes the IO.
     * 
     * @return The IO.
     */
    @JRubyMethod(name = "closed?")
    public RubyBoolean closed_p(ThreadContext context) {
        return context.getRuntime().newBoolean(openFile.getMainStream() == null && openFile.getPipeStream() == null);
    }

    /** 
     * <p>Closes all open resources for the IO.  It also removes
     * it from our magical all open file descriptor pool.</p>
     * 
     * @return The IO.
     */
    @JRubyMethod(name = "close")
    public IRubyObject close() {
        Ruby runtime = getRuntime();
        
        if (runtime.getSafeLevel() >= 4 && isTaint()) {
            throw runtime.newSecurityError("Insecure: can't close");
        }
        
        openFile.checkClosed(runtime);
        return close2(runtime);
    }
        
    protected IRubyObject close2(Ruby runtime) {
        if (openFile == null) return runtime.getNil();
        
        // These would be used when we notify threads...if we notify threads
        interruptBlockingThreads();
        
        ChannelDescriptor main, pipe;
        if (openFile.getPipeStream() != null) {
            pipe = openFile.getPipeStream().getDescriptor();
        } else {
            if (openFile.getMainStream() == null) {
                return runtime.getNil();
            }
            pipe = null;
        }
        
        main = openFile.getMainStream().getDescriptor();
        
        // cleanup, raising errors if any
        openFile.cleanup(runtime, true);
        
        // TODO: notify threads waiting on descriptors/IO? probably not...
        
        if (openFile.getProcess() != null) {
            try {
                IRubyObject processResult = RubyProcess.RubyStatus.newProcessStatus(runtime, openFile.getProcess().waitFor());
                runtime.getGlobalVariables().set("$?", processResult);
            } catch (InterruptedException ie) {
                // TODO: do something here?
            }
        }
        
        return runtime.getNil();
    }

    @JRubyMethod(name = "close_write")
    public IRubyObject close_write(ThreadContext context) throws BadDescriptorException {
        try {
            if (context.getRuntime().getSafeLevel() >= 4 && isTaint()) {
                throw context.getRuntime().newSecurityError("Insecure: can't close");
            }
            
            OpenFile myOpenFile = getOpenFileChecked();
            
            if (myOpenFile.getPipeStream() == null && myOpenFile.isReadable()) {
                throw context.getRuntime().newIOError("closing non-duplex IO for writing");
            }
            
            if (myOpenFile.getPipeStream() == null) {
                close();
            } else{
                myOpenFile.getPipeStream().fclose();
                myOpenFile.setPipeStream(null);
                myOpenFile.setMode(myOpenFile.getMode() & ~OpenFile.WRITABLE);
                // TODO
                // n is result of fclose; but perhaps having a SysError below is enough?
                // if (n != 0) rb_sys_fail(fptr->path);
            }
        } catch (IOException ioe) {
            // hmmmm
        }
        return this;
    }

    @JRubyMethod(name = "close_read")
    public IRubyObject close_read(ThreadContext context) throws BadDescriptorException {
        Ruby runtime = context.getRuntime();
        
        try {
            if (runtime.getSafeLevel() >= 4 && isTaint()) {
                throw runtime.newSecurityError("Insecure: can't close");
            }
            
            OpenFile myOpenFile = getOpenFileChecked();
            
            if (myOpenFile.getPipeStream() == null && myOpenFile.isWritable()) {
                throw runtime.newIOError("closing non-duplex IO for reading");
            }
            
            if (myOpenFile.getPipeStream() == null) {
                close();
            } else{
                myOpenFile.getMainStream().fclose();
                myOpenFile.setMode(myOpenFile.getMode() & ~OpenFile.READABLE);
                myOpenFile.setMainStream(myOpenFile.getPipeStream());
                myOpenFile.setPipeStream(null);
                // TODO
                // n is result of fclose; but perhaps having a SysError below is enough?
                // if (n != 0) rb_sys_fail(fptr->path);
            }
        } catch (IOException ioe) {
            // I believe Ruby bails out with a "bug" if closing fails
            throw runtime.newIOErrorFromException(ioe);
        }
        return this;
    }

    /** Flushes the IO output stream.
     * 
     * @return The IO.
     */
    @JRubyMethod(name = "flush")
    public RubyIO flush() {
        try { 
            getOpenFileChecked().getWriteStream().fflush();
        } catch (BadDescriptorException e) {
            throw getRuntime().newErrnoEBADFError();
        } catch (IOException e) {
            throw getRuntime().newIOError(e.getMessage());
        }

        return this;
    }

    /** Read a line.
     * 
     */
    @JRubyMethod(name = "gets", optional = 1, writes = FrameField.LASTLINE)
    public IRubyObject gets(ThreadContext context, IRubyObject[] args) {
        Ruby runtime = context.getRuntime();
        ByteList separator = getSeparatorForGets(runtime, args);
        
        IRubyObject result = getline(runtime, separator);

        if (!result.isNil()) context.getCurrentFrame().setLastLine(result);

        return result;
    }

    public boolean getBlocking() {
        return ((ChannelStream) openFile.getMainStream()).isBlocking();
    }

    @JRubyMethod(name = "fcntl", required = 2)
    public IRubyObject fcntl(ThreadContext context, IRubyObject cmd, IRubyObject arg) {
        // TODO: This version differs from ioctl by checking whether fcntl exists
        // and raising notimplemented if it doesn't; perhaps no difference for us?
        return ctl(context.getRuntime(), cmd, arg);
    }

    @JRubyMethod(name = "ioctl", required = 1, optional = 1)
    public IRubyObject ioctl(ThreadContext context, IRubyObject[] args) {
        IRubyObject cmd = args[0];
        IRubyObject arg;
        
        if (args.length == 2) {
            arg = args[1];
        } else {
            arg = context.getRuntime().getNil();
        }
        
        return ctl(context.getRuntime(), cmd, arg);
    }

    public IRubyObject ctl(Ruby runtime, IRubyObject cmd, IRubyObject arg) {
        long realCmd = cmd.convertToInteger().getLongValue();
        long nArg = 0;
        
        // FIXME: Arg may also be true, false, and nil and still be valid.  Strangely enough, 
        // protocol conversion is not happening in Ruby on this arg?
        if (arg.isNil() || arg == runtime.getFalse()) {
            nArg = 0;
        } else if (arg instanceof RubyFixnum) {
            nArg = RubyFixnum.fix2long(arg);
        } else if (arg == runtime.getTrue()) {
            nArg = 1;
        } else {
            throw runtime.newNotImplementedError("JRuby does not support string for second fcntl/ioctl argument yet");
        }
        
        OpenFile myOpenFile = getOpenFileChecked();

        // Fixme: Only F_SETFL is current supported
        if (realCmd == 1L) {  // cmd is F_SETFL
            boolean block = true;
            
            if ((nArg & ModeFlags.NONBLOCK) == ModeFlags.NONBLOCK) {
                block = false;
            }

            try {
                myOpenFile.getMainStream().setBlocking(block);
            } catch (IOException e) {
                throw runtime.newIOError(e.getMessage());
            }
        } else {
            throw runtime.newNotImplementedError("JRuby only supports F_SETFL for fcntl/ioctl currently");
        }
        
        return runtime.newFixnum(0);
    }
    
    private static final ByteList NIL_BYTELIST = ByteList.create("nil");
    private static final ByteList RECURSIVE_BYTELIST = ByteList.create("[...]");

    @JRubyMethod(name = "puts", rest = true)
    public IRubyObject puts(ThreadContext context, IRubyObject[] args) {
        Ruby runtime = context.getRuntime();
        assert runtime.getGlobalVariables().getDefaultSeparator() instanceof RubyString;
        RubyString separator = (RubyString) runtime.getGlobalVariables().getDefaultSeparator();
        
        if (args.length == 0) {
            write(context, separator.getByteList());
            return runtime.getNil();
        }

        for (int i = 0; i < args.length; i++) {
            ByteList line;
            
            if (args[i].isNil()) {
                line = NIL_BYTELIST;
            } else if (runtime.isInspecting(args[i])) {
                line = RECURSIVE_BYTELIST;
            } else if (args[i] instanceof RubyArray) {
                inspectPuts(context, (RubyArray) args[i]);
                continue;
            } else {
                line = args[i].asString().getByteList();
            }
            
            write(context, line);
            
            if (line.length() == 0 || !line.endsWith(separator.getByteList())) {
                write(context, separator.getByteList());
            }
        }
        return runtime.getNil();
    }

    protected void write(ThreadContext context, ByteList byteList) {
        callMethod(context, "write", RubyString.newStringShared(context.getRuntime(), byteList));
    }

    private IRubyObject inspectPuts(ThreadContext context, RubyArray array) {
        try {
            context.getRuntime().registerInspecting(array);
            return puts(context, array.toJavaArray());
        } finally {
            context.getRuntime().unregisterInspecting(array);
        }
    }

    /** Read a line.
     * 
     */
    @JRubyMethod(name = "readline", optional = 1, writes = FrameField.LASTLINE)
    public IRubyObject readline(ThreadContext context, IRubyObject[] args) {
        IRubyObject line = gets(context, args);

        if (line.isNil()) throw context.getRuntime().newEOFError();
        
        return line;
    }

    /** Read a byte. On EOF returns nil.
     * 
     */
    @JRubyMethod(name = "getc")
    public IRubyObject getc() {
        try {
            OpenFile myOpenFile = getOpenFileChecked();

            myOpenFile.checkReadable(getRuntime());
            myOpenFile.setReadBuffered();

            Stream stream = myOpenFile.getMainStream();
            
            readCheck(stream);
            stream.clearerr();
        
            int c = myOpenFile.getMainStream().fgetc();
            
            if (c == -1) {
                // TODO: check for ferror, clear it, and try once more up above readCheck
//                if (ferror(f)) {
//                    clearerr(f);
//                    if (!rb_io_wait_readable(fileno(f)))
//                        rb_sys_fail(fptr->path);
//                    goto retry;
//                }
                return getRuntime().getNil();
            }
        
            return getRuntime().newFixnum(c);
        } catch (PipeException ex) {
            throw getRuntime().newErrnoEPIPEError();
        } catch (InvalidValueException ex) {
            throw getRuntime().newErrnoEINVALError();
        } catch (BadDescriptorException e) {
            throw getRuntime().newErrnoEBADFError();
        } catch (EOFException e) {
            throw getRuntime().newEOFError();
        } catch (IOException e) {
            throw getRuntime().newIOError(e.getMessage());
        }
    }
    
    private void readCheck(Stream stream) {
        if (!stream.readDataBuffered()) {
            openFile.checkClosed(getRuntime());
        }
    }
    
    /** 
     * <p>Pushes char represented by int back onto IOS.</p>
     * 
     * @param number to push back
     */
    @JRubyMethod(name = "ungetc", required = 1)
    public IRubyObject ungetc(IRubyObject number) {
        int ch = RubyNumeric.fix2int(number);
        
        OpenFile myOpenFile = getOpenFileChecked();
        
        if (!myOpenFile.isReadBuffered()) {
            throw getRuntime().newIOError("unread stream");
        }
        
        try {
            myOpenFile.checkReadable(getRuntime());
            myOpenFile.setReadBuffered();

            if (myOpenFile.getMainStream().ungetc(ch) == -1 && ch != -1) {
                throw getRuntime().newIOError("ungetc failed");
            }
        } catch (PipeException ex) {
            throw getRuntime().newErrnoEPIPEError();
        } catch (InvalidValueException ex) {
            throw getRuntime().newErrnoEINVALError();
        } catch (BadDescriptorException e) {
            throw getRuntime().newErrnoEBADFError();
        } catch (EOFException e) {
            throw getRuntime().newEOFError();
        } catch (IOException e) {
            throw getRuntime().newIOError(e.getMessage());
        }

        return getRuntime().getNil();
    }
    
    @JRubyMethod(name = "read_nonblock", required = 1, optional = 1)
    public IRubyObject read_nonblock(ThreadContext context, IRubyObject[] args) {
        Ruby runtime = context.getRuntime();

        openFile.checkClosed(runtime);

        if(!(openFile.getMainStream() instanceof ChannelStream)) {
            // cryptic for the uninitiated...
            throw runtime.newNotImplementedError("read_nonblock only works with Nio based handlers");
        }
        try {
            int maxLength = RubyNumeric.fix2int(args[0]);
            if (maxLength < 0) {
                throw runtime.newArgumentError("negative length " + maxLength + " given");
            }
            ByteList buf = ((ChannelStream)openFile.getMainStream()).readnonblock(RubyNumeric.fix2int(args[0]));
            IRubyObject strbuf = RubyString.newString(runtime, buf == null ? new ByteList(ByteList.NULL_ARRAY) : buf);
            if(args.length > 1) {
                args[1].callMethod(context, "<<", strbuf);
                return args[1];
            }

            return strbuf;
        } catch (BadDescriptorException e) {
            throw runtime.newErrnoEBADFError();
        } catch (EOFException e) {
            return runtime.getNil();
        } catch (IOException e) {
            throw runtime.newIOError(e.getMessage());
        }
    }
    
    @JRubyMethod(name = "readpartial", required = 1, optional = 1)
    public IRubyObject readpartial(ThreadContext context, IRubyObject[] args) {
        Ruby runtime = context.getRuntime();

        openFile.checkClosed(runtime);

        if(!(openFile.getMainStream() instanceof ChannelStream)) {
            // cryptic for the uninitiated...
            throw runtime.newNotImplementedError("readpartial only works with Nio based handlers");
        }
        try {
            int maxLength = RubyNumeric.fix2int(args[0]);
            if (maxLength < 0) {
                throw runtime.newArgumentError("negative length " + maxLength + " given");
            }
            ByteList buf = ((ChannelStream)openFile.getMainStream()).readpartial(RubyNumeric.fix2int(args[0]));
            IRubyObject strbuf = RubyString.newString(runtime, buf == null ? new ByteList(ByteList.NULL_ARRAY) : buf);
            if(args.length > 1) {
                args[1].callMethod(context, "<<", strbuf);
                return args[1];
            }

            return strbuf;
        } catch (BadDescriptorException e) {
            throw runtime.newErrnoEBADFError();
        } catch (EOFException e) {
            return runtime.getNil();
        } catch (IOException e) {
            throw runtime.newIOError(e.getMessage());
        }
    }

    @JRubyMethod(name = "sysread", required = 1, optional = 1)
    public IRubyObject sysread(ThreadContext context, IRubyObject[] args) {
        int len = (int)RubyNumeric.num2long(args[0]);
        if (len < 0) throw getRuntime().newArgumentError("Negative size");

        try {
            RubyString str;
            ByteList buffer;
            if (args.length == 1 || args[1].isNil()) {
                if (len == 0) {
                    return RubyString.newEmptyString(getRuntime());
                }
                
                buffer = new ByteList(len);
                str = RubyString.newString(getRuntime(), buffer);
            } else {
                str = args[1].convertToString();
                str.modify(len);
                
                if (len == 0) {
                    return str;
                }
                
                buffer = str.getByteList();
                buffer.length(0);
            }
            
            OpenFile myOpenFile = getOpenFileChecked();
            
            myOpenFile.checkReadable(getRuntime());
            
            if (myOpenFile.getMainStream().readDataBuffered()) {
                throw getRuntime().newIOError("sysread for buffered IO");
            }
            
            // TODO: Ruby locks the string here
            
            context.getThread().beforeBlockingCall();
            myOpenFile.checkClosed(getRuntime());
            
            // TODO: Ruby re-checks that the buffer string hasn't been modified
            
            int bytesRead = myOpenFile.getMainStream().getDescriptor().read(len, str.getByteList());
            
            // TODO: Ruby unlocks the string here
            
            // TODO: Ruby truncates string to specific size here, but our bytelist should handle this already?
            
            if (bytesRead == -1 || (bytesRead == 0 && len > 0)) {
                throw getRuntime().newEOFError();
            }
            
            str.setTaint(true);
            
            return str;
        } catch (BadDescriptorException e) {
            throw getRuntime().newErrnoEBADFError();
        } catch (InvalidValueException e) {
            throw getRuntime().newErrnoEINVALError();
        } catch (PipeException e) {
            throw getRuntime().newErrnoEPIPEError();
        } catch (EOFException e) {
            throw getRuntime().newEOFError();
    	} catch (IOException e) {
            // All errors to sysread should be SystemCallErrors, but on a closed stream
            // Ruby returns an IOError.  Java throws same exception for all errors so
            // we resort to this hack...
            if ("File not open".equals(e.getMessage())) {
                    throw getRuntime().newIOError(e.getMessage());
            }
    	    throw getRuntime().newSystemCallError(e.getMessage());
    	} finally {
            context.getThread().afterBlockingCall();
        }
    }
    
    public IRubyObject read(IRubyObject[] args) {
        ThreadContext context = getRuntime().getCurrentContext();
        
        switch (args.length) {
        case 0: return read(context);
        case 1: return read(context, args[0]);
        case 2: return read(context, args[0], args[1]);
        default: throw getRuntime().newArgumentError(args.length, 2);
        }
    }
    
    @JRubyMethod(name = "read")
    public IRubyObject read(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        OpenFile myOpenFile = getOpenFileChecked();
        
        try {
            myOpenFile.checkReadable(runtime);
            myOpenFile.setReadBuffered();

            return readAll(getRuntime().getNil());
        } catch (PipeException ex) {
            throw getRuntime().newErrnoEPIPEError();
        } catch (InvalidValueException ex) {
            throw getRuntime().newErrnoEINVALError();
        } catch (EOFException ex) {
            throw getRuntime().newEOFError();
        } catch (IOException ex) {
            throw getRuntime().newIOErrorFromException(ex);
        } catch (BadDescriptorException ex) {
            throw getRuntime().newErrnoEBADFError();
        }
    }
    
    @JRubyMethod(name = "read")
    public IRubyObject read(ThreadContext context, IRubyObject arg0) {
        if (arg0.isNil()) {
            return read(context);
        }
        
        OpenFile myOpenFile = getOpenFileChecked();
        
        int length = RubyNumeric.num2int(arg0);
        
        if (length < 0) {
            throw getRuntime().newArgumentError("negative length " + length + " given");
        }
        
        RubyString str = null;

        return readNotAll(context, myOpenFile, length, str);
    }
    
    @JRubyMethod(name = "read")
    public IRubyObject read(ThreadContext context, IRubyObject arg0, IRubyObject arg1) {
        OpenFile myOpenFile = getOpenFileChecked();
        
        if (arg0.isNil()) {
            try {
                myOpenFile.checkReadable(getRuntime());
                myOpenFile.setReadBuffered();

                return readAll(arg1);
            } catch (PipeException ex) {
                throw getRuntime().newErrnoEPIPEError();
            } catch (InvalidValueException ex) {
                throw getRuntime().newErrnoEINVALError();
            } catch (EOFException ex) {
                throw getRuntime().newEOFError();
            } catch (IOException ex) {
                throw getRuntime().newIOErrorFromException(ex);
            } catch (BadDescriptorException ex) {
                throw getRuntime().newErrnoEBADFError();
            }
        }
        
        int length = RubyNumeric.num2int(arg0);
        
        if (length < 0) {
            throw getRuntime().newArgumentError("negative length " + length + " given");
        }
        
        RubyString str = null;
//        ByteList buffer = null;
        if (arg1.isNil()) {
//            buffer = new ByteList(length);
//            str = RubyString.newString(getRuntime(), buffer);
        } else {
            str = arg1.convertToString();
            str.modify(length);

            if (length == 0) {
                return str;
            }

//            buffer = str.getByteList();
        }
        
        return readNotAll(context, myOpenFile, length, str);
    }
    
    private IRubyObject readNotAll(ThreadContext context, OpenFile myOpenFile, int length, RubyString str) {
        Ruby runtime = context.getRuntime();
        
        try {
            myOpenFile.checkReadable(runtime);
            myOpenFile.setReadBuffered();

            if (myOpenFile.getMainStream().feof()) {
                return runtime.getNil();
            }

            // TODO: Ruby locks the string here

            // READ_CHECK from MRI io.c
            readCheck(myOpenFile.getMainStream());

            // TODO: check buffer length again?
    //        if (RSTRING(str)->len != len) {
    //            rb_raise(rb_eRuntimeError, "buffer string modified");
    //        }

            // TODO: read into buffer using all the fread logic
    //        int read = openFile.getMainStream().fread(buffer);
            ByteList newBuffer = myOpenFile.getMainStream().fread(length);

            // TODO: Ruby unlocks the string here

            // TODO: change this to check number read into buffer once that's working
    //        if (read == 0) {
            
            if (newBuffer == null || newBuffer.length() == 0) {
                if (myOpenFile.getMainStream() == null) {
                    return runtime.getNil();
                }

                if (myOpenFile.getMainStream().feof()) {
                    // truncate buffer string to zero, if provided
                    if (str != null) {
                        str.setValue(ByteList.EMPTY_BYTELIST.dup());
                    }
                
                    return runtime.getNil();
                }

                // Removed while working on JRUBY-2386, since fixes for that
                // modified EOF logic such that this check is not really valid.
                // We expect that an EOFException will be thrown now in EOF
                // cases.
//                if (length > 0) {
//                    // I think this is only partly correct; sys fail based on errno in Ruby
//                    throw getRuntime().newEOFError();
//                }
            }


            // TODO: Ruby truncates string to specific size here, but our bytelist should handle this already?

            // FIXME: I don't like the null checks here
            if (str == null) {
                if (newBuffer == null) {
                    str = RubyString.newEmptyString(runtime);
                } else {
                    str = RubyString.newString(runtime, newBuffer);
                }
            } else {
                if (newBuffer == null) {
                    str.empty();
                } else {
                    str.setValue(newBuffer);
                }
            }
            str.setTaint(true);

            return str;
        } catch (EOFException ex) {
            throw runtime.newEOFError();
        } catch (PipeException ex) {
            throw runtime.newErrnoEPIPEError();
        } catch (InvalidValueException ex) {
            throw runtime.newErrnoEINVALError();
        } catch (IOException ex) {
            throw runtime.newIOErrorFromException(ex);
        } catch (BadDescriptorException ex) {
            throw runtime.newErrnoEBADFError();
        }
    }
    
    protected IRubyObject readAll(IRubyObject buffer) throws BadDescriptorException, EOFException, IOException {
        Ruby runtime = getRuntime();
        // TODO: handle writing into original buffer better
        
        RubyString str = null;
        if (buffer instanceof RubyString) {
            str = (RubyString)buffer;
        }
        
        // TODO: ruby locks the string here
        
        // READ_CHECK from MRI io.c
        if (openFile.getMainStream().readDataBuffered()) {
            openFile.checkClosed(runtime);
        }
        
        ByteList newBuffer = openFile.getMainStream().readall();

        // TODO same zero-length checks as file above

        if (str == null) {
            if (newBuffer == null) {
                str = RubyString.newEmptyString(runtime);
            } else {
                str = RubyString.newString(runtime, newBuffer);
            }
        } else {
            if (newBuffer == null) {
                str.empty();
            } else {
                str.setValue(newBuffer);
            }
        }

        str.taint(runtime.getCurrentContext());

        return str;
//        long bytes = 0;
//        long n;
//
//        if (siz == 0) siz = BUFSIZ;
//        if (NIL_P(str)) {
//            str = rb_str_new(0, siz);
//        }
//        else {
//            rb_str_resize(str, siz);
//        }
//        for (;;) {
//            rb_str_locktmp(str);
//            READ_CHECK(fptr->f);
//            n = io_fread(RSTRING(str)->ptr+bytes, siz-bytes, fptr);
//            rb_str_unlocktmp(str);
//            if (n == 0 && bytes == 0) {
//                if (!fptr->f) break;
//                if (feof(fptr->f)) break;
//                if (!ferror(fptr->f)) break;
//                rb_sys_fail(fptr->path);
//            }
//            bytes += n;
//            if (bytes < siz) break;
//            siz += BUFSIZ;
//            rb_str_resize(str, siz);
//        }
//        if (bytes != siz) rb_str_resize(str, bytes);
//        OBJ_TAINT(str);
//
//        return str;
    }
    
    // TODO: There's a lot of complexity here due to error handling and
    // nonblocking IO; much of this goes away, but for now I'm just
    // having read call ChannelStream.fread directly.
//    protected int fread(int len, ByteList buffer) {
//        long n = len;
//        int c;
//        int saved_errno;
//
//        while (n > 0) {
//            c = read_buffered_data(ptr, n, fptr->f);
//            if (c < 0) goto eof;
//            if (c > 0) {
//                ptr += c;
//                if ((n -= c) <= 0) break;
//            }
//            rb_thread_wait_fd(fileno(fptr->f));
//            rb_io_check_closed(fptr);
//            clearerr(fptr->f);
//            TRAP_BEG;
//            c = getc(fptr->f);
//            TRAP_END;
//            if (c == EOF) {
//              eof:
//                if (ferror(fptr->f)) {
//                    switch (errno) {
//                      case EINTR:
//    #if defined(ERESTART)
//                      case ERESTART:
//    #endif
//                        clearerr(fptr->f);
//                        continue;
//                      case EAGAIN:
//    #if defined(EWOULDBLOCK) && EWOULDBLOCK != EAGAIN
//                      case EWOULDBLOCK:
//    #endif
//                        if (len > n) {
//                            clearerr(fptr->f);
//                        }
//                        saved_errno = errno;
//                        rb_warning("nonblocking IO#read is obsolete; use IO#readpartial or IO#sysread");
//                        errno = saved_errno;
//                    }
//                    if (len == n) return 0;
//                }
//                break;
//            }
//            *ptr++ = c;
//            n--;
//        }
//        return len - n;
//        
//    }

    /** Read a byte. On EOF throw EOFError.
     * 
     */
    @JRubyMethod(name = "readchar")
    public IRubyObject readchar() {
        IRubyObject c = getc();
        
        if (c.isNil()) throw getRuntime().newEOFError();
        
        return c;
    }
    
    @JRubyMethod
    public IRubyObject stat(ThreadContext context) {
        openFile.checkClosed(context.getRuntime());
        return context.getRuntime().newFileStat(getOpenFileChecked().getMainStream().getDescriptor().getFileDescriptor());
    }

    /** 
     * <p>Invoke a block for each byte.</p>
     */
    @JRubyMethod(name = "each_byte", frame = true)
    public IRubyObject each_byte(ThreadContext context, Block block) {
        Ruby runtime = context.getRuntime();
        
    	try {
            OpenFile myOpenFile = getOpenFileChecked();
            
            while (true) {
                myOpenFile.checkReadable(runtime);
                myOpenFile.setReadBuffered();

                // TODO: READ_CHECK from MRI
                
                int c = myOpenFile.getMainStream().fgetc();
                
                if (c == -1) {
                    // TODO: check for error, clear it, and wait until readable before trying once more
//                    if (ferror(f)) {
//                        clearerr(f);
//                        if (!rb_io_wait_readable(fileno(f)))
//                            rb_sys_fail(fptr->path);
//                        continue;
//                    }
                    break;
                }
                
                assert c < 256;
                block.yield(context, getRuntime().newFixnum(c));
            }

            // TODO: one more check for error
//            if (ferror(f)) rb_sys_fail(fptr->path);
            return this;
        } catch (PipeException ex) {
            throw runtime.newErrnoEPIPEError();
        } catch (InvalidValueException ex) {
            throw runtime.newErrnoEINVALError();
        } catch (BadDescriptorException e) {
            throw runtime.newErrnoEBADFError();
        } catch (EOFException e) {
            return runtime.getNil();
    	} catch (IOException e) {
    	    throw runtime.newIOError(e.getMessage());
        }
    }

    @JRubyMethod(name = "each_byte", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject each_byte19(final ThreadContext context, final Block block) {
        return block.isGiven() ? each_byte(context, block) : enumeratorize(context.getRuntime(), this, "each_byte");
    }

    /** 
     * <p>Invoke a block for each line.</p>
     */
    @JRubyMethod(name = {"each_line", "each"}, optional = 1, frame = true)
    public RubyIO each_line(ThreadContext context, IRubyObject[] args, Block block) {
        Ruby runtime = context.getRuntime();
        ByteList separator = getSeparatorForGets(runtime, args);
        
        for (IRubyObject line = getline(runtime, separator); !line.isNil(); 
        	line = getline(runtime, separator)) {
            block.yield(context, line);
        }
        
        return this;
    }

    @JRubyMethod(name = "each", optional = 1, frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject each19(final ThreadContext context, IRubyObject[]args, final Block block) {
        return block.isGiven() ? each_line(context, args, block) : enumeratorize(context.getRuntime(), this, "each", args);
    }

    @JRubyMethod(name = "each_line", optional = 1, frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject each_line19(final ThreadContext context, IRubyObject[]args, final Block block) {
        return block.isGiven() ? each_line(context, args, block) : enumeratorize(context.getRuntime(), this, "each_line", args);
    }

    @JRubyMethod(name = "readlines", optional = 1)
    public RubyArray readlines(ThreadContext context, IRubyObject[] args) {
        Ruby runtime = context.getRuntime();
        IRubyObject[] separatorArgs = args.length > 0 ? new IRubyObject[] { args[0] } : IRubyObject.NULL_ARRAY;
        ByteList separator = getSeparatorForGets(runtime, separatorArgs);
        RubyArray result = runtime.newArray();
        IRubyObject line;
        
        while (! (line = getline(runtime, separator)).isNil()) {
            result.append(line);
        }
        return result;
    }
    
    @JRubyMethod(name = "to_io")
    public RubyIO to_io() {
    	return this;
    }

    @Override
    public String toString() {
        return "RubyIO(" + openFile.getMode() + ", " + openFile.getMainStream().getDescriptor().getFileno() + ")";
    }
    
    /* class methods for IO */
    
    /** rb_io_s_foreach
    *
    */
    @JRubyMethod(name = "foreach", required = 1, optional = 1, frame = true, meta = true)
    public static IRubyObject foreach(ThreadContext context, IRubyObject recv, IRubyObject[] args, Block block) {
        Ruby runtime = context.getRuntime();
        int count = args.length;
        IRubyObject filename = args[0].convertToString();
        runtime.checkSafeString(filename);
       
        ByteList separator = getSeparatorFromArgs(runtime, args, 1);

        RubyIO io = (RubyIO)RubyFile.open(context, runtime.getFile(), new IRubyObject[] { filename }, Block.NULL_BLOCK);
        
        if (!io.isNil()) {
            try {
                IRubyObject str = io.getline(runtime, separator);
                while (!str.isNil()) {
                    block.yield(context, str);
                    str = io.getline(runtime, separator);
                }
            } finally {
                io.close();
            }
        }
       
        return runtime.getNil();
    }
    
    @JRubyMethod(name = "foreach", required = 1, optional = 1, frame = true, meta = true, compat = CompatVersion.RUBY1_9)
    public static IRubyObject foreach19(final ThreadContext context, IRubyObject recv, IRubyObject[] args, final Block block) {
        return block.isGiven() ? foreach(context, recv, args, block) : enumeratorize(context.getRuntime(), recv, "foreach", args);
    }

    private static RubyIO convertToIO(ThreadContext context, IRubyObject obj) {
        return (RubyIO)TypeConverter.convertToType(obj, context.getRuntime().getIO(), "to_io");
    }
   
    private static boolean registerSelect(ThreadContext context, Selector selector, IRubyObject obj, RubyIO ioObj, int ops) throws IOException {
       Channel channel = ioObj.getChannel();
       if (channel == null || !(channel instanceof SelectableChannel)) {
           return false;
       }
       
       ((SelectableChannel) channel).configureBlocking(false);
       int real_ops = ((SelectableChannel) channel).validOps() & ops;
       SelectionKey key = ((SelectableChannel) channel).keyFor(selector);
       
       if (key == null) {
           ((SelectableChannel) channel).register(selector, real_ops, obj);
       } else {
           key.interestOps(key.interestOps()|real_ops);
       }
       
       return true;
    }
   
    @JRubyMethod(name = "select", required = 1, optional = 3, meta = true)
    public static IRubyObject select(ThreadContext context, IRubyObject recv, IRubyObject[] args) {
        return select_static(context, context.getRuntime(), args);
    }

    private static void checkArrayType(Ruby runtime, IRubyObject obj) {
        if (!(obj instanceof RubyArray)) {
            throw runtime.newTypeError("wrong argument type "
                    + obj.getMetaClass().getName() + " (expected Array)");
        }
    }

    public static IRubyObject select_static(ThreadContext context, Ruby runtime, IRubyObject[] args) {
       try {
           Set pending = new HashSet();
           Set unselectable_reads = new HashSet();
           Set unselectable_writes = new HashSet();
           Selector selector = Selector.open();
           if (!args[0].isNil()) {
               // read
               checkArrayType(runtime, args[0]);
               for (Iterator i = ((RubyArray) args[0]).getList().iterator(); i.hasNext(); ) {
                   IRubyObject obj = (IRubyObject) i.next();
                   RubyIO ioObj = convertToIO(context, obj);
                   if (registerSelect(context, selector, obj, ioObj, SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) {
                       if (ioObj.writeDataBuffered()) {
                           pending.add(obj);
                       }
                   } else {
                       if (( ioObj.openFile.getMode() & OpenFile.READABLE ) != 0) {
                           unselectable_reads.add(obj);
                       }
                   }
               }
           }

           if (args.length > 1 && !args[1].isNil()) {
               // write
               checkArrayType(runtime, args[1]);
               for (Iterator i = ((RubyArray) args[1]).getList().iterator(); i.hasNext(); ) {
                   IRubyObject obj = (IRubyObject) i.next();
                   RubyIO ioObj = convertToIO(context, obj);
                   if (!registerSelect(context, selector, obj, ioObj, SelectionKey.OP_WRITE)) {
                       if (( ioObj.openFile.getMode() & OpenFile.WRITABLE ) != 0) {
                           unselectable_writes.add(obj);
                       }
                   }
               }
           }

           if (args.length > 2 && !args[2].isNil()) {
               checkArrayType(runtime, args[2]);
               // Java's select doesn't do anything about this, so we leave it be.
           }

           final boolean has_timeout = ( args.length > 3 && !args[3].isNil() );
           long timeout = 0;
           if(has_timeout) {
               IRubyObject timeArg = args[3];
               if (timeArg instanceof RubyFloat) {
                   timeout = Math.round(((RubyFloat) timeArg).getDoubleValue() * 1000);
               } else if (timeArg instanceof RubyFixnum) {
                   timeout = Math.round(((RubyFixnum) timeArg).getDoubleValue() * 1000);
               } else { // TODO: MRI also can hadle Bignum here
                   throw runtime.newTypeError("can't convert "
                           + timeArg.getMetaClass().getName() + " into time interval");
               }

               if (timeout < 0) {
                   throw runtime.newArgumentError("negative timeout given");
               }
           }
           
           if (pending.isEmpty() && unselectable_reads.isEmpty() && unselectable_writes.isEmpty()) {
               if (has_timeout) {
                   if (timeout==0) {
                       selector.selectNow();
                   } else {
                       selector.select(timeout);                       
                   }
               } else {
                   selector.select();
               }
           } else {
               selector.selectNow();               
           }
           
           List r = new ArrayList();
           List w = new ArrayList();
           List e = new ArrayList();
           for (Iterator i = selector.selectedKeys().iterator(); i.hasNext(); ) {
               SelectionKey key = (SelectionKey) i.next();
               try {
                   int interestAndReady = key.interestOps() & key.readyOps();
                   if ((interestAndReady
                           & (SelectionKey.OP_READ|SelectionKey.OP_ACCEPT|SelectionKey.OP_CONNECT)) != 0) {
                       r.add(key.attachment());
                       pending.remove(key.attachment());
                   }
                   if ((interestAndReady & (SelectionKey.OP_WRITE)) != 0) {
                       w.add(key.attachment());
                   }
               } catch (CancelledKeyException cke) {
                   // TODO: is this the right thing to do?
                   pending.remove(key.attachment());
                   e.add(key.attachment());
               }
           }
           r.addAll(pending);
           r.addAll(unselectable_reads);
           w.addAll(unselectable_writes);
           
           // make all sockets blocking as configured again
           for (Iterator i = selector.keys().iterator(); i.hasNext(); ) {
               SelectionKey key = (SelectionKey) i.next();
               SelectableChannel channel = key.channel();
               synchronized(channel.blockingLock()) {
                   RubyIO originalIO = (RubyIO) TypeConverter.convertToType(
                           (IRubyObject) key.attachment(), runtime.getIO(), "to_io");
                   boolean blocking = originalIO.getBlocking();
                   key.cancel();
                   channel.configureBlocking(blocking);
               }
           }
           selector.close();
           
           if (r.size() == 0 && w.size() == 0 && e.size() == 0) {
               return runtime.getNil();
           }
           
           List ret = new ArrayList();
           
           ret.add(RubyArray.newArray(runtime, r));
           ret.add(RubyArray.newArray(runtime, w));
           ret.add(RubyArray.newArray(runtime, e));
           
           return RubyArray.newArray(runtime, ret);
       } catch(IOException e) {
           throw runtime.newIOError(e.getMessage());
       }
   }
   
    public static IRubyObject read(ThreadContext context, IRubyObject recv, IRubyObject[] args, Block block) {
        switch (args.length) {
        case 0: throw context.getRuntime().newArgumentError(0, 1);
        case 1: return read(context, recv, args[0], block);
        case 2: return read(context, recv, args[0], args[1], block);
        case 3: return read(context, recv, args[0], args[1], args[2], block);
        default: throw context.getRuntime().newArgumentError(args.length, 3);
        }
   }
   
    @JRubyMethod(name = "read", meta = true)
    public static IRubyObject read(ThreadContext context, IRubyObject recv, IRubyObject arg0, Block block) {
       IRubyObject[] fileArguments = new IRubyObject[] {arg0};
       RubyIO file = (RubyIO) RubyKernel.open(context, recv, fileArguments, block);
       
       try {
           return file.read(context);
       } finally {
           file.close();
       }
   }
   
    @JRubyMethod(name = "read", meta = true)
    public static IRubyObject read(ThreadContext context, IRubyObject recv, IRubyObject arg0, IRubyObject arg1, Block block) {
       IRubyObject[] fileArguments = new IRubyObject[] {arg0};
       RubyIO file = (RubyIO) RubyKernel.open(context, recv, fileArguments, block);
       
        try {
            if (!arg1.isNil()) {
                return file.read(context, arg1);
            } else {
                return file.read(context);
            }
        } finally  {
            file.close();
        }
   }
   
    @JRubyMethod(name = "read", meta = true)
    public static IRubyObject read(ThreadContext context, IRubyObject recv, IRubyObject arg0, IRubyObject arg1, IRubyObject arg2, Block block) {
        IRubyObject[] fileArguments = new IRubyObject[]{arg0};
        RubyIO file = (RubyIO) RubyKernel.open(context, recv, fileArguments, block);

        if (!arg2.isNil()) {
            file.seek(context, arg2);
        }

        try {
            if (!arg1.isNil()) {
                return file.read(context, arg1);
            } else {
                return file.read(context);
            }
        } finally  {
            file.close();
        }
    }
   
    @JRubyMethod(name = "readlines", required = 1, optional = 1, meta = true)
    public static RubyArray readlines(ThreadContext context, IRubyObject recv, IRubyObject[] args, Block block) {
        int count = args.length;

        IRubyObject[] fileArguments = new IRubyObject[]{ args[0].convertToString() };
        IRubyObject[] separatorArguments = count >= 2 ? new IRubyObject[]{args[1]} : IRubyObject.NULL_ARRAY;
        RubyIO file = (RubyIO) RubyKernel.open(context, recv, fileArguments, block);
        try {
            return file.readlines(context, separatorArguments);
        } finally {
            file.close();
        }
    }
   
    @JRubyMethod(name = "popen", required = 1, optional = 1, meta = true)
    public static IRubyObject popen(ThreadContext context, IRubyObject recv, IRubyObject[] args, Block block) {
        Ruby runtime = context.getRuntime();
        int mode;

        IRubyObject cmdObj = args[0].convertToString();
        runtime.checkSafeString(cmdObj);

        if ("-".equals(cmdObj.toString())) {
            throw runtime.newNotImplementedError("popen(\"-\") is unimplemented");
        }

        try {
            if (args.length == 1) {
                mode = ModeFlags.RDONLY;
            } else if (args[1] instanceof RubyFixnum) {
                mode = RubyFixnum.num2int(args[1]);
            } else {
                mode = getIOModesIntFromString(runtime, args[1].convertToString().toString());
            }

            ModeFlags modes = new ModeFlags(mode);
        
            ShellLauncher.POpenProcess process = ShellLauncher.popen(runtime, cmdObj, modes);
            RubyIO io = new RubyIO(runtime, process, modes);

            if (block.isGiven()) {
                try {
                    return block.yield(context, io);
                } finally {
                    if (io.openFile.isOpen()) {
                        io.close();
                    }
                    runtime.getGlobalVariables().set("$?", RubyProcess.RubyStatus.newProcessStatus(runtime, (process.waitFor() * 256)));
                }
            }
            return io;
        } catch (InvalidValueException ex) {
            throw runtime.newErrnoEINVALError();
        } catch (IOException e) {
            throw runtime.newIOErrorFromException(e);
        } catch (InterruptedException e) {
            throw runtime.newThreadError("unexpected interrupt");
        }
    }
   
    @JRubyMethod(required = 1, rest = true, frame = true, meta = true)
    public static IRubyObject popen3(ThreadContext context, IRubyObject recv, IRubyObject[] args, Block block) {
        Ruby runtime = context.getRuntime();

        try {        
            ShellLauncher.POpenProcess process = ShellLauncher.popen3(runtime, args);
            RubyIO input = process.getInput() != null ?
                new RubyIO(runtime, process.getInput()) :
                new RubyIO(runtime, process.getInputStream());
            RubyIO output = process.getOutput() != null ?
                new RubyIO(runtime, process.getOutput()) :
                new RubyIO(runtime, process.getOutputStream());
            RubyIO error = process.getError() != null ?
                new RubyIO(runtime, process.getError()) :
                new RubyIO(runtime, process.getErrorStream());

            RubyArray yieldArgs = RubyArray.newArrayLight(runtime, output, input, error);
            if (block.isGiven()) {
                try {
                    return block.yield(context, yieldArgs);
                } finally {
                    if (input.openFile.isOpen()) {
                        input.close();
                    }
                    if (output.openFile.isOpen()) {
                        output.close();
                    }
                    if (error.openFile.isOpen()) {
                        error.close();
                    }
                    runtime.getGlobalVariables().set("$?", RubyProcess.RubyStatus.newProcessStatus(runtime, (process.waitFor() * 256)));
                }
            }
            return yieldArgs;
        } catch (IOException e) {
            throw runtime.newIOErrorFromException(e);
        } catch (InterruptedException e) {
            throw runtime.newThreadError("unexpected interrupt");
        }
    }

    // NIO based pipe
    @JRubyMethod(name = "pipe", meta = true)
    public static IRubyObject pipe(ThreadContext context, IRubyObject recv) throws Exception {
        // TODO: This isn't an exact port of MRI's pipe behavior, so revisit
       Ruby runtime = context.getRuntime();
       Pipe pipe = Pipe.open();
       
       RubyIO source = new RubyIO(runtime, pipe.source());
       RubyIO sink = new RubyIO(runtime, pipe.sink());
       
       sink.openFile.getMainStream().setSync(true);
       return runtime.newArrayNoCopy(new IRubyObject[] { source, sink });
   }
    
    @JRubyMethod(name = "copy_stream", meta = true, compat = RUBY1_9)
    public static IRubyObject copy_stream(ThreadContext context, IRubyObject recv, 
            IRubyObject stream1, IRubyObject stream2) throws IOException {
        RubyIO io1 = (RubyIO)stream1;
        RubyIO io2 = (RubyIO)stream2;

        ChannelDescriptor d1 = io1.openFile.getMainStream().getDescriptor();
        if (!d1.isSeekable()) {
            throw context.getRuntime().newTypeError("only supports file-to-file copy");
        }
        ChannelDescriptor d2 = io2.openFile.getMainStream().getDescriptor();
        if (!d2.isSeekable()) {
            throw context.getRuntime().newTypeError("only supports file-to-file copy");
        }

        FileChannel f1 = (FileChannel)d1.getChannel();
        FileChannel f2 = (FileChannel)d2.getChannel();

        long size = f1.size();

        f1.transferTo(f2.position(), size, f2);

        return context.getRuntime().newFixnum(size);
    }
    
    /**
     * Add a thread to the list of blocking threads for this IO.
     * 
     * @param thread A thread blocking on this IO
     */
    public synchronized void addBlockingThread(RubyThread thread) {
        if (blockingThreads == null) {
            blockingThreads = new ArrayList<RubyThread>(1);
        }
        blockingThreads.add(thread);
    }
    
    /**
     * Remove a thread from the list of blocking threads for this IO.
     * 
     * @param thread A thread blocking on this IO
     */
    public synchronized void removeBlockingThread(RubyThread thread) {
        if (blockingThreads == null) {
            return;
        }
        for (int i = 0; i < blockingThreads.size(); i++) {
            if (blockingThreads.get(i) == thread) {
                // not using remove(Object) here to avoid the equals() call
                blockingThreads.remove(i);
            }
        }
    }
    
    /**
     * Fire an IOError in all threads blocking on this IO object
     */
    protected synchronized void interruptBlockingThreads() {
        if (blockingThreads == null) {
            return;
        }
        for (int i = 0; i < blockingThreads.size(); i++) {
            RubyThread thread = blockingThreads.get(i);
            
            // raise will also wake the thread from selection
            thread.raise(new IRubyObject[] {getRuntime().newIOError("stream closed").getException()}, Block.NULL_BLOCK);
        }
    }
}
