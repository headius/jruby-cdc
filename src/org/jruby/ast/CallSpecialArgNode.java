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
 * Copyright (C) 2001 Alan Moore <alan_moore@gmx.net>
 * Copyright (C) 2001-2002 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2001-2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
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

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.lexer.yacc.ISourcePosition;
import org.jruby.runtime.Block;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * A method or operator call.
 */
public final class CallSpecialArgNode extends CallNode {
    public CallSpecialArgNode(ISourcePosition position, Node receiverNode, String name, Node args) {
        super(position, receiverNode, name, args, null);
    }
    
    @Override
    public Node setIterNode(Node iterNode) {
        return new CallSpecialArgBlockNode(getPosition(), getReceiverNode(), getName(), getArgsNode(), (IterNode) iterNode);
    }    
    
    @Override
    public IRubyObject interpret(Ruby runtime, ThreadContext context, IRubyObject self, Block aBlock) {
        IRubyObject receiver = getReceiverNode().interpret(runtime, context, self, aBlock);
        IRubyObject arg = getArgsNode().interpret(runtime, context, self, aBlock);
        
        if (arg instanceof RubyArray) {
            RubyArray nodes = (RubyArray) arg;
            
            switch (nodes.size()) {
                case 0:
                    return callAdapter.call(context, receiver);
                case 1:
                    return callAdapter.call(context, receiver, nodes.eltInternal(0));
                case 2:
                    return callAdapter.call(context, receiver, nodes.eltInternal(0), nodes.eltInternal(1));
                case 3:
                    return callAdapter.call(context, receiver, nodes.eltInternal(0), nodes.eltInternal(1), nodes.eltInternal(2));
                default:
                    return callAdapter.call(context, receiver, nodes.toJavaArrayMaybeUnsafe());
            }
        }
        
        return callAdapter.call(context, receiver, arg);
    }
}
