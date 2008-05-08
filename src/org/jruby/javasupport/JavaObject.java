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
 * Copyright (C) 2001 Alan Moore <alan_moore@gmx.net>
 * Copyright (C) 2001-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2004 David Corbin <dcorbin@users.sourceforge.net>
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
package org.jruby.javasupport;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyModule;
import org.jruby.RubyObject;
import org.jruby.RubyString;
import org.jruby.anno.JRubyMethod;
import org.jruby.anno.JRubyClass;
import org.jruby.runtime.Block;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ObjectMarshal;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

/**
 *
 * @author  jpetersen
 */
@JRubyClass(name="Java::JavaObject")
public class JavaObject extends RubyObject {

    private static Object NULL_LOCK = new Object();
    private final Object value;

    protected JavaObject(Ruby runtime, RubyClass rubyClass, Object value) {
        super(runtime, rubyClass);
        this.value = value;
    }

    protected JavaObject(Ruby runtime, Object value) {
        this(runtime, runtime.getJavaSupport().getJavaObjectClass(), value);
    }

    public static JavaObject wrap(Ruby runtime, Object value) {
        if (value != null) {
            if (value instanceof Class) {
                return JavaClass.get(runtime, (Class<?>) value);
            } else if (value.getClass().isArray()) {
                return new JavaArray(runtime, value);
            }
        }
        return new JavaObject(runtime, value);
    }

    public Class<?> getJavaClass() {
        return value != null ? value.getClass() : Void.TYPE;
    }

    public Object getValue() {
        return value;
    }

    public static RubyClass createJavaObjectClass(Ruby runtime, RubyModule javaModule) {
        // FIXME: Ideally JavaObject instances should be marshallable, which means that
        // the JavaObject metaclass should have an appropriate allocator. JRUBY-414
        RubyClass result = javaModule.defineClassUnder("JavaObject", runtime.getObject(), ObjectAllocator.NOT_ALLOCATABLE_ALLOCATOR);

        registerRubyMethods(runtime, result);

        result.getMetaClass().undefineMethod("new");
        result.getMetaClass().undefineMethod("allocate");

        result.setMarshal(ObjectMarshal.NOT_MARSHALABLE_MARSHAL);

        return result;
    }

    protected static void registerRubyMethods(Ruby runtime, RubyClass result) {
        result.defineAnnotatedMethods(JavaObject.class);
    }

    public boolean equals(Object other) {
        return other instanceof JavaObject &&
                this.value == ((JavaObject) other).value;
    }

    public int hashCode() {
        if (value != null) {
            return value.hashCode();
        }
        return 0;
    }

    @JRubyMethod
    public RubyFixnum hash() {
        return getRuntime().newFixnum(hashCode());
    }

    @JRubyMethod
    public IRubyObject to_s() {
        if (value != null) {
            String stringValue = value.toString();
            if (stringValue != null) {
                return RubyString.newUnicodeString(getRuntime(), value.toString());
            }

            return getRuntime().getNil();
        }
        return RubyString.newEmptyString(getRuntime());
    }

    @JRubyMethod(name = {"==", "eql?"}, required = 1)
    public IRubyObject op_equal(IRubyObject other) {
        if (!(other instanceof JavaObject)) {
            other = other.getInstanceVariables().fastGetInstanceVariable("@java_object");
            if (!(other instanceof JavaObject)) {
                return getRuntime().getFalse();
            }
        }

        if (getValue() == null && ((JavaObject) other).getValue() == null) {
            return getRuntime().getTrue();
        }

        boolean isEqual = getValue().equals(((JavaObject) other).getValue());
        return isEqual ? getRuntime().getTrue() : getRuntime().getFalse();
    }

    @JRubyMethod(name = "equal?", required = 1)
    public IRubyObject same(IRubyObject other) {
        if (!(other instanceof JavaObject)) {
            other = other.getInstanceVariables().fastGetInstanceVariable("@java_object");
            if (!(other instanceof JavaObject)) {
                return getRuntime().getFalse();
            }
        }

        if (getValue() == null && ((JavaObject) other).getValue() == null) {
            return getRuntime().getTrue();
        }

        boolean isSame = getValue() == ((JavaObject) other).getValue();
        return isSame ? getRuntime().getTrue() : getRuntime().getFalse();
    }

    @JRubyMethod
    public RubyString java_type() {
        return getRuntime().newString(getJavaClass().getName());
    }

    @JRubyMethod
    public IRubyObject java_class() {
        return JavaClass.get(getRuntime(), getJavaClass());
    }

    @JRubyMethod
    public RubyFixnum length() {
        throw getRuntime().newTypeError("not a java array");
    }

    @JRubyMethod(name = "[]", required = 1)
    public IRubyObject aref(IRubyObject index) {
        throw getRuntime().newTypeError("not a java array");
    }

    @JRubyMethod(name = "[]=", required = 2)
    public IRubyObject aset(IRubyObject index, IRubyObject someValue) {
        throw getRuntime().newTypeError("not a java array");
    }

    @JRubyMethod(name = "fill", required = 3)
    public IRubyObject afill(IRubyObject beginIndex, IRubyObject endIndex, IRubyObject someValue) {
        throw getRuntime().newTypeError("not a java array");
    }

    @JRubyMethod(name = "java_proxy?")
    public IRubyObject is_java_proxy() {
        return getRuntime().getTrue();
    }

    @JRubyMethod(name = "synchronized")
    public IRubyObject ruby_synchronized(ThreadContext context, Block block) {
        Object lock = getValue();
        synchronized (lock != null ? lock : NULL_LOCK) {
            return block.yield(context, null);
        }
    }
}
