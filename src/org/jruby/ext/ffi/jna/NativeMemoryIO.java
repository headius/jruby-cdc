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
 * Copyright (C) 2008 JRuby project
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
package org.jruby.ext.ffi.jna;

import org.jruby.ext.ffi.*;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JNA implementation of memory I/O operations.
 */
public class NativeMemoryIO implements MemoryIO {

    /**
     * The native pointer.
     */
    final Pointer ptr;

    public NativeMemoryIO(Pointer ptr) {
        this.ptr = ptr;
    }

    Pointer getPointer() {
        return ptr;
    }
    void free() {}
    void autorelease(boolean autorelease) {}

    /**
     * Allocates a new block of native memory and wraps it in a {@link MemoryIO}
     * accessor.
     *
     * @param size The size in bytes of memory to allocate.
     *
     * @return A new <tt>MemoryIO</tt> instance that can access the memory.
     */
    static final NativeMemoryIO allocateDirect(int size) {
        return new Allocated(size);
    }

    public final boolean isNull() {
        return ptr == null;
    }

    public final byte getByte(long offset) {
        return ptr.getByte(offset);
    }

    public final short getShort(long offset) {
        return ptr.getShort(offset);
    }

    public final int getInt(long offset) {
        return ptr.getInt(offset);
    }

    public final long getLong(long offset) {
        return ptr.getLong(offset);
    }

    public final long getNativeLong(long offset) {
        return ptr.getNativeLong(offset).longValue();
    }

    public long getAddress(long offset) {
        return Platform.getPlatform().addressSize() == 32
                ? getInt(offset) : getLong(offset);
    }

    public MemoryIO getMemoryIO(long offset) {
        return new NativeMemoryIO(ptr.getPointer(offset));
    }

    public final float getFloat(long offset) {
        return ptr.getFloat(offset);
    }

    public final double getDouble(long offset) {
        return ptr.getDouble(offset);
    }

    public final Pointer getPointer(long offset) {
        return ptr.getPointer(offset);
    }

    public final void putByte(long offset, byte value) {
        ptr.setByte(offset, value);
    }

    public final void putShort(long offset, short value) {
        ptr.setShort(offset, value);
    }

    public final void putInt(long offset, int value) {
        ptr.setInt(offset, value);
    }

    public final void putLong(long offset, long value) {
        ptr.setLong(offset, value);
    }

    public final void putNativeLong(long offset, long value) {
        ptr.setNativeLong(offset, new NativeLong(value));
    }

    public final void putAddress(long offset, long value) {
        if (Platform.getPlatform().addressSize() == 32) {
            ptr.setInt(offset, (int) value);
        } else {
            ptr.setLong(offset, value);
        }
    }

    public final void putMemoryIO(long offset, MemoryIO value) {
        if (!(value instanceof NativeMemoryIO)) {
            throw new UnsupportedOperationException("Cannot put non-native MemoryIO");
        }
        ptr.setPointer(offset, ((NativeMemoryIO) value).ptr);
    }

    public final void putFloat(long offset, float value) {
        ptr.setFloat(offset, value);
    }

    public final void putDouble(long offset, double value) {
        ptr.setDouble(offset, value);
    }

    public final void putPointer(long offset, Pointer value) {
        ptr.setPointer(offset, value);
    }

    public final void get(long offset, byte[] dst, int off, int len) {
        ptr.read(offset, dst, off, len);
    }

    public final void put(long offset, byte[] dst, int off, int len) {
        ptr.write(offset, dst, off, len);
    }

    public final void get(long offset, short[] dst, int off, int len) {
        ptr.read(offset, dst, off, len);
    }

    public final void put(long offset, short[] dst, int off, int len) {
        ptr.write(offset, dst, off, len);
    }

    public final void get(long offset, int[] dst, int off, int len) {
        ptr.read(offset, dst, off, len);
    }

    public final void put(long offset, int[] dst, int off, int len) {
        ptr.write(offset, dst, off, len);
    }

    public final void get(long offset, long[] dst, int off, int len) {
        ptr.read(offset, dst, off, len);
    }

    public final void put(long offset, long[] dst, int off, int len) {
        ptr.write(offset, dst, off, len);
    }

    public final void get(long offset, float[] dst, int off, int len) {
        ptr.read(offset, dst, off, len);
    }

    public final void put(long offset, float[] dst, int off, int len) {
        ptr.write(offset, dst, off, len);
    }

    public final void get(long offset, double[] dst, int off, int len) {
        ptr.read(offset, dst, off, len);
    }

    public final void put(long offset, double[] dst, int off, int len) {
        ptr.write(offset, dst, off, len);
    }

    public final int indexOf(long offset, byte value) {
        return (int) ptr.indexOf(offset, value);
    }

    public final int indexOf(long offset, byte value, int maxlen) {
        return (int) ptr.indexOf(offset, value);
    }

    public final void setMemory(long offset, long size, byte value) {
        ptr.setMemory(offset, size, value);
    }

    public void clear() {
    }

    public NativeMemoryIO slice(long offset) {
        return offset == 0 ? this : new NativeMemoryIO(ptr.share(offset));
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof NativeMemoryIO)) {
            return false;
        }
        final NativeMemoryIO other = (NativeMemoryIO) obj;
        if (this.ptr != other.ptr && (this.ptr == null || !this.ptr.equals(other.ptr))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return ptr == null ? 0 : ptr.hashCode();
    }
    static final class Allocated extends NativeMemoryIO {
        /**
         * Used to hold a permanent reference to a memory pointer so it does not get
         * garbage collected
         */
        private static final Map<Allocated, Object> pointerSet
                = new ConcurrentHashMap<Allocated, Object>();

        public Allocated(int size) {
            super(new com.sun.jna.Memory(size));
        }
        @Override
        void free() {
            // Just let the GC collect and free the pointer
            pointerSet.remove(this);
        }
        @Override
        void autorelease(boolean autorelease) {
            if (autorelease) {
                pointerSet.remove(this);
            } else {
                pointerSet.put(this, Boolean.TRUE);
            }
        }
    }
}
