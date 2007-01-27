/*******************************************************************************
 * BEGIN LICENSE BLOCK *** Version: CPL 1.0/GPL 2.0/LGPL 2.1
 * 
 * The contents of this file are subject to the Common Public License Version
 * 1.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 * 
 * Copyright (C) 2006 Charles Oliver Nutter <headius@headius.com>
 * Copytight (C) 2006-2007 Thomas E Enebo <enebo@acm.org>
 * 
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"), in
 * which case the provisions of the GPL or the LGPL are applicable instead of
 * those above. If you wish to allow use of your version of this file only under
 * the terms of either the GPL or the LGPL, and not to allow others to use your
 * version of this file under the terms of the CPL, indicate your decision by
 * deleting the provisions above and replace them with the notice and other
 * provisions required by the GPL or the LGPL. If you do not delete the
 * provisions above, a recipient may use your version of this file under the
 * terms of any one of the CPL, the GPL or the LGPL. END LICENSE BLOCK ****
 ******************************************************************************/

package org.jruby.evaluator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jruby.IRuby;
import org.jruby.MetaClass;
import org.jruby.RubyArray;
import org.jruby.RubyBignum;
import org.jruby.RubyClass;
import org.jruby.RubyException;
import org.jruby.RubyFloat;
import org.jruby.RubyHash;
import org.jruby.RubyKernel;
import org.jruby.RubyModule;
import org.jruby.RubyProc;
import org.jruby.RubyRange;
import org.jruby.RubyRegexp;
import org.jruby.RubyString;
import org.jruby.ast.AliasNode;
import org.jruby.ast.ArgsCatNode;
import org.jruby.ast.ArgsNode;
import org.jruby.ast.ArgsPushNode;
import org.jruby.ast.ArrayNode;
import org.jruby.ast.AttrAssignNode;
import org.jruby.ast.BackRefNode;
import org.jruby.ast.BeginNode;
import org.jruby.ast.BignumNode;
import org.jruby.ast.BinaryOperatorNode;
import org.jruby.ast.BlockNode;
import org.jruby.ast.BlockPassNode;
import org.jruby.ast.BreakNode;
import org.jruby.ast.CallNode;
import org.jruby.ast.CaseNode;
import org.jruby.ast.ClassNode;
import org.jruby.ast.ClassVarAsgnNode;
import org.jruby.ast.ClassVarDeclNode;
import org.jruby.ast.ClassVarNode;
import org.jruby.ast.Colon2Node;
import org.jruby.ast.Colon3Node;
import org.jruby.ast.ConstDeclNode;
import org.jruby.ast.ConstNode;
import org.jruby.ast.DAsgnNode;
import org.jruby.ast.DRegexpNode;
import org.jruby.ast.DStrNode;
import org.jruby.ast.DSymbolNode;
import org.jruby.ast.DVarNode;
import org.jruby.ast.DXStrNode;
import org.jruby.ast.DefinedNode;
import org.jruby.ast.DefnNode;
import org.jruby.ast.DefsNode;
import org.jruby.ast.DotNode;
import org.jruby.ast.EnsureNode;
import org.jruby.ast.EvStrNode;
import org.jruby.ast.FCallNode;
import org.jruby.ast.FixnumNode;
import org.jruby.ast.FlipNode;
import org.jruby.ast.FloatNode;
import org.jruby.ast.ForNode;
import org.jruby.ast.GlobalAsgnNode;
import org.jruby.ast.GlobalVarNode;
import org.jruby.ast.HashNode;
import org.jruby.ast.IfNode;
import org.jruby.ast.InstAsgnNode;
import org.jruby.ast.InstVarNode;
import org.jruby.ast.IterNode;
import org.jruby.ast.ListNode;
import org.jruby.ast.LocalAsgnNode;
import org.jruby.ast.LocalVarNode;
import org.jruby.ast.Match2Node;
import org.jruby.ast.Match3Node;
import org.jruby.ast.MatchNode;
import org.jruby.ast.ModuleNode;
import org.jruby.ast.MultipleAsgnNode;
import org.jruby.ast.NewlineNode;
import org.jruby.ast.NextNode;
import org.jruby.ast.Node;
import org.jruby.ast.NodeTypes;
import org.jruby.ast.NotNode;
import org.jruby.ast.NthRefNode;
import org.jruby.ast.OpAsgnNode;
import org.jruby.ast.OpAsgnOrNode;
import org.jruby.ast.OpElementAsgnNode;
import org.jruby.ast.OptNNode;
import org.jruby.ast.OrNode;
import org.jruby.ast.RegexpNode;
import org.jruby.ast.RescueBodyNode;
import org.jruby.ast.RescueNode;
import org.jruby.ast.ReturnNode;
import org.jruby.ast.RootNode;
import org.jruby.ast.SClassNode;
import org.jruby.ast.SValueNode;
import org.jruby.ast.SplatNode;
import org.jruby.ast.StrNode;
import org.jruby.ast.SuperNode;
import org.jruby.ast.SymbolNode;
import org.jruby.ast.ToAryNode;
import org.jruby.ast.UndefNode;
import org.jruby.ast.UntilNode;
import org.jruby.ast.VAliasNode;
import org.jruby.ast.VCallNode;
import org.jruby.ast.WhenNode;
import org.jruby.ast.WhileNode;
import org.jruby.ast.XStrNode;
import org.jruby.ast.YieldNode;
import org.jruby.ast.types.INameNode;
import org.jruby.ast.util.ArgsUtil;
import org.jruby.exceptions.JumpException;
import org.jruby.exceptions.RaiseException;
import org.jruby.exceptions.JumpException.JumpType;
import org.jruby.internal.runtime.methods.DefaultMethod;
import org.jruby.internal.runtime.methods.WrapperMethod;
import org.jruby.lexer.yacc.ISourcePosition;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.Block;
import org.jruby.runtime.CallType;
import org.jruby.runtime.DynamicMethod;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.KCode;

public class EvaluationState {
    public static IRubyObject eval(ThreadContext context, Node node, IRubyObject self, Block block) {
        try {
            return evalInternal(context, node, self, block);
        } catch (StackOverflowError sfe) {
            throw context.getRuntime().newSystemStackError("stack level too deep");
        }
    }

