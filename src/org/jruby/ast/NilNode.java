/*
 * NilNode.java - description
 * Created on 01.03.2002, 01:54:30
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

import org.ablaf.ast.visitor.INodeVisitor;
import org.ablaf.common.ISourcePosition;
import org.jruby.ast.types.INameNode;
import org.jruby.ast.visitor.NodeVisitor;

/**
 *
 * @author  jpetersen
 * @version $Revision$
 */
public class NilNode extends AbstractNode implements INameNode {
    static final long serialVersionUID = -8702073984472296708L;

    public NilNode(ISourcePosition position) {
        super(position);
    }

    /**
     * Accept for the visitor pattern.
     * @param iVisitor the visitor
     **/
    public void accept(INodeVisitor iVisitor) {
        ((NodeVisitor)iVisitor).visitNilNode(this);
    }
    
    /**
     * Name of nil node.
     **/
    public String getName() {
        return "nil";
    }
}
