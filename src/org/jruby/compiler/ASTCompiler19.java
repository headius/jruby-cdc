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
 * Copyright (C) 2006 Charles O Nutter <headius@headius.com>
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

package org.jruby.compiler;

import org.jruby.RubyInstanceConfig;
import org.jruby.ast.ArgsNode;
import org.jruby.ast.ArgsPushNode;
import org.jruby.ast.IterNode;
import org.jruby.ast.Node;
import org.jruby.ast.LambdaNode;
import org.jruby.ast.ListNode;
import org.jruby.ast.MultipleAsgnNode;
import org.jruby.ast.NodeType;
import org.jruby.runtime.Arity;
import org.jruby.runtime.BlockBody;

/**
 *
 * @author headius
 */
public class ASTCompiler19 extends ASTCompiler {
    @Override
    public void compile(Node node, BodyCompiler context, boolean expr) {
        if (node == null) {
            context.loadNil();
            return;
        }
        switch (node.getNodeType()) {
            case LAMBDANODE:
                compileLambda(node, context, expr);
                break;
            default:
                super.compile(node, context, expr);
        }
    }

    @Override
    public void compileArgs(Node node, BodyCompiler context, boolean expr) {
        final ArgsNode argsNode = (ArgsNode) node;

        final int required = argsNode.getRequiredArgsCount();
        final int opt = argsNode.getOptionalArgsCount();
        final int rest = argsNode.getRestArg();
        
        context.getVariableCompiler().checkMethodArity(required, opt, rest);
        compileMethodArgs(node, context, expr);
    }

    public void compileMethodArgs(Node node, BodyCompiler context, boolean expr) {
        final ArgsNode argsNode = (ArgsNode) node;

        final int required = argsNode.getRequiredArgsCount();
        final int opt = argsNode.getOptionalArgsCount();
        final int rest = argsNode.getRestArg();

        ArrayCallback requiredAssignment = null;
        ArrayCallback optionalGiven = null;
        ArrayCallback optionalNotGiven = null;
        CompilerCallback restAssignment = null;
        CompilerCallback blockAssignment = null;

        if (required > 0) {
            requiredAssignment = new ArrayCallback() {

                        public void nextValue(BodyCompiler context, Object object, int index) {
                            // FIXME: Somehow I'd feel better if this could get the appropriate var index from the ArgumentNode
                            context.getVariableCompiler().assignLocalVariable(index, false);
                        }
                    };
        }

        if (opt > 0) {
            optionalGiven = new ArrayCallback() {

                        public void nextValue(BodyCompiler context, Object object, int index) {
                            Node optArg = ((ListNode) object).get(index);

                            compileAssignment(optArg, context,true);
                            context.consumeCurrentValue();
                        }
                    };
            optionalNotGiven = new ArrayCallback() {

                        public void nextValue(BodyCompiler context, Object object, int index) {
                            Node optArg = ((ListNode) object).get(index);

                            compile(optArg, context,true);
                            context.consumeCurrentValue();
                        }
                    };
        }

        if (rest > -1) {
            restAssignment = new CompilerCallback() {

                        public void call(BodyCompiler context) {
                            context.getVariableCompiler().assignLocalVariable(argsNode.getRestArg(), false);
                        }
                    };
        }

        if (argsNode.getBlock() != null) {
            blockAssignment = new CompilerCallback() {

                        public void call(BodyCompiler context) {
                            context.getVariableCompiler().assignLocalVariable(argsNode.getBlock().getCount(), false);
                        }
                    };
        }

        context.getVariableCompiler().assignMethodArguments19(
                argsNode.getPre(),
                argsNode.getPreCount(),
                argsNode.getPost(),
                argsNode.getPostCount(),
                argsNode.getPostIndex(),
                argsNode.getOptArgs(),
                argsNode.getOptionalArgsCount(),
                requiredAssignment,
                optionalGiven,
                optionalNotGiven,
                restAssignment,
                blockAssignment);
        // TODO: don't require pop
        if (!expr) context.consumeCurrentValue();
    }

    @Override
    public void compileArgsPush(Node node, BodyCompiler context, boolean expr) {
        ArgsPushNode argsPush = (ArgsPushNode) node;

        compile(argsPush.getFirstNode(), context,true);
        compile(argsPush.getSecondNode(), context,true);
        context.appendToArray();
        // TODO: don't require pop
        if (!expr) context.consumeCurrentValue();
    }

    @Override
    public void compileAssignment(Node node, BodyCompiler context, boolean expr) {
        switch (node.getNodeType()) {
            case ARGSNODE:
                if (((ArgsNode)node).getArity().getValue() != 0) {
                    throw new NotCompilableException("Can't compile args arity > 0 yet (1.9): " + node);
                }
                break;
            default:
                super.compileAssignment(node, context, expr);
        }
    }

    @Override
    public void compileIter(Node node, BodyCompiler context) {
        final IterNode iterNode = (IterNode)node;
        final ArgsNode argsNode = (ArgsNode)iterNode.getVarNode();

        // create the closure class and instantiate it
        final CompilerCallback closureBody = new CompilerCallback() {
            public void call(BodyCompiler context) {
                if (iterNode.getBodyNode() != null) {
                    compile(iterNode.getBodyNode(), context, true);
                } else {
                    context.loadNil();
                }
            }
        };

        // create the closure class and instantiate it
        final CompilerCallback closureArgs = new CompilerCallback() {
            public void call(BodyCompiler context) {
                if (iterNode.getVarNode() != null) {
                    final int required = argsNode.getRequiredArgsCount();
                    final int opt = argsNode.getOptionalArgsCount();
                    final int rest = argsNode.getRestArg();
                    if (iterNode instanceof LambdaNode) {

                        context.getVariableCompiler().checkMethodArity(required, opt, rest);
                        compileMethodArgs(argsNode, context, true);
                    } else {
                        compileMethodArgs(argsNode, context, true);
                    }
                }
            }
        };

        boolean hasMultipleArgsHead = false;
        if (iterNode.getVarNode() instanceof MultipleAsgnNode) {
            hasMultipleArgsHead = ((MultipleAsgnNode) iterNode.getVarNode()).getHeadNode() != null;
        }

        NodeType argsNodeId = BlockBody.getArgumentTypeWackyHack(iterNode);

        ASTInspector inspector = new ASTInspector();
        inspector.inspect(iterNode.getBodyNode());
        inspector.inspect(iterNode.getVarNode());

        if (argsNodeId == null) {
            // no args, do not pass args processor
            context.createNewClosure19(iterNode.getPosition().getStartLine(), iterNode.getScope(), Arity.procArityOf(iterNode.getVarNode()).getValue(),
                    closureBody, null, hasMultipleArgsHead, argsNodeId, inspector);
        } else {
            context.createNewClosure19(iterNode.getPosition().getStartLine(), iterNode.getScope(), Arity.procArityOf(iterNode.getVarNode()).getValue(),
                    closureBody, closureArgs, hasMultipleArgsHead, argsNodeId, inspector);
        }
    }

    public void compileLambda(Node node, BodyCompiler context, boolean expr) {
        final LambdaNode lambdaNode = (LambdaNode)node;

        boolean doit = expr || !RubyInstanceConfig.PEEPHOLE_OPTZ;
        boolean popit = !RubyInstanceConfig.PEEPHOLE_OPTZ && !expr;

        if (doit) {
            context.createNewLambda(new CompilerCallback() {
                public void call(BodyCompiler context) {
                    compileIter(lambdaNode, context);
                }
            });
        }

        if (popit) context.consumeCurrentValue();
    }
}