    private static IRubyObject evalInternal(ThreadContext context, Node node, IRubyObject self, Block aBlock) {
        IRuby runtime = context.getRuntime();
        
        bigloop: do {
            if (node == null) return runtime.getNil();

            switch (node.nodeId) {
            case NodeTypes.ALIASNODE: {
                AliasNode iVisited = (AliasNode) node;
    
                if (context.getRubyClass() == null) {
                    throw runtime.newTypeError("no class to make alias");
                }
    
                context.getRubyClass().defineAlias(iVisited.getNewName(), iVisited.getOldName());
                context.getRubyClass().callMethod(context, "method_added", runtime.newSymbol(iVisited.getNewName()));
    
                return runtime.getNil();
            }
            case NodeTypes.ANDNODE: {
                BinaryOperatorNode iVisited = (BinaryOperatorNode) node;
    
                IRubyObject result = evalInternal(context, iVisited.getFirstNode(), self, aBlock);
                if (!result.isTrue()) return result;
                node = iVisited.getSecondNode();
                continue bigloop;
            }
            case NodeTypes.ARGSCATNODE: {
                ArgsCatNode iVisited = (ArgsCatNode) node;
    
                IRubyObject args = evalInternal(context, iVisited.getFirstNode(), self, aBlock);
                IRubyObject secondArgs = splatValue(evalInternal(context, iVisited.getSecondNode(), self, aBlock));
                RubyArray list = args instanceof RubyArray ? (RubyArray) args : runtime.newArray(args);
    
                return list.concat(secondArgs);
            }
            case NodeTypes.ARGSPUSHNODE: {
                ArgsPushNode iVisited = (ArgsPushNode) node;
                
                RubyArray args = (RubyArray) evalInternal(context, iVisited.getFirstNode(), self, aBlock).dup();
                return args.append(evalInternal(context, iVisited.getSecondNode(), self, aBlock));
            }
                //                case NodeTypes.ARGSNODE:
                //                EvaluateVisitor.argsNodeVisitor.execute(this, node);
                //                break;
                //                case NodeTypes.ARGUMENTNODE:
                //                EvaluateVisitor.argumentNodeVisitor.execute(this, node);
                //                break;
            case NodeTypes.ARRAYNODE: {
                ArrayNode iVisited = (ArrayNode) node;
                IRubyObject[] array = new IRubyObject[iVisited.size()];
                int i = 0;
                for (Iterator iterator = iVisited.iterator(); iterator.hasNext();) {
                    Node next = (Node) iterator.next();
    
                    array[i++] = evalInternal(context, next, self, aBlock);
                }
    
                return runtime.newArray(array);
            }
                //                case NodeTypes.ASSIGNABLENODE:
                //                EvaluateVisitor.assignableNodeVisitor.execute(this, node);
                //                break;
            case NodeTypes.ATTRASSIGNNODE: {
                AttrAssignNode iVisited = (AttrAssignNode) node;
    
                IRubyObject receiver = evalInternal(context, iVisited.getReceiverNode(), self, aBlock);
                IRubyObject[] args = setupArgs(context, iVisited.getArgsNode(), self);
                
                assert receiver.getMetaClass() != null : receiver.getClass().getName();
                
                // If reciever is self then we do the call the same way as vcall
                CallType callType = (receiver == self ? CallType.VARIABLE : CallType.NORMAL);
    
                receiver.callMethod(context, iVisited.getName(), args, callType);
                
                return args[args.length - 1]; 
            }
            case NodeTypes.BACKREFNODE: {
                BackRefNode iVisited = (BackRefNode) node;
                IRubyObject backref = context.getBackref();
                switch (iVisited.getType()) {
                case '~':
                    return backref;
                case '&':
                    return RubyRegexp.last_match(backref);
                case '`':
                    return RubyRegexp.match_pre(backref);
                case '\'':
                    return RubyRegexp.match_post(backref);
                case '+':
                    return RubyRegexp.match_last(backref);
                }
                break;
            }
            case NodeTypes.BEGINNODE: {
                BeginNode iVisited = (BeginNode) node;
    
                node = iVisited.getBodyNode();
                continue bigloop;
            }
            case NodeTypes.BIGNUMNODE: {
                BignumNode iVisited = (BignumNode) node;
                return RubyBignum.newBignum(runtime, iVisited.getValue());
            }
                //                case NodeTypes.BINARYOPERATORNODE:
                //                EvaluateVisitor.binaryOperatorNodeVisitor.execute(this, node);
                //                break;
                //                case NodeTypes.BLOCKARGNODE:
                //                EvaluateVisitor.blockArgNodeVisitor.execute(this, node);
                //                break;
            case NodeTypes.BLOCKNODE: {
                BlockNode iVisited = (BlockNode) node;
    
                IRubyObject result = runtime.getNil();
                for (Iterator iter = iVisited.iterator(); iter.hasNext();) {
                    result = evalInternal(context, (Node) iter.next(), self, aBlock);
                }
    
                return result;
            }
            case NodeTypes.BLOCKPASSNODE:
                assert false: "Call nodes and friends deal with this";
            case NodeTypes.BREAKNODE: {
                BreakNode iVisited = (BreakNode) node;
    
                IRubyObject result = evalInternal(context, iVisited.getValueNode(), self, aBlock);
    
                JumpException je = new JumpException(JumpException.JumpType.BreakJump);
    
                je.setPrimaryData(result);
                je.setSecondaryData(node);
    
                throw je;
            }
            case NodeTypes.CALLNODE: {
                CallNode iVisited = (CallNode) node;
    
                IRubyObject receiver = evalInternal(context, iVisited.getReceiverNode(), self, aBlock);
                IRubyObject[] args = setupArgs(context, iVisited.getArgsNode(), self);
                
                assert receiver.getMetaClass() != null : receiver.getClass().getName();

                Block block = getBlock(context, self, aBlock, iVisited.getIterNode());
                
                // No block provided lets look at fast path for STI dispatch.
                if (block == null) {
                    RubyModule module = receiver.getMetaClass();
                    if (module.index != 0) {
                         return receiver.callMethod(context, module,
                                runtime.getSelectorTable().table[module.index][iVisited.index],
                                iVisited.getName(), args, CallType.NORMAL);
                    }                    
                }
                    
                try {
                    while (true) {
                        try {
                            return receiver.callMethod(context, iVisited.getName(), args,
                                    CallType.NORMAL, block);
                        } catch (JumpException je) {
                            switch (je.getJumpType().getTypeId()) {
                            case JumpType.RETRY:
                                // allow loop to retry
                                break;
                            default:
                                throw je;
                            }
                        }
                    }
                } catch (JumpException je) {
                    switch (je.getJumpType().getTypeId()) {
                    case JumpType.BREAK:
                        IRubyObject breakValue = (IRubyObject) je.getPrimaryData();
                        
                        return breakValue == null ? runtime.getNil() : breakValue;
                    default:
                        throw je;
                    }
                }
            }
            case NodeTypes.CASENODE: {
                CaseNode iVisited = (CaseNode) node;
                IRubyObject expression = null;
                if (iVisited.getCaseNode() != null) {
                    expression = evalInternal(context, iVisited.getCaseNode(), self, aBlock);
                }
    
                context.pollThreadEvents();
    
                IRubyObject result = runtime.getNil();
    
                Node firstWhenNode = iVisited.getFirstWhenNode();
                while (firstWhenNode != null) {
                    if (!(firstWhenNode instanceof WhenNode)) {
                        node = firstWhenNode;
                        continue bigloop;
                    }
    
                    WhenNode whenNode = (WhenNode) firstWhenNode;
    
                    if (whenNode.getExpressionNodes() instanceof ArrayNode) {
                        for (Iterator iter = ((ArrayNode) whenNode.getExpressionNodes()).iterator(); iter
                                .hasNext();) {
                            Node tag = (Node) iter.next();
    
                            context.setPosition(tag.getPosition());
                            if (isTrace(runtime)) {
                                callTraceFunction(context, "line", self);
                            }
    
                            // Ruby grammar has nested whens in a case body because of
                            // productions case_body and when_args.
                            if (tag instanceof WhenNode) {
                                RubyArray expressions = (RubyArray) evalInternal(context, ((WhenNode) tag)
                                                .getExpressionNodes(), self, aBlock);
    
                                for (int j = 0; j < expressions.getLength(); j++) {
                                    IRubyObject condition = expressions.entry(j);
    
                                    if ((expression != null && condition.callMethod(context, "===", expression)
                                            .isTrue())
                                            || (expression == null && condition.isTrue())) {
                                        node = ((WhenNode) firstWhenNode).getBodyNode();
                                        continue bigloop;
                                    }
                                }
                                continue;
                            }
    
                            result = evalInternal(context, tag, self, aBlock);
    
                            if ((expression != null && result.callMethod(context, "===", expression).isTrue())
                                    || (expression == null && result.isTrue())) {
                                node = whenNode.getBodyNode();
                                continue bigloop;
                            }
                        }
                    } else {
                        result = evalInternal(context, whenNode.getExpressionNodes(), self, aBlock);
    
                        if ((expression != null && result.callMethod(context, "===", expression).isTrue())
                                || (expression == null && result.isTrue())) {
                            node = ((WhenNode) firstWhenNode).getBodyNode();
                            continue bigloop;
                        }
                    }
    
                    context.pollThreadEvents();
    
                    firstWhenNode = whenNode.getNextCase();
                }
    
                return runtime.getNil();
            }
            case NodeTypes.CLASSNODE: {
                ClassNode iVisited = (ClassNode) node;
                Node superNode = iVisited.getSuperNode();
                RubyClass superClass = superNode == null ? null : (RubyClass) evalInternal(context, superNode, self, aBlock);
                Node classNameNode = iVisited.getCPath();
                String name = ((INameNode) classNameNode).getName();
                RubyModule enclosingClass = getEnclosingModule(context, classNameNode, self, aBlock);
                RubyClass rubyClass = enclosingClass.defineOrGetClassUnder(name, superClass);
    
                if (context.getWrapper() != null) {
                    rubyClass.extendObject(context.getWrapper());
                    rubyClass.includeModule(context.getWrapper());
                }
                return evalClassDefinitionBody(context, iVisited.getScope(), iVisited.getBodyNode(), rubyClass, self, aBlock);
            }
            case NodeTypes.CLASSVARASGNNODE: {
                ClassVarAsgnNode iVisited = (ClassVarAsgnNode) node;
                IRubyObject result = evalInternal(context, iVisited.getValueNode(), self, aBlock);
                RubyModule rubyClass = (RubyModule) context.peekCRef().getValue();
    
                if (rubyClass == null) {
                    rubyClass = self.getMetaClass();
                } else if (rubyClass.isSingleton()) {
                    rubyClass = (RubyModule) rubyClass.getInstanceVariable("__attached__");
                }
    
                rubyClass.setClassVar(iVisited.getName(), result);
    
                return result;
            }
            case NodeTypes.CLASSVARDECLNODE: {
    
                ClassVarDeclNode iVisited = (ClassVarDeclNode) node;
    
                // FIXME: shouldn't we use cref here?
                if (context.getRubyClass() == null) {
                    throw runtime.newTypeError("no class/module to define class variable");
                }
                IRubyObject result = evalInternal(context, iVisited.getValueNode(), self, aBlock);
                ((RubyModule) context.peekCRef().getValue()).setClassVar(iVisited.getName(),
                        result);
    
                return result;
            }
            case NodeTypes.CLASSVARNODE: {
                ClassVarNode iVisited = (ClassVarNode) node;
                RubyModule rubyClass = (RubyModule) context.peekCRef().getValue();
    
                if (rubyClass == null) {
                    rubyClass = self.getMetaClass();
                } else if (rubyClass.isSingleton()) {
                    rubyClass = (RubyModule)rubyClass.getInstanceVariable("__attached__");
                }
                
                    return rubyClass.getClassVar(iVisited.getName());
                    }
            case NodeTypes.COLON2NODE: {
                Colon2Node iVisited = (Colon2Node) node;
                Node leftNode = iVisited.getLeftNode();
    
                // TODO: Made this more colon3 friendly because of cpath production
                // rule in grammar (it is convenient to think of them as the same thing
                // at a grammar level even though evaluation is).
                if (leftNode == null) {
                    return runtime.getObject().getConstantFrom(iVisited.getName());
                } else {
                    IRubyObject result = evalInternal(context, iVisited.getLeftNode(), self, aBlock);
                    if (result instanceof RubyModule) {
                        return ((RubyModule) result).getConstantFrom(iVisited.getName());
                    } else {
                        return result.callMethod(context, iVisited.getName(), aBlock);
                    }
                }
            }
            case NodeTypes.COLON3NODE: {
                Colon3Node iVisited = (Colon3Node) node;
                return runtime.getObject().getConstantFrom(iVisited.getName());
            }
            case NodeTypes.CONSTDECLNODE: {
                ConstDeclNode iVisited = (ConstDeclNode) node;
    
                IRubyObject result = evalInternal(context, iVisited.getValueNode(), self, aBlock);
                IRubyObject module;
    
                if (iVisited.getPathNode() != null) {
                    module = evalInternal(context, iVisited.getPathNode(), self, aBlock);
                } else {
                    
    
                    // FIXME: why do we check RubyClass and then use CRef?
                    if (context.getRubyClass() == null) {
                        // TODO: wire into new exception handling mechanism
                        throw runtime.newTypeError("no class/module to define constant");
                    }
                    module = (RubyModule) context.peekCRef().getValue();
                }
    
                // FIXME: shouldn't we use the result of this set in setResult?
                ((RubyModule) module).setConstant(iVisited.getName(), result);
    
                return result;
            }
            case NodeTypes.CONSTNODE: {
                ConstNode iVisited = (ConstNode) node;
                return context.getConstant(iVisited.getName());
            }
            case NodeTypes.DASGNNODE: {
                DAsgnNode iVisited = (DAsgnNode) node;
    
                IRubyObject result = evalInternal(context, iVisited.getValueNode(), self, aBlock);

                // System.out.println("DSetting: " + iVisited.getName() + " at index " + iVisited.getIndex() + " and at depth " + iVisited.getDepth() + " and set " + result);
                context.getCurrentScope().setValue(iVisited.getIndex(), result, iVisited.getDepth());
    
                return result;
            }
            case NodeTypes.DEFINEDNODE: {
                DefinedNode iVisited = (DefinedNode) node;
                String definition = getDefinition(context, iVisited.getExpressionNode(), self, aBlock);
                if (definition != null) {
                    return runtime.newString(definition);
                } else {
                    return runtime.getNil();
                }
            }
            case NodeTypes.DEFNNODE: {
                DefnNode iVisited = (DefnNode) node;
                
                RubyModule containingClass = context.getRubyClass();
    
                if (containingClass == null) {
                    throw runtime.newTypeError("No class to add method.");
                }
    
                String name = iVisited.getName();
                if (containingClass == runtime.getObject() && name == "initialize") {
                    runtime.getWarnings().warn("redefining Object#initialize may cause infinite loop");
                }
    
                Visibility visibility = context.getCurrentVisibility();
                if (name == "initialize" || visibility.isModuleFunction() || context.isTopLevel()) {
                    visibility = Visibility.PRIVATE;
                }
                
                if (containingClass.isSingleton()) {
                    IRubyObject attachedObject = ((MetaClass) containingClass).getAttachedObject();
                    
                    if (!attachedObject.singletonMethodsAllowed()) {
                        throw runtime.newTypeError("can't define singleton method \"" + 
                                iVisited.getName() + "\" for " + attachedObject.getType());
                    }
                }    
                DefaultMethod newMethod = new DefaultMethod(containingClass, iVisited.getScope(), 
                        iVisited.getBodyNode(), (ArgsNode) iVisited.getArgsNode(), visibility, context.peekCRef());
    
                containingClass.addMethod(name, newMethod);
    
                if (context.getCurrentVisibility().isModuleFunction()) {
                    containingClass.getSingletonClass().addMethod(
                            name,
                            new WrapperMethod(containingClass.getSingletonClass(), newMethod,
                                    Visibility.PUBLIC));
                    containingClass.callMethod(context, "singleton_method_added", runtime.newSymbol(name));
                }
    
                // 'class << state.self' and 'class << obj' uses defn as opposed to defs
                if (containingClass.isSingleton()) {
                    ((MetaClass) containingClass).getAttachedObject().callMethod(
                            context, "singleton_method_added", runtime.newSymbol(iVisited.getName()));
                } else {
                    containingClass.callMethod(context, "method_added", runtime.newSymbol(name));
                }
    
                return runtime.getNil();
            }
            case NodeTypes.DEFSNODE: {
                DefsNode iVisited = (DefsNode) node;
                IRubyObject receiver = evalInternal(context, iVisited.getReceiverNode(), self, aBlock);
    
                RubyClass rubyClass;
    
                if (receiver.isNil()) {
                    rubyClass = runtime.getNilClass();
                } else if (receiver == runtime.getTrue()) {
                    rubyClass = runtime.getClass("TrueClass");
                } else if (receiver == runtime.getFalse()) {
                    rubyClass = runtime.getClass("FalseClass");
                } else {
                    if (runtime.getSafeLevel() >= 4 && !receiver.isTaint()) {
                        throw runtime.newSecurityError("Insecure; can't define singleton method.");
                    }
                    if (receiver.isFrozen()) {
                        throw runtime.newFrozenError("object");
                    }
                    if (!receiver.singletonMethodsAllowed()) {
                        throw runtime.newTypeError("can't define singleton method \"" + iVisited.getName()
                                                   + "\" for " + receiver.getType());
                    }
    
                    rubyClass = receiver.getSingletonClass();
                }
    
                if (runtime.getSafeLevel() >= 4) {
                    Object method = rubyClass.getMethods().get(iVisited.getName());
                    if (method != null) {
                        throw runtime.newSecurityError("Redefining method prohibited.");
                    }
                }
    
                DefaultMethod newMethod = new DefaultMethod(rubyClass, iVisited.getScope(), 
                        iVisited.getBodyNode(), (ArgsNode) iVisited.getArgsNode(), 
                        Visibility.PUBLIC, context.peekCRef());
    
                rubyClass.addMethod(iVisited.getName(), newMethod);
                receiver.callMethod(context, "singleton_method_added", runtime.newSymbol(iVisited.getName()));
    
                return runtime.getNil();
            }
            case NodeTypes.DOTNODE: {
                DotNode iVisited = (DotNode) node;
                return RubyRange.newRange(runtime, 
                        evalInternal(context, iVisited.getBeginNode(), self, aBlock), 
                        evalInternal(context, iVisited.getEndNode(), self, aBlock), 
                        iVisited.isExclusive());
            }
            case NodeTypes.DREGEXPNODE: {
                DRegexpNode iVisited = (DRegexpNode) node;
    
                StringBuffer sb = new StringBuffer();
                for (Iterator iterator = iVisited.iterator(); iterator.hasNext();) {
                    Node iterNode = (Node) iterator.next();
    
                    sb.append(evalInternal(context, iterNode, self, aBlock).toString());
                }
    
                String lang = null;
                int opts = iVisited.getOptions();
                if((opts & 16) != 0) { // param n
                    lang = "n";
                } else if((opts & 48) != 0) { // param s
                    lang = "s";
                } else if((opts & 64) != 0) { // param s
                    lang = "u";
                }

                return RubyRegexp.newRegexp(runtime, sb.toString(), iVisited.getOptions(), lang);
            }
            case NodeTypes.DSTRNODE: {
                DStrNode iVisited = (DStrNode) node;
    
                StringBuffer sb = new StringBuffer();
                for (Iterator iterator = iVisited.iterator(); iterator.hasNext();) {
                    Node iterNode = (Node) iterator.next();
    
                    sb.append(evalInternal(context, iterNode, self, aBlock).toString());
                }
    
                return runtime.newString(sb.toString());
            }
            case NodeTypes.DSYMBOLNODE: {
                DSymbolNode iVisited = (DSymbolNode) node;
    
                StringBuffer sb = new StringBuffer();
                for (Iterator iterator = iVisited.getNode().iterator(); iterator.hasNext();) {
                    Node iterNode = (Node) iterator.next();
    
                    sb.append(evalInternal(context, iterNode, self, aBlock).toString());
                }
    
                return runtime.newSymbol(sb.toString());
            }
            case NodeTypes.DVARNODE: {
                DVarNode iVisited = (DVarNode) node;

                // System.out.println("DGetting: " + iVisited.getName() + " at index " + iVisited.getIndex() + " and at depth " + iVisited.getDepth());
                IRubyObject obj = context.getCurrentScope().getValue(iVisited.getIndex(), iVisited.getDepth());

                // FIXME: null check is removable once we figure out how to assign to unset named block args
                return obj == null ? runtime.getNil() : obj;
            }
            case NodeTypes.DXSTRNODE: {
                DXStrNode iVisited = (DXStrNode) node;
    
                StringBuffer sb = new StringBuffer();
                for (Iterator iterator = iVisited.iterator(); iterator.hasNext();) {
                    Node iterNode = (Node) iterator.next();
    
                    sb.append(evalInternal(context, iterNode, self, aBlock).toString());
                }
    
                return self.callMethod(context, "`", runtime.newString(sb.toString()));
            }
            case NodeTypes.ENSURENODE: {
                EnsureNode iVisited = (EnsureNode) node;
    
                // save entering the try if there's nothing to ensure
                if (iVisited.getEnsureNode() != null) {
                    IRubyObject result = runtime.getNil();
    
                    try {
                        result = evalInternal(context, iVisited.getBodyNode(), self, aBlock);
                    } finally {
                        evalInternal(context, iVisited.getEnsureNode(), self, aBlock);
                    }
    
                    return result;
                }
    
                node = iVisited.getBodyNode();
                continue bigloop;
            }
            case NodeTypes.EVSTRNODE: {
                EvStrNode iVisited = (EvStrNode) node;
    
                node = iVisited.getBody();
                continue bigloop;
            }
            case NodeTypes.FALSENODE: {
                context.pollThreadEvents();
                return runtime.getFalse();
            }
            case NodeTypes.FCALLNODE: {
                FCallNode iVisited = (FCallNode) node;
                
                IRubyObject[] args = setupArgs(context, iVisited.getArgsNode(), self);
                Block block = getBlock(context, self, aBlock, iVisited.getIterNode());

                try {
                    while (true) {
                        try {
                            IRubyObject result = self.callMethod(context, iVisited.getName(), args,
                                    CallType.FUNCTIONAL, block);
                            if (result == null) {
                                result = runtime.getNil();
                            }
                            
                            return result; 
                        } catch (JumpException je) {
                            switch (je.getJumpType().getTypeId()) {
                            case JumpType.RETRY:
                                // allow loop to retry
                                break;
                            default:
                                throw je;
                            }
                        }
                    }
                } catch (JumpException je) {
                    switch (je.getJumpType().getTypeId()) {
                    case JumpType.BREAK:
                        IRubyObject breakValue = (IRubyObject) je.getPrimaryData();

                        return breakValue == null ? runtime.getNil() : breakValue;
                    default:
                        throw je;
                    }
                } 
            }
            case NodeTypes.FIXNUMNODE: {
                FixnumNode iVisited = (FixnumNode) node;
                return iVisited.getFixnum(runtime);
            }
            case NodeTypes.FLIPNODE: {
                FlipNode iVisited = (FlipNode) node;
                IRubyObject result = runtime.getNil();
    
                if (iVisited.isExclusive()) {
                    if (!context.getCurrentScope().getValue(iVisited.getIndex(), iVisited.getDepth()).isTrue()) {
                        result = evalInternal(context, iVisited.getBeginNode(), self, aBlock).isTrue() ? runtime.getFalse()
                                : runtime.getTrue();
                        context.getCurrentScope().setValue(iVisited.getIndex(), result, iVisited.getDepth());
                        return result;
                    } else {
                        if (evalInternal(context, iVisited.getEndNode(), self, aBlock).isTrue()) {
                            context.getCurrentScope().setValue(iVisited.getIndex(), runtime.getFalse(), iVisited.getDepth());
                        }
                        return runtime.getTrue();
                    }
                } else {
                    if (!context.getCurrentScope().getValue(iVisited.getIndex(), iVisited.getDepth()).isTrue()) {
                        if (evalInternal(context, iVisited.getBeginNode(), self, aBlock).isTrue()) {
                            context.getCurrentScope().setValue(
                                    iVisited.getIndex(),
                                    evalInternal(context, iVisited.getEndNode(), self, aBlock).isTrue() ? runtime.getFalse()
                                            : runtime.getTrue(), iVisited.getDepth());
                            return runtime.getTrue();
                        } else {
                            return runtime.getFalse();
                        }
                    } else {
                        if (evalInternal(context, iVisited.getEndNode(), self, aBlock).isTrue()) {
                            context.getCurrentScope().setValue(iVisited.getIndex(), runtime.getFalse(), iVisited.getDepth());
                        }
                        return runtime.getTrue();
                    }
                }
            }
            case NodeTypes.FLOATNODE: {
                FloatNode iVisited = (FloatNode) node;
                return RubyFloat.newFloat(runtime, iVisited.getValue());
            }
            case NodeTypes.FORNODE: {
                ForNode iVisited = (ForNode) node;
                
                // For nodes do not have to create an addition scope so we just pass null
                // ENEBO: Not sure we need to pass actual block along or not
                Block block = Block.createBlock(context, iVisited.getVarNode(), null,
                        iVisited.getCallable(), self, aBlock);
    
                try {
                    while (true) {
                        try {
                            ISourcePosition position = context.getPosition();
    
                            IRubyObject recv = null;
                            try {
                                recv = evalInternal(context, iVisited.getIterNode(), self, aBlock);
                            } finally {
                                context.setPosition(position);
                            }
    
                            return recv.callMethod(context, "each", IRubyObject.NULL_ARRAY, CallType.NORMAL, block);
                        } catch (JumpException je) {
                            switch (je.getJumpType().getTypeId()) {
                            case JumpType.RETRY:
                                // do nothing, allow loop to retry
                                break;
                            default:
                                throw je;
                            }
                        }
                    }
                } catch (JumpException je) {
                    switch (je.getJumpType().getTypeId()) {
                    case JumpType.BREAK:
                        IRubyObject breakValue = (IRubyObject) je.getPrimaryData();
    
                        return breakValue == null ? runtime.getNil() : breakValue;
                    default:
                        throw je;
                    }
                }
            }
            case NodeTypes.GLOBALASGNNODE: {
                GlobalAsgnNode iVisited = (GlobalAsgnNode) node;
    
                IRubyObject result = evalInternal(context, iVisited.getValueNode(), self, aBlock);
    
                runtime.getGlobalVariables().set(iVisited.getName(), result);
    
                // FIXME: this should be encapsulated along with the set above
                if (iVisited.getName() == "$KCODE") {
                    runtime.setKCode(KCode.create(runtime, result.toString()));
                }
    
                return result;
            }
            case NodeTypes.GLOBALVARNODE: {
                GlobalVarNode iVisited = (GlobalVarNode) node;
                return runtime.getGlobalVariables().get(iVisited.getName());
            }
            case NodeTypes.HASHNODE: {
                HashNode iVisited = (HashNode) node;
    
                Map hash = null;
                if (iVisited.getListNode() != null) {
                    hash = new HashMap(iVisited.getListNode().size() / 2);
    
                    for (Iterator iterator = iVisited.getListNode().iterator(); iterator.hasNext();) {
                        // insert all nodes in sequence, hash them in the final instruction
                        // KEY
                        IRubyObject key = evalInternal(context, (Node) iterator.next(), self, aBlock);
                        IRubyObject value = evalInternal(context, (Node) iterator.next(), self, aBlock);
    
                        hash.put(key, value);
                    }
                }
    
                if (hash == null) {
                    return RubyHash.newHash(runtime);
                }
    
                return RubyHash.newHash(runtime, hash, runtime.getNil());
            }
            case NodeTypes.IFNODE: {
                IfNode iVisited = (IfNode) node;
                IRubyObject result = evalInternal(context, iVisited.getCondition(), self, aBlock);
    
                if (result.isTrue()) {
                    node = iVisited.getThenBody();
                    continue bigloop;
                } else {
                    node = iVisited.getElseBody();
                    continue bigloop;
                }
            }
            case NodeTypes.INSTASGNNODE: {
                InstAsgnNode iVisited = (InstAsgnNode) node;
    
                IRubyObject result = evalInternal(context, iVisited.getValueNode(), self, aBlock);
                self.setInstanceVariable(iVisited.getName(), result);
    
                return result;
            }
            case NodeTypes.INSTVARNODE: {
                InstVarNode iVisited = (InstVarNode) node;
                IRubyObject variable = self.getInstanceVariable(iVisited.getName());
    
                return variable == null ? runtime.getNil() : variable;
            }
                //                case NodeTypes.ISCOPINGNODE:
                //                EvaluateVisitor.iScopingNodeVisitor.execute(this, node);
                //                break;
            case NodeTypes.ITERNODE: 
                assert false: "Call nodes deal with these directly";
            case NodeTypes.LOCALASGNNODE: {
                LocalAsgnNode iVisited = (LocalAsgnNode) node;
                IRubyObject result = evalInternal(context, iVisited.getValueNode(), self, aBlock);
                
                // System.out.println("LSetting: " + iVisited.getName() + " at index " + iVisited.getIndex() + " and at depth " + iVisited.getDepth() + " and set " + result);
                context.getCurrentScope().setValue(iVisited.getIndex(), result, iVisited.getDepth());

                return result;
            }
            case NodeTypes.LOCALVARNODE: {
                LocalVarNode iVisited = (LocalVarNode) node;

                //System.out.println("DGetting: " + iVisited.getName() + " at index " + iVisited.getIndex() + " and at depth " + iVisited.getDepth());
                IRubyObject result = context.getCurrentScope().getValue(iVisited.getIndex(), iVisited.getDepth());

                return result == null ? runtime.getNil() : result;
            }
            case NodeTypes.MATCH2NODE: {
                Match2Node iVisited = (Match2Node) node;
                IRubyObject recv = evalInternal(context, iVisited.getReceiverNode(), self, aBlock);
                IRubyObject value = evalInternal(context, iVisited.getValueNode(), self, aBlock);
    
                return ((RubyRegexp) recv).match(value);
            }
            case NodeTypes.MATCH3NODE: {
                Match3Node iVisited = (Match3Node) node;
                IRubyObject recv = evalInternal(context, iVisited.getReceiverNode(), self, aBlock);
                IRubyObject value = evalInternal(context, iVisited.getValueNode(), self, aBlock);
    
                if (value instanceof RubyString) {
                    return ((RubyRegexp) recv).match(value);
                } else {
                    return value.callMethod(context, "=~", recv);
                }
            }
            case NodeTypes.MATCHNODE: {
                MatchNode iVisited = (MatchNode) node;
                return ((RubyRegexp) evalInternal(context, iVisited.getRegexpNode(), self, aBlock)).match2();
            }
            case NodeTypes.MODULENODE: {
                ModuleNode iVisited = (ModuleNode) node;
                Node classNameNode = iVisited.getCPath();
                String name = ((INameNode) classNameNode).getName();
                RubyModule enclosingModule = getEnclosingModule(context, classNameNode, self, aBlock);
    
                if (enclosingModule == null) {
                    throw runtime.newTypeError("no outer class/module");
                }
    
                RubyModule module;
                if (enclosingModule == runtime.getObject()) {
                    module = runtime.getOrCreateModule(name);
                } else {
                    module = enclosingModule.defineModuleUnder(name);
                }
                return evalClassDefinitionBody(context, iVisited.getScope(), iVisited.getBodyNode(), module, self, aBlock);
            }
            case NodeTypes.MULTIPLEASGNNODE: {
                MultipleAsgnNode iVisited = (MultipleAsgnNode) node;
                return AssignmentVisitor.assign(context, self, iVisited, 
                        evalInternal(context, iVisited.getValueNode(), self, aBlock), 
                        aBlock, false);
            }
            case NodeTypes.NEWLINENODE: {
                NewlineNode iVisited = (NewlineNode) node;
    
                // something in here is used to build up ruby stack trace...
                context.setPosition(iVisited.getPosition());
    
                if (isTrace(runtime)) {
                    callTraceFunction(context, "line", self);
                }
    
                // TODO: do above but not below for additional newline nodes
                node = iVisited.getNextNode();
                continue bigloop;
            }
            case NodeTypes.NEXTNODE: {
                NextNode iVisited = (NextNode) node;
    
                context.pollThreadEvents();
    
                IRubyObject result = evalInternal(context, iVisited.getValueNode(), self, aBlock);
    
                // now used as an interpreter event
                JumpException je = new JumpException(JumpException.JumpType.NextJump);
    
                je.setPrimaryData(result);
                je.setSecondaryData(iVisited);
    
                //state.setCurrentException(je);
                throw je;
            }
            case NodeTypes.NILNODE:
                return runtime.getNil();
            case NodeTypes.NOTNODE: {
                NotNode iVisited = (NotNode) node;
    
                IRubyObject result = evalInternal(context, iVisited.getConditionNode(), self, aBlock);
                return result.isTrue() ? runtime.getFalse() : runtime.getTrue();
            }
            case NodeTypes.NTHREFNODE: {
                NthRefNode iVisited = (NthRefNode) node;
                return RubyRegexp.nth_match(iVisited.getMatchNumber(), context.getBackref());
            }
            case NodeTypes.OPASGNANDNODE: {
                BinaryOperatorNode iVisited = (BinaryOperatorNode) node;
    
                // add in reverse order
                IRubyObject result = evalInternal(context, iVisited.getFirstNode(), self, aBlock);
                if (!result.isTrue()) return result;
                node = iVisited.getSecondNode();
                continue bigloop;
            }
            case NodeTypes.OPASGNNODE: {
                OpAsgnNode iVisited = (OpAsgnNode) node;
                IRubyObject receiver = evalInternal(context, iVisited.getReceiverNode(), self, aBlock);
                IRubyObject value = receiver.callMethod(context, iVisited.getVariableName());
    
                if (iVisited.getOperatorName() == "||") {
                    if (value.isTrue()) {
                        return value;
                    }
                    value = evalInternal(context, iVisited.getValueNode(), self, aBlock);
                } else if (iVisited.getOperatorName() == "&&") {
                    if (!value.isTrue()) {
                        return value;
                    }
                    value = evalInternal(context, iVisited.getValueNode(), self, aBlock);
                } else {
                    value = value.callMethod(context, iVisited.getOperatorName(), evalInternal(context,
                            iVisited.getValueNode(), self, aBlock));
                }
    
                receiver.callMethod(context, iVisited.getVariableNameAsgn(), value);
    
                context.pollThreadEvents();
    
                return value;
            }
            case NodeTypes.OPASGNORNODE: {
                OpAsgnOrNode iVisited = (OpAsgnOrNode) node;
                String def = getDefinition(context, iVisited.getFirstNode(), self, aBlock);
    
                IRubyObject result = runtime.getNil();
                if (def != null) {
                    result = evalInternal(context, iVisited.getFirstNode(), self, aBlock);
                }
                if (!result.isTrue()) {
                    result = evalInternal(context, iVisited.getSecondNode(), self, aBlock);
                }
    
                return result;
            }
            case NodeTypes.OPELEMENTASGNNODE: {
                OpElementAsgnNode iVisited = (OpElementAsgnNode) node;
                IRubyObject receiver = evalInternal(context, iVisited.getReceiverNode(), self, aBlock);
    
                IRubyObject[] args = setupArgs(context, iVisited.getArgsNode(), self);
    
                IRubyObject firstValue = receiver.callMethod(context, "[]", args);
    
                if (iVisited.getOperatorName() == "||") {
                    if (firstValue.isTrue()) {
                        return firstValue;
                    }
                    firstValue = evalInternal(context, iVisited.getValueNode(), self, aBlock);
                } else if (iVisited.getOperatorName() == "&&") {
                    if (!firstValue.isTrue()) {
                        return firstValue;
                    }
                    firstValue = evalInternal(context, iVisited.getValueNode(), self, aBlock);
                } else {
                    firstValue = firstValue.callMethod(context, iVisited.getOperatorName(), evalInternal(context, iVisited
                                    .getValueNode(), self, aBlock));
                }
    
                IRubyObject[] expandedArgs = new IRubyObject[args.length + 1];
                System.arraycopy(args, 0, expandedArgs, 0, args.length);
                expandedArgs[expandedArgs.length - 1] = firstValue;
                return receiver.callMethod(context, "[]=", expandedArgs);
            }
            case NodeTypes.OPTNNODE: {
                OptNNode iVisited = (OptNNode) node;
    
                IRubyObject result = runtime.getNil();
                while (RubyKernel.gets(runtime.getTopSelf(), IRubyObject.NULL_ARRAY).isTrue()) {
                    loop: while (true) { // Used for the 'redo' command
                        try {
                            result = evalInternal(context, iVisited.getBodyNode(), self, aBlock);
                            break;
                        } catch (JumpException je) {
                            switch (je.getJumpType().getTypeId()) {
                            case JumpType.REDO:
                                // do nothing, this iteration restarts
                                break;
                            case JumpType.NEXT:
                                // recheck condition
                                break loop;
                            case JumpType.BREAK:
                                // end loop
                                return (IRubyObject) je.getPrimaryData();
                            default:
                                throw je;
                            }
                        }
                    }
                }
                return result;
            }
            case NodeTypes.ORNODE: {
                OrNode iVisited = (OrNode) node;
    
                IRubyObject result = evalInternal(context, iVisited.getFirstNode(), self, aBlock);
    
                if (!result.isTrue()) {
                    result = evalInternal(context, iVisited.getSecondNode(), self, aBlock);
                }
    
                return result;
            }
                //                case NodeTypes.POSTEXENODE:
                //                EvaluateVisitor.postExeNodeVisitor.execute(this, node);
                //                break;
            case NodeTypes.REDONODE: {
                context.pollThreadEvents();
    
                // now used as an interpreter event
                JumpException je = new JumpException(JumpException.JumpType.RedoJump);
    
                je.setSecondaryData(node);
    
                throw je;
            }
            case NodeTypes.REGEXPNODE: {
                RegexpNode iVisited = (RegexpNode) node;
                String lang = null;
                int opts = iVisited.getOptions();
                if((opts & 16) != 0) { // param n
                    lang = "n";
                } else if((opts & 48) != 0) { // param s
                    lang = "s";
                } else if((opts & 64) != 0) { // param s
                    lang = "u";
                }
                try {
                    return RubyRegexp.newRegexp(runtime, iVisited.getPattern(), lang);
                } catch(java.util.regex.PatternSyntaxException e) {
                    throw runtime.newSyntaxError(e.getMessage());
                }
            }
            case NodeTypes.RESCUEBODYNODE: {
                RescueBodyNode iVisited = (RescueBodyNode) node;
                node = iVisited.getBodyNode();
                continue bigloop;
            }
            case NodeTypes.RESCUENODE: {
                RescueNode iVisited = (RescueNode)node;
                RescuedBlock : while (true) {
                    IRubyObject globalExceptionState = runtime.getGlobalVariables().get("$!");
                    boolean anotherExceptionRaised = false;
                    try {
                        // Execute rescue block
                        IRubyObject result = evalInternal(context, iVisited.getBodyNode(), self, aBlock);

                        // If no exception is thrown execute else block
                        if (iVisited.getElseNode() != null) {
                            if (iVisited.getRescueNode() == null) {
                                runtime.getWarnings().warn(iVisited.getElseNode().getPosition(), "else without rescue is useless");
                            }
                            result = evalInternal(context, iVisited.getElseNode(), self, aBlock);
                        }

                        return result;
                    } catch (RaiseException raiseJump) {
                        RubyException raisedException = raiseJump.getException();
                        // TODO: Rubicon TestKernel dies without this line.  A cursory glance implies we
                        // falsely set $! to nil and this sets it back to something valid.  This should 
                        // get fixed at the same time we address bug #1296484.
                        runtime.getGlobalVariables().set("$!", raisedException);

                        RescueBodyNode rescueNode = iVisited.getRescueNode();

                        while (rescueNode != null) {
                            Node  exceptionNodes = rescueNode.getExceptionNodes();
                            ListNode exceptionNodesList;
                            
                            if (exceptionNodes instanceof SplatNode) {                    
                                exceptionNodesList = (ListNode) evalInternal(context, exceptionNodes, self, aBlock);
                            } else {
                                exceptionNodesList = (ListNode) exceptionNodes;
                            }
                            
                            if (isRescueHandled(context, raisedException, exceptionNodesList, self)) {
                                try {
                                    return evalInternal(context, rescueNode, self, aBlock);
                                } catch (JumpException je) {
                                    if (je.getJumpType() == JumpException.JumpType.RetryJump) {
                                        // should be handled in the finally block below
                                        //state.runtime.getGlobalVariables().set("$!", state.runtime.getNil());
                                        //state.threadContext.setRaisedException(null);
                                        continue RescuedBlock;
                                        
                                    } else {
                                        anotherExceptionRaised = true;
                                        throw je;
                                    }
                                }
                            }
                            
                            rescueNode = rescueNode.getOptRescueNode();
                        }

                        // no takers; bubble up
                        throw raiseJump;
                    } finally {
                        // clear exception when handled or retried
                        if (!anotherExceptionRaised)
                            runtime.getGlobalVariables().set("$!", globalExceptionState);
                    }
                }
            }
            case NodeTypes.RETRYNODE: {
                context.pollThreadEvents();
    
                JumpException je = new JumpException(JumpException.JumpType.RetryJump);
    
                throw je;
            }
            case NodeTypes.RETURNNODE: {
                ReturnNode iVisited = (ReturnNode) node;
    
                IRubyObject result = evalInternal(context, iVisited.getValueNode(), self, aBlock);
    
                JumpException je = new JumpException(JumpException.JumpType.ReturnJump);
    
                je.setPrimaryData(iVisited.getTarget());
                je.setSecondaryData(result);
                je.setTertiaryData(iVisited);
    
                throw je;
            }
            case NodeTypes.ROOTNODE: {
                RootNode iVisited = (RootNode) node;
                DynamicScope scope = iVisited.getScope();
                
                // Serialization killed our dynamic scope.  We can just create an empty one
                // since serialization cannot serialize an eval (which is the only thing
                // which is capable of having a non-empty dynamic scope).
                if (scope == null) {
                    scope = new DynamicScope(iVisited.getStaticScope(), null);
                }
                
                // Each root node has a top-level scope that we need to push
                context.preRootNode(scope);
                
                // FIXME: Wire up BEGIN and END nodes

                try {
                    return eval(context, iVisited.getBodyNode(), self, aBlock);
                } finally {
                    context.postRootNode();
                }
            }
            case NodeTypes.SCLASSNODE: {
                SClassNode iVisited = (SClassNode) node;
                IRubyObject receiver = evalInternal(context, iVisited.getReceiverNode(), self, aBlock);
    
                RubyClass singletonClass;
    
                if (receiver.isNil()) {
                    singletonClass = runtime.getNilClass();
                } else if (receiver == runtime.getTrue()) {
                    singletonClass = runtime.getClass("TrueClass");
                } else if (receiver == runtime.getFalse()) {
                    singletonClass = runtime.getClass("FalseClass");
				} else if (receiver.getMetaClass() == runtime.getFixnum() || receiver.getMetaClass() == runtime.getClass("Symbol")) {
					throw runtime.newTypeError("no virtual class for " + receiver.getMetaClass().getBaseName());
				} else {
                    if (runtime.getSafeLevel() >= 4 && !receiver.isTaint()) {
                        throw runtime.newSecurityError("Insecure: can't extend object.");
                    }
    
                    singletonClass = receiver.getSingletonClass();
                }
    
                
    
                if (context.getWrapper() != null) {
                    singletonClass.extendObject(context.getWrapper());
                    singletonClass.includeModule(context.getWrapper());
                }
    
                return evalClassDefinitionBody(context, iVisited.getScope(), iVisited.getBodyNode(), singletonClass, self, aBlock);
            }
            case NodeTypes.SELFNODE:
                return self;
            case NodeTypes.SPLATNODE: {
                SplatNode iVisited = (SplatNode) node;
                return splatValue(evalInternal(context, iVisited.getValue(), self, aBlock));
            }
                ////                case NodeTypes.STARNODE:
                ////                EvaluateVisitor.starNodeVisitor.execute(this, node);
                ////                break;
            case NodeTypes.STRNODE: {
                StrNode iVisited = (StrNode) node;
                return runtime.newString(iVisited.getValue());
            }
            case NodeTypes.SUPERNODE: {
                SuperNode iVisited = (SuperNode) node;
                
    
                if (context.getFrameLastClass() == null) {
                    String name = context.getFrameLastFunc();
                    throw runtime.newNameError("Superclass method '" + name
                            + "' disabled.", name);
                }
                IRubyObject[] args = setupArgs(context, iVisited.getArgsNode(), self);
                Block block = getBlock(context, self, aBlock, iVisited.getIterNode());
                
                // If no explicit block passed to super, then use the one passed in.
                if (block == null) block = aBlock;
                
                return context.callSuper(args, block);
            }
            case NodeTypes.SVALUENODE: {
                SValueNode iVisited = (SValueNode) node;
                return aValueSplat(evalInternal(context, iVisited.getValue(), self, aBlock));
            }
            case NodeTypes.SYMBOLNODE: {
                SymbolNode iVisited = (SymbolNode) node;
                return runtime.newSymbol(iVisited.getName());
            }
            case NodeTypes.TOARYNODE: {
                ToAryNode iVisited = (ToAryNode) node;
                return aryToAry(evalInternal(context, iVisited.getValue(), self, aBlock));
            }
            case NodeTypes.TRUENODE: {
                context.pollThreadEvents();
                return runtime.getTrue();
            }
            case NodeTypes.UNDEFNODE: {
                UndefNode iVisited = (UndefNode) node;
                
    
                if (context.getRubyClass() == null) {
                    throw runtime
                            .newTypeError("No class to undef method '" + iVisited.getName() + "'.");
                }
                context.getRubyClass().undef(iVisited.getName());
    
                return runtime.getNil();
            }
            case NodeTypes.UNTILNODE: {
                UntilNode iVisited = (UntilNode) node;
    
                IRubyObject result = runtime.getNil();
                
                while (!(result = evalInternal(context, iVisited.getConditionNode(), self, aBlock)).isTrue()) {
                    loop: while (true) { // Used for the 'redo' command
                        try {
                            result = evalInternal(context, iVisited.getBodyNode(), self, aBlock);
                            break loop;
                        } catch (JumpException je) {
                            switch (je.getJumpType().getTypeId()) {
                            case JumpType.REDO:
                                continue;
                            case JumpType.NEXT:
                                break loop;
                            case JumpType.BREAK:
                                return (IRubyObject) je.getPrimaryData();
                            default:
                                throw je;
                            }
                        }
                    }
                }
                
                return result;
            }
            case NodeTypes.VALIASNODE: {
                VAliasNode iVisited = (VAliasNode) node;
                runtime.getGlobalVariables().alias(iVisited.getNewName(), iVisited.getOldName());
    
                return runtime.getNil();
            }
            case NodeTypes.VCALLNODE: {
                VCallNode iVisited = (VCallNode) node;
                return self.callMethod(context, iVisited.getName(), 
                        IRubyObject.NULL_ARRAY, CallType.VARIABLE);
            }
            case NodeTypes.WHENNODE:
                assert false;
                return null;
            case NodeTypes.WHILENODE: {
                WhileNode iVisited = (WhileNode) node;
    
                IRubyObject result = runtime.getNil();
                boolean firstTest = iVisited.evaluateAtStart();
                
                while (!firstTest || (result = evalInternal(context, iVisited.getConditionNode(), self, aBlock)).isTrue()) {
                    firstTest = true;
                    loop: while (true) { // Used for the 'redo' command
                        try {
                            evalInternal(context, iVisited.getBodyNode(), self, aBlock);
                            break loop;
                        } catch (JumpException je) {
                            switch (je.getJumpType().getTypeId()) {
                            case JumpType.REDO:
                                continue;
                            case JumpType.NEXT:
                                break loop;
                            case JumpType.BREAK:
                                return result;
                            default:
                                throw je;
                            }
                        }
                    }
                }
                
                return result;
            }
            case NodeTypes.XSTRNODE: {
                XStrNode iVisited = (XStrNode) node;
                return self.callMethod(context, "`", runtime.newString(iVisited.getValue()));
            }
            case NodeTypes.YIELDNODE: {
                YieldNode iVisited = (YieldNode) node;
    
                IRubyObject result = evalInternal(context, iVisited.getArgsNode(), self, aBlock);
                if (iVisited.getArgsNode() == null) {
                    result = null;
                }

                
               Block block = context.getCurrentFrame().getBlock();
                // FIXME: Every caller where I directly yield needs to have this warning (refactor to method)
                if (block == null) throw runtime.newLocalJumpError("yield called out of block");

                return block.yield(context, result, null, null, iVisited.getCheckState());
                                
            }
            case NodeTypes.ZARRAYNODE: {
                return runtime.newArray();
            }
            case NodeTypes.ZSUPERNODE: {
                
    
                if (context.getFrameLastClass() == null) {
                    String name = context.getFrameLastFunc();
                    throw runtime.newNameError("superclass method '" + name
                            + "' disabled", name);
                }
                
                // Has the method that is calling super received a block argument
                Block block = context.getCurrentFrame().getBlock();
                
                return context.callSuper(context.getFrameArgs(), true, block);
            }
            default:
                throw new RuntimeException("Invalid node encountered in interpreter: \"" + node.getClass().getName() + "\", please report this at www.jruby.org");
            }
        } while (true);
    }
    
