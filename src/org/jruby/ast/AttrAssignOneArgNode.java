/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jruby.ast;

import org.jruby.Ruby;
import org.jruby.lexer.yacc.ISourcePosition;
import org.jruby.runtime.Block;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

/**
 *
 * @author enebo
 */
public class AttrAssignOneArgNode extends AttrAssignNode {
    private Node arg1;
    
    public AttrAssignOneArgNode(ISourcePosition position, Node receiverNode, String name, ArrayNode argsNode) {
        super(position, receiverNode, name, argsNode);
        
        assert argsNode.size() == 1 : "argsNode.size() is 1";
        
        arg1 = argsNode.get(0);
    }

    @Override
    public IRubyObject interpret(Ruby runtime, ThreadContext context, IRubyObject self, Block aBlock) {
        IRubyObject receiver = receiverNode.interpret(runtime, context, self, aBlock);
        IRubyObject param1 = arg1.interpret(runtime, context, self, aBlock);
        
        assert receiver.getMetaClass() != null : receiver.getClass().getName();
        
        // If reciever is self then we do the call the same way as vcall
        if (receiver == self) {
            variableCallAdapter.callFrom(context, self, receiver, param1);
        } else {
            normalCallAdapter.callFrom(context, self, receiver, param1);
        }

        return param1;
    }
    
        
    @Override
    public IRubyObject assign(Ruby runtime, ThreadContext context, IRubyObject self, IRubyObject value, Block aBlock, boolean checkArity) {
        IRubyObject receiver = receiverNode.interpret(runtime, context, self, aBlock);
        IRubyObject param1 = arg1.interpret(runtime, context, self, aBlock);
        
        assert receiver.getMetaClass() != null : receiver.getClass().getName();
        
        // If reciever is self then we do the call the same way as vcall
        if (receiver == self) {
            variableCallAdapter.callFrom(context, self, receiver, param1, value);
        } else {
            normalCallAdapter.callFrom(context, self, receiver, param1, value);
        }
        
        return runtime.getNil();
    }
}
