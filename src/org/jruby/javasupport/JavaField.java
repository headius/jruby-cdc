/*
 * Copyright (C) 2002 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 *
 * JRuby - http://jruby.sourceforge.net
 *
 * This file is part of JRuby
 *
 * JRuby is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * JRuby is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with JRuby; if not, write to
 * the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA  02111-1307 USA
 */
package org.jruby.javasupport;

import org.jruby.RubyObject;
import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.RubyString;
import org.jruby.RubyBoolean;
import org.jruby.runtime.IndexedCallback;
import org.jruby.runtime.IndexCallable;
import org.jruby.runtime.builtin.IRubyObject;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class JavaField extends RubyObject implements IndexCallable {
    private final Field field;

    private static final int VALUE_TYPE = 1;
    private static final int PUBLIC_P = 2;
    private static final int STATIC_P = 3;

    public static RubyClass createJavaFieldClass(Ruby runtime, RubyModule javaModule) {
        RubyClass javaFieldClass =
                javaModule.defineClassUnder("JavaField", runtime.getClasses().getObjectClass());

        javaFieldClass.defineMethod("value_type", IndexedCallback.create(VALUE_TYPE, 0));
        javaFieldClass.defineMethod("public?", IndexedCallback.create(PUBLIC_P, 0));
        javaFieldClass.defineMethod("static?", IndexedCallback.create(STATIC_P, 0));

        return javaFieldClass;
    }

    public JavaField(Ruby runtime, Field field) {
        super(runtime, (RubyClass) runtime.getClasses().getClassFromPath("Java::JavaField"));
        this.field = field;
    }

    public RubyString value_type() {
        return RubyString.newString(getRuntime(), field.getType().getName());
    }

    public RubyBoolean public_p() {
        return RubyBoolean.newBoolean(getRuntime(), Modifier.isPublic(field.getModifiers()));
    }

    public RubyBoolean static_p() {
        return RubyBoolean.newBoolean(getRuntime(), Modifier.isStatic(field.getModifiers()));
    }

    public IRubyObject callIndexed(int index, IRubyObject[] args) {
        switch (index) {
            case VALUE_TYPE :
                return value_type();
            case PUBLIC_P :
                return public_p();
            case STATIC_P :
                return static_p();
            default :
                return super.callIndexed(index, args);
        }
    }
}
