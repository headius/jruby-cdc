/*
 * RubyGlobal.java - No description
 * Created on 12.01.2002, 17:33:23
 * 
 * Copyright (C) 2001, 2002 Jan Arne Petersen, Alan Moore, Benoit Cerrina, Chad Fowler
 * Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Alan Moore <alan_moore@gmx.net>
 * Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Chad Fowler <chadfowler@yahoo.com>
 * 
 * JRuby - http://jruby.sourceforge.net
 * 
 * This file is part of JRuby
 * 
 * JRuby is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * JRuby is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with JRuby; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 */
package org.jruby;

import java.io.*;
import java.util.*;

import org.jruby.exceptions.*;
import org.jruby.runtime.*;

/** This class initializes global variables and constants.
 * 
 * @author jpetersen
 * @version $Revision$
 * @fixme autoload method needs to be implemented
 */
public class RubyGlobal {
    public static void createGlobals(Ruby ruby) {

        // Version information:
        RubyObject version = RubyString.newString(ruby, Ruby.RUBY_VERSION).freeze();
        RubyObject release = RubyString.newString(ruby, "$Date$").freeze(); // XXX
        RubyObject platform = RubyString.newString(ruby, "java").freeze();

        ruby.defineGlobalConstant("RUBY_VERSION", version);
        ruby.defineGlobalConstant("RUBY_RELEASE_DATE", release);
        ruby.defineGlobalConstant("RUBY_PLATFORM", platform);

        ruby.defineGlobalConstant("VERSION", version);
        ruby.defineGlobalConstant("RELEASE_DATE", release);
        ruby.defineGlobalConstant("PLATFORM", platform);

        ruby.defineVariable(new StringGlobalVariable(ruby, "$/", RubyString.newString(ruby, "\n")));
        ruby.defineVariable(new StringGlobalVariable(ruby, "$\\", ruby.getNil()));
        ruby.defineVariable(new StringGlobalVariable(ruby, "$,", ruby.getNil()));

        ruby.defineVariable(new LineNumberGlobalVariable(ruby, "$.", RubyFixnum.one(ruby)));
        ruby.defineVariable(new LastlineGlobalVariable(ruby, "$_"));

        ruby.defineVariable(new ErrorInfoGlobalVariable(ruby, "$!", ruby.getNil()));

        ruby.defineVariable(new SafeGlobalVariable(ruby, "$SAFE"));

        ruby.defineVariable(new BacktraceGlobalVariable(ruby, "$@"));

        RubyObject stdin = RubyIO.stdin(ruby, ruby.getClasses().getIoClass(), System.in);
        RubyObject stdout = RubyIO.stdout(ruby, ruby.getClasses().getIoClass(), System.out);
        RubyObject stderr = RubyIO.stderr(ruby, ruby.getClasses().getIoClass(), System.err);

        ruby.defineVariable(new InputGlobalVariable(ruby, "$stdin", stdin));

        ruby.defineVariable(new OutputGlobalVariable(ruby, "$stdout", stdout));
        ruby.defineVariable(new OutputGlobalVariable(ruby, "$stderr", stderr));
        ruby.defineVariable(new OutputGlobalVariable(ruby, "$>", stdout));
        ruby.defineVariable(new OutputGlobalVariable(ruby, "$defout", stdout));

        ruby.defineGlobalConstant("STDIN", stdin);
        ruby.defineGlobalConstant("STDOUT", stdout);
        ruby.defineGlobalConstant("STDERR", stderr);

        ruby.defineReadonlyVariable("$\"", RubyArray.newArray(ruby));
        ruby.defineReadonlyVariable("$*", RubyArray.newArray(ruby));

        RubyArray loadPath = RubyArray.newArray(ruby);
        ruby.defineReadonlyVariable("$:", loadPath);
        ruby.defineReadonlyVariable("$-I", loadPath);
        ruby.defineReadonlyVariable("$LOAD_PATH", loadPath);

        // ARGF, $< object
        RubyArgsFile argsFile = new RubyArgsFile(ruby);
        argsFile.initArgsFile();
    }

