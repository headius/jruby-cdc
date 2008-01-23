/*
 ***** BEGIN LICENSE BLOCK *****
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
 * Copyright (C) 2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004-2005 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2005 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2007 Damian Steer <pldms@mac.com>
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
package org.jruby.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;

import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.spi.SelectorProvider;
import org.jruby.Finalizable;
import org.jruby.Ruby;
import org.jruby.RubyIO;
import org.jruby.util.IOHandler.BadDescriptorException;
import org.jruby.util.io.SplitChannel;

/**
 * <p>This file implements a seekable IO file.</p>
 */
public class IOHandlerNioBuffered extends AbstractIOHandler implements Finalizable {
    private final static int BUFSIZE = 16 * 1024;
    
    protected ByteBuffer buffer; // r/w buffer
    protected boolean reading; // are we reading or writing?
    private RubyIO.ChannelDescriptor descriptor;
    protected int ungotc = -1;

    public IOHandlerNioBuffered(Ruby runtime, RubyIO.ChannelDescriptor descriptor, IOModes modes, FileDescriptor fileDescriptor) throws IOException {
        super(runtime);
        this.descriptor = descriptor;
        this.isOpen = true;
        // TODO: Confirm modes correspond to the available modes on the channel
        this.modes = modes;
        this.buffer = ByteBuffer.allocate(BUFSIZE);
        buffer.flip();
        this.reading = true;
        this.fileDescriptor = fileDescriptor;
    }

    public IOHandlerNioBuffered(Ruby runtime, RubyIO.ChannelDescriptor descriptor) throws IOException {
        this(runtime, descriptor, (FileDescriptor) null);
    }

    public IOHandlerNioBuffered(Ruby runtime, RubyIO.ChannelDescriptor descriptor, FileDescriptor fileDescriptor) throws IOException {
        super(runtime);
        String mode = "";
        this.descriptor = descriptor;
        if (descriptor.getChannel() instanceof ReadableByteChannel) {
            mode += "r";
            isOpen = true;
        }
        if (descriptor.getChannel() instanceof WritableByteChannel) {
            mode += "w";
            isOpen = true;
        }
        if ("rw".equals(mode)) {
            modes = new IOModes(runtime, IOModes.RDWR);
            isOpen = true;
        } else {
            if (!isOpen) {
                // Neither stream exists?
                // throw new IOException("Opening nothing?");
	        // Hack to cover the ServerSocketChannel case
                mode = "r";
                isOpen = true;
            }
            modes = new IOModes(runtime, mode);
        }
        this.fileDescriptor = fileDescriptor;
        buffer = ByteBuffer.allocate(BUFSIZE);
        buffer.flip();
        this.reading = true;
    }

    public IOHandlerNioBuffered(Ruby runtime, RubyIO.ChannelDescriptor descriptor, IOModes modes) throws IOException {
        super(runtime);
        this.descriptor = descriptor;
        this.isOpen = true;
        // TODO: Confirm modes correspond to the available modes on the channel
        this.modes = modes;
        buffer = ByteBuffer.allocate(BUFSIZE);
        buffer.flip();
        this.reading = true;
    }

    public ByteList gets(ByteList separatorString) throws IOException, BadDescriptorException {
        checkReadable();

        if (separatorString == null) {
            return getsEntireStream();
        }

        final ByteList separator = (separatorString == PARAGRAPH_DELIMETER) ?
            PARAGRAPH_SEPARATOR : separatorString;

        int c = read();
        
        if (c == -1) {
            return null;
        }
        
        // unread back
        buffer.position(buffer.position() - 1);

        ByteList buf = new ByteList(40);
        
        byte first = separator.bytes[separator.begin];

        LineLoop : while (true) {
            ReadLoop: while (true) {
                byte[] bytes = buffer.array();
                int offset = buffer.position();
                int max = buffer.limit();
                
                // iterate over remainder of buffer until we find a match
                for (int i = offset; i < max; i++) {
                    c = bytes[i];
                    if (c == first) {
                        // terminate and advance buffer when we find our char
                        buf.append(bytes, offset, i - offset);
                        if (i >= max) {
                            buffer.clear();
                        } else {
                            buffer.position(i + 1);
                        }
                        break ReadLoop;
                    }
                }
                
                // no match, append remainder of buffer and continue with next block
                buf.append(bytes, offset, buffer.remaining());
                buffer.clear();
                int read = ((ReadableByteChannel)descriptor.getChannel()).read(buffer);
                buffer.flip();
                if (read == -1) break LineLoop;
            }
            
            // found a match above, check if remaining separator characters match, appending as we go
            for (int i = 0; i < separator.realSize; i++) {
                if (c == -1) {
                    break LineLoop;
                } else if (c != separator.bytes[separator.begin + i]) {
                    buf.append(c);
                    continue LineLoop;
                }
                buf.append(c);
                if (i < separator.realSize - 1) {
                    c = read();
                }
            }
            break;
        }

        if (separatorString == PARAGRAPH_DELIMETER) {
            while (c == separator.bytes[separator.begin]) {
                c = read();
            }
            ungetc(c);
        }

        return buf;
    }
    
