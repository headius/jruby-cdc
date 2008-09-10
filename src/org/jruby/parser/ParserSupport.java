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
 * Copyright (C) 2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2002-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2004 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2006-2007 Mirko Stocker <me@misto.ch>
 * Copyright (C) 2006 Thomas Corbat <tcorbat@hsr.ch>
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
package org.jruby.parser;

import org.jruby.ast.AndNode;
import org.jruby.ast.ArgsCatNode;
import org.jruby.ast.ArgsPushNode;
import org.jruby.ast.ArgumentNode;
import org.jruby.ast.ArrayNode;
import org.jruby.ast.AssignableNode;
import org.jruby.ast.AttrAssignNode;
import org.jruby.ast.AttrAssignOneArgNode;
import org.jruby.ast.AttrAssignThreeArgNode;
import org.jruby.ast.AttrAssignTwoArgNode;
import org.jruby.ast.BackRefNode;
import org.jruby.ast.BeginNode;
import org.jruby.ast.BignumNode;
import org.jruby.ast.BlockNode;
import org.jruby.ast.BlockPassNode;
import org.jruby.ast.BreakNode;
import org.jruby.ast.CallManyArgsBlockNode;
import org.jruby.ast.CallManyArgsBlockPassNode;
import org.jruby.ast.CallManyArgsNode;
import org.jruby.ast.CallNoArgBlockNode;
import org.jruby.ast.CallNoArgBlockPassNode;
import org.jruby.ast.CallNoArgNode;
import org.jruby.ast.CallNode;
import org.jruby.ast.CallOneArgNode;
import org.jruby.ast.CallOneArgBlockNode;
import org.jruby.ast.CallOneArgBlockPassNode;
import org.jruby.ast.CallSpecialArgNode;
import org.jruby.ast.CallSpecialArgBlockNode;
import org.jruby.ast.CallSpecialArgBlockPassNode;
import org.jruby.ast.CallThreeArgBlockNode;
import org.jruby.ast.CallThreeArgBlockPassNode;
import org.jruby.ast.CallThreeArgNode;
import org.jruby.ast.CallTwoArgBlockNode;
import org.jruby.ast.CallTwoArgBlockPassNode;
import org.jruby.ast.CallTwoArgNode;
import org.jruby.ast.ClassVarAsgnNode;
import org.jruby.ast.ClassVarDeclNode;
import org.jruby.ast.ClassVarNode;
import org.jruby.ast.ConstDeclNode;
import org.jruby.ast.ConstNode;
import org.jruby.ast.DAsgnNode;
import org.jruby.ast.DRegexpNode;
import org.jruby.ast.DStrNode;
import org.jruby.ast.DotNode;
import org.jruby.ast.EvStrNode;
import org.jruby.ast.FCallManyArgsBlockNode;
import org.jruby.ast.FCallManyArgsBlockPassNode;
import org.jruby.ast.FCallManyArgsNode;
import org.jruby.ast.FCallNoArgBlockNode;
import org.jruby.ast.FCallNoArgBlockPassNode;
import org.jruby.ast.FCallNoArgNode;
import org.jruby.ast.FCallOneArgBlockNode;
import org.jruby.ast.FCallOneArgBlockPassNode;
import org.jruby.ast.FCallOneArgNode;
import org.jruby.ast.FCallSpecialArgBlockNode;
import org.jruby.ast.FCallSpecialArgBlockPassNode;
import org.jruby.ast.FCallSpecialArgNode;
import org.jruby.ast.FCallThreeArgBlockNode;
import org.jruby.ast.FCallThreeArgBlockPassNode;
import org.jruby.ast.FCallThreeArgNode;
import org.jruby.ast.FCallTwoArgBlockNode;
import org.jruby.ast.FCallTwoArgBlockPassNode;
import org.jruby.ast.FCallTwoArgNode;
import org.jruby.ast.FalseNode;
import org.jruby.ast.FixnumNode;
import org.jruby.ast.FlipNode;
import org.jruby.ast.FloatNode;
import org.jruby.ast.GlobalAsgnNode;
import org.jruby.ast.GlobalVarNode;
import org.jruby.ast.IArgumentNode;
import org.jruby.ast.IfNode;
import org.jruby.ast.InstAsgnNode;
import org.jruby.ast.InstVarNode;
import org.jruby.ast.IterNode;
import org.jruby.ast.ListNode;
import org.jruby.ast.LocalAsgnNode;
import org.jruby.ast.Match2Node;
import org.jruby.ast.Match3Node;
import org.jruby.ast.MatchNode;
import org.jruby.ast.MultipleAsgnNode;
import org.jruby.ast.NewlineNode;
import org.jruby.ast.NilImplicitNode;
import org.jruby.ast.NilNode;
import org.jruby.ast.Node;
import org.jruby.ast.NthRefNode;
import org.jruby.ast.OpElementAsgnNode;
import org.jruby.ast.OpElementOneArgAndAsgnNode;
import org.jruby.ast.OpElementOneArgAsgnNode;
import org.jruby.ast.OpElementOneArgOrAsgnNode;
import org.jruby.ast.OrNode;
import org.jruby.ast.RegexpNode;
import org.jruby.ast.RootNode;
import org.jruby.ast.SValueNode;
import org.jruby.ast.SelfNode;
import org.jruby.ast.SplatNode;
import org.jruby.ast.StrNode;
import org.jruby.ast.SuperNode;
import org.jruby.ast.SymbolNode;
import org.jruby.ast.TrueNode;
import org.jruby.ast.WhenNode;
import org.jruby.ast.YieldNode;
import org.jruby.ast.types.ILiteralNode;
import org.jruby.ast.types.INameNode;
import org.jruby.common.IRubyWarnings;
import org.jruby.common.IRubyWarnings.ID;
import org.jruby.lexer.yacc.ISourcePosition;
import org.jruby.lexer.yacc.ISourcePositionHolder;
import org.jruby.lexer.yacc.IDESourcePosition;
import org.jruby.lexer.yacc.SyntaxException;
import org.jruby.lexer.yacc.Token;
import org.jruby.lexer.yacc.SyntaxException.PID;
import org.jruby.runtime.DynamicScope;
import org.jruby.util.ByteList;

