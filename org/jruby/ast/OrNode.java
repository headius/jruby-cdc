/*
 * OrNode.java - No description
 * Created on 05. November 2001, 21:46
 * 
 * Copyright (C) 2001, 2002 Jan Arne Petersen, Alan Moore, Benoit Cerrina
 * Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Alan Moore <alan_moore@gmx.net>
 * Benoit Cerrina <b.cerrina@wanadoo.fr>
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

import org.ablaf.ast.*;
import org.ablaf.common.*;
import org.jruby.ast.visitor.*;

/**
 *
 * @author  jpetersen
 * @version $Revision$
 */
public class OrNode extends AbstractNode {
    private INode firstNode;
    private INode secondNode;

    public OrNode(ISourcePosition position, INode firstNode, INode secondNode) {
        super(position);

        this.firstNode = firstNode;
        this.secondNode = secondNode;
    }

    /**
     * Accept for the visitor pattern.
     * @param iVisitor the visitor
     **/
    public void accept(NodeVisitor iVisitor) {
        iVisitor.visitOrNode(this);
    }

    /**
     * Gets the firstNode.
     * @return Returns a INode
     */
    public INode getFirstNode() {
        return firstNode;
    }

    /**
     * Sets the firstNode.
     * @param firstNode The firstNode to set
     */
    public void setFirstNode(INode firstNode) {
        this.firstNode = firstNode;
    }

    /**
     * Gets the secondNode.
     * @return Returns a INode
     */
    public INode getSecondNode() {
        return secondNode;
    }

    /**
     * Sets the secondNode.
     * @param secondNode The secondNode to set
     */
    public void setSecondNode(INode secondNode) {
        this.secondNode = secondNode;
    }
}