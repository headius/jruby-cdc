/*
 * BignumNode.java - description
 * Created on 23.02.2002, 22:23:09
 * 
 * Copyright (C) 2001, 2002 Jan Arne Petersen
 * Jan Arne Petersen <jpetersen@uni-bonn.de>
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
package org.jruby.ast;

import java.math.*;

import org.ablaf.common.*;

import org.jruby.ast.types.*;
import org.jruby.ast.visitor.*;

/** Represents a big integer literal.
 *
 * @author  jpetersen
 * @version $Revision$
 */
public class BignumNode extends AbstractNode implements ILiteralNode {
    private BigInteger value;

	public BignumNode(ISourcePosition position, BigInteger value) {
	    super(position);
	    
	    this.value = value;
	}
	
	public void accept(NodeVisitor visitor) {
	    visitor.visitBignumNode(this);
	}
	
    /**
     * Gets the value.
     * @return Returns a BigInteger
     */
    public BigInteger getValue() {
        return value;
    }

    /**
     * Sets the value.
     * @param value The value to set
     */
    public void setValue(BigInteger value) {
        this.value = value;
    }
}