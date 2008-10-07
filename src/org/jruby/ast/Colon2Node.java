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
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004-2006 Thomas E Enebo <enebo@acm.org>
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
import org.jruby.RubyModule;
import org.jruby.ast.types.INameNode;
import org.jruby.ast.visitor.NodeVisitor;
import org.jruby.evaluator.Instruction;
import org.jruby.exceptions.JumpException;
import org.jruby.javasupport.util.RuntimeHelpers;
import org.jruby.lexer.yacc.ISourcePosition;
import org.jruby.runtime.Block;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.IdUtil;

/** 
 * Represents a '::' constant access or method call (Java::JavaClass).
 */
public final class Colon2Node extends Colon3Node implements INameNode {
    private final Node leftNode;
    private final boolean isConstant;

    public Colon2Node(ISourcePosition position, Node leftNode, String name) {
        super(position, NodeType.COLON2NODE, name);
        this.leftNode = leftNode;
        this.isConstant = IdUtil.isConstant(name);
    }
    
    /**
     * Accept for the visitor pattern.
     * @param iVisitor the visitor
     **/
    @Override
    public Instruction accept(NodeVisitor iVisitor) {
        return iVisitor.visitColon2Node(this);
    }

    /**
     * Gets the leftNode.
     * @return Returns a Node
     */
    public Node getLeftNode() {
        return leftNode;
    }

    @Override
    public List<Node> childNodes() {
        return Node.createList(leftNode);
    }
    
    @Override
    public String toString() {
        String result = "Colon2Node [";
        if (leftNode != null) result += leftNode;
        result += getName();
        return result + "]";
    }
 
    /** Get parent module/class that this module represents */
    @Override
    public RubyModule getEnclosingModule(Ruby runtime, ThreadContext context, IRubyObject self, Block aBlock) {
        if (leftNode != null) {
            IRubyObject result = leftNode.interpret(runtime, context, self, aBlock);
            return RuntimeHelpers.prepareClassNamespace(context, result);
        } else {
            return context.getCurrentScope().getStaticScope().getModule();
        }
    }
    
    @Override
    public IRubyObject interpret(Ruby runtime, ThreadContext context, IRubyObject self, Block aBlock) {
        // TODO: Made this more colon3 friendly because of cpath production
        // rule in grammar (it is convenient to think of them as the same thing
        // at a grammar level even though evaluation is).

        // TODO: Can we eliminate leftnode by making implicit Colon3node?
        if (leftNode == null) return runtime.getObject().fastGetConstantFrom(name);

        IRubyObject result = leftNode.interpret(runtime, context, self, aBlock);
        
        if (isConstant) {
            return getConstantFrom(runtime, result);
        }

        return RuntimeHelpers.invoke(context, result, name, aBlock);
    }
    
    private IRubyObject getConstantFrom(Ruby runtime, IRubyObject result) {
        return RuntimeHelpers.checkIsModule(result).fastGetConstantFrom(name);
    }
    
    @Override
    public String definition(Ruby runtime, ThreadContext context, IRubyObject self, Block aBlock) {
       try {
            IRubyObject left = leftNode.interpret(runtime, context, self, aBlock);

            if (isModuleAndHasConstant(left)) {
                return "constant";
            } else if (hasMethod(left)) {
                return "method";
            }
        } catch (JumpException e) {
        }
            
        return null;
    }
    
    private boolean isModuleAndHasConstant(IRubyObject left) {
        return left instanceof RubyModule && ((RubyModule) left).fastGetConstantAt(name) != null;
    }
    
    private boolean hasMethod(IRubyObject left) {
        return left.getMetaClass().isMethodBound(name, true);
    }
 }