    private static String getArgumentDefinition(ThreadContext context, Node node, String type, IRubyObject self, Block block) {
        if (node == null) return type;
            
        if (node instanceof ArrayNode) {
            for (Iterator iter = ((ArrayNode)node).iterator(); iter.hasNext(); ) {
                if (getDefinitionInner(context, (Node)iter.next(), self, block) == null) return null;
            }
        } else if (getDefinitionInner(context, node, self, block) == null) {
            return null;
        }

        return type;
    }
    
    private static String getDefinition(ThreadContext context, Node node, IRubyObject self, Block aBlock) {
        try {
            context.setWithinDefined(true);
            return getDefinitionInner(context, node, self, aBlock);
        } finally {
            context.setWithinDefined(false);
        }
    }
    
    private static String getDefinitionInner(ThreadContext context, Node node, IRubyObject self, Block aBlock) {
        if (node == null) return "expression";
        
        switch(node.nodeId) {
        case NodeTypes.ATTRASSIGNNODE: {
            AttrAssignNode iVisited = (AttrAssignNode) node;
            
            if (getDefinitionInner(context, iVisited.getReceiverNode(), self, aBlock) != null) {
                try {
                    IRubyObject receiver = eval(context, iVisited.getReceiverNode(), self, aBlock);
                    RubyClass metaClass = receiver.getMetaClass();
                    DynamicMethod method = metaClass.searchMethod(iVisited.getName());
                    Visibility visibility = method.getVisibility();

                    if (!visibility.isPrivate() && 
                            (!visibility.isProtected() || self.isKindOf(metaClass.getRealClass()))) {
                        if (metaClass.isMethodBound(iVisited.getName(), false)) {
                            return getArgumentDefinition(context, iVisited.getArgsNode(), "assignment", self, aBlock);
                        }
                    }
                } catch (JumpException excptn) {
                }
            }

            return null;
        }
        case NodeTypes.BACKREFNODE:
            return "$" + ((BackRefNode) node).getType();
        case NodeTypes.CALLNODE: {
            CallNode iVisited = (CallNode) node;
            
            if (getDefinitionInner(context, iVisited.getReceiverNode(), self, aBlock) != null) {
                try {
                    IRubyObject receiver = eval(context, iVisited.getReceiverNode(), self, aBlock);
                    RubyClass metaClass = receiver.getMetaClass();
                    DynamicMethod method = metaClass.searchMethod(iVisited.getName());
                    Visibility visibility = method.getVisibility();

                    if (!visibility.isPrivate() && 
                            (!visibility.isProtected() || self.isKindOf(metaClass.getRealClass()))) {
                        if (metaClass.isMethodBound(iVisited.getName(), false)) {
                            return getArgumentDefinition(context, iVisited.getArgsNode(), "method", self, aBlock);
                        }
                    }
                } catch (JumpException excptn) {
                }
            }

            return null;
        }
        case NodeTypes.CLASSVARASGNNODE: case NodeTypes.CLASSVARDECLNODE: case NodeTypes.CONSTDECLNODE:
        case NodeTypes.DASGNNODE: case NodeTypes.GLOBALASGNNODE: case NodeTypes.LOCALASGNNODE:
        case NodeTypes.MULTIPLEASGNNODE: case NodeTypes.OPASGNNODE: case NodeTypes.OPELEMENTASGNNODE:
            return "assignment";
            
        case NodeTypes.CLASSVARNODE: {
            ClassVarNode iVisited = (ClassVarNode) node;
            
            if (context.getRubyClass() == null && self.getMetaClass().isClassVarDefined(iVisited.getName())) {
                return "class_variable";
            } else if (!context.getRubyClass().isSingleton() && context.getRubyClass().isClassVarDefined(iVisited.getName())) {
                return "class_variable";
            } 
              
            RubyModule module = (RubyModule) context.getRubyClass().getInstanceVariable("__attached__");
            if (module != null && module.isClassVarDefined(iVisited.getName())) return "class_variable"; 

            return null;
        }
        case NodeTypes.COLON2NODE: {
            Colon2Node iVisited = (Colon2Node) node;
            
            try {
                IRubyObject left = EvaluationState.eval(context, iVisited.getLeftNode(), self, aBlock);
                if (left instanceof RubyModule &&
                        ((RubyModule) left).getConstantAt(iVisited.getName()) != null) {
                    return "constant";
                } else if (left.getMetaClass().isMethodBound(iVisited.getName(), true)) {
                    return "method";
                }
            } catch (JumpException excptn) {}
            
            return null;
        }
        case NodeTypes.CONSTNODE:
            if (context.getConstantDefined(((ConstNode) node).getName())) {
                return "constant";
            }
            return null;
        case NodeTypes.DVARNODE:
            return "local-variable(in-block)";
        case NodeTypes.FALSENODE:
            return "false";
        case NodeTypes.FCALLNODE: {
            FCallNode iVisited = (FCallNode) node;
            if (self.getMetaClass().isMethodBound(iVisited.getName(), false)) {
                return getArgumentDefinition(context, iVisited.getArgsNode(), "method", self, aBlock);
            }
            
            return null;
        }
        case NodeTypes.GLOBALVARNODE:
            if (context.getRuntime().getGlobalVariables().isDefined(((GlobalVarNode) node).getName())) {
                return "global-variable";
            }
            return null;
        case NodeTypes.INSTVARNODE:
            if (self.getInstanceVariable(((InstVarNode) node).getName()) != null) {
                return "instance-variable";
            }
            return null;
        case NodeTypes.LOCALVARNODE:
            return "local-variable";
        case NodeTypes.MATCH2NODE: case NodeTypes.MATCH3NODE:
            return "method";
        case NodeTypes.NILNODE:
            return "nil";
        case NodeTypes.NTHREFNODE:
            return "$" + ((NthRefNode) node).getMatchNumber();
        case NodeTypes.SELFNODE:
            return "state.getSelf()";
        case NodeTypes.SUPERNODE: {
            SuperNode iVisited = (SuperNode) node;
            String lastMethod = context.getFrameLastFunc();
            RubyModule lastClass = context.getFrameLastClass();
            if (lastMethod != null && lastClass != null && 
                    lastClass.getSuperClass().isMethodBound(lastMethod, false)) {
                return getArgumentDefinition(context, iVisited.getArgsNode(), "super", self, aBlock);
            }
            
            return null;
        }
        case NodeTypes.TRUENODE:
            return "true";
        case NodeTypes.VCALLNODE: {
            VCallNode iVisited = (VCallNode) node;
            if (self.getMetaClass().isMethodBound(iVisited.getName(), false)) {
                return "method";
            }
            
            return null;
        }
        case NodeTypes.YIELDNODE:
            return aBlock != null ? "yield" : null;
        case NodeTypes.ZSUPERNODE: {
            String lastMethod = context.getFrameLastFunc();
            RubyModule lastClass = context.getFrameLastClass();
            if (lastMethod != null && lastClass != null && 
                    lastClass.getSuperClass().isMethodBound(lastMethod, false)) {
                return "super";
            }
            return null;
        }
        default:
            try {
                EvaluationState.eval(context, node, self, aBlock);
                return "expression";
            } catch (JumpException jumpExcptn) {}
        }
        
        return null;
    }

