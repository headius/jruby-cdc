/*
 * AliasNode.java - description
 * Created on 26.02.2002, 16:01:47
 * 
 * Copyright (C) 2001, 2002 Jan Arne Petersen, Benoit Cerrina
 * Jan Arne Petersen <jpetersen@uni-bonn.de>
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

import org.ablaf.ast.visitor.INodeVisitor;
import org.ablaf.common.ISourcePosition;
import org.jruby.ast.visitor.NodeVisitor;

/** An AliasNode represents an alias statement.
 * ast node for the 
 * <code>alias newName oldName</code>
 * @author  jpetersen
 * @version $Revision$
 */
public class AliasNode extends AbstractNode {
    static final long serialVersionUID = -498707070925086399L;

    private final String oldName;
    private final String newName;

    public AliasNode(ISourcePosition position, String newName, String oldName) {
        super(position);
        this.oldName = oldName;
        this.newName = newName;
    }

    /**
     * Accept for the visitor pattern.
     * @param iVisitor the visitor
     **/
    public void accept(INodeVisitor iVisitor) {
        ((NodeVisitor)iVisitor).visitAliasNode(this);
    }

    /**
     * Gets the newName.
     * @return the newName as in the alias statement :  <code> alias <b>newName</b> oldName </code>
     */
    public String getNewName() {
        return newName;
    }

    /**
     * Gets the oldName.
     * @return the oldName as in the alias statement :  <code> alias newName <b>oldName</b></code>
     */
    public String getOldName() {
        return oldName;
    }
}
