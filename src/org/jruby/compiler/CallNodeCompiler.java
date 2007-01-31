/*
 * CallNodeCompiler.java
 *
 * Created on January 3, 2007, 4:11 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.jruby.compiler;

import org.jruby.ast.CallNode;
import org.jruby.ast.IterNode;
import org.jruby.ast.Node;
import org.jruby.runtime.Arity;

/**
 *
 * @author headius
 */
public class CallNodeCompiler implements NodeCompiler {
    
    /** Creates a new instance of CallNodeCompiler */
    public CallNodeCompiler() {
    }
    
    public void compile(Node node, Compiler context) {
        context.lineNumber(node.getPosition());
        
        CallNode callNode = (CallNode)node;
        
        if (callNode.getIterNode() == null) {
            // no block, go for simple version
            
            // handle receiver
            NodeCompilerFactory.getCompiler(callNode.getReceiverNode()).compile(callNode.getReceiverNode(), context);
            
            if (callNode.getArgsNode() != null) {
                // args compiler processes args and results in an args array for invocation
                NodeCompiler argsCompiler = NodeCompilerFactory.getArgumentsCompiler(callNode.getArgsNode());
                
                argsCompiler.compile(callNode.getArgsNode(), context);

                context.invokeDynamic(callNode.getName(), true, true, null);
            } else {
                context.invokeDynamic(callNode.getName(), true, false, null);
            }
        } else {
            // FIXME: Missing blockpassnode handling
            final IterNode iterNode = (IterNode) callNode.getIterNode();

            // create the closure class and instantiate it
            final ClosureCallback closureBody = new ClosureCallback() {
                public void compile(Compiler context) {
                    NodeCompilerFactory.getCompiler(iterNode.getBodyNode()).compile(iterNode.getBodyNode(), context);
                }
            };
            
            final ClosureCallback closureArg = new ClosureCallback() {
                public void compile(Compiler context) {
                    context.createNewClosure(iterNode.getScope(), Arity.procArityOf(iterNode.getVarNode()).getValue(), closureBody);
                }
            };
            
            // handle receiver
            NodeCompilerFactory.getCompiler(callNode.getReceiverNode()).compile(callNode.getReceiverNode(), context);
            
            if (callNode.getArgsNode() != null) {
                // args compiler processes args and results in an args array for invocation
                NodeCompiler argsCompiler = NodeCompilerFactory.getArgumentsCompiler(callNode.getArgsNode());
                
                argsCompiler.compile(callNode.getArgsNode(), context);

                context.invokeDynamic(callNode.getName(), true, true, closureArg);
            } else {
                context.createNewClosure(iterNode.getScope(), Arity.procArityOf(iterNode.getVarNode()).getValue(), closureBody);
                
                context.invokeDynamic(callNode.getName(), true, false, closureArg);
            }
        }
    }
    
}
