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
 * Copyright (C) 2001-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004-2007 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2006 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2006 Miguel Covarrubias <mlcovarrubias@gmail.com>
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
package org.jruby.runtime;

import org.jruby.RubyModule;
import org.jruby.internal.runtime.JumpTarget;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * A Frame holds per-call information that needs to persist outside the
 * execution of a given method. Currently a frame holds the following:
 * <ul>
 * <li>The class against which this method is being invoked. This is usually
 * (always?) the class of "self" within this call.</li>
 * <li>The current "self" for the call.</li>
 * <li>The name of the method being invoked during this frame, used for
 * backtraces and "super" invocations.</li>
 * <li>The block passed to this invocation. If the given code body can't
 * accept a block, it will be Block.NULL_BLOCK.</li>
 * <li>Whether this is the frame used for a binding-related call like eval. This
 * is used to determine where to terminate evaled code's backtrace.</li>
 * <li>The current visibility for methods defined during this call. Starts out
 * as PUBLIC by default (in most cases) and can be modified by appropriate
 * Kernel.public/private/protected calls.</li>
 * <li>The jump target marker for non-local returns.</li>
 * </ul>
 * Frames are allocated for all Ruby methods (in compatibility mode, default)
 * and for some core methods. In general, a frame is required for a method to
 * show up in a backtrace, and so some methods only use frame for backtrace
 * information (so-called "backtrace frames").
 * 
 * @see ThreadContext
 */
public final class Frame implements JumpTarget {
    /** The class against which this call is executing. */
    private RubyModule klazz;
    
    /** The 'self' for this frame. */
    private IRubyObject self;
    
    /** The name of the method being invoked in this frame. */
    private String name;

    /**
     * The block that was passed in for this frame (as either a block or a &amp;block argument).
     * The frame captures the block for super/zsuper, but also for Proc.new (with no arguments)
     * and also for block_given?.  Both of those methods needs access to the block of the 
     * previous frame to work.
     */ 
    private Block block = Block.NULL_BLOCK;
    
    /** Delimit a frame where an eval with binding occurred.  Used for stack traces. */
    private boolean isBindingFrame = false;

    /** The current visibility for anything defined under this frame */
    private Visibility visibility = Visibility.PUBLIC;
    
    /** The target for non-local jumps, like return from a block */
    private final JumpTarget jumpTarget;
    
    /** A tuple representing the $_ and $~ values for this frame */
    private static class BackrefAndLastline {
        public IRubyObject backref;
        public IRubyObject lastline;
    }
    
    /** The current backref/lastline tuple for this frame */
    private BackrefAndLastline backrefAndLastline;
    
    /** The filename where the calling method is located */
    private String fileName;
    
    /** The line number in the calling method where this call is made */
    private int line;
    
    /**
     * Empty constructor, since Frame objects are pre-allocated and updated
     * when needed.
     */
    public Frame() {
        jumpTarget = this;
    }
    
    /**
     * Copy constructor, since Frame objects are pre-allocated and updated
     * when needed.
     */
    private Frame(Frame frame) {
        assert frame.block != null : "Block uses null object pattern.  It should NEVER be null";

        this.self = frame.self;
        this.name = frame.name;
        this.klazz = frame.klazz;
        this.fileName = frame.fileName;
        this.line = frame.line;
        this.block = frame.block;
        this.visibility = frame.visibility;
        this.isBindingFrame = frame.isBindingFrame;
        this.jumpTarget = frame.jumpTarget;
        
        // we force the lazy allocation of backref/lastline here to allow
        // closures to update the original frame
        frame.lazyBackrefAndLastline();
        this.backrefAndLastline = frame.backrefAndLastline;
    }

    /**
     * Update the frame with just filename and line, used for top-level frames
     * and method.
     * 
     * @param fileName The file where the calling method is located
     * @param line The line number in the calling method where the call is made
     */
    public void updateFrame(String fileName, int line) {
        updateFrame(null, null, null, Block.NULL_BLOCK, fileName, line); 
    }

