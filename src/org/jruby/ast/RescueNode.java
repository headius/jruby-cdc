/*
 * RescueNode.java - description
 * Created on 01.03.2002, 23:52:26
 * 
 * Copyright (C) 2001, 2002 Jan Arne Petersen
 * Jan Arne Petersen <jpetersen@uni-bonn.de>
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
package org.jruby.ast;

import org.ablaf.ast.INode;
import org.ablaf.ast.visitor.INodeVisitor;
import org.ablaf.common.ISourcePosition;
import org.jruby.ast.visitor.NodeVisitor;

/**
 *
 * @author  jpetersen
 * @version $Revision$
 */
public class RescueNode extends AbstractNode {
    static final long serialVersionUID = -4757038578511808125L;

    private INode bodyNode;
    private RescueBodyNode rescueNode;
    private INode elseNode;
    
    public RescueNode(ISourcePosition position, INode bodyNode, RescueBodyNode rescueNode, INode elseNode) {
        super(position);
        this.bodyNode = bodyNode;
        this.rescueNode = rescueNode;
        this.elseNode = elseNode;
    }

    /**
     * Accept for the visitor pattern.
     * @param iVisitor the visitor
     **/
    public void accept(INodeVisitor iVisitor) {
        ((NodeVisitor)iVisitor).visitRescueNode(this);
    }

    /**
     * Gets the bodyNode.
     * @return Returns a INode
     */
    public INode getBodyNode() {
        return bodyNode;
    }

    /**
     * Gets the elseNode.
     * @return Returns a INode
     */
    public INode getElseNode() {
        return elseNode;
    }

    /**
     * Gets the first rescueNode.
     * @return Returns a INode
     */
    public RescueBodyNode getRescueNode() {
        return rescueNode;
    }
}
