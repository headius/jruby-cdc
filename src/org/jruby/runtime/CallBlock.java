/***** BEGIN LICENSE BLOCK *****
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
 * Copyright (C) 2006 Ola Bini <ola@ologix.com>
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
package org.jruby.runtime;

import org.jruby.RubyModule;
import org.jruby.exceptions.JumpException;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * A Block implemented using a Java-based BlockCallback implementation
 * rather than with an ICallable. For lightweight block logic within
 * Java code.
 */
public class CallBlock extends BlockBody {
    private Arity arity;
    private BlockCallback callback;
    private RubyModule imClass;
    private ThreadContext context;
    
    public static Block newCallClosure(IRubyObject self, RubyModule imClass, Arity arity, BlockCallback callback, ThreadContext context) {
        Binding binding = new Binding(self,
                context.getCurrentFrame().duplicate(),
                Visibility.PUBLIC,
                context.getRubyClass(),
                context.getCurrentScope());
        BlockBody body = new CallBlock(imClass, arity, callback, context);
        
        return new Block(body, binding);
    }

    private CallBlock(RubyModule imClass, Arity arity, BlockCallback callback, ThreadContext context) {
        this.arity = arity;
        this.callback = callback;
        this.imClass = imClass;
        this.context = context;
    }
    
    protected void pre(ThreadContext context, RubyModule klass, Binding binding) {
        // FIXME: This could be a "light" block
        context.preYieldNoScope(binding, klass);
    }
    
    protected void post(ThreadContext context, Binding binding) {
        context.postYieldNoScope();
    }

    public IRubyObject call(ThreadContext context, IRubyObject[] args, Binding binding, Block.Type type) {
        return callback.call(context, args, Block.NULL_BLOCK);
    }
    
    public IRubyObject yield(ThreadContext context, IRubyObject value, Binding binding, Block.Type type) {
        return yield(context, value, null, null, false, binding, type);
    }

    /**
     * Yield to this block, usually passed to the current call.
     * 
     * @param context represents the current thread-specific data
     * @param value The value to yield, either a single value or an array of values
     * @param self The current self
     * @param klass
     * @param aValue Should value be arrayified or not?
     * @return
     */
    public IRubyObject yield(ThreadContext context, IRubyObject value, IRubyObject self, 
            RubyModule klass, boolean aValue, Binding binding, Block.Type type) {
        if (klass == null) {
            self = binding.getSelf();
            // FIXME: We never set this back!
            binding.getFrame().setSelf(self);
        }
        
        pre(context, klass, binding);

        try {
            IRubyObject[] args = new IRubyObject[] {value};
            // This while loop is for restarting the block call in case a 'redo' fires.
            while (true) {
                try {
                    return callback.call(context, args, Block.NULL_BLOCK);
                } catch (JumpException.RedoJump rj) {
                    context.pollThreadEvents();
                    // do nothing, allow loop to redo
                } catch (JumpException.BreakJump bj) {
                    if (bj.getTarget() == null) {
                        bj.setTarget(this);                            
                    }                        
                    throw bj;
                }
            }
        } catch (JumpException.NextJump nj) {
            // A 'next' is like a local return from the block, ending this call or yield.
            return (IRubyObject)nj.getValue();
        } finally {
            post(context, binding);
        }
    }
    
    public StaticScope getStaticScope() {
        throw new RuntimeException("CallBlock does not have a static scope; this should not be called");
    }

    public Block cloneBlock(Binding binding) {
        binding = new Binding(binding.getSelf(), context.getCurrentFrame().duplicate(),
                Visibility.PUBLIC,
                context.getRubyClass(),
                context.getCurrentScope());
        return new Block(this, binding);
    }

    public Arity arity() {
        return arity;
    }
}