    /**
     * Update the frame with caller information and method name, so it will
     * show up correctly in call stacks.
     * 
     * @param name The name of the method being called
     * @param fileName The file of the calling method
     * @param line The line number of the call to this method
     */
    public void updateFrame(String name, String fileName, int line) {
        this.name = name;
        this.fileName = fileName;
        this.line = line;
    }

    /**
     * Update the frame based on information from another frame. Used for
     * cloning frames (for blocks, usually) and when entering class bodies.
     * 
     * @param frame The frame whose data to duplicate in this frame
     */
    public void updateFrame(Frame frame) {
        assert frame.block != null : "Block uses null object pattern.  It should NEVER be null";

        this.self = frame.self;
        this.name = frame.name;
        this.klazz = frame.klazz;
        this.fileName = frame.fileName;
        this.line = frame.line;
        this.block = frame.block;
        this.visibility = frame.visibility;
        this.isBindingFrame = frame.isBindingFrame;
        
        // we force the lazy allocation of backref/lastline here to allow
        // closures to update the original frame
        frame.lazyBackrefAndLastline();
        this.backrefAndLastline = frame.backrefAndLastline;
    }

    /**
     * Update the frame based on the given values.
     * 
     * @param klazz The class against which the method is being called
     * @param self The 'self' for the method
     * @param name The name under which the method is being invoked
     * @param block The block passed to the method
     * @param fileName The filename of the calling method
     * @param line The line number where the call is being made
     * @param jumpTarget The target for non-local jumps (return in block)
     */
    public void updateFrame(RubyModule klazz, IRubyObject self, String name,
                 Block block, String fileName, int line) {
        assert block != null : "Block uses null object pattern.  It should NEVER be null";

        this.self = self;
        this.name = name;
        this.klazz = klazz;
        this.fileName = fileName;
        this.line = line;
        this.block = block;
        this.visibility = Visibility.PUBLIC;
        this.isBindingFrame = false;
    }

    /**
     * Update the frame based on the given values.
     * 
     * @param klazz The class against which the method is being called
     * @param self The 'self' for the method
     * @param name The name under which the method is being invoked
     * @param block The block passed to the method
     * @param fileName The filename of the calling method
     * @param line The line number where the call is being made
     * @param jumpTarget The target for non-local jumps (return in block)
     */
    public void updateFrameForEval(IRubyObject self, String fileName, int line) {
        this.self = self;
        this.name = null;
        this.fileName = fileName;
        this.line = line;
        this.visibility = Visibility.PRIVATE;
        this.isBindingFrame = false;
    }

    /**
     * Clear the frame, as when the call completes. Clearing prevents cached
     * frames from holding references after the call is done.
     */
    public void clear() {
        this.self = null;
        this.klazz = null;
        this.block = Block.NULL_BLOCK;
        this.backrefAndLastline = null;
    }
    
    /**
     * Clone this frame.
     * 
     * @return A new frame with duplicate information to the target frame
     */
    public Frame duplicate() {
        return new Frame(this);
    }

    /**
     * Get the jump target for non-local returns in this frame.
     * 
     * @return The jump target for non-local returns
     */
    public JumpTarget getJumpTarget() {
        return jumpTarget;
    }

    /**
     * Set the jump target for non-local returns in this frame.
     * 
     * @param jumpTarget The new jump target for non-local returns
     */
    @Deprecated
    public void setJumpTarget(JumpTarget jumpTarget) {
    }

    /**
     * Get the backref for this frame.
     * 
     * @return The backref for this frame
     */
    public IRubyObject getBackRef() {
        if (hasBackref()) {
            return self.getRuntime().getNil();
        }
        return backrefAndLastline.backref;
    }
    
    /**
     * Whether a backref has been set for this frame.
     * 
     * @return True if a backref has been set; false otherwise
     */
    private boolean hasBackref() {
        return backrefAndLastline == null || backrefAndLastline.backref == null;
    }

