/*
 * Copyright (C) 2002 Anders Bengtsson <ndrsbngtssn@yahoo.se>
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

package org.jruby.runtime.marshal;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.jruby.Ruby;
import org.jruby.RubyFixnum;
import org.jruby.RubyInteger;
import org.jruby.RubyString;
import org.jruby.RubySymbol;
import org.jruby.exceptions.ArgumentError;
import org.jruby.runtime.Constants;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * Marshals objects into Ruby's binary marshal format.
 *
 * @author Anders
 * $Revision$
 */
public class MarshalStream extends FilterOutputStream {
    private final Ruby ruby;
    private final int depthLimit;
    private int depth = 0;
    private Map dumpedObjects = new HashMap();
    private Map dumpedSymbols = new HashMap();

    public MarshalStream(Ruby ruby, OutputStream out, int depthLimit) throws IOException {
        super(out);

        this.ruby = ruby;
        this.depthLimit = (depthLimit >= 0 ? depthLimit : Integer.MAX_VALUE);

        out.write(Constants.MARSHAL_MAJOR);
        out.write(Constants.MARSHAL_MINOR);
    }

    public void dumpObject(IRubyObject value) throws IOException {
        depth++;
        if (depth > depthLimit) {
            throw new ArgumentError(ruby, "exceed depth limit");
        }
        if (value.isNil()) {
            out.write('0');
        } else if (hasUserDefinedMarshaling(value)) {
            userMarshal(value);
        } else {
            writeAndRegister(value);
        }
        depth--;
    }

    private void writeAndRegister(IRubyObject value) throws IOException {
        writeAndRegister(dumpedObjects, '@', value);
    }
    private void writeAndRegister(RubySymbol value) throws IOException {
        writeAndRegister(dumpedSymbols, ';', value);
    }

    private void writeAndRegister(Map registry, char linkSymbol, IRubyObject value) throws IOException {
        if (registry.containsKey(value)) {
            out.write(linkSymbol);
            dumpInt(((Integer) registry.get(value)).intValue());
        } else {
            registry.put(value, new Integer(registry.size()));
            value.marshalTo(this);
        }
    }

    private boolean hasUserDefinedMarshaling(IRubyObject value) {
        return value.respondsTo("_dump");
    }

    private void userMarshal(IRubyObject value) throws IOException {
        out.write('u');
        dumpObject(RubySymbol.newSymbol(ruby, value.getInternalClass().getClassname()));

        RubyInteger depth = RubyFixnum.newFixnum(ruby, depthLimit);
        RubyString marshaled = (RubyString) value.callMethod("_dump", depth);
        dumpString(marshaled.getValue());
    }

    public void dumpString(String value) throws IOException {
        dumpInt(value.length());
        out.write(RubyString.stringToBytes(value));
    }

    public void dumpInt(int value) throws IOException {
        if (value == 0) {
            out.write(0);
        } else if (0 < value && value < 123) {
            out.write(value + 5);
        } else if (-124 < value && value < 0) {
            out.write((value - 5) & 0xff);
        } else {
            int[] buf = new int[4];
            int i;
            for (i = 0; i < buf.length; i++) {
                buf[i] = value & 0xff;
                value = value >> 8;
                if (value == 0 || value == -1) {
                    break;
                }
            }
            int len = i + 1;
            out.write(value < 0 ? -len : len);
            for (i = 0; i < len; i++) {
                out.write(buf[i]);
            }
        }
    }
}