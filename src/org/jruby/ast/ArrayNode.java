/*
 * ArrayNode.java - description
 * Created on 24.02.2002, 20:51:56
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

import java.util.*;

import org.ablaf.ast.INode;
import org.ablaf.ast.visitor.INodeVisitor;
import org.ablaf.common.ISourcePosition;
import org.jruby.ast.types.*;
import org.jruby.ast.visitor.NodeVisitor;

/**
 * Represents an array. This could be an array literal, quoted words or
 * some args stuff.
 *
 * @author  jpetersen
 * @version $Revision$
 */
public class ArrayNode extends AbstractNode implements IListNode, ILiteralNode {
    private ArrayList list;

   
    public ArrayNode(ISourcePosition position) {
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
        if (list == null) {
            return Collections.EMPTY_LIST.iterator();
        } else {
            return list.iterator();
        }
    }

    /**
     * Accept for the visitor pattern.
     * @param iVisitor the visitor
     **/
    public void accept(INodeVisitor iVisitor) {
        ((NodeVisitor)iVisitor).visitArrayNode(this);
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

}
