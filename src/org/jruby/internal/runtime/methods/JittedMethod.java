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
 * Copyright (C) 2004-2008 Thomas E Enebo <enebo@acm.org>
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
package org.jruby.internal.runtime.methods;

import org.jruby.Ruby;
import org.jruby.RubyModule;
import org.jruby.ast.executable.Script;
import org.jruby.exceptions.JumpException;
import org.jruby.internal.runtime.JumpTarget;
import org.jruby.lexer.yacc.ISourcePosition;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * This is the mixed-mode method type.  It will call out to JIT compiler to see if the compiler
 * wants to JIT or not.  If the JIT compiler does JIT this it will return the new method
 * to be executed here instead of executing the interpreted version of this method.  The next
 * invocation of the method will end up causing the runtime to load and execute the newly JIT'd
 * method.
 *
 */
public class JittedMethod extends DynamicMethod implements JumpTarget {
    private final StaticScope staticScope;
    private final Script jitCompiledScript;
    private final ISourcePosition position;
    private final Arity arity;
    
    public JittedMethod(RubyModule implementationClass, StaticScope staticScope, Script jitCompiledScript,
            CallConfiguration jitCallConfig, Visibility visibility, Arity arity, ISourcePosition position) {
        super(implementationClass, visibility, jitCallConfig);
        this.position = position;
        this.jitCompiledScript = jitCompiledScript;
        this.staticScope = staticScope;
        this.arity = arity;
    }

    public StaticScope getStaticScope() {
        return staticScope;
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject[] args, Block block) {
        Ruby runtime = context.getRuntime();
        
        try {
            pre(context, self, name, block, args.length);

            return jitCompiledScript.__file__(context, self, args, block);
        } catch (JumpException.ReturnJump rj) {
            return handleReturn(context, rj);
        } catch (JumpException.RedoJump rj) {
            return handleRedo(runtime);
        } finally {
            post(runtime, context, name);
        }
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject[] args) {
        Ruby runtime = context.getRuntime();

        try {
            pre(context, self, name, Block.NULL_BLOCK, args.length);

            return jitCompiledScript.__file__(context, self, args, Block.NULL_BLOCK);
        } catch (JumpException.ReturnJump rj) {
            return handleReturn(context, rj);
        } catch (JumpException.RedoJump rj) {
            return handleRedo(runtime);
        } finally {
            post(runtime,context, name);
        }
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name) {
        Ruby runtime = context.getRuntime();

        try {
            pre(context, self, name, Block.NULL_BLOCK, 0);

            return jitCompiledScript.__file__(context, self, Block.NULL_BLOCK);
        } catch (JumpException.ReturnJump rj) {
            return handleReturn(context, rj);
        } catch (JumpException.RedoJump rj) {
            return handleRedo(runtime);
        } finally {
            post(runtime, context, name);
        }
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, Block block) {
        Ruby runtime = context.getRuntime();

        try {
            pre(context, self, name, block, 0);

            return jitCompiledScript.__file__(context, self, block);
        } catch (JumpException.ReturnJump rj) {
            return handleReturn(context, rj);
        } catch (JumpException.RedoJump rj) {
            return handleRedo(runtime);
        } finally {
            post(runtime, context, name);
        }
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject arg0) {
        Ruby runtime = context.getRuntime();

        try {
            pre(context, self, name, Block.NULL_BLOCK, 1);

            return jitCompiledScript.__file__(context, self, arg0, Block.NULL_BLOCK);
        } catch (JumpException.ReturnJump rj) {
            return handleReturn(context, rj);
        } catch (JumpException.RedoJump rj) {
            return handleRedo(runtime);
        } finally {
            post(runtime, context, name);
        }
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject arg0, Block block) {
        Ruby runtime = context.getRuntime();

        try {
            pre(context, self, name, block, 1);

            return jitCompiledScript.__file__(context, self, arg0, block);
        } catch (JumpException.ReturnJump rj) {
            return handleReturn(context, rj);
        } catch (JumpException.RedoJump rj) {
            return handleRedo(runtime);
        } finally {
            post(runtime, context, name);
        }
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject arg0, IRubyObject arg1) {
        Ruby runtime = context.getRuntime();

        try {
            pre(context, self, name, Block.NULL_BLOCK, 2);

            return jitCompiledScript.__file__(context, self, arg0, arg1, Block.NULL_BLOCK);
        } catch (JumpException.ReturnJump rj) {
            return handleReturn(context, rj);
        } catch (JumpException.RedoJump rj) {
            return handleRedo(runtime);
        } finally {
            post(runtime, context, name);
        }
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject arg0, IRubyObject arg1, Block block) {
        Ruby runtime = context.getRuntime();

        try {
            pre(context, self, name, block, 2);

            return jitCompiledScript.__file__(context, self, arg0, arg1, block);
        } catch (JumpException.ReturnJump rj) {
            return handleReturn(context, rj);
        } catch (JumpException.RedoJump rj) {
            return handleRedo(runtime);
        } finally {
            post(runtime, context, name);
        }
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject arg0, IRubyObject arg1, IRubyObject arg2) {
        Ruby runtime = context.getRuntime();

        try {
            pre(context, self, name, Block.NULL_BLOCK, 3);

            return jitCompiledScript.__file__(context, self, arg0, arg1, arg2, Block.NULL_BLOCK);
        } catch (JumpException.ReturnJump rj) {
            return handleReturn(context, rj);
        } catch (JumpException.RedoJump rj) {
            return handleRedo(runtime);
        } finally {
            post(runtime, context, name);
        }
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject arg0, IRubyObject arg1, IRubyObject arg2, Block block) {
        Ruby runtime = context.getRuntime();

        try {
            pre(context, self, name, block, 3);

            return jitCompiledScript.__file__(context, self, arg0, arg1, arg2, block);
        } catch (JumpException.ReturnJump rj) {
            return handleReturn(context, rj);
        } catch (JumpException.RedoJump rj) {
            return handleRedo(runtime);
        } finally {
            post(runtime, context, name);
        }
    }

    protected void pre(ThreadContext context, IRubyObject self, String name, Block block, int argsLength) {
        callConfig.pre(context, self, getImplementationClass(), name, block, staticScope, this);

        getArity().checkArity(context.getRuntime(), argsLength);
    }

    protected void post(Ruby runtime, ThreadContext context, String name) {
        callConfig.post(context);
    }

    public ISourcePosition getPosition() {
        return position;
    }

    @Override
    public Arity getArity() {
        return arity;
    }

    public DynamicMethod dup() {
        return new JittedMethod(getImplementationClass(), staticScope, jitCompiledScript, callConfig, getVisibility(), arity, position);
    }


}