/** 
 *
 */
public class ParserSupport {
    // Parser states:
    private StaticScope currentScope;
    
    // Is the parser current within a singleton (value is number of nested singletons)
    private int inSingleton;
    
    // Is the parser currently within a method definition
    private boolean inDefinition;

    private IRubyWarnings warnings;

    private ParserConfiguration configuration;
    private RubyParserResult result;

    public void reset() {
        inSingleton = 0;
        inDefinition = false;
    }
    
    public void allowDubyExtension(ISourcePosition position) {
        if (!configuration.isDubyExtensionsEnabled()) {
            throw new SyntaxException(PID.DUBY_EXTENSIONS_OFF, position, "Duby extensions not configured");
        }
    }
    
    public StaticScope getCurrentScope() {
        return currentScope;
    }
    
    public ParserConfiguration getConfiguration() {
        return configuration;
    }
    
    public void popCurrentScope() {
        currentScope = currentScope.getEnclosingScope();
    }
    
    public void pushBlockScope() {
        currentScope = new BlockStaticScope(currentScope);
    }
    
    public void pushLocalScope() {
        currentScope = new LocalStaticScope(currentScope);
    }
    
    public Node arg_concat(ISourcePosition position, Node node1, Node node2) {
        return node2 == null ? node1 : new ArgsCatNode(position, node1, node2);
    }

    public Node arg_blk_pass(Node firstNode, BlockPassNode secondNode) {
        if (secondNode != null) {
            secondNode.setArgsNode(firstNode);
            return secondNode;
        }
        return firstNode;
    }

    /**
     * We know for callers of this that it cannot be any of the specials checked in gettable.
     * 
     * @param node to check its variable type
     * @return an AST node representing this new variable
     */
    public Node gettable2(Node node) {
        switch (node.nodeId) {
        case DASGNNODE: // LOCALVAR
        case LOCALASGNNODE:
            return currentScope.declare(node.getPosition(), ((INameNode) node).getName());
        case CONSTDECLNODE: // CONSTANT
            return currentScope.declare(node.getPosition(), ((INameNode) node).getName());
        case INSTASGNNODE: // INSTANCE VARIABLE
            return new InstVarNode(node.getPosition(), ((INameNode) node).getName());
        case CLASSVARDECLNODE:
        case CLASSVARASGNNODE:
            return new ClassVarNode(node.getPosition(), ((INameNode) node).getName());
        case GLOBALASGNNODE:
            return new GlobalVarNode(node.getPosition(), ((INameNode) node).getName());
        }
        
        throw new SyntaxException(PID.BAD_IDENTIFIER, node.getPosition(), "identifier " + 
                ((INameNode) node).getName() + " is not valid", ((INameNode) node).getName());
    }
    
    /**
     * Create AST node representing variable type it represents.
     * 
     * @param token to check its variable type
     * @return an AST node representing this new variable
     */
    public Node gettable(Token token) {
        switch (token.getType()) {
        case Tokens.kSELF:
            return new SelfNode(token.getPosition());
        case Tokens.kNIL:
            return new NilNode(token.getPosition());
        case Tokens.kTRUE:
            return new TrueNode(token.getPosition());
        case Tokens.kFALSE:
            return new FalseNode(token.getPosition());
        case Tokens.k__FILE__:
            return new StrNode(token.getPosition(), ByteList.create(token.getPosition().getFile()));
        case Tokens.k__LINE__:
            return new FixnumNode(token.getPosition(), token.getPosition().getEndLine()+1);
        case Tokens.tIDENTIFIER:
            return currentScope.declare(token.getPosition(), (String) token.getValue());
        case Tokens.tCONSTANT:
            return new ConstNode(token.getPosition(), (String) token.getValue());
        case Tokens.tIVAR:
            return new InstVarNode(token.getPosition(), (String) token.getValue());
        case Tokens.tCVAR:
            return new ClassVarNode(token.getPosition(), (String) token.getValue());
        case Tokens.tGVAR:
            return new GlobalVarNode(token.getPosition(), (String) token.getValue());
        }

        throw new SyntaxException(PID.BAD_IDENTIFIER, token.getPosition(), "identifier " + 
                (String) token.getValue() + " is not valid", token.getValue());
    }
    