    public ByteList getsEntireStream() throws IOException, BadDescriptorException {
        if (descriptor.isSeekable()) {
            invalidateBuffer();
            FileChannel channel = (FileChannel)descriptor.getChannel();
            long left = channel.size() - channel.position();
            if (left == 0) return null;

            try {
                return sysread((int) left);
            } catch (BadDescriptorException e) {
                throw new IOException(e.getMessage()); // Ugh! But why rewrite the same code?
            }
        } else {
            try {
                checkReadable();
            } catch (BadDescriptorException bde) {
                throw new IOException(bde.getMessage());
            }

            ByteList byteList = new ByteList();
            ByteList read = read(BUFSIZE);

            while (read != null) {
                byteList.append(read);
                read = read(BUFSIZE);
            }

            return byteList;
        } 
    }
    
    /**
     * <p>Close IO handler resources.</p>
     * @throws IOException 
     * @throws BadDescriptorException 
     * 
     * @see org.jruby.util.IOHandler#close()
     */
    public void close() throws IOException, BadDescriptorException {
        close(false); // not closing from finalise
    }
    
    /**
     * Internal close, to safely work for finalizing.
     * @param finalizing true if this is in a finalizing context
     * @throws IOException 
     * @throws BadDescriptorException
     */
    private void close(boolean finalizing) throws IOException, BadDescriptorException {
        if (descriptor.isSeekable()) {
            checkOpen();
            flushWrite();

            isOpen = false;
            descriptor.getChannel().close();
        } else {
            if (!isOpen()) throw new BadDescriptorException();

            isOpen = false;
            flushWrite();
            descriptor.getChannel().close();
            if (!finalizing) getRuntime().removeInternalFinalizer(this);
        }
    }

    /**
     * @throws IOException 
     * @throws BadDescriptorException 
     * @see org.jruby.util.IOHandler#flush()
     */
    public void flush() throws IOException, BadDescriptorException {
        checkWritable();
        flushWrite();
    }
    
    /**
     * Flush the write buffer to the channel (if needed)
     * @throws IOException
     */
    private void flushWrite() throws IOException {
        if (reading || !modes.isWritable() || buffer.position() == 0) return; // Don't bother
            
        buffer.flip();
        ((WritableByteChannel)descriptor.getChannel()).write(buffer);
        buffer.clear();
    }

    /**
     * @see org.jruby.util.IOHandler#getInputStream()
     */
    public InputStream getInputStream() {
        return new BufferedInputStream(Channels.newInputStream((ReadableByteChannel)descriptor.getChannel()));
    }

    /**
     * @see org.jruby.util.IOHandler#getOutputStream()
     */
    public OutputStream getOutputStream() {
        return new BufferedOutputStream(Channels.newOutputStream((WritableByteChannel)descriptor.getChannel()));
    }
    
    /**
     * @throws IOException 
     * @throws BadDescriptorException 
     * @see org.jruby.util.IOHandler#isEOF()
     */
    public boolean isEOF() throws IOException, BadDescriptorException {
        checkReadable();
        
        if (reading && buffer.hasRemaining()) return false;
        
        if (descriptor.isSeekable()) {
            FileChannel fileChannel = (FileChannel)descriptor.getChannel();
            return (fileChannel.size() == fileChannel.position());
        } else {
            checkReadable();
            ensureRead();

            if (ungotc > 0) {
                return false;
            }
            // TODO: this is new to replace what's below
            ungotc = read();
            if (ungotc == -1) {
                return true;
            }
            // FIXME: this was here before; need a better way?
//            if (fillInBuffer() < 0) {
//                return true;
//            }
            return false;
        }
    }
    
