/*
 * RubySymbol.java - No description
 * Created on 26. Juli 2001, 00:01
 * 
 * Copyright (C) 2001 Jan Arne Petersen, Stefan Matthias Aust, Alan Moore, Benoit Cerrina
 * Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Stefan Matthias Aust <sma@3plus4.de>
 * Alan Moore <alan_moore@gmx.net>
 * Benoit Cerrina <b.cerrina@wanadoo.fr>
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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.marshal.MarshalStream;
import org.jruby.runtime.marshal.UnmarshalStream;

/**
 *
 * @author  jpetersen
 */
public class RubySymbol extends RubyObject {
    private static int lastId = 0;

    private final String symbol;
    private final int id;

    private RubySymbol(Ruby runtime, String symbol) {
        super(runtime, runtime.getClass("Symbol"));
        this.symbol = symbol;

        lastId++;
        this.id = lastId;
    }

    /** rb_to_id
     * 
     * @return a String representation of the symbol 
     */
    public String asSymbol() {
        return symbol;
    }

    public static RubyClass createSymbolClass(Ruby runtime) {
		RubyClass symbolClass = runtime.defineClass("Symbol", runtime.getClasses().getObjectClass());
    	
		CallbackFactory callbackFactory = runtime.callbackFactory(RubySymbol.class);
        
		symbolClass.defineMethod("to_i",
			callbackFactory.getMethod("to_i"));
		symbolClass.defineMethod("to_int", 
			callbackFactory.getMethod("to_i"));
		symbolClass.defineMethod("to_s", 
			callbackFactory.getMethod("to_s"));
		symbolClass.defineMethod("id2name", 
			callbackFactory.getMethod("to_s"));
		symbolClass.defineMethod("==", 
			callbackFactory.getMethod("equal", IRubyObject.class));
		symbolClass.defineMethod("hash", 
			callbackFactory.getMethod("hash"));
		symbolClass.defineMethod("inspect", 
			callbackFactory.getMethod("inspect"));
		symbolClass.defineMethod("clone", 
			callbackFactory.getMethod("rbClone"));
		symbolClass.defineMethod("dup", 
			callbackFactory.getMethod("rbClone"));
		symbolClass.defineMethod("freeze", 
			callbackFactory.getMethod("freeze"));
		symbolClass.defineMethod("taint", 
			callbackFactory.getMethod("taint"));
		
		symbolClass.getMetaClass().undefineMethod("new");
		
		return symbolClass;
    }

    public boolean isImmediate() {
    	return true;
    }
    
    public boolean singletonMethodsAllowed() {
        return false;
    }

    public static String getSymbol(Ruby runtime, long id) {
        RubySymbol result = runtime.symbolTable.lookup(id);
        if (result != null) {
            return result.symbol;
        }
        return null;
    }

    /* Symbol class methods.
     * 
     */

    public static RubySymbol newSymbol(Ruby runtime, String name) {
        RubySymbol result;
        synchronized (RubySymbol.class) {
            // Locked to prevent the creation of multiple instances of
            // the same symbol. Most code depends on them being unique.

            result = runtime.symbolTable.lookup(name);
            if (result == null) {
                result = new RubySymbol(runtime, name);
                runtime.symbolTable.store(result);
            }
        }
        return result;
    }

    public RubyFixnum to_i() {
        return getRuntime().newFixnum(id);
    }

    public RubyString inspect() {
        return getRuntime().newString(":" + symbol);
    }

    public RubyString to_s() {
        return getRuntime().newString(symbol);
    }

    public IRubyObject equal(IRubyObject other) {
        // Symbol table ensures only one instance for every name,
        // so object identity is enough to compare symbols.
        return getRuntime().newBoolean(this == other);
    }

    public RubyFixnum hash() {
        return getRuntime().newFixnum(symbol.hashCode());
    }

    public IRubyObject rbClone() {
        throw getRuntime().newTypeError("can't clone Symbol");
    }

    public IRubyObject freeze() {
        return this;
    }

    public IRubyObject taint() {
        return this;
    }

    public void marshalTo(MarshalStream output) throws java.io.IOException {
        output.write(':');
        output.dumpString(symbol);
    }

    public static RubySymbol unmarshalFrom(UnmarshalStream input) throws java.io.IOException {
        RubySymbol result = RubySymbol.newSymbol(input.getRuntime(), input.unmarshalString());
        input.registerLinkTarget(result);
        return result;
    }

    public static class SymbolTable {
        /* Using Java's GC to keep the table free from unused symbols. */
        private ReferenceQueue unusedSymbols = new ReferenceQueue();
        private Map table = new HashMap();

        public RubySymbol lookup(String name) {
            clean();
            WeakSymbolEntry ref = (WeakSymbolEntry) table.get(name);
            if (ref == null) {
                return null;
            }
            return (RubySymbol) ref.get();
        }

        public RubySymbol lookup(long symbolId) {
            Iterator iter = table.values().iterator();
            while (iter.hasNext()) {
                WeakSymbolEntry entry = (WeakSymbolEntry) iter.next();
                RubySymbol symbol = (RubySymbol) entry.get();
                if (symbol != null) {
                    if (symbol.id == symbolId) {
                        return symbol;
                    }
                }
            }
            return null;
        }

        public void store(RubySymbol symbol) {
            clean();
            table.put(symbol.asSymbol(), new WeakSymbolEntry(symbol, unusedSymbols));
        }

        private void clean() {
            WeakSymbolEntry ref;
            while ((ref = (WeakSymbolEntry) unusedSymbols.poll()) != null) {
                table.remove(ref.name());
            }
        }

        private class WeakSymbolEntry extends WeakReference {
            private final String name;

            public WeakSymbolEntry(RubySymbol symbol, ReferenceQueue queue) {
                super(symbol, queue);
                this.name = symbol.asSymbol();
            }

            public String name() {
                return name;
            }
        }
    }
}