    public AssignableNode assignable(Token lhs, Node value) {
        checkExpression(value);

        switch (lhs.getType()) {
            case Tokens.kSELF:
                throw new SyntaxException(PID.CANNOT_CHANGE_SELF, lhs.getPosition(), "Can't change the value of self");
            case Tokens.kNIL:
                throw new SyntaxException(PID.INVALID_ASSIGNMENT, lhs.getPosition(), "Can't assign to nil", "nil");
            case Tokens.kTRUE:
                throw new SyntaxException(PID.INVALID_ASSIGNMENT, lhs.getPosition(), "Can't assign to true", "true");
            case Tokens.kFALSE:
                throw new SyntaxException(PID.INVALID_ASSIGNMENT, lhs.getPosition(), "Can't assign to false", "false");
            case Tokens.k__FILE__:
                throw new SyntaxException(PID.INVALID_ASSIGNMENT, lhs.getPosition(), "Can't assign to __FILE__", "__FILE__");
            case Tokens.k__LINE__:
                throw new SyntaxException(PID.INVALID_ASSIGNMENT, lhs.getPosition(), "Can't assign to __LINE__", "__LINE__");
            case Tokens.tIDENTIFIER:
                return currentScope.assign(value != NilImplicitNode.NIL ? union(lhs, value) : lhs.getPosition(), (String) lhs.getValue(), makeNullNil(value));
            case Tokens.tCONSTANT:
                if (isInDef() || isInSingle()) {
                    throw new SyntaxException(PID.DYNAMIC_CONSTANT_ASSIGNMENT, lhs.getPosition(), "dynamic constant assignment");
                }
                return new ConstDeclNode(lhs.getPosition(), (String) lhs.getValue(), null, value);
            case Tokens.tIVAR:
                return new InstAsgnNode(lhs.getPosition(), (String) lhs.getValue(), value);
            case Tokens.tCVAR:
                if (isInDef() || isInSingle()) {
                    return new ClassVarAsgnNode(lhs.getPosition(), (String) lhs.getValue(), value);
                }
                return new ClassVarDeclNode(lhs.getPosition(), (String) lhs.getValue(), value);
            case Tokens.tGVAR:
                return new GlobalAsgnNode(lhs.getPosition(), (String) lhs.getValue(), value);
        }

        throw new SyntaxException(PID.BAD_IDENTIFIER, lhs.getPosition(), "identifier " + 
                (String) lhs.getValue() + " is not valid", lhs.getValue());
    }

    /**
     *  Wraps node with NEWLINE node.
     *
     *@param node
     *@return a NewlineNode or null if node is null.
     */
    public Node newline_node(Node node, ISourcePosition position) {
        if (node == null) return null;
        
        return node instanceof NewlineNode ? node : new NewlineNode(position, node); 
    }
    
    public ISourcePosition union(ISourcePositionHolder first, ISourcePositionHolder second) {
        while (first instanceof NewlineNode) {
            first = ((NewlineNode) first).getNextNode();
        }

        while (second instanceof NewlineNode) {
            second = ((NewlineNode) second).getNextNode();
        }
        
        if (second == null) return first.getPosition();
        if (first == null) return second.getPosition();
        
        return first.getPosition().union(second.getPosition());
    }
    
    public ISourcePosition union(ISourcePosition first, ISourcePosition second) {
		if (first.getStartOffset() < second.getStartOffset()) return first.union(second); 

		return second.union(first);
	}
    
    public Node addRootNode(Node topOfAST, ISourcePosition position) {
        position = topOfAST != null ? topOfAST.getPosition() : position;

        if (result.getBeginNodes().size() == 0) {
            if (topOfAST == null) topOfAST = NilImplicitNode.NIL;
            
            return new RootNode(position, result.getScope(), topOfAST);
        }
        
        BlockNode newTopOfAST = new BlockNode(position);
        for (Node beginNode: result.getBeginNodes()) {
            appendToBlock(newTopOfAST, beginNode);
        }
        
        // Add real top to new top (unless this top is empty [only begin/end nodes or truly empty])
        if (topOfAST != null) newTopOfAST.add(topOfAST);
        
        return new RootNode(position, result.getScope(), newTopOfAST);
    }
    
    /* MRI: block_append */
    public Node appendToBlock(Node head, Node tail) {
        if (tail == null) return head;
        if (head == null) return tail;

        // Reduces overhead in interp by not set position every single line we encounter. 
        if (!configuration.hasExtraPositionInformation()) {
            head = compactNewlines(head);
        }

        if (!(head instanceof BlockNode)) {
            head = new BlockNode(head.getPosition()).add(head);
        }

        if (warnings.isVerbose() && isBreakStatement(((ListNode) head).getLast())) {
            warnings.warning(ID.STATEMENT_NOT_REACHED, tail.getPosition(), "Statement not reached.");
        }

        // Assumption: tail is never a list node
        ((ListNode) head).addAll(tail);
        head.setPosition(union(head, tail));
        return head;
    }

    public Node getOperatorCallNode(Node firstNode, String operator) {
        checkExpression(firstNode);

        return new CallNoArgNode(firstNode.getPosition(), firstNode, operator);
    }
    
    public Node getOperatorCallNode(Node firstNode, String operator, Node secondNode) {
        return getOperatorCallNode(firstNode, operator, secondNode, null);
    }

    public Node getOperatorCallNode(Node firstNode, String operator, Node secondNode, ISourcePosition defaultPosition) {
        if (defaultPosition != null) {
            firstNode = checkForNilNode(firstNode, defaultPosition);
            secondNode = checkForNilNode(secondNode, defaultPosition);
        }
        
        checkExpression(firstNode);
        checkExpression(secondNode);
        
        return new CallOneArgNode(union(firstNode.getPosition(), secondNode.getPosition()), firstNode, operator, new ArrayNode(secondNode.getPosition(), secondNode));
    }

    public Node getMatchNode(Node firstNode, Node secondNode) {
        if (firstNode instanceof DRegexpNode || firstNode instanceof RegexpNode) {
            return new Match2Node(firstNode.getPosition(), firstNode, secondNode);
        } else if (secondNode instanceof DRegexpNode || secondNode instanceof RegexpNode) {
            return new Match3Node(firstNode.getPosition(), secondNode, firstNode);
        } 

        return getOperatorCallNode(firstNode, "=~", secondNode);
    }

    /**
     * Define an array set condition so we can return lhs
     * 
     * @param receiver array being set
     * @param index node which should evalute to index of array set
     * @return an AttrAssignNode
     */
    public Node aryset(Node receiver, Node index) {
        checkExpression(receiver);

        return new_attrassign(receiver.getPosition(), receiver, "[]=", index);
    }