    private static IRubyObject aryToAry(IRubyObject value) {
        if (value instanceof RubyArray) {
            return value;
        }

        if (value.respondsTo("to_ary")) {
            return value.convertToType("Array", "to_ary", false);
        }

        return value.getRuntime().newArray(value);
    }

    /** Evaluates the body in a class or module definition statement.
     *
     */
    private static IRubyObject evalClassDefinitionBody(ThreadContext context, StaticScope scope, 
            Node bodyNode, RubyModule type, IRubyObject self, Block block) {
        IRuby runtime = context.getRuntime();
        context.preClassEval(scope, type);

        try {
            if (isTrace(runtime)) {
                callTraceFunction(context, "class", type);
            }

            return evalInternal(context, bodyNode, type, block);
        } finally {
            context.postClassEval();

            if (isTrace(runtime)) {
                callTraceFunction(context, "end", null);
            }
        }
    }
    
    /**
     * Helper method.
     *
     * test if a trace function is avaiable.
     *
     */
    private static boolean isTrace(IRuby runtime) {
        return runtime.getTraceFunction() != null;
    }

    private static void callTraceFunction(ThreadContext context, String event, IRubyObject zelf) {
        IRuby runtime = context.getRuntime();
        String name = context.getFrameLastFunc();
        RubyModule type = context.getFrameLastClass();
        runtime.callTraceFunction(context, event, context.getPosition(), zelf, name, type);
    }

