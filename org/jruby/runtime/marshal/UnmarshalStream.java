/*
 * Copyright (C) 2002 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2002 Jan Arne Petersen <jpetersen@uni-bonn.de>
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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyBignum;
import org.jruby.RubyBoolean;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyHash;
import org.jruby.RubyModule;
import org.jruby.RubyObject;
import org.jruby.RubyString;
import org.jruby.RubyStruct;
import org.jruby.RubySymbol;
import org.jruby.util.Asserts;
import org.jruby.util.RubyHashMap;
import org.jruby.util.RubyMap;

/**
 * Unmarshals objects from strings or streams in Ruby's marsal format.
 *
 * @author Anders
 * $Revision$
 */
public class UnmarshalStream extends FilterInputStream {
    protected final Ruby ruby;

    public UnmarshalStream(Ruby ruby, InputStream in) throws IOException {
        super(in);

        this.ruby = ruby;
        in.read(); // Major
        in.read(); // Minor
    }

    public RubyObject unmarshalObject() throws IOException {
        int type = readUnsignedByte();
        switch (type) {
            case '0' :
                return RubyObject.nilObject(ruby);
            case 'T' :
                return RubyBoolean.newBoolean(ruby, true);
            case 'F' :
                return RubyBoolean.newBoolean(ruby, false);
            case '"' :
                return RubyString.unmarshalFrom(this);
            case 'i' :
                return RubyFixnum.unmarshalFrom(this);
            case ':' :
                return RubySymbol.unmarshalFrom(this);
            case '[' :
                return RubyArray.unmarshalFrom(this);
            case '{' :
                return RubyHash.unmarshalFrom(this);
            case 'c' :
                return RubyClass.unmarshalFrom(this);
            case 'm' :
                return RubyModule.unmarshalFrom(this);
            case 'l' :
                return RubyBignum.unmarshalFrom(this);
            case 'S' :
                return RubyStruct.unmarshalFrom(this);
            case 'o' :
                return defaultObjectUnmarshal();
            case 'u' :
                return userUnmarshal();
            default :
                Asserts.assertNotReached();
                return null;
        }
    }

    public Ruby getRuby() {
        return ruby;
    }

    public int readUnsignedByte() throws IOException {
        int result = read();
        if (result == -1) {
            throw new IOException("Unexpected end of stream");
        }
        return result;
    }

    public byte readSignedByte() throws IOException {
        int b = readUnsignedByte();
        if (b > 127) {
            return (byte) (b - 256);
        } else {
            return (byte) b;
        }
    }

    public String unmarshalString() throws IOException {
        int length = unmarshalInt();
        byte[] buffer = new byte[length];
        int bytesRead = read(buffer);
        if (bytesRead != length) {
            throw new IOException("Unexpected end of stream");
        }
        return RubyString.bytesToString(buffer);
    }

    public int unmarshalInt() throws IOException {
        int c = readSignedByte();
        if (c == 0) {
            return 0;
        } else if (4 < c && c < 128) {
            return c - 5;
        } else if (-129 < c && c < -4) {
            return c + 5;
        }
        long result;
        if (c > 0) {
            result = 0;
            for (int i = 0; i < c; i++) {
                result |= (long) readUnsignedByte() << (8 * i);
            }
        } else {
            c = -c;
            result = -1;
            for (int i = 0; i < c; i++) {
                result &= ~((long) 0xff << (8 * i));
                result |= (long) readUnsignedByte() << (8 * i);
            }
        }
        return (int) result;
    }

    private RubyObject defaultObjectUnmarshal() throws IOException {
        RubySymbol className = (RubySymbol) unmarshalObject();
        int variableCount = unmarshalInt();

        RubyMap variables = new RubyHashMap(variableCount);
        for (int i = 0; i < variableCount; i++) {
            RubySymbol name = (RubySymbol) unmarshalObject();
            RubyObject value = unmarshalObject();
            variables.put(name.toId(), value);
        }

        // ... FIXME: handle if class doesn't exist ...

        RubyClass rubyClass = (RubyClass) ruby.getRubyClass(className.toId());
        RubyObject result = new RubyObject(ruby, rubyClass);
        result.setInstanceVariables(variables);
        return result;
    }

    private RubyObject userUnmarshal() throws IOException {
        String className = ((RubySymbol) unmarshalObject()).toId();
        String marshaled = unmarshalString();
        RubyModule classInstance = ruby.getRubyModule(className);
        return classInstance.callMethod(
            "_load",
            RubyString.newString(ruby, marshaled));
    }
}