    /**
     * Define an attribute set condition so we can return lhs
     * 
     * @param receiver object which contains attribute
     * @param name of the attribute being set
     * @return an AttrAssignNode
     */
    public Node attrset(Node receiver, String name) {
        checkExpression(receiver);

        return new_attrassign(receiver.getPosition(), receiver, name + "=", null);
    }

    public void backrefAssignError(Node node) {
        if (node instanceof NthRefNode) {
            String varName = "$" + ((NthRefNode) node).getMatchNumber();
            throw new SyntaxException(PID.INVALID_ASSIGNMENT, node.getPosition(), 
                    "Can't set variable " + varName + '.', varName);
        } else if (node instanceof BackRefNode) {
            String varName = "$" + ((BackRefNode) node).getType();
            throw new SyntaxException(PID.INVALID_ASSIGNMENT, node.getPosition(), "Can't set variable " + varName + '.', varName);
        }
    }

    public Node arg_add(ISourcePosition position, Node node1, Node node2) {
        if (node1 == null) {
            if (node2 == null) {
                return new ArrayNode(position, NilImplicitNode.NIL);
            } else {
                return new ArrayNode(node2.getPosition(), node2);
            }
        }
        if (node1 instanceof ArrayNode) return ((ArrayNode) node1).add(node2);
        
        return new ArgsPushNode(position, node1, node2);
    }
    
	/**
	 * @fixme position
	 **/
    public Node node_assign(Node lhs, Node rhs) {
        if (lhs == null) return null;

        Node newNode = lhs;

        checkExpression(rhs);
        if (lhs instanceof AssignableNode) {
    	    ((AssignableNode) lhs).setValueNode(rhs);
    	    lhs.setPosition(union(lhs, rhs));
        } else if (lhs instanceof IArgumentNode) {
            IArgumentNode invokableNode = (IArgumentNode) lhs;
            
            return invokableNode.setArgsNode(arg_add(lhs.getPosition(), invokableNode.getArgsNode(), rhs));
        }
        
        return newNode;
    }
    
    public Node ret_args(Node node, ISourcePosition position) {
        if (node != null) {
            if (node instanceof BlockPassNode) {
                throw new SyntaxException(PID.DYNAMIC_CONSTANT_ASSIGNMENT, position, "Dynamic constant assignment.");
            } else if (node instanceof ArrayNode && ((ArrayNode)node).size() == 1) {
                node = ((ArrayNode)node).get(0);
            } else if (node instanceof SplatNode) {
                node = new SValueNode(position, node);
            }
        }
        
        return node;
    }

    /**
     * Is the supplied node a break/control statement?
     * 
     * @param node to be checked
     * @return true if a control node, false otherwise
     */
    public boolean isBreakStatement(Node node) {
        breakLoop: do {
            if (node == null) return false;

            switch (node.nodeId) {
            case NEWLINENODE:
                node = ((NewlineNode) node).getNextNode();
                continue breakLoop;
            case BREAKNODE: case NEXTNODE: case REDONODE:
            case RETRYNODE: case RETURNNODE:
                return true;
            default:
                return false;
            }
        } while (true);                    
    }
    
    public void warnUnlessEOption(ID id, Node node, String message) {
        if (!configuration.isInlineSource()) {
            warnings.warn(id, node.getPosition(), message);
        }
    }

    public void warningUnlessEOption(ID id, Node node, String message) {
        if (!configuration.isInlineSource()) {
            warnings.warning(id, node.getPosition(), message);
        }
    }

    /**
     * Does this node represent an expression?
     * @param node to be checked
     * @return true if an expression, false otherwise
     */
    public void checkExpression(Node node) {
        if (!isExpression(node)) {
            warnings.warning(ID.VOID_VALUE_EXPRESSION, node.getPosition(), "void value expression");
        }
    }

    private Node compactNewlines(Node head) {
        while (head instanceof NewlineNode) {
            Node nextNode = ((NewlineNode) head).getNextNode();

            if (!(nextNode instanceof NewlineNode)) {
                break;
            }
            head = nextNode;
        }
        return head;
    }
    
    private boolean isExpression(Node node) {
        do {
            if (node == null) return true;
            
            switch (node.nodeId) {
            case BEGINNODE:
                node = ((BeginNode) node).getBodyNode();
                break;
            case BLOCKNODE:
                node = ((BlockNode) node).getLast();
                break;
            case BREAKNODE:
                node = ((BreakNode) node).getValueNode();
                break;
            case CLASSNODE: case DEFNNODE: case DEFSNODE: case MODULENODE: case NEXTNODE: 
            case REDONODE: case RETRYNODE: case RETURNNODE: case UNTILNODE: case WHILENODE:
                return false;
            case IFNODE:
                return isExpression(((IfNode) node).getThenBody()) &&
                  isExpression(((IfNode) node).getElseBody());
            case NEWLINENODE:
                node = ((NewlineNode) node).getNextNode();
                break;
            default: // Node
                return true;
            }
        } while (true);
    }
    
    /**
     * Is this a literal in the sense that MRI has a NODE_LIT for.  This is different than
     * ILiteralNode.  We should pick a different name since ILiteralNode is something we created
     * which is similiar but used for a slightly different condition (can I do singleton things).
     * 
     * @param node to be tested
     * @return true if it is a literal
     */
    public boolean isLiteral(Node node) {
        return node != null && (node instanceof FixnumNode || node instanceof BignumNode || 
                node instanceof FloatNode || node instanceof SymbolNode || 
                (node instanceof RegexpNode && 
                        (((RegexpNode) node).getOptions() & ~ReOptions.RE_OPTION_ONCE) == 0));
    }

