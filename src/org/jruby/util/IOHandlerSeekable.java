/*
 * IOHandlerSeekable.java
 *
 * Copyright (C) 2004 Thomas E Enebo
 * Thomas E Enebo <enebo@acm.org>
 *
 * JRuby - http://jruby.sourceforge.net
 *
 * This file is part of JRuby
 *
 * JRuby is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * JRuby is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with JRuby; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.jruby.util;

import org.jruby.Ruby;
import org.jruby.RubyIO;
import org.jruby.exceptions.ErrnoError;
import org.jruby.exceptions.IOError;
import org.jruby.exceptions.SystemCallError;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;

/**
 * <p>This file implements a seekable IO file.</p>
 * 
 * @author Thomas E Enebo (enebo@acm.org)
 */
public class IOHandlerSeekable extends IOHandler {
    protected RandomAccessFile file;
    protected String path;
    
    public IOHandlerSeekable(Ruby ruby, String path, IOModes modes) 
    	throws IOException {
        super(ruby);
        
        this.path = path;
        this.modes = modes;
        File theFile = new File(path);
        
        if (theFile.exists()) {
            if (modes.isReadable() == false && modes.isWriteable()== true) {
                // If we only want to open for writing we should remove
                // the old file before opening the fresh one.  If it fails
                // to remove it we should do something?
                if (theFile.delete() == false) {
                }
            }
        } else {
            if (modes.isReadable() == true && modes.isWriteable() == false) {
                throw ErrnoError.getErrnoError(ruby, "ENOENT", "No such file");
            }
        }

        // We always open this rw since we can only open it r or rw.
        file = new RandomAccessFile(theFile, "rw");
        isOpen = true;
        
        if (modes.isAppendable()) {
            seek(0, SEEK_END);
        }

        // We give a fileno last so that we do not consume these when
        // we have a problem opening a file.
        fileno = RubyIO.getNewFileno();
    }
    
    public IOHandler cloneIOHandler() {
        try {
            IOHandler newHandler =
                new IOHandlerSeekable(getRuntime(), path, modes); 
            
            newHandler.seek(pos(), SEEK_CUR);
            
            return newHandler;
        } catch (IOException e) {
            throw new IOError(getRuntime(), e.toString());
        }
    }
    
    /**
     * <p>Close IO handler resources.</p>
     * 
     * @see org.jruby.util.IOHandler#close()
     */
    public void close() {
        if (isOpen() == false) {
            throw ErrnoError.getErrnoError(getRuntime(), "EBADF", "Bad File Descriptor");
        }
        
        isOpen = false;

        try {
            file.close();
        } catch (IOException e) {
            throw IOError.fromException(getRuntime(), e);
        }
    }

    /**
     * @see org.jruby.util.IOHandler#flush()
     */
    public void flush() {
        checkWriteable();

        // No flushing a random access file.
    }

    /**
     * @see org.jruby.util.IOHandler#getInputStream()
     */
    public InputStream getInputStream() {
        return new BufferedInputStream(new DataInputBridgeStream(file));
    }

    /**
     * @see org.jruby.util.IOHandler#getOutputStream()
     */
    public OutputStream getOutputStream() {
        return new BufferedOutputStream(new DataOutputBridgeStream(file));
    }
    
    /**
     * @see org.jruby.util.IOHandler#isEOF()
     */
    public boolean isEOF() {
        checkReadable();

        try {
            int c = file.read();
            if (c == -1) {
                return true;
            }
            file.seek(file.getFilePointer() - 1);
            return false;
        } catch (IOException e) {
            throw IOError.fromException(getRuntime(), e);
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
     * @see org.jruby.util.IOHandler#pos()
     */
    public long pos() {
        checkOpen();
        
        try {
            return file.getFilePointer();
        } catch (IOException e) {
            throw IOError.fromException(getRuntime(), e);
        }
    }
    
    public void resetByModes(IOModes modes) {
        if (modes.isAppendable()) {
            seek(0L, SEEK_END);
        } else if (modes.isWriteable()) {
            rewind();
        }
    }

    /**
     * @see org.jruby.util.IOHandler#rewind()
     */
    public void rewind() {
        seek(0, SEEK_SET);
    }
    
    /**
     * @see org.jruby.util.IOHandler#seek(long, int)
     */
    public void seek(long offset, int type) {
        checkOpen();
        
        try {
            switch (type) {
            case SEEK_SET:
                file.seek(offset);
                break;
            case SEEK_CUR:
                file.seek(file.getFilePointer() + offset);
                break;
            case SEEK_END:
                file.seek(file.length() + offset);
                break;
            }
        } catch (IOException e) {
            throw ErrnoError.getErrnoError(getRuntime(), "EINVAL", e.toString());
        }
    }

    /**
     * @see org.jruby.util.IOHandler#sync()
     */
    public void sync() throws IOException {
        file.getFD().sync();
        // RandomAccessFile is always synced?
    }
    
    /**
     * @see org.jruby.util.IOHandler#sysread()
     */
    public int sysread() throws IOException {
        return file.read();
    }
    
    /**
     * @see org.jruby.util.IOHandler#syswrite(String buf)
     */
    public int syswrite(String buf) {
        getRuntime().secure(4);
        checkWriteable();
        
        // Ruby ignores empty syswrites
        if (buf == null || buf.length() == 0) {
            return 0;
        }
        
        try {
            file.writeBytes(buf);
            
            if (isSync()) {
                sync();
            }
            
            return buf.length();
        } catch (IOException e) {
            throw new SystemCallError(getRuntime(), e.toString());
        }
    }
    
    public void truncate(long newLength) throws IOException {
        file.setLength(newLength);
    }
}