    /**
     * @see org.jruby.util.IOHandler#pid()
     */
    public int pid() {
        // A file is not a process.
        return -1;
    }
    
    /**
     * @throws IOException 
     * @see org.jruby.util.IOHandler#pos()
     */
    public long pos() throws IOException, BadDescriptorException {
        checkOpen();
        // Correct position for read / write buffering (we could invalidate, but expensive)
        int offset = (reading) ? - buffer.remaining() : buffer.position();
        FileChannel fileChannel = (FileChannel)descriptor.getChannel();
        return fileChannel.position() + offset;
    }
    
    public void resetByModes(IOModes newModes) throws IOException, InvalidValueException, PipeException, BadDescriptorException {
        if (descriptor.getChannel() instanceof FileChannel) {
            if (newModes.isAppendable()) {
                seek(0L, SEEK_END);
            } else if (newModes.isWritable()) {
                try {
                    rewind();
                } catch(PipeException e) {} // don't throw
            }
        }
    }

    /**
     * @throws IOException 
     * @throws InvalidValueException 
     * @see org.jruby.util.IOHandler#rewind()
     */
    public void rewind() throws IOException, InvalidValueException, PipeException, BadDescriptorException {
        seek(0, SEEK_SET);
    }
    
    /**
     * @throws IOException 
     * @throws InvalidValueException 
     * @see org.jruby.util.IOHandler#seek(long, int)
     */
    public void seek(long offset, int type) throws IOException, InvalidValueException, PipeException, BadDescriptorException {
        checkOpen();
        if (descriptor.getChannel() instanceof FileChannel) {
            invalidateBuffer();
            FileChannel fileChannel = (FileChannel)descriptor.getChannel();
            try {
                switch (type) {
                case SEEK_SET:
                    fileChannel.position(offset);
                    break;
                case SEEK_CUR:
                    fileChannel.position(fileChannel.position() + offset);
                    break;
                case SEEK_END:
                    fileChannel.position(fileChannel.size() + offset);
                    break;
                }
            } catch (IllegalArgumentException e) {
                throw new InvalidValueException();
            }
        } else {
            throw new AbstractIOHandler.PipeException();
        }
    }

    /**
     * @see org.jruby.util.IOHandler#sync()
     */
    public void sync() throws IOException {
        flushWrite();
    }

    /**
     * Put one buffer into another, truncating the put (rather than throwing an exception)
     * if src doesn't fit into dest. Shame this doesn't exist already.
     * @param dest The destination buffer which will receive bytes
     * @param src The buffer to read bytes from
     */
    private static void putInto(ByteBuffer dest, ByteBuffer src) {
        int destAvail = dest.capacity() - dest.position();
        if (src.remaining() > destAvail) { // already have more than enough bytes available
            // ByteBuffer seems to be missing a useful method here
            int oldLimit = src.limit();
            src.limit(src.position() + destAvail);
            dest.put(src);
            src.limit(oldLimit);
        } else {
            dest.put(src);
        }
    }

    /**
     * Ensure buffer is ready for reading, flushing remaining writes if required
     * @throws IOException
     */
    private void ensureRead() throws IOException {
        if (reading) return;
        flushWrite();
        buffer.clear();
        buffer.flip();
        reading = true;
    }

    /**
     * Ensure buffer is ready for reading, flushing remaining writes if required
     * @throws IOException
     */
    private void ensureReadNonBuffered() throws IOException {
        if (reading) {
            if (buffer.hasRemaining()) {
                throw getRuntime().newIOError("sysread for buffered IO");
            }
        } else {
            // libc flushes writes on any read from the actual file, so we flush here
            flushWrite();
            buffer.clear();
            buffer.flip();
            reading = true;
        }
    }
    
    private void resetForWrite() throws IOException {
        if (descriptor.isSeekable()) {
            FileChannel fileChannel = (FileChannel)descriptor.getChannel();
            if (buffer.hasRemaining()) { // we have read ahead, and need to back up
                fileChannel.position(fileChannel.position() - buffer.remaining());
            }
        }
        // FIXME: Clearing read buffer here...is this appropriate?
        buffer.clear();
        reading = false;
    }
    
    /**
     * Ensure buffer is ready for writing.
     * @throws IOException
     */
    private void ensureWrite() throws IOException {
        if (!reading) return;
        resetForWrite();
    }
    
    private void ensureWriteNonBuffered() throws IOException {
        if (!reading) {
            if (buffer.position() > 0) {
                getRuntime().getWarnings().warn("syswrite for buffered IO");
            }
            return;
        }
        resetForWrite();
    }