    private static IRubyObject splatValue(IRubyObject value) {
        if (value.isNil()) {
            return value.getRuntime().newArray(value);
        }

        return arrayValue(value);
    }

    private static IRubyObject aValueSplat(IRubyObject value) {
        IRuby runtime = value.getRuntime();
        if (!(value instanceof RubyArray) || ((RubyArray) value).length().getLongValue() == 0) {
            return runtime.getNil();
        }

        RubyArray array = (RubyArray) value;

        return array.getLength() == 1 ? array.first(IRubyObject.NULL_ARRAY) : array;
    }

    private static RubyArray arrayValue(IRubyObject value) {
        IRubyObject newValue = value.convertToType("Array", "to_ary", false);

        if (newValue.isNil()) {
            IRuby runtime = value.getRuntime();
            // Object#to_a is obsolete.  We match Ruby's hack until to_a goes away.  Then we can 
            // remove this hack too.
            if (value.getType().searchMethod("to_a").getImplementationClass() != runtime
                    .getKernel()) {
                newValue = value.convertToType("Array", "to_a", false);
                if (newValue.getType() != runtime.getClass("Array")) {
                    throw runtime.newTypeError("`to_a' did not return Array");
                }
            } else {
                newValue = runtime.newArray(value);
            }
        }

        return (RubyArray) newValue;
    }