    private void handleUselessWarn(Node node, String useless) {
        warnings.warn(ID.USELESS_EXPRESSION, node.getPosition(), "Useless use of " + useless + " in void context.", useless);
    }

    /**
     * Check to see if current node is an useless statement.  If useless a warning if printed.
     * 
     * @param node to be checked.
     */
    public void checkUselessStatement(Node node) {
        if (!warnings.isVerbose()) return;
        
        uselessLoop: do {
            if (node == null) return;
            
            switch (node.nodeId) {
            case NEWLINENODE:
                node = ((NewlineNode) node).getNextNode();
                continue uselessLoop;
            case CALLNODE: {
                String name = ((CallNode) node).getName();
                
                if (name == "+" || name == "-" || name == "*" || name == "/" || name == "%" || 
                    name == "**" || name == "+@" || name == "-@" || name == "|" || name == "^" || 
                    name == "&" || name == "<=>" || name == ">" || name == ">=" || name == "<" || 
                    name == "<=" || name == "==" || name == "!=") {
                    handleUselessWarn(node, name);
                }
                return;
            }
            case BACKREFNODE: case DVARNODE: case GLOBALVARNODE:
            case LOCALVARNODE: case NTHREFNODE: case CLASSVARNODE:
            case INSTVARNODE:
                handleUselessWarn(node, "a variable"); return;
            // FIXME: Temporarily disabling because this fires way too much running Rails tests. JRUBY-518
            /*case CONSTNODE:
                handleUselessWarn(node, "a constant"); return;*/
            case BIGNUMNODE: case DREGEXPNODE: case DSTRNODE: case DSYMBOLNODE:
            case FIXNUMNODE: case FLOATNODE: case REGEXPNODE:
            case STRNODE: case SYMBOLNODE:
                handleUselessWarn(node, "a literal"); return;
            // FIXME: Temporarily disabling because this fires way too much running Rails tests. JRUBY-518
            /*case CLASSNODE: case COLON2NODE:
                handleUselessWarn(node, "::"); return;*/
            case DOTNODE:
                handleUselessWarn(node, ((DotNode) node).isExclusive() ? "..." : ".."); return;
            case DEFINEDNODE:
                handleUselessWarn(node, "defined?"); return;
            case FALSENODE:
                handleUselessWarn(node, "false"); return;
            case NILNODE: 
                handleUselessWarn(node, "nil"); return;
            // FIXME: Temporarily disabling because this fires way too much running Rails tests. JRUBY-518
            /*case SELFNODE:
                handleUselessWarn(node, "self"); return;*/
            case TRUENODE:
                handleUselessWarn(node, "true"); return;
            default: return;
            }
        } while (true);
    }

    /**
     * Check all nodes but the last one in a BlockNode for useless (void context) statements.
     * 
     * @param blockNode to be checked.
     */
    public void checkUselessStatements(BlockNode blockNode) {
        if (warnings.isVerbose()) {
            Node lastNode = blockNode.getLast();

            for (int i = 0; i < blockNode.size(); i++) {
                Node currentNode = blockNode.get(i);
        		
                if (lastNode != currentNode ) {
                    checkUselessStatement(currentNode);
                }
            }
        }
    }

	/**
	 * @fixme error handling
	 **/
    private boolean checkAssignmentInCondition(Node node) {
        if (node instanceof MultipleAsgnNode) {
            throw new SyntaxException(PID.MULTIPLE_ASSIGNMENT_IN_CONDITIONAL, node.getPosition(), "Multiple assignment in conditional.");
        } else if (node instanceof LocalAsgnNode || node instanceof DAsgnNode || node instanceof GlobalAsgnNode || node instanceof InstAsgnNode) {
            Node valueNode = ((AssignableNode) node).getValueNode();
            if (valueNode instanceof ILiteralNode || valueNode instanceof NilNode || valueNode instanceof TrueNode || valueNode instanceof FalseNode) {
                warnings.warn(ID.ASSIGNMENT_IN_CONDITIONAL, node.getPosition(), "Found '=' in conditional, should be '=='.");
            }
            return true;
        } 

        return false;
    }
    
    private Node makeNullNil(Node node) {
        return node == null ? NilImplicitNode.NIL : node;
    }

    private Node cond0(Node node) {
        checkAssignmentInCondition(node);
        
        Node leftNode = null;
        Node rightNode = null;

        switch(node.nodeId) {
        case DREGEXPNODE: {
            ISourcePosition position = node.getPosition();

            return new Match2Node(position, node, new GlobalVarNode(position, "$_"));
        }
        case ANDNODE:
            leftNode = cond0(((AndNode) node).getFirstNode());
            rightNode = cond0(((AndNode) node).getSecondNode());
            
            return new AndNode(node.getPosition(), makeNullNil(leftNode), makeNullNil(rightNode));
        case ORNODE:
            leftNode = cond0(((OrNode) node).getFirstNode());
            rightNode = cond0(((OrNode) node).getSecondNode());
            
            return new OrNode(node.getPosition(), makeNullNil(leftNode), makeNullNil(rightNode));
        case DOTNODE: {
            DotNode dotNode = (DotNode) node;
            if (dotNode.isLiteral()) return node; 
            
            String label = String.valueOf("FLIP" + node.hashCode());
            currentScope.getLocalScope().addVariable(label);
            int slot = currentScope.isDefined(label);
            
            return new FlipNode(node.getPosition(),
                    getFlipConditionNode(((DotNode) node).getBeginNode()),
                    getFlipConditionNode(((DotNode) node).getEndNode()),
                    dotNode.isExclusive(), slot);
        }
        case REGEXPNODE:
            warningUnlessEOption(ID.REGEXP_LITERAL_IN_CONDITION, node, "regex literal in condition");
            
            return new MatchNode(node.getPosition(), node);
        } 

        return node;
    }