    public ByteList sysread(int number) throws IOException, BadDescriptorException {
        checkOpen();
        checkReadable();
        ensureReadNonBuffered();
        
        return sysread(number, (ReadableByteChannel)descriptor.getChannel());
    }
    
    /**
     * @throws IOException 
     * @throws BadDescriptorException 
     * @see org.jruby.util.IOHandler#syswrite(String buf)
     */
    public int syswrite(ByteList buf) throws IOException, BadDescriptorException {
        getRuntime().secure(4);
        checkWritable();
        ensureWriteNonBuffered();
        
        return syswrite(buf, (WritableByteChannel)descriptor.getChannel());
    }
    
    /**
     * @throws IOException 
     * @throws BadDescriptorException 
     * @see org.jruby.util.IOHandler#syswrite(String buf)
     */
    public int syswrite(int c) throws IOException, BadDescriptorException {
        getRuntime().secure(4);
        checkWritable();
        ensureWriteNonBuffered();
        
        return syswrite(c, (WritableByteChannel)descriptor.getChannel());
    }

    public ByteList bufferedRead(int number) throws IOException, BadDescriptorException {
        checkOpen();
        checkReadable();
        ensureRead();
        
        ByteList result = new ByteList();
        int len = -1;
        if (buffer.hasRemaining()) { // already have some bytes buffered
            len = (number <= buffer.remaining()) ? number : buffer.remaining();
            result.append(buffer, len);
        }
        
        ReadableByteChannel readChannel = (ReadableByteChannel)descriptor.getChannel();
        int read = BUFSIZE;
        while (read == BUFSIZE && result.length() != number) { // not complete. try to read more
            buffer.clear(); 
            read = readChannel.read(buffer);
            buffer.flip();
            if (read == -1) break;
            int desired = number - result.length();
            len = (desired < read) ? desired : read;
            result.append(buffer, len);
        }
        
        if (result.length() == 0 && number != 0) throw new java.io.EOFException();
        return result;
    }
    
    /**
     * @see org.jruby.util.IOHandler#sysread()
     */
    public int bufferedRead() throws IOException {
        ensureRead();
        
        if (!buffer.hasRemaining()) {
            buffer.clear();
            int read = ((ReadableByteChannel)descriptor.getChannel()).read(buffer);
            buffer.flip();
            
            if (read == -1) return -1;
        }
        return buffer.get() & 0xFF;
    }
    
    /**
     * @throws IOException 
     * @throws BadDescriptorException 
     * @see org.jruby.util.IOHandler#syswrite(String buf)
     */
    public int bufferedWrite(ByteList buf) throws IOException, BadDescriptorException {
        getRuntime().secure(4);
        checkWritable();
        ensureWrite();
        
        // Ruby ignores empty syswrites
        if (buf == null || buf.length() == 0) return 0;
        
        if (buf.length() > buffer.capacity()) { // Doesn't fit in buffer. Write immediately.
            flushWrite(); // ensure nothing left to write
            
            ((WritableByteChannel)descriptor.getChannel()).write(ByteBuffer.wrap(buf.unsafeBytes(), buf.begin(), buf.length()));
        } else {
            if (buf.length() > buffer.remaining()) flushWrite();
            
            buffer.put(buf.unsafeBytes(), buf.begin(), buf.length());
        }
        
        if (isSync()) sync();
        
        return buf.realSize;
    }
    
    /**
     * @throws IOException 
     * @throws BadDescriptorException 
     * @see org.jruby.util.IOHandler#syswrite(String buf)
     */
    public int bufferedWrite(int c) throws IOException, BadDescriptorException {
        getRuntime().secure(4);
        checkWritable();
        ensureWrite();

        if (!buffer.hasRemaining()) flushWrite();
        
        buffer.put((byte) c);
            
        if (isSync()) sync();
            
        return 1;
    }
    
    public void truncate(long newLength) throws IOException {
        invalidateBuffer();
        FileChannel fileChannel = (FileChannel)descriptor.getChannel();
        if (newLength > fileChannel.size()) {
            // truncate can't lengthen files, so we save position, seek/write, and go back
            long position = fileChannel.position();
            int difference = (int)(newLength - fileChannel.size());
            
            fileChannel.position(fileChannel.size());
            // FIXME: This worries me a bit, since it could allocate a lot with a large newLength
            fileChannel.write(ByteBuffer.allocate(difference));
            fileChannel.position(position);
        } else {
            fileChannel.truncate(newLength);
        }        
    }
    
