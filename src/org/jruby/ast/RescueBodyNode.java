/*
 * RescueBodyNode.java - description
 * Created on 01.03.2002, 23:54:04
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

import org.ablaf.ast.INode;
import org.ablaf.ast.visitor.INodeVisitor;
import org.ablaf.common.ISourcePosition;
import org.jruby.ast.types.IListNode;
import org.jruby.ast.visitor.NodeVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author  jpetersen
 * @version $Revision$
 */
public class RescueBodyNode extends AbstractNode implements IListNode {
    static final long serialVersionUID = -6414517081810625663L;

    private final IListNode exceptionNodes;
    private final INode bodyNode;

    private List list = null;

    public RescueBodyNode(ISourcePosition position, IListNode exceptionNodes, INode bodyNode) {
        super(position);
        this.exceptionNodes = exceptionNodes;
        this.bodyNode = bodyNode;
    }

    /**
     * @see IListNode#add(INode)
     */
    public IListNode add(INode node) {
        if (list == null) {
            list = new ArrayList();
        }
        list.add(node);
        return this;
    }

    /**
     * @see IListNode#iterator()
     */
    public Iterator iterator() {
        if (list == null) {
            return Collections.EMPTY_LIST.iterator();
        } else {
            return list.iterator();
        }
    }
    
    /**
     * @see org.jruby.ast.types.IListNode#size()
     */
    public int size() {
        if (list == null) {
            return 0;
        }
        return list.size();
    }

    /**
     * Accept for the visitor pattern.
     * @param iVisitor the visitor
     **/
    public void accept(INodeVisitor iVisitor) {
        ((NodeVisitor)iVisitor).visitRescueBodyNode(this);
    }

    /**
     * Gets the bodyNode.
     * @return Returns a INode
     */
    public INode getBodyNode() {
        return bodyNode;
    }

    /**
     * Gets the exceptionNodes.
     * @return Returns a IListNode
     */
    public IListNode getExceptionNodes() {
        return exceptionNodes;
    }
}