    private static IRubyObject[] setupArgs(ThreadContext context, Node node, IRubyObject self) {
        if (node == null) return IRubyObject.NULL_ARRAY;

        if (node instanceof ArrayNode) {
            ArrayNode argsArrayNode = (ArrayNode) node;
            ISourcePosition position = context.getPosition();
            int size = argsArrayNode.size();
            IRubyObject[] argsArray = new IRubyObject[size];

            for (int i = 0; i < size; i++) {
                argsArray[i] = evalInternal(context, argsArrayNode.get(i), self, null);
            }

            context.setPosition(position);

            return argsArray;
        }

        return ArgsUtil.convertToJavaArray(evalInternal(context, node, self, null));
    }

    private static RubyModule getEnclosingModule(ThreadContext context, Node node, IRubyObject self, Block block) {
        RubyModule enclosingModule = null;

        if (node instanceof Colon2Node) {
            IRubyObject result = evalInternal(context, ((Colon2Node) node).getLeftNode(), self, block);

            if (result != null && !result.isNil()) {
                enclosingModule = (RubyModule) result;
            }
        } else if (node instanceof Colon3Node) {
            enclosingModule = context.getRuntime().getObject();
        }

        if (enclosingModule == null) {
            enclosingModule = (RubyModule) context.peekCRef().getValue();
        }

        return enclosingModule;
    }