    public Node getConditionNode(Node node) {
        if (node == null) return NilImplicitNode.NIL;

        if (node instanceof NewlineNode) {
            return new NewlineNode(node.getPosition(), cond0(((NewlineNode) node).getNextNode()));
        } 

        return cond0(node);
    }

    /* MRI: range_op */
    private Node getFlipConditionNode(Node node) {
        if (!configuration.isInlineSource()) return node;
        
        node = getConditionNode(node);

        if (node instanceof NewlineNode) return ((NewlineNode) node).getNextNode();
        
        if (node instanceof FixnumNode) {
            warnUnlessEOption(ID.LITERAL_IN_CONDITIONAL_RANGE, node, "integer literal in conditional range");
            return getOperatorCallNode(node, "==", new GlobalVarNode(node.getPosition(), "$."));
        } 

        return node;
    }
    
    public SplatNode newSplatNode(ISourcePosition position, Node node) {
        return new SplatNode(position, makeNullNil(node));
    }
    
    public ArrayNode newArrayNode(ISourcePosition position, Node firstNode) {
        return new ArrayNode(position, makeNullNil(firstNode));
    }

    public AndNode newAndNode(ISourcePosition position, Node left, Node right) {
        checkExpression(left);
        
        if (left == null && right == null) return new AndNode(position, makeNullNil(left), makeNullNil(right));
        
        return new AndNode(union(left, right), makeNullNil(left), makeNullNil(right));
    }

    public OrNode newOrNode(ISourcePosition position, Node left, Node right) {
        checkExpression(left);

        if (left == null && right == null) return new OrNode(position, makeNullNil(left), makeNullNil(right));
        
        return new OrNode(union(left, right), makeNullNil(left), makeNullNil(right));
    }
    
    public WhenNode newWhenNode(ISourcePosition position, Node expressionNodes, Node bodyNode, Node nextCase) {
        if (bodyNode == null) bodyNode = NilImplicitNode.NIL;

        return new WhenNode(position, expressionNodes, bodyNode, nextCase);
    }

    public Node getReturnArgsNode(Node node) {
        if (node instanceof ArrayNode && ((ArrayNode) node).size() == 1) { 
            return ((ListNode) node).get(0);
        } else if (node instanceof BlockPassNode) {
            throw new SyntaxException(PID.BLOCK_ARG_UNEXPECTED, node.getPosition(), "Block argument should not be given.");
        }
        return node;
    }
    
    public Node new_opElementAsgnNode(ISourcePosition position, Node receiverNode, String operatorName, Node argsNode, Node valueNode) {
        if (argsNode instanceof ArrayNode) {
            ArrayNode array = (ArrayNode) argsNode;
            
            if (array.size() == 1) {
                if (operatorName.equals("||")) {
                    return new OpElementOneArgOrAsgnNode(position, receiverNode, operatorName, array, valueNode);
                } else if (operatorName.equals("&&")) {
                    return new OpElementOneArgAndAsgnNode(position, receiverNode, operatorName, array, valueNode);                    
                } else {
                    return new OpElementOneArgAsgnNode(position, receiverNode, operatorName, array, valueNode);
                }
            }
        }
        return new OpElementAsgnNode(position, receiverNode, operatorName, argsNode, valueNode);
    }
    
    public Node new_attrassign(ISourcePosition position, Node receiver, String name, Node args) {
        if (!(args instanceof ArrayNode)) return new AttrAssignNode(position, receiver, name, args);
        
        ArrayNode argsNode = (ArrayNode) args;
        
        switch (argsNode.size()) {
            case 1:
                return new AttrAssignOneArgNode(position, receiver, name, argsNode);
            case 2:
                return new AttrAssignTwoArgNode(position, receiver, name, argsNode);
            case 3:
                return new AttrAssignThreeArgNode(position, receiver, name, argsNode);
            default:
                return new AttrAssignNode(position, receiver, name, argsNode);
        }
    }
    
    private Node new_call_noargs(Node receiver, Token name, IterNode iter) {
        ISourcePosition position = union(receiver, name);
        
        if (receiver == null) receiver = NilImplicitNode.NIL;
        
        if (iter != null) return new CallNoArgBlockNode(position, receiver, (String) name.getValue(), iter);
        
        return new CallNoArgNode(position, receiver, (String) name.getValue());
    }
    
    private Node new_call_complexargs(Node receiver, Token name, Node args, Node iter) {
        if (args instanceof BlockPassNode) {
            // Block and block pass passed in at same time....uh oh
            if (iter != null) {
                throw new SyntaxException(PID.BLOCK_ARG_AND_BLOCK_GIVEN, iter.getPosition(), "Both block arg and actual block given.");
            }

            return new_call_blockpass(receiver, name, (BlockPassNode) args);
        }

        if (iter != null) return new CallSpecialArgBlockNode(union(receiver, args), receiver,(String) name.getValue(), args, (IterNode) iter);

        return new CallSpecialArgNode(union(receiver, args), receiver, (String) name.getValue(), args);
    }
    
