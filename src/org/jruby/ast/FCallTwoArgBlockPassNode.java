/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jruby.ast;

import org.jruby.Ruby;
import org.jruby.exceptions.JumpException;
import org.jruby.lexer.yacc.ISourcePosition;
import org.jruby.runtime.Block;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

/**
 *
 * @author enebo
 */
public class FCallTwoArgBlockPassNode extends FCallNode {
    private Node arg1;
    private Node arg2;
    
    public FCallTwoArgBlockPassNode(ISourcePosition position, String name, ArrayNode args, BlockPassNode iter) {
        super(position, name, args, iter);
        
        assert args.size() == 2 : "args.size() is 2";
        
        arg1 = args.get(0);
        arg2 = args.get(1);
    }

    @Override
    public IRubyObject interpret(Ruby runtime, ThreadContext context, IRubyObject self, Block aBlock) {
        Block block = getBlock(runtime, context, self, aBlock);
        
        while (true) {
            try {
                return callAdapter.call(context, self,
                        arg1.interpret(runtime, context, self, aBlock),
                        arg2.interpret(runtime, context, self, aBlock), block);
            } catch (JumpException.RetryJump rj) {
                // allow loop to retry
            }
        }
    }
}
