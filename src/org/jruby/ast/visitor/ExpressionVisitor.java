/*
 * ValueExpressionVisitor.java - description
 * Created on 19.02.2002, 14:26:33
 * 
 * Copyright (C) 2001, 2002 Jan Arne Petersen
 * Copyright (C) 2004 Thomas E Enebo
 * Jan Arne Petersen <jpetersen@uni-bonn.de>
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
package org.jruby.ast.visitor;

import org.jruby.ast.BeginNode;
import org.jruby.ast.BlockNode;
import org.jruby.ast.BreakNode;
import org.jruby.ast.ClassNode;
import org.jruby.ast.DefnNode;
import org.jruby.ast.DefsNode;
import org.jruby.ast.IfNode;
import org.jruby.ast.ModuleNode;
import org.jruby.ast.NewlineNode;
import org.jruby.ast.NextNode;
import org.jruby.ast.Node;
import org.jruby.ast.RedoNode;
import org.jruby.ast.RetryNode;
import org.jruby.ast.ReturnNode;
import org.jruby.ast.UntilNode;
import org.jruby.ast.WhileNode;
import org.jruby.ast.util.ListNodeUtil;

/**
 *
 * @author  jpetersen
 * @version $Revision$
 */
public class ExpressionVisitor extends AbstractVisitor {
    private boolean expression = false;
    
    public boolean isExpression(Node node) {
        acceptNode(node);
       	return isExpression();
    }

    /**
     * Gets the expression.
     * @return Returns a boolean
     */
    public boolean isExpression() {
        return expression;
    }

    /**
     * Sets the expression.
     * @param expression The expression to set
     */
    public void setExpression(boolean expression) {
        this.expression = expression;
    }

    /**
     * @see AbstractVisitor#visitNode(Node)
     */
    protected void visitNode(Node iVisited) {
        expression = true;
    }

    /**
     * @see NodeVisitor#visitBeginNode(BeginNode)
     */
    public void visitBeginNode(BeginNode iVisited) {
        acceptNode(iVisited.getBodyNode());
    }

    /**
     * @see NodeVisitor#visitBlockNode(BlockNode)
     */
    public void visitBlockNode(BlockNode iVisited) {
		acceptNode(ListNodeUtil.getLast(iVisited));
    }

    /**
     * @see NodeVisitor#visitBreakNode(BreakNode)
     */
    public void visitBreakNode(BreakNode iVisited) {
	acceptNode(iVisited.getValueNode());
    }

    /**
     * @see NodeVisitor#visitClassNode(ClassNode)
     */
    public void visitClassNode(ClassNode iVisited) {
        expression = false;
    }

    /**
     * @see NodeVisitor#visitDefnNode(DefnNode)
     */
    public void visitDefnNode(DefnNode iVisited) {
        expression = false;
    }

    /**
     * @see NodeVisitor#visitDefsNode(DefsNode)
     */
    public void visitDefsNode(DefsNode iVisited) {
        expression = false;
    }

    /**
     * @see NodeVisitor#visitIfNode(IfNode)
     */
    public void visitIfNode(IfNode iVisited) {
        expression = isExpression(iVisited.getThenBody()) &&
        isExpression(iVisited.getElseBody());
    }

    /**
     * @see NodeVisitor#visitModuleNode(ModuleNode)
     */
    public void visitModuleNode(ModuleNode iVisited) {
        expression = false;
    }

    /**
     * @see NodeVisitor#visitNewlineNode(NewlineNode)
     */
    public void visitNewlineNode(NewlineNode iVisited) {
        acceptNode(iVisited.getNextNode());
    }

    /**
     * @see NodeVisitor#visitNextNode(NextNode)
     */
    public void visitNextNode(NextNode iVisited) {
        expression = false;
    }

    /**
     * @see NodeVisitor#visitRedoNode(RedoNode)
     */
    public void visitRedoNode(RedoNode iVisited) {
        expression = false;
    }
    
    /**
     * @see NodeVisitor#visitRetryNode(RetryNode)
     */
    public void visitRetryNode(RetryNode iVisited) {
        expression = false;
    }

    /**
     * @see NodeVisitor#visitReturnNode(ReturnNode)
     */
    public void visitReturnNode(ReturnNode iVisited) {
        expression = false;
    }

    /**
     * @see NodeVisitor#visitUntilNode(UntilNode)
     */
    public void visitUntilNode(UntilNode iVisited) {
        expression = false;
    }

    /**
     * @see NodeVisitor#visitWhileNode(WhileNode)
     */
    public void visitWhileNode(WhileNode iVisited) {
        expression = false;
    }
}