    private Node new_call_blockpass(Node receiver, Token operation, BlockPassNode blockPass) {
        ISourcePosition position = union(receiver, blockPass);
        String name = (String) operation.getValue();
        Node args = blockPass.getArgsNode();
        
        if (args == null) return new CallNoArgBlockPassNode(position, receiver, name, args, blockPass);
        if (!(args instanceof ArrayNode)) return new CallSpecialArgBlockPassNode(position, receiver, name, args, blockPass);
        
        switch (((ArrayNode) args).size()) {
            case 0:  // foo()
                return new CallNoArgBlockPassNode(position, receiver, name, args, blockPass);
            case 1:
                return new CallOneArgBlockPassNode(position, receiver, name, (ArrayNode) args, blockPass);
            case 2:
                return new CallTwoArgBlockPassNode(position, receiver, name, (ArrayNode) args, blockPass);
            case 3:
                return new CallThreeArgBlockPassNode(position, receiver, name, (ArrayNode) args, blockPass);
            default:
                return new CallManyArgsBlockPassNode(position, receiver, name, args, blockPass);
        } 
    }

    public Node new_call(Node receiver, Token name, Node argsNode, Node iter) {
        if (argsNode == null) return new_call_noargs(receiver, name, (IterNode) iter);
        if (!(argsNode instanceof ArrayNode)) return new_call_complexargs(receiver, name, argsNode, iter);
        
        ArrayNode args = (ArrayNode) argsNode;

        switch (args.size()) {
            case 0:
                if (iter != null) return new CallNoArgBlockNode(union(receiver, args), receiver, (String) name.getValue(), args, (IterNode) iter);
                    
                return new CallNoArgNode(union(receiver, args), receiver, args, (String) name.getValue());
            case 1:
                if (iter != null) return new CallOneArgBlockNode(union(receiver, args), receiver, (String) name.getValue(), args, (IterNode) iter);
                
                return new CallOneArgNode(union(receiver, args), receiver, (String) name.getValue(), args);
            case 2:
                if (iter != null) return new CallTwoArgBlockNode(union(receiver, args), receiver, (String) name.getValue(), args, (IterNode) iter);
                
                return new CallTwoArgNode(union(receiver, args), receiver, (String) name.getValue(), args);
            case 3:
                if (iter != null) return new CallThreeArgBlockNode(union(receiver, args), receiver, (String) name.getValue(), args, (IterNode) iter);
                
                return new CallThreeArgNode(union(receiver, args), receiver, (String) name.getValue(), args);
            default:
                if (iter != null) return new CallManyArgsBlockNode(union(receiver, args), receiver, (String) name.getValue(), args, (IterNode) iter);

                return new CallManyArgsNode(union(receiver, args), receiver, (String) name.getValue(), args);
        }
    }
    
    private Node new_fcall_noargs(Token operation, IterNode iter) {
        if (iter != null) return new FCallNoArgBlockNode(operation.getPosition(), (String) operation.getValue(), iter);
        return new FCallNoArgNode(operation.getPosition(), (String) operation.getValue());
    }
    
    private Node new_fcall_simpleargs(Token operation, ArrayNode args, Node iter) {
        String name = (String) operation.getValue();
            
        switch (args.size()) {
            case 0:  // foo()
                if (iter != null) return new FCallNoArgBlockNode(union(operation, args), name, args, (IterNode) iter);
                    
                return new FCallNoArgNode(union(operation, args), args, name);
            case 1:
                if (iter != null) return new FCallOneArgBlockNode(union(operation, args), name, args, (IterNode) iter);
                
                return new FCallOneArgNode(union(operation, args), name, args);
            case 2:
                if (iter != null) return new FCallTwoArgBlockNode(union(operation, args), name, args, (IterNode) iter);
                
                return new FCallTwoArgNode(union(operation, args), name, args);
            case 3:
                if (iter != null) return new FCallThreeArgBlockNode(union(operation, args), name, args, (IterNode) iter);
                
                return new FCallThreeArgNode(union(operation, args), name, args);
            default:
                if (iter != null) return new FCallManyArgsBlockNode(union(operation, args), name, args, (IterNode) iter);

                return new FCallManyArgsNode(union(operation, args), name, args);
        }
    }
    
    private Node new_fcall_blockpass(Token operation, BlockPassNode blockPass) {
        ISourcePosition position = union(operation, blockPass);
        String name = (String) operation.getValue();
        Node args = blockPass.getArgsNode();
        
        if (args == null) return new FCallNoArgBlockPassNode(position, name, args, blockPass);
        if (!(args instanceof ArrayNode)) return new FCallSpecialArgBlockPassNode(position, name, args, blockPass);
        
        switch (((ArrayNode) args).size()) {
            case 0:  // foo()
                return new FCallNoArgBlockPassNode(position, name, args, blockPass);
            case 1:
                return new FCallOneArgBlockPassNode(position, name, (ArrayNode) args, blockPass);
            case 2:
                return new FCallTwoArgBlockPassNode(position, name, (ArrayNode) args, blockPass);
            case 3:
                return new FCallThreeArgBlockPassNode(position, name, (ArrayNode) args, blockPass);
            default:
                return new FCallManyArgsBlockPassNode(position, name, args, blockPass);
        }        
    }
    
    public Node new_fcall(Token operation, Node args, Node iter) {
        if (args == null) return new_fcall_noargs(operation, (IterNode) iter);
        if (args instanceof ArrayNode) return new_fcall_simpleargs(operation, (ArrayNode) args, iter);
        if (args instanceof BlockPassNode) {
            if (iter == null) return new_fcall_blockpass(operation, (BlockPassNode) args);

            throw new SyntaxException(PID.BLOCK_ARG_AND_BLOCK_GIVEN, iter.getPosition(), "Both block arg and actual block given.");
        }

        if (iter != null) new FCallSpecialArgBlockNode(union(operation, args), (String) operation.getValue(), args, (IterNode) iter);
        return new FCallSpecialArgNode(union(operation, args), (String) operation.getValue(), args);
    }

