/*
 * BlockNode.java - description
 * Created on 27.02.2002, 12:22:41
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

import org.ablaf.ast.visitor.INodeVisitor;
import org.ablaf.ast.INode;
import org.ablaf.common.ISourcePosition;
import org.jruby.ast.types.IListNode;
import org.jruby.ast.visitor.NodeVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

/**
 * A structuring node (linked list of other nodes).
 * This type of node is used to structure the AST.
 * Used in many places it is created throught the {@link org.jruby.parser.ParserSupport#appendToBlock appendToBlock} method
 * @author  jpetersen
 * @version $Revision$
 */
public class BlockNode extends AbstractNode implements IListNode {
    static final long serialVersionUID = 6070308619613804520L;

    private ArrayList list;

    public BlockNode(ISourcePosition position) {
        super(position);
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
        return list != null ? list.iterator() : Collections.EMPTY_LIST.iterator();
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
     * Method used by visitors.
     * accepts the visitor
     * @param iVisitor the visitor to accept
     **/
    public void accept(INodeVisitor iVisitor) {
        ((NodeVisitor)iVisitor).visitBlockNode(this);
    }
}
