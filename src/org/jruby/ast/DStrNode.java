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
 * Copyright (C) 2001-2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2001-2002 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
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

import org.jruby.Ruby;
import org.jruby.RubyString;
import org.jruby.ast.types.ILiteralNode;
import org.jruby.ast.visitor.NodeVisitor;
import org.jruby.evaluator.Instruction;
import org.jruby.lexer.yacc.ISourcePosition;
import org.jruby.runtime.Block;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;

/**
 * A string which contains some dynamic elements which needs to be evaluated (introduced by #).
 */
public class DStrNode extends ListNode implements ILiteralNode {
    public DStrNode(ISourcePosition position) {
        super(position, NodeType.DSTRNODE);
    }

    /**
     * Accept for the visitor pattern.
     * @param iVisitor the visitor
     **/
    @Override
    public Instruction accept(NodeVisitor iVisitor) {
        return iVisitor.visitDStrNode(this);
    }
    
    @Override
    public IRubyObject interpret(Ruby runtime, ThreadContext context, IRubyObject self, Block aBlock) {
        return buildDynamicString(runtime, context, self, aBlock, this);
    }
    
    public static RubyString buildDynamicString(Ruby runtime, ThreadContext context, IRubyObject self, Block aBlock, ListNode list) {
        RubyString string = runtime.newString(new ByteList());
        
        int size = list.size();
        for (int i = 0; i < size; i++) {
            DStrNode.appendToString(runtime, context, self, aBlock, string, list.get(i));
        }
   
        return string;
    }
    
    public static void appendToString(Ruby runtime, ThreadContext context, IRubyObject self, Block aBlock, RubyString string, Node node) {
        if (node instanceof StrNode) {
            string.getByteList().append(((StrNode) node).getValue());
        } else {
            string.append(node.interpret(runtime, context, self, aBlock));
        }
    }
}