    public Node new_super(Node args, Token operation) {
        if (args != null && args instanceof BlockPassNode) {
            return new SuperNode(union(operation, args), ((BlockPassNode) args).getArgsNode(), args);
        }
        return new SuperNode(operation.getPosition(), args);
    }

    /**
    *  Description of the RubyMethod
    */
    public void initTopLocalVariables() {
        DynamicScope scope = configuration.getScope(); 
        currentScope = scope.getStaticScope(); 
        
        result.setScope(scope);
    }

    /** Getter for property inSingle.
     * @return Value of property inSingle.
     */
    public boolean isInSingle() {
        return inSingleton != 0;
    }

    /** Setter for property inSingle.
     * @param inSingle New value of property inSingle.
     */
    public void setInSingle(int inSingle) {
        this.inSingleton = inSingle;
    }

    public boolean isInDef() {
        return inDefinition;
    }

    public void setInDef(boolean inDef) {
        this.inDefinition = inDef;
    }

    /** Getter for property inSingle.
     * @return Value of property inSingle.
     */
    public int getInSingle() {
        return inSingleton;
    }

    /**
     * Gets the result.
     * @return Returns a RubyParserResult
     */
    public RubyParserResult getResult() {
        return result;
    }

    /**
     * Sets the result.
     * @param result The result to set
     */
    public void setResult(RubyParserResult result) {
        this.result = result;
    }

    /**
     * Sets the configuration.
     * @param configuration The configuration to set
     */
    public void setConfiguration(ParserConfiguration configuration) {
        this.configuration = configuration;
    }

    public void setWarnings(IRubyWarnings warnings) {
        this.warnings = warnings;
    }
    
    public Node literal_concat(ISourcePosition position, Node head, Node tail) { 
        if (head == null) return tail;
        if (tail == null) return head;
        
        if (head instanceof EvStrNode) {
            head = new DStrNode(union(head.getPosition(), position)).add(head);
        } 

        if (tail instanceof StrNode) {
            if (head instanceof StrNode) {
        	    return new StrNode(union(head, tail), (StrNode) head, (StrNode) tail);
            } 
            head.setPosition(union(head, tail));
            return ((ListNode) head).add(tail);
        	
        } else if (tail instanceof DStrNode) {
            if (head instanceof StrNode){
                ((DStrNode)tail).prepend(head);
                return tail;
            } 

            return ((ListNode) head).addAll(tail);
        } 

        // tail must be EvStrNode at this point 
        if (head instanceof StrNode) {
        	
            //Do not add an empty string node
            if(((StrNode) head).getValue().length() == 0) {
                head = new DStrNode(head.getPosition());
            } else {
                // All first element StrNode's do not include syntacical sugar.
                head.getPosition().adjustStartOffset(-1);
                head = new DStrNode(head.getPosition()).add(head);
            }
        }
        return ((DStrNode) head).add(tail);
    }
    
    public Node newEvStrNode(ISourcePosition position, Node node) {
        Node head = node;
        while (true) {
            if (node == null) break;
            
            if (node instanceof StrNode || node instanceof DStrNode || node instanceof EvStrNode) {
                return node;
            }
                
            if (!(node instanceof NewlineNode)) break;
                
            node = ((NewlineNode) node).getNextNode();
        }
        
        return new EvStrNode(position, head);
    }
    
    public Node new_yield(ISourcePosition position, Node node) {
        boolean state = true;
        
        if (node != null) {
            if (node instanceof BlockPassNode) {
                throw new SyntaxException(PID.BLOCK_ARG_UNEXPECTED, node.getPosition(), "Block argument should not be given.");
            }
            
            if (node instanceof ArrayNode && ((ArrayNode)node).size() == 1) {
                node = ((ArrayNode)node).get(0);
                state = false;
            }
            
            if (node != null && node instanceof SplatNode) {
                state = true;
            }
        } else {
            state = false;
        }
        
        return new YieldNode(position, node, state);
    }
    
    public Node negateInteger(Node integerNode) {
        if (integerNode instanceof FixnumNode) {
            FixnumNode fixnumNode = (FixnumNode) integerNode;
            
            fixnumNode.setValue(-fixnumNode.getValue());
            return fixnumNode;
        } else if (integerNode instanceof BignumNode) {
            BignumNode bignumNode = (BignumNode) integerNode;
            
            bignumNode.setValue(bignumNode.getValue().negate());
        }
        
        return integerNode;
    }
    
    public FloatNode negateFloat(FloatNode floatNode) {
        floatNode.setValue(-floatNode.getValue());
        
        return floatNode;
    }
    
    public ISourcePosition createEmptyArgsNodePosition(ISourcePosition pos) {
        return new IDESourcePosition(pos.getFile(), pos.getStartLine(), pos.getEndLine(), pos.getEndOffset() - 1, pos.getEndOffset() - 1);
    }
    
    public Node unwrapNewlineNode(Node node) {
    	if(node instanceof NewlineNode) {
    		return ((NewlineNode) node).getNextNode();
    	}
    	return node;
    }
    
    private Node checkForNilNode(Node node, ISourcePosition defaultPosition) {
        return (node == null) ? new NilNode(defaultPosition) : node; 
    }

    public ArgumentNode getRestArgNode(Token token) {
        int index = ((Integer) token.getValue()).intValue();
        if(index < 0) {
            return null;
        }
        String name = getCurrentScope().getLocalScope().getVariables()[index];
        ISourcePosition position = new IDESourcePosition(token.getPosition().getFile(), token.getPosition().getStartLine(), token.getPosition().getEndLine(), token.getPosition().getStartOffset(), token.getPosition().getEndOffset() + name.length());
        return new ArgumentNode(position, name);
    }
}
