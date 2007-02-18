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
 * Copyright (C) 2007 Charles Oliver Nutter <headius@headius.com>
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
import org.jruby.lexer.yacc.ISourcePosition;
import org.jruby.runtime.Arity;
import org.jruby.runtime.DynamicMethod;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.exceptions.RaiseException;
import org.jruby.exceptions.JumpException;
import org.jruby.exceptions.ThreadKill;
import org.jruby.exceptions.MainExitException;
import org.jruby.runtime.Block;
import org.jruby.util.collections.SinglyLinkedList;

public abstract class CompiledMethod extends AbstractMethod implements Cloneable{
    private Arity arity;
    private SinglyLinkedList cref;
    
    public CompiledMethod(RubyModule implementationClass, Arity arity, Visibility visibility, SinglyLinkedList cref) {
    	super(implementationClass, visibility);
        this.arity = arity;
        this.cref = cref;
    }

    public void preMethod(ThreadContext context, RubyModule lastClass, IRubyObject recv, String name, IRubyObject[] args, boolean noSuper) {
        // needed for const lookups to work
        context.preCompiledMethod(implementationClass, cref);
    }
    
    public void postMethod(ThreadContext context) {
        context.postCompiledMethod();
    }
    
    public IRubyObject internalCall(ThreadContext context, IRubyObject receiver, RubyModule lastClass, String name, IRubyObject[] args, boolean noSuper, Block block) {
        assert false;
        return null;
    }
    
    private IRubyObject wrap(ThreadContext context, Ruby runtime, IRubyObject receiver, IRubyObject[] args, Block block) {
        try {
            return call(context, receiver, args, block);
        } catch(RaiseException e) {
            throw e;
        } catch(JumpException e) {
            throw e;
        } catch(ThreadKill e) {
            throw e;
        } catch(MainExitException e) {
            throw e;
        } catch(Exception e) {
            runtime.getJavaSupport().handleNativeException(e);
            return runtime.getNil();
        }        
    }

    public IRubyObject call(ThreadContext context, IRubyObject receiver, RubyModule lastClass, String name, IRubyObject[] args, boolean noSuper, Block block) {
        Ruby runtime = context.getRuntime();
        arity.checkArity(runtime, args);

        if(runtime.getTraceFunction() != null) {
            ISourcePosition position = context.getPosition();

            runtime.callTraceFunction(context, "c-call", position, receiver, name, getImplementationClass());
            try {
                return wrap(context, runtime, receiver, args, block);
            } finally {
                runtime.callTraceFunction(context, "c-return", position, receiver, name, getImplementationClass());
            }
        }
        return wrap(context, runtime, receiver, args, block);
    }

    public abstract IRubyObject call(ThreadContext context, IRubyObject receiver, IRubyObject[] args, Block block);
    
    public DynamicMethod dup() {
        try {
            CompiledMethod msm = (CompiledMethod)clone();
            return msm;
        } catch (CloneNotSupportedException cnse) {
        return null;
    }
    }

    public Arity getArity() {
        return arity;
    }
}// SimpleInvocationMethod
