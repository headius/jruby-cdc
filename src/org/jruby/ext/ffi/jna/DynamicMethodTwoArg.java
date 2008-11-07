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
 * Copyright (C) 2008 JRuby project
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

package org.jruby.ext.ffi.jna;


import com.sun.jna.Function;
import org.jruby.RubyModule;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

final class DynamicMethodTwoArg extends JNADynamicMethod {
    private final Marshaller marshaller1;
    private final Marshaller marshaller2;
    public DynamicMethodTwoArg(RubyModule implementationClass, Function function, 
            FunctionInvoker functionInvoker, Marshaller[] marshallers) {
        super(implementationClass, Arity.TWO_ARGUMENTS, function, functionInvoker);
        marshaller1 = marshallers[0];
        marshaller2 = marshallers[1];
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject[] args, Block block) {
        arity.checkArity(context.getRuntime(), args);
        return call(context, self, clazz, name, args[0], args[1], block);
    }
    
    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self,
            RubyModule klazz, String name, IRubyObject arg1, IRubyObject arg2) {
        final Invocation invocation = new Invocation(context);
        final Object[] nativeArgs = new Object[]{
            marshaller1.marshal(invocation, arg1),
            marshaller2.marshal(invocation, arg2),
        };
        IRubyObject retVal = functionInvoker.invoke(context.getRuntime(), function, nativeArgs);
        invocation.finish();
        return retVal;
    }
}
