/*
 * CallConfiguration.java
 * 
 * Created on Jul 13, 2007, 6:51:14 PM
 * 
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jruby.internal.runtime.methods;

import org.jruby.RubyModule;
import org.jruby.anno.JRubyMethod;
import org.jruby.internal.runtime.JumpTarget;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.Block;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

/**
 *
 * @author headius
 */
public abstract class CallConfiguration {
    public static CallConfiguration FrameFullScopeFull = new CallConfiguration(Framing.Full, Scoping.Full) {
        void pre(ThreadContext context, IRubyObject self, RubyModule implementer, String name, Block block, StaticScope scope, JumpTarget jumpTarget) {
            context.preMethodFrameAndScope(implementer, name, self, block, scope);
        }
        void post(ThreadContext context) {
            context.postMethodFrameAndScope();
        }
    };
    public static CallConfiguration FrameFullScopeDummy = new CallConfiguration(Framing.Full, Scoping.Dummy) {
        void pre(ThreadContext context, IRubyObject self, RubyModule implementer, String name, Block block, StaticScope scope, JumpTarget jumpTarget) {
            context.preMethodFrameAndDummyScope(implementer, name, self, block, scope);
        }
        void post(ThreadContext context) {
            context.postMethodFrameAndScope();
        }
    };
    public static CallConfiguration FrameFullScopeNone = new CallConfiguration(Framing.Full, Scoping.None) {
        void pre(ThreadContext context, IRubyObject self, RubyModule implementer, String name, Block block, StaticScope scope, JumpTarget jumpTarget) {
            context.preMethodFrameOnly(implementer, name, self, block);
        }
        void post(ThreadContext context) {
            context.postMethodFrameOnly();
        }
    };
    public static CallConfiguration FrameBacktraceScopeFull = new CallConfiguration(Framing.Backtrace, Scoping.Full) {
        void pre(ThreadContext context, IRubyObject self, RubyModule implementer, String name, Block block, StaticScope scope, JumpTarget jumpTarget) {
            context.preMethodBacktraceAndScope(name, implementer, scope);
        }
        void post(ThreadContext context) {
            context.postMethodBacktraceAndScope();
        }
    };
    public static CallConfiguration FrameBacktraceScopeDummy = new CallConfiguration(Framing.Backtrace, Scoping.Dummy) {
        void pre(ThreadContext context, IRubyObject self, RubyModule implementer, String name, Block block, StaticScope scope, JumpTarget jumpTarget) {
            context.preMethodBacktraceDummyScope(implementer, name, scope);
        }
        void post(ThreadContext context) {
            context.postMethodBacktraceDummyScope();
        }
    };
    public static CallConfiguration FrameBacktraceScopeNone = new CallConfiguration(Framing.Backtrace, Scoping.None) {
        void pre(ThreadContext context, IRubyObject self, RubyModule implementer, String name, Block block, StaticScope scope, JumpTarget jumpTarget) {
            context.preMethodBacktraceOnly(name);
        }
        void post(ThreadContext context) {
            context.postMethodBacktraceOnly();
        }
    };
    public static CallConfiguration FrameNoneScopeFull = new CallConfiguration(Framing.None, Scoping.Full) {
        void pre(ThreadContext context, IRubyObject self, RubyModule implementer, String name, Block block, StaticScope scope, JumpTarget jumpTarget) {
            context.preMethodScopeOnly(implementer, scope);
        }
        void post(ThreadContext context) {
            context.postMethodScopeOnly();
        }
    };
    public static CallConfiguration FrameNoneScopeDummy = new CallConfiguration(Framing.None, Scoping.Dummy) {
        void pre(ThreadContext context, IRubyObject self, RubyModule implementer, String name, Block block, StaticScope scope, JumpTarget jumpTarget) {
            context.preMethodNoFrameAndDummyScope(implementer, scope);
        }
        void post(ThreadContext context) {
            context.postMethodScopeOnly();
        }
    };
    public static CallConfiguration FrameNoneScopeNone = new CallConfiguration(Framing.None, Scoping.None) {
        void pre(ThreadContext context, IRubyObject self, RubyModule implementer, String name, Block block, StaticScope scope, JumpTarget jumpTarget) {
        }
        void post(ThreadContext context) {
        }
    };
    
    public static CallConfiguration getCallConfigByAnno(JRubyMethod anno) {
        return getCallConfig(anno.frame(), anno.scope(), anno.backtrace());
    }
    
    public static CallConfiguration getCallConfig(boolean frame, boolean scope, boolean backtrace) {
        if (frame) {
            if (scope) {
                return FrameFullScopeFull;
            } else {
                return FrameFullScopeNone;
            }
        } else if (scope) {
            if (backtrace) {
                return FrameBacktraceScopeFull;
            } else {
                return FrameNoneScopeFull;
            }
        } else if (backtrace) {
            return FrameBacktraceScopeNone;
        } else {
            return FrameNoneScopeNone;
        }
    }

    private final Framing framing;
    private final Scoping scoping;

    CallConfiguration(Framing framing, Scoping scoping) {
        this.framing = framing;
        this.scoping = scoping;
    }

    public final Framing framing() {return framing;}
    public final Scoping scoping() {return scoping;}
    
    abstract void pre(ThreadContext context, IRubyObject self, RubyModule implementer, String name, Block block, StaticScope scope, JumpTarget jumpTarget);
    abstract void post(ThreadContext context);
    boolean isNoop() { return framing == Framing.None && scoping == Scoping.None; }
}
