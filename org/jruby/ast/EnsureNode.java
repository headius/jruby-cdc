/*
 * EnsureNode.java - description
 * Created on 01.03.2002, 15:53:13
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

import org.ablaf.ast.*;
import org.ablaf.common.*;
import org.jruby.*;
import org.jruby.exceptions.*;
import org.jruby.ast.visitor.*;
import org.jruby.runtime.*;

/**
 *	an ensure statement.
 * @author  jpetersen
 * @version $Revision$
 */
public class EnsureNode extends AbstractNode {
    private INode bodyNode;
    private INode ensureNode;

    public EnsureNode(ISourcePosition position, INode bodyNode, INode ensureNode) {
        super(position);

        this.bodyNode = bodyNode;
        this.ensureNode = ensureNode;
    }

    /**
     * Accept for the visitor pattern.
     * @param iVisitor the visitor
     **/
    public void accept(NodeVisitor iVisitor) {
        iVisitor.visitEnsureNode(this);
    }

    /**
     * Gets the bodyNode.
     * @return Returns a INode
     */
    public INode getBodyNode() {
        return bodyNode;
    }

    /**
     * Sets the bodyNode.
     * @param bodyNode The bodyNode to set
     */
    public void setBodyNode(INode bodyNode) {
        this.bodyNode = bodyNode;
    }

    /**
     * Gets the ensureNode.
     * @return Returns a INode
     */
    public INode getEnsureNode() {
        return ensureNode;
    }

    /**
     * Sets the ensureNode.
     * @param ensureNode The ensureNode to set
     */
    public void setEnsureNode(INode ensureNode) {
        this.ensureNode = ensureNode;
    }
}