    public FileChannel getFileChannel() {
        return (FileChannel)descriptor.getChannel();
    }
    
    /**
     * Invalidate buffer before a position change has occurred (e.g. seek),
     * flushing writes if required, and correcting file position if reading
     * @throws IOException 
     */
    private void invalidateBuffer() throws IOException {
        if (!reading) flushWrite();
        int posOverrun = buffer.remaining(); // how far ahead we are when reading
        buffer.clear();
        if (reading) {
            buffer.flip();
            // if the read buffer is ahead, back up
            FileChannel fileChannel = (FileChannel)descriptor.getChannel();
            if (posOverrun != 0) fileChannel.position(fileChannel.position() - posOverrun);
        }
    }
    
    /**
     * Ensure close (especially flush) when we're finished with
     */
    public void finalize() {
        try {
            if (descriptor.isSeekable() && isOpen) close(true); // close without removing from finalizers
        } catch (Exception e) { // What else could we do?
            e.printStackTrace();
        }
    }
    
    public int ready() throws IOException {
        return getInputStream().available();
    }

    public void putc(int c) throws IOException, BadDescriptorException {
        try {
            bufferedWrite(c);
            flush();
        } catch (IOException e) {
        }
    }

    public void ungetc(int c) {
        // Ruby silently ignores negative ints for some reason?
        if (c >= 0) {
            ungotc = c;
        }
    }

    public int getc() throws IOException, BadDescriptorException {
        checkReadable();

        int c = read();

        if (c == -1) {
            return c;
        }
        return c & 0xff;
    }

    public int write(ByteList string) throws IOException, BadDescriptorException {
        return bufferedWrite(string);
    }

    public ByteList read(int number) throws IOException, BadDescriptorException {
        try {

            if (ungotc >= 0) {
                ByteList buf2 = bufferedRead(number - 1);
                buf2.prepend((byte)ungotc);
                ungotc = -1;
                return buf2;
            }

            return bufferedRead(number);
        } catch (EOFException e) {
            return null;
        }
    }

    public ByteList readpartial(int number) throws IOException, BadDescriptorException, EOFException {
        if (descriptor.getChannel() instanceof SelectableChannel) {
            if (ungotc >= 0) {
                ByteList buf2 = bufferedRead(number - 1);
                buf2.prepend((byte)ungotc);
                ungotc = -1;
                return buf2;
            } else {
                return bufferedRead(number);
            }
        } else {
            return null;
        }
    }

    public int read() throws IOException {
        try {
            if (ungotc >= 0) {
                int c = ungotc;
                ungotc = -1;
                return c;
            }

            return bufferedRead();
        } catch (EOFException eof) {
            return -1;
        }
    }
    
    public RubyIO.ChannelDescriptor getDescriptor() {
        return descriptor;
    }

    public void freopen(String path, IOModes modes) throws DirectoryAsFileException, IOException, InvalidValueException, PipeException, BadDescriptorException {
        // flush first
        flushWrite();
        
        this.modes = modes;
        String cwd = getRuntime().getCurrentDirectory();
        JRubyFile theFile = JRubyFile.create(cwd,path);

        if (theFile.isDirectory() && modes.isWritable()) throw new DirectoryAsFileException();
        
        if (modes.isCreate()) {
            if (theFile.exists() && modes.isExclusive()) {
                throw getRuntime().newErrnoEEXISTError("File exists - " + path);
            }
            theFile.createNewFile();
        } else {
            if (!theFile.exists()) {
                throw getRuntime().newErrnoENOENTError("file not found - " + path);
            }
        }

        // We always open this rw since we can only open it r or rw.
        RandomAccessFile file = new RandomAccessFile(theFile, modes.javaMode());

        if (modes.shouldTruncate()) file.setLength(0L);

        descriptor.setChannel(file.getChannel());
        
        isOpen = true;
        
        fileDescriptor = file.getFD();
        
        if (modes.isAppendable()) seek(0, SEEK_END);
    }

    public void closeWrite() throws IOException, BadDescriptorException {
        checkOpen();
        flushWrite();
        
        if (descriptor.getChannel() instanceof SplitChannel) {
            // split channels have separate read/write, so we *can* close write
            ((SplitChannel)descriptor.getChannel()).closeWrite();
        }
    }
}
