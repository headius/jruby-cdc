package org.jruby;

import org.jruby.runtime.Arity;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;

/**
 * 
 * @author jpetersen
 * @version $Revision$
 */
public final class TopSelfFactory {

    /**
     * Constructor for TopSelfFactory.
     */
    private TopSelfFactory() {
        super();
    }
    
    public static IRubyObject createTopSelf(final Ruby runtime) {
        IRubyObject topSelf = new RubyObject(runtime, runtime.getClasses().getObjectClass());
        
        topSelf.defineSingletonMethod("to_s", new Callback() {
            /**
             * @see org.jruby.runtime.callback.Callback#execute(IRubyObject, IRubyObject[])
             */
            public IRubyObject execute(IRubyObject recv, IRubyObject[] args) {
                return runtime.newString("main");
            }

            /**
             * @see org.jruby.runtime.callback.Callback#getArity()
             */
            public Arity getArity() {
                return Arity.noArguments();
            }
        });
        
        topSelf.defineSingletonMethod("include", new Callback() {
            /**
             * @see org.jruby.runtime.callback.Callback#execute(IRubyObject, IRubyObject[])
             */
            public IRubyObject execute(IRubyObject recv, IRubyObject[] args) {
                runtime.secure(4);
                return runtime.getClasses().getObjectClass().include(args);
            }

            /**
             * @see org.jruby.runtime.callback.Callback#getArity()
             */
            public Arity getArity() {
                return Arity.optional();
            }
        });
        
        topSelf.defineSingletonMethod("public", new Callback() {
            /**
             * @see org.jruby.runtime.callback.Callback#execute(IRubyObject, IRubyObject[])
             */
            public IRubyObject execute(IRubyObject recv, IRubyObject[] args) {
                return runtime.getClasses().getObjectClass().rbPublic(args);
            }

            /**
             * @see org.jruby.runtime.callback.Callback#getArity()
             */
            public Arity getArity() {
                return Arity.optional();
            }
        });
        
        topSelf.defineSingletonMethod("private", new Callback() {
            /**
             * @see org.jruby.runtime.callback.Callback#execute(IRubyObject, IRubyObject[])
             */
            public IRubyObject execute(IRubyObject recv, IRubyObject[] args) {
                return runtime.getClasses().getObjectClass().rbPrivate(args);
            }

            /**
             * @see org.jruby.runtime.callback.Callback#getArity()
             */
            public Arity getArity() {
                return Arity.optional();
            }
        });
        
        return topSelf;
    }
}