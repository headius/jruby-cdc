/*
 * Copyright (C) 2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 *
 * JRuby - http://jruby.sourceforge.net
 *
 * This file is part of JRuby
 *
 * JRuby is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License or
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * JRuby is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License and GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public
 * License and GNU Lesser General Public License along with JRuby;
 * if not, write to
 * the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA  02111-1307 USA
 */
package org.jruby.runtime.builtin.meta;

import org.jruby.BuiltinClass;
import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.RubyString;
import org.jruby.runtime.Arity;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.builtin.IRubyObject;

public class StringMetaClass extends BuiltinClass {
    public StringMetaClass(Ruby runtime) {
        super("String", RubyString.class, runtime.getClasses().getObjectClass());
    }

    private StringMetaClass(String name, RubyClass superClass, RubyModule parentModule) {
        super(name, RubyString.class, superClass, parentModule);
    }

    /* FIXME Not needed because of the new allocObject method.
     * This is an example for the definition of singleton methods.
     *

	public RubyString rb_new(IRubyObject[] args) {
		RubyString newString = getRuntime().newString("");
		newString.setMetaClass(this);
		newString.callInit(args);
		return newString;
	}

	 */
	
    protected void initializeClass() {
        includeModule(getRuntime().getClasses().getComparableModule());
        includeModule(getRuntime().getClasses().getEnumerableModule());

        /* FIXME Not needed because of the new allocObject method.
         * This is an example for the definition of singleton methods.

        defineSingletonMethod("new", Arity.optional(), "rb_new");

         */

        // FIXME Arity.optional() should be Arity.range(0, 1)
        defineMethod("initialize", Arity.optional(), "initialize");
        defineMethod("initialize_copy", Arity.fixed(1), "replace");

        // FIXME replace with new definition code like above
        CallbackFactory callbackFactory = getRuntime().callbackFactory(RubyString.class);

        defineMethod("clone", callbackFactory.getMethod("rbClone"));
        defineMethod("dup", callbackFactory.getMethod("dup"));

        defineMethod("<=>", callbackFactory.getMethod("op_cmp", IRubyObject.class));
        defineMethod("casecmp", callbackFactory.getMethod("casecmp", IRubyObject.class));
        defineMethod("==", callbackFactory.getMethod("equal", IRubyObject.class));
        defineMethod("===", callbackFactory.getMethod("veryEqual", IRubyObject.class));
        defineMethod("eql?", callbackFactory.getMethod("equal", IRubyObject.class));
        defineMethod("hash", callbackFactory.getMethod("hash"));

        defineMethod("+", callbackFactory.getMethod("op_plus", IRubyObject.class));
        defineMethod("*", callbackFactory.getMethod("op_mul", IRubyObject.class));
        defineMethod("%", callbackFactory.getMethod("format", IRubyObject.class));
        defineMethod("[]", callbackFactory.getOptMethod("aref"));
        defineMethod("[]=", callbackFactory.getOptMethod("aset"));
        defineMethod("length", callbackFactory.getMethod("length"));
        defineMethod("size", callbackFactory.getMethod("length"));
        defineMethod("empty?", callbackFactory.getMethod("empty"));
        defineMethod("=~", callbackFactory.getMethod("match", IRubyObject.class));
        defineMethod("~", callbackFactory.getMethod("match2"));
        defineMethod("match", callbackFactory.getMethod("match3", IRubyObject.class));
        defineMethod("succ", callbackFactory.getMethod("succ"));
        defineMethod("succ!", callbackFactory.getMethod("succ_bang"));
        defineMethod("next", callbackFactory.getMethod("succ"));
        defineMethod("next!", callbackFactory.getMethod("succ_bang"));
        defineMethod("upto", callbackFactory.getMethod("upto", IRubyObject.class));
        defineMethod("index", callbackFactory.getOptMethod("index"));
        defineMethod("rindex", callbackFactory.getOptMethod("rindex"));
        defineMethod("replace", callbackFactory.getMethod("replace", IRubyObject.class));

        defineMethod("to_i", callbackFactory.getMethod("to_i"));
        defineMethod("to_f", callbackFactory.getMethod("to_f"));

        defineMethod("to_s", callbackFactory.getSelfMethod(0));
        defineMethod("to_str", callbackFactory.getSelfMethod(0));
        defineMethod("to_sym", callbackFactory.getMethod("to_sym"));
        defineAlias("intern", "to_sym");
        defineMethod("inspect", callbackFactory.getMethod("inspect"));
        defineMethod("dump", callbackFactory.getMethod("dump"));

        defineMethod("upcase", callbackFactory.getMethod("upcase"));
        defineMethod("downcase", callbackFactory.getMethod("downcase"));
        defineMethod("capitalize", callbackFactory.getMethod("capitalize"));
        defineMethod("swapcase", callbackFactory.getMethod("swapcase"));

        defineMethod("upcase!", callbackFactory.getMethod("upcase_bang"));
        defineMethod("downcase!", callbackFactory.getMethod("downcase_bang"));
        defineMethod("capitalize!", callbackFactory.getMethod("capitalize_bang"));
        defineMethod("swapcase!", callbackFactory.getMethod("swapcase_bang"));

        defineMethod("hex", callbackFactory.getMethod("hex"));
        defineMethod("oct", callbackFactory.getMethod("oct"));
        defineMethod("split", callbackFactory.getOptMethod("split"));
        defineMethod("reverse", callbackFactory.getMethod("reverse"));
        defineMethod("reverse!", callbackFactory.getMethod("reverse_bang"));
        defineMethod("concat", callbackFactory.getMethod("concat", IRubyObject.class));
        defineMethod("<<", callbackFactory.getMethod("concat", IRubyObject.class));

        defineMethod("include?", callbackFactory.getMethod("include", IRubyObject.class));

        defineMethod("scan", callbackFactory.getMethod("scan", IRubyObject.class));

        defineMethod("ljust", callbackFactory.getMethod("ljust", IRubyObject.class));
        defineMethod("rjust", callbackFactory.getMethod("rjust", IRubyObject.class));
        defineMethod("center", callbackFactory.getMethod("center", IRubyObject.class));

        defineMethod("sub", callbackFactory.getOptMethod("sub"));
        defineMethod("gsub", callbackFactory.getOptMethod("gsub"));
        defineMethod("chop", callbackFactory.getMethod("chop"));
        defineMethod("chomp", callbackFactory.getOptMethod("chomp"));
        defineMethod("strip", callbackFactory.getMethod("strip"));
        defineMethod("lstrip", callbackFactory.getMethod("lstrip"));
        defineMethod("rstrip", callbackFactory.getMethod("rstrip"));

        defineMethod("sub!", callbackFactory.getOptMethod("sub_bang"));
        defineMethod("gsub!", callbackFactory.getOptMethod("gsub_bang"));
        defineMethod("chop!", callbackFactory.getMethod("chop_bang"));
        defineMethod("chomp!", callbackFactory.getOptMethod("chomp_bang"));
        defineMethod("strip!", callbackFactory.getMethod("strip_bang"));
        defineMethod("lstrip!", callbackFactory.getMethod("lstrip_bang"));
        defineMethod("rstrip!", callbackFactory.getMethod("rstrip_bang"));

        defineMethod("tr", callbackFactory.getMethod("tr", RubyString.class, RubyString.class));
        defineMethod("tr_s", callbackFactory.getMethod("tr_s", RubyString.class, RubyString.class));
        defineMethod("delete", callbackFactory.getOptMethod("delete"));
        defineMethod("squeeze", callbackFactory.getOptMethod("squeeze"));
        defineMethod("count", callbackFactory.getOptMethod("count"));

        defineMethod("tr!", callbackFactory.getMethod("tr_bang", RubyString.class, RubyString.class));
        defineMethod("tr_s!", callbackFactory.getMethod("tr_s_bang", RubyString.class, RubyString.class));
        defineMethod("delete!", callbackFactory.getOptMethod("delete_bang"));
        defineMethod("squeeze!", callbackFactory.getOptMethod("squeeze_bang"));

        defineMethod("each_line", callbackFactory.getOptMethod("each_line"));
        defineMethod("each", callbackFactory.getOptMethod("each_line"));
        defineMethod("each_byte", callbackFactory.getMethod("each_byte"));
        defineMethod("sum", callbackFactory.getOptMethod("sum"));

        defineMethod("slice", callbackFactory.getOptMethod("aref"));
        defineMethod("slice!", callbackFactory.getOptMethod("slice_bang"));

        defineMethod("unpack", callbackFactory.getMethod("unpack", IRubyObject.class));
    }

    public RubyClass newSubClass(String name, RubyModule parentModule) {
        return new StringMetaClass(name, this, parentModule);
    }

    protected IRubyObject allocateObject() {
        RubyString newString = getRuntime().newString("");
		newString.setMetaClass(this);
		return newString;
    }
}