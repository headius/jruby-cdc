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
import org.jruby.lexer.yacc.ISourcePosition;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * <p>Frame for a full (read: not 'fast') Ruby method invocation.  Any Ruby method which calls 
 * another Ruby method (or yields to a block) will get a Frame.  A fast method by contrast does 
 * not get a Frame because we know that we will not be calling/yielding.</p>  
 * 
 * A Frame is also needed for a few special cases:
 * <ul>
 * <li>Proc.new must check previous frame to get the block it is getting constructed for
 * <li>block_given? must check the previous frame to see if a block is active
 * </li>
 * 
 */
public final class Frame implements JumpTarget {
    /**
     * The class for the method we are invoking for this frame.  Note: This may not be the
     * class where the implementation of the method lives.
     */
    private RubyModule klazz;
    
    /**
     * The 'self' for this frame.
     */
    private IRubyObject self;
    
    /**
     * The name of the method being invoked in this frame.  Note: Blocks are backed by frames
     * and do not have a name.
     */
    private String name;

    /**
     * The block that was passed in for this frame (as either a block or a &amp;block argument).
     * The frame captures the block for super/zsuper, but also for Proc.new (with no arguments)
     * and also for block_given?.  Both of those methods needs access to the block of the 
     * previous frame to work.
     */ 
    private Block block;
    
    /**
     * Does this delimit a frame where an eval with binding occurred.  Used for stack traces.
     */
    private boolean isBindingFrame = false;

    /**
     * The current visibility for anything defined under this frame
     */
    private Visibility visibility = Visibility.PUBLIC;
    
    private JumpTarget jumpTarget;
    
    private IRubyObject backref;
    private IRubyObject lastline;

    public JumpTarget getJumpTarget() {
        return jumpTarget;
    }

    public void setJumpTarget(JumpTarget jumpTarget) {
        this.jumpTarget = jumpTarget;
    }
    
    public Frame() {
    }
    /**
     * The location in source where this block/method invocation is happening
     */
    private ISourcePosition position;

    public void updateFrame(ISourcePosition position) {
        updateFrame(null, null, null, Block.NULL_BLOCK, position, null); 
    }

    public void updateFrame(Frame frame) {
        assert frame.block != null : "Block uses null object pattern.  It should NEVER be null";

        this.self = frame.self;
        this.name = frame.name;
        this.klazz = frame.klazz;
        this.position = frame.position;
        this.block = frame.block;
        this.jumpTarget = frame.jumpTarget;
        this.visibility = frame.visibility;
        this.isBindingFrame = frame.isBindingFrame;
        this.backref = frame.backref;
        this.lastline = frame.lastline;
    }

    public void updateFrame(RubyModule klazz, IRubyObject self, String name,
                 Block block, ISourcePosition position, JumpTarget jumpTarget) {
        assert block != null : "Block uses null object pattern.  It should NEVER be null";

        this.self = self;
        this.name = name;
        this.klazz = klazz;
        this.position = position;
        this.block = block;
        this.jumpTarget = jumpTarget;
        this.visibility = Visibility.PUBLIC;
        this.isBindingFrame = false;
        this.backref = null;
        this.lastline = null;
    }
    
    public Frame duplicate() {
        Frame newFrame = new Frame();
        
        newFrame.updateFrame(this);
        
        return newFrame;
    }

    public IRubyObject getBackRef() {
        return backref == null ? self.getRuntime().getNil() : backref;
    }

    public void setBackRef(IRubyObject backref) {
        this.backref = backref;
    }

    public IRubyObject getLastLine() {
        return lastline == null ? self.getRuntime().getNil() : lastline;
    }

    public void setLastLine(IRubyObject lastline) {
        this.lastline = lastline;
    }

    /**
     * @return the frames current position
     */
    ISourcePosition getPosition() {
        return position;
    }

    /** 
     * Return class that we are supposedly calling for this invocation
     * 
     * @return the current class
     */
    public RubyModule getKlazz() {
        return klazz;
    }

    /**
     * Set class that this method is supposedly calling on.  Note: This is different than
     * a native method's implementation class.
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
     * @return the self
     */
    IRubyObject getSelf() {
        return self;
    }

    /** 
     * Set the self associated with this frame
     * 
     * @param self is the new value of self
     */
    public void setSelf(IRubyObject self) {
        this.self = self;
    }
    
    /**
     * Get the visibility at the time of this frame
     * 
     * @return the visibility
     */
    public Visibility getVisibility() {
        return visibility;
    }
    
    /**
     * Change the visibility associated with this frame
     * 
     * @param visibility the new visibility
     */
    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }
    
    /**
     * Is this frame the frame which started a binding eval?
     * 
     * @return true if it is a binding frame
     */
    public boolean isBindingFrame() {
        return isBindingFrame;
    }
    
    /**
     * Set whether this is a binding frame or not
     * 
     * @param isBindingFrame true if it is
     */
    public void setIsBindingFrame(boolean isBindingFrame) {
        this.isBindingFrame = isBindingFrame;
    }
    
    /**
     * What block is associated with this frame?
     * 
     * @return the block of this frame or NULL_BLOCK if no block given
     */
    public Block getBlock() {
        return block;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    public String toString() {
        StringBuffer sb = new StringBuffer(50);
        sb.append(position != null ? position.toString() : "-1");
        sb.append(':');
        sb.append(klazz + " " + name);
        if (name != null) {
            sb.append("in ");
            sb.append(name);
        }
        return sb.toString();
    }
}