    // Accessor methods.

    private static class LineNumberGlobalVariable extends GlobalVariable {
        public LineNumberGlobalVariable(Ruby ruby, String name, RubyFixnum value) {
            super(ruby, name, value);
        }
        public RubyObject set(RubyObject value) {
            ((RubyArgsFile) ruby.getGlobalVar("$<")).setCurrentLineNumber(RubyFixnum.fix2int(value));
            return super.set(value);
        }
    }

    private static class ErrorInfoGlobalVariable extends GlobalVariable {
        public ErrorInfoGlobalVariable(Ruby ruby, String name, RubyObject value) {
            super(ruby, name, value);
        }
        public RubyObject set(RubyObject value) {
            if (!value.isNil() && value.kind_of(ruby.getClasses().getExceptionClass()).isFalse()) {
                throw new TypeError(ruby, "assigning non-exception to $!");
            }
            return super.set(value);
        }
    }

    // FIXME: move out of this class!
    public static class StringGlobalVariable extends GlobalVariable {
        public StringGlobalVariable(Ruby ruby, String name, RubyObject value) {
            super(ruby, name, value);
        }
        public RubyObject set(RubyObject value) {
            if (! (value instanceof RubyString)) {
                throw new TypeError(ruby, "value of " + name() + " must be a String");
            }
            return super.set(value);
        }
    }

    private static class SafeGlobalVariable extends GlobalVariable {
        public SafeGlobalVariable(Ruby ruby, String name) {
            super(ruby, name, null);
        }
        public RubyObject get() {
            return RubyFixnum.newFixnum(ruby, ruby.getSafeLevel());
        }
        public RubyObject set(RubyObject value) {
            int level = RubyFixnum.fix2int(value);
            if (level < ruby.getSafeLevel()) {
                throw new SecurityException("tried to downgrade level from " + ruby.getSafeLevel() + " to " + level);
            }
            ruby.setSafeLevel(level);
            // thread.setSafeLevel(level);
            return value;
        }
    }

    private static class BacktraceGlobalVariable extends GlobalVariable {
        public BacktraceGlobalVariable(Ruby ruby, String name) {
            super(ruby, name, null);
        }
        public RubyObject get() {
            RubyObject errorInfo = ruby.getGlobalVar("$!");
            return errorInfo.isNil() ? ruby.getNil() : errorInfo.funcall("backtrace");
        }

        public RubyObject set(RubyObject value) {
            if (ruby.getGlobalVar("$!").isNil()) {
                throw new ArgumentError(ruby, "$! not set.");
            }
            ruby.getGlobalVar("$!").funcall("set_backtrace", value);
            return value;
        }
    }

    private static class LastlineGlobalVariable extends GlobalVariable {
        public LastlineGlobalVariable(Ruby ruby, String name) {
            super(ruby, name, null);
        }
        public RubyObject get() {
            return ruby.getLastline();
        }
        public RubyObject set(RubyObject value) {
            ruby.setLastline(value);
            return value;
        }
    }

    private static class InputGlobalVariable extends GlobalVariable {
        public InputGlobalVariable(Ruby ruby, String name, RubyObject value) {
            super(ruby, name, value);
        }
        public RubyObject set(RubyObject value) {
            if (value == get()) {
                return value;
            }
            if (value instanceof RubyIO) {
                ((RubyIO) value).checkReadable();
            }
            return super.set(value);
        }
    }

    private static class OutputGlobalVariable extends GlobalVariable {
        public OutputGlobalVariable(Ruby ruby, String name, RubyObject value) {
            super(ruby, name, value);
        }
        public RubyObject set(RubyObject value) {
            if (value == get()) {
                return value;
            }
            if (value instanceof RubyIO) {
                ((RubyIO) value).checkWriteable();
            }
            if (! value.respondsTo("write")) {
                throw new TypeError(ruby, name() + " must have write method, " +
                                    value.type().toName() + " given");
            }
            return super.set(value);
        }
    }
}
