/*
 ***** BEGIN LICENSE BLOCK *****
 * Version: CPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Common Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/cpl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2001-2002 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2001-2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2002 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * 
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the CPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the CPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jruby.ast;

import java.util.List;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.ast.visitor.NodeVisitor;
import org.jruby.evaluator.ASTInterpreter;
import org.jruby.evaluator.Instruction;
import org.jruby.javasupport.util.RuntimeHelpers;
import org.jruby.lexer.yacc.ISourcePosition;
import org.jruby.runtime.Block;
import org.jruby.runtime.EventHook;
import org.jruby.runtime.MethodIndex;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * A Case statement.  Represents a complete case statement, including the body with its
 * when statements.
 */
public class CaseNode extends Node {
	/**
	 * the case expression.
	 **/
    private final Node caseNode;
	/**
	 * the body of the case.
	 */
    private final Node caseBody;
    
    public CaseNode(ISourcePosition position, Node caseNode, Node caseBody) {
        super(position, NodeType.CASENODE);
        
        assert caseBody != null : "caseBody is not null";
        // TODO: Rewriter and compiler assume case when empty expression.  In MRI this is just
        // a when.
//        assert caseNode != null : "caseNode is not null";
        
        this.caseNode = caseNode;
        this.caseBody = caseBody;
    }

 	/**
     * Accept for the visitor pattern.
     * @param iVisitor the visitor
     **/
    public Instruction accept(NodeVisitor iVisitor) {
        return iVisitor.visitCaseNode(this);
    }

    /**
     * Gets the caseNode.
	 * caseNode is the case expression 
     * @return caseNode
     */
    public Node getCaseNode() {
        return caseNode;
    }

    /**
     * Gets the first whenNode.
	 * the body of the case statement, the first of a list of WhenNodes
     * @return whenNode
     */
    public Node getFirstWhenNode() {
        return caseBody;
    }
    
    public List<Node> childNodes() {
        return Node.createList(caseNode, caseBody);
    }
    
    @Override
    public IRubyObject interpret(Ruby runtime, ThreadContext context, IRubyObject self, Block aBlock) {
        IRubyObject expression = null;
        
        if (caseNode != null) {
            expression = caseNode.interpret(runtime, context, self, aBlock);
        }

        context.pollThreadEvents();

        IRubyObject result = runtime.getNil();

        Node firstWhenNode = caseBody;
        while (firstWhenNode != null) {
            if (!(firstWhenNode instanceof WhenNode)) {
                return firstWhenNode.interpret(runtime, context, self, aBlock);
            }

            WhenNode whenNode = (WhenNode) firstWhenNode;

            if (whenNode.getExpressionNodes() instanceof ArrayNode) {
                ArrayNode arrayNode = (ArrayNode)whenNode.getExpressionNodes();
                // All expressions in a while are in same file
                context.setFile(arrayNode.getPosition().getFile());
                for (int i = 0; i < arrayNode.size(); i++) {
                    Node tag = arrayNode.get(i);

                    context.setLine(tag.getPosition().getStartLine());
                    
                    if (ASTInterpreter.isTrace(runtime)) {
                        ASTInterpreter.callTraceFunction(runtime, context, EventHook.RUBY_EVENT_LINE);
                    }

                    // Ruby grammar has nested whens in a case body because of
                    // productions case_body and when_args.
                    if (tag instanceof WhenNode) {
                        IRubyObject expressionsObject = ((WhenNode) tag).getExpressionNodes().interpret(runtime, context, self, aBlock);
                        RubyArray expressions = RuntimeHelpers.splatValue(expressionsObject);

                        for (int j = 0,k = expressions.getLength(); j < k; j++) {
                            IRubyObject condition = expressions.eltInternal(j);

                            if ((expression != null && condition.callMethod(context, MethodIndex.OP_EQQ, "===", expression)
                                    .isTrue())
                                    || (expression == null && condition.isTrue())) {
                                return firstWhenNode.interpret(runtime, context, self, aBlock);
                            }
                        }
                        continue;
                    }

                    result = tag.interpret(runtime,context, self, aBlock);

                    if ((expression != null && result.callMethod(context, MethodIndex.OP_EQQ, "===", expression).isTrue())
                            || (expression == null && result.isTrue())) {
                        return whenNode.interpret(runtime, context, self, aBlock);
                    }
                }
            } else {
                result = whenNode.getExpressionNodes().interpret(runtime,context, self, aBlock);

                if ((expression != null && result.callMethod(context, MethodIndex.OP_EQQ, "===", expression).isTrue())
                        || (expression == null && result.isTrue())) {
                    return firstWhenNode.interpret(runtime, context, self, aBlock);
                }
            }

            context.pollThreadEvents();

            firstWhenNode = whenNode.getNextCase();
        }

        return runtime.getNil();        
    }
}