    private static boolean isRescueHandled(ThreadContext context, RubyException currentException, ListNode exceptionNodes,
            IRubyObject self) {
        IRuby runtime = context.getRuntime();
        if (exceptionNodes == null) {
            return currentException.isKindOf(runtime.getClass("StandardError"));
        }

        IRubyObject[] args = setupArgs(context, exceptionNodes, self);

        for (int i = 0; i < args.length; i++) {
            if (!args[i].isKindOf(runtime.getClass("Module"))) {
                throw runtime.newTypeError("class or module required for rescue clause");
            }
            if (args[i].callMethod(context, "===", currentException).isTrue()) return true;
        }
        return false;
    }
    
    public static Block getBlock(ThreadContext context, IRubyObject self, Block currentBlock, Node blockNode) {
        if (blockNode == null) return null;
        
        if (blockNode instanceof IterNode) {
            IterNode iterNode = (IterNode) blockNode;
            // Create block for this iter node
            return Block.createBlock(context, iterNode.getVarNode(),
                    new DynamicScope(iterNode.getScope(), context.getCurrentScope()),
                    iterNode.getCallable(), self, currentBlock);
        } else if (blockNode instanceof BlockPassNode) {
            BlockPassNode blockPassNode = (BlockPassNode) blockNode;
            IRubyObject proc = evalInternal(context, blockPassNode.getBodyNode(), self, currentBlock);

            // No block from a nil proc
            if (proc.isNil()) return null;

            // If not already a proc then we should try and make it one.
            if (!(proc instanceof RubyProc)) {
                proc = proc.convertToType("Proc", "to_proc", false);

                if (!(proc instanceof RubyProc)) {
                    throw context.getRuntime().newTypeError("wrong argument type "
                            + proc.getMetaClass().getName() + " (expected Proc)");
                }
            }

            // TODO: Add safety check for taintedness
            
            if (currentBlock != null) {
                IRubyObject blockObject = currentBlock.getBlockObject();
                // The current block is already associated with proc.  No need to create a new one
                if (blockObject != null && blockObject == proc) return currentBlock;
            }
            
            return ((RubyProc) proc).getBlock();
        }
         
        assert false: "Trying to get block from something which cannot deliver";
        return null;
    }
}
