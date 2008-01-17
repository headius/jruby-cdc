/***** BEGIN LICENSE BLOCK *****
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
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2005 Charles O Nutter <headius@headius.com>
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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;

import org.jruby.Ruby;
import org.jruby.RubyIO;

public class IOHandlerProcess extends AbstractIOHandler {
    protected InputStream input = null;
    protected OutputStream output = null;
    protected Process process = null;
    protected int ungotc = -1;

    public IOHandlerProcess(Ruby runtime, Process process, IOModes modes) throws IOException {
        super(runtime);
        
        if (process == null) {
        	throw new IOException("Null process");
        }

        this.process = process;
        this.input = process.getInputStream();
        this.output = process.getOutputStream();
        
        isOpen = true;

        this.modes = modes;
        this.isSync = true;
        fileno = RubyIO.getNewFileno();
    }
    
    public AbstractIOHandler cloneIOHandler() throws IOException {
    	// may need to pass streams instead?
        return new IOHandlerProcess(getRuntime(), process, modes); 
    }

    /**
     * <p>Close IO handler resources.</p>
     * @throws IOException 
     * @throws BadDescriptorException 
     * 
     * @see org.jruby.util.IOHandler#close()
     */
    public void close() throws IOException, BadDescriptorException {
        if (!isOpen()) throw new BadDescriptorException();
        
        isOpen = false;

        input.close();
        output.close();

        // null out and let the process eventually get collected
        process = null;
    }

    /**
     * @throws IOException 
     * @throws BadDescriptorException 
     * @see org.jruby.util.IOHandler#flush()
     */
    public void flush() throws IOException, BadDescriptorException {
        checkWritable();

        output.flush();
    }
    
    /**
     * @see org.jruby.util.IOHandler#getInputStream()
     */
    public InputStream getInputStream() {
        return input;
    }

    /**
     * @see org.jruby.util.IOHandler#getOutputStream()
     */
    public OutputStream getOutputStream() {
        return output;
    }

    /**
     * @throws IOException 
     * @throws BadDescriptorException 
     * @see org.jruby.util.IOHandler#isEOF()
     */
    public boolean isEOF() throws IOException, BadDescriptorException {
        checkReadable();

        int c = input.read();
        if (c == -1) {
            return true;
        }
        ungetc(c);
        return false;
    }
    
    /**
     * @see org.jruby.util.IOHandler#pid()
     */
    public int pid() {
    	// no way to get pid, so faking it
        return process.hashCode();
    }
    
    /**
     * @throws PipeException 
     * @see org.jruby.util.IOHandler#pos()
     */
    public long pos() throws PipeException {
        throw new AbstractIOHandler.PipeException();
    }
    
    public void resetByModes(IOModes newModes) {
    }
    
    /**
     * @throws PipeException 
     * @see org.jruby.util.IOHandler#rewind()
     */
    public void rewind() throws PipeException {
        throw new AbstractIOHandler.PipeException();
    }
    
    /**
     * @throws PipeException 
     * @see org.jruby.util.IOHandler#seek(long, int)
     */
    public void seek(long offset, int type) throws PipeException {
        throw new AbstractIOHandler.PipeException();
    }
    
    /**
     * @see org.jruby.util.IOHandler#sync()
     */
    public void sync() throws IOException {
        output.flush();
    }
    
    /**
     * @see org.jruby.util.IOHandler#sysread()
     */
    public int sysread() throws IOException {
        return input.read();
    }

    public ByteList sysread(int number) throws IOException, BadDescriptorException {
        checkReadable();
        byte[] buf = new byte[number];
        int read = 0;
        int n;
        while(read < number) {
            n = input.read(buf,read,number-read);
            if(n == -1) {
                if(read == 0) {
                    throw new java.io.EOFException();
                } else {
                    break;
                }
            }
            read += n;
        }
        
        return new ByteList(buf, 0, read, false);
    }

    /**
     * @throws IOException 
     * @throws BadDescriptorException 
     * @see org.jruby.util.IOHandler#syswrite(String buf)
     */
    public int syswrite(ByteList buf) throws IOException, BadDescriptorException {
        getRuntime().secure(4);
        checkWritable();
        
        if (buf == null || buf.realSize == 0) return 0;
        
        output.write(buf.bytes, buf.begin, buf.realSize);

        // Should syswrite sync?
        if (isSync) sync();
            
        return buf.realSize;
    }

    /**
     * @throws IOException 
     * @throws BadDescriptorException 
     * @see org.jruby.util.IOHandler#syswrite(String buf)
     */
    public int syswrite(int c) throws IOException, BadDescriptorException {
        getRuntime().secure(4);
        checkWritable();
        
        output.write(c);

        // Should syswrite sync?
        if (isSync) {
            sync();
        }
            
        return 1;
    }
    
    public void truncate(long newLength) throws IOException, PipeException {
        throw new AbstractIOHandler.PipeException();
    }

    public FileChannel getFileChannel() {
        assert false : "No file channel for process streams";
        return null;
    }
    
    public int ready() throws IOException {
        return getInputStream().available();
    }

    public void putc(int c) throws IOException, BadDescriptorException {
        try {
            syswrite(c);
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
        return syswrite(string);
    }

    public ByteList read(int number) throws IOException, BadDescriptorException {
        try {

            if (ungotc >= 0) {
                ByteList buf2 = sysread(number - 1);
                buf2.prepend((byte)ungotc);
                ungotc = -1;
                return buf2;
            }

            return sysread(number);
        } catch (EOFException e) {
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

            return sysread();
        } catch (EOFException e) {
            return -1;
        }
    }

    public ByteList getsEntireStream() throws IOException {
        ByteList result = new ByteList();
        int c;
        while ((c = (byte)read()) != -1) {
            result.append(c);
        }

        // We are already at EOF
        if (result.realSize == 0) {
            return null;
        }

        return result;
    }

    public ByteList gets(ByteList separatorString) throws IOException, BadDescriptorException {
        checkReadable();

        if (separatorString == null) {
            return getsEntireStream();
        }

        final ByteList separator = (separatorString == PARAGRAPH_DELIMETER) ?
            ByteList.create("\n\n") : separatorString;

        byte c = (byte)read();
        if (c == -1) {
            return null;
        }

        ByteList buffer = new ByteList();

        LineLoop : while (true) {
            while (c != separator.bytes[separator.begin] && c != -1) {
                buffer.append(c);
                c = (byte)read();
            }
            for (int i = 0; i < separator.realSize; i++) {
                if (c == -1) {
                    break LineLoop;
                } else if (c != separator.bytes[separator.begin + i]) {
                    continue LineLoop;
                }
                buffer.append(c);
                if (i < separator.realSize - 1) {
                    c = (byte)read();
                }
            }
            break;
        }

        if (separatorString == PARAGRAPH_DELIMETER) {
            while (c == separator.bytes[separator.begin]) {
                c = (byte)read();
            }
            ungetc(c);
        }

        return buffer;
    }
}