    /**
     * Set the backref for this frame.
     * 
     * @param backref The new backref for this frame
     * @return The passed-in backref value
     */
    public IRubyObject setBackRef(IRubyObject backref) {
        lazyBackrefAndLastline();
        return this.backrefAndLastline.backref = backref;
    }

    /**
     * Get the lastline for this frame.
     * 
     * @return The lastline for this frame.
     */
    public IRubyObject getLastLine() {
        if (hasLastline()) {
            return self.getRuntime().getNil();
        }
        return backrefAndLastline.lastline;
    }
    
    /**
     * Whether a lastline has been set for this frame.
     * 
     * @return True if a lastline has been set; false otherwise
     */
    private boolean hasLastline() {
        return backrefAndLastline == null || backrefAndLastline.lastline == null;
    }

    /**
     * Set the lastline for this frame.
     * 
     * @param lastline The new lastline for this frame
     * @return The passed-in lastline value
     */
    public IRubyObject setLastLine(IRubyObject lastline) {
        lazyBackrefAndLastline();
        return this.backrefAndLastline.lastline = lastline;
    }
    
    /**
     * Initialize the backref/lastline tuple.
     */
    private void lazyBackrefAndLastline() {
         if (backrefAndLastline == null) backrefAndLastline = new BackrefAndLastline();
    }

    /**
     * Get the filename of the caller.
     * 
     * @return The filename of the caller
     */
    public String getFile() {
        return fileName;
    }

    /**
     * Set the filename of the caller.
     * @param fileName
     */
    public void setFile(String fileName) {
        this.fileName = fileName;
    }
    
    /**
     * Get the line number where this call is being made.
     * 
     * @return The line number where this call is being made
     */
    public int getLine() {
        return line;
    }
    
    /**
     * Set the line number where this call is being made
     * @param line The new line number where this call is being made
     */
    public void setLine(int line) {
        this.line = line;
    }

    /**
     * Set both the file and line
     */
    public void setFileAndLine(String file, int line) {
        this.fileName = file;
        this.line = line;
    }

    /** 
     * Return class that we are calling against
     * 
     * @return The class we are calling against
     */
    public RubyModule getKlazz() {
        return klazz;
    }

    /**
     * Set the class we are calling against.
     * 
     * @param klazz the new class
     */
    public void setKlazz(RubyModule klazz) {
        this.klazz = klazz;
    }

    /**
     * Set the method name associated with this frame
     * 
     * @param name the new name
     */
    public void setName(String name) {
        this.name = name;
    }

    /** 
     * Get the method name associated with this frame
     * 
     * @return the method name
     */
    public String getName() {
        return name;
    }

    /**
     * Get the self associated with this frame
     * 
     * @return The self for the frame
     */
    IRubyObject getSelf() {
        return self;
    }

    /** 
     * Set the self associated with this frame
     * 
     * @param self The new value of self
     */
    public void setSelf(IRubyObject self) {
        this.self = self;
    }
    
    /**
     * Get the visibility at the time of this frame
     * 
     * @return The visibility
     */
    public Visibility getVisibility() {
        return visibility;
    }
    
    /**
     * Change the visibility associated with this frame
     * 
     * @param visibility The new visibility
     */
    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }
    
    /**
     * Is this frame the frame which started a binding eval?
     * 
     * @return Whether this is a binding frame
     */
    public boolean isBindingFrame() {
        return isBindingFrame;
    }
    
    /**
     * Set whether this is a binding frame or not
     * 
     * @param isBindingFrame Whether this is a binding frame
     */
    public void setIsBindingFrame(boolean isBindingFrame) {
        this.isBindingFrame = isBindingFrame;
    }
    
    /**
     * Retrieve the block associated with this frame.
     * 
     * @return The block of this frame or NULL_BLOCK if no block given
     */
    public Block getBlock() {
        return block;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    public String toString() {
        StringBuilder sb = new StringBuilder(50);
        
        sb.append(fileName).append(':').append(line+1).append(':').append(klazz);
        if (name != null) sb.append(" in ").append(name);

        return sb.toString();
    }
}
