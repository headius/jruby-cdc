/*
 ***** BEGIN LICENSE BLOCK *****
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
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Joey Gibson <joey@joeygibson.com>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2006 Derek Berner <derek.berner@state.nm.us>
 * Copyright (C) 2006 Miguel Covarrubias <mlcovarrubias@gmail.com>
 * Copyright (C) 2007 William N Dortch <bill.dortch@gmail.com>
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
package org.jruby;

import java.util.concurrent.locks.ReentrantLock;

import org.jruby.anno.JRubyMethod;
import org.jruby.anno.JRubyClass;
import org.jruby.common.IRubyWarnings.ID;
import org.jruby.javasupport.util.RuntimeHelpers;
import org.jruby.runtime.ClassIndex;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Block;
import org.jruby.runtime.BlockCallback;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.marshal.UnmarshalStream;
import org.jruby.util.ByteList;

/**
 * Represents a Ruby symbol (e.g. :bar)
 */
@JRubyClass(name="Symbol")
public class RubySymbol extends RubyObject {
    private final String symbol;
    private final int id;
    private final ByteList symbolBytes;
    
    /**
     * 
     * @param runtime
     * @param internedSymbol the String value of the new Symbol. This <em>must</em>
     *                       have been previously interned
     */
    private RubySymbol(Ruby runtime, String internedSymbol) {
        super(runtime, runtime.getSymbol(), false);
        // symbol string *must* be interned

        assert internedSymbol == internedSymbol.intern() : internedSymbol + " is not interned";

        this.symbol = internedSymbol;
        this.symbolBytes = ByteList.create(symbol);

        this.id = runtime.allocSymbolId();
    }
    
    public static RubyClass createSymbolClass(Ruby runtime) {
        RubyClass symbolClass = runtime.defineClass("Symbol", runtime.getObject(), ObjectAllocator.NOT_ALLOCATABLE_ALLOCATOR);
        runtime.setSymbol(symbolClass);
        RubyClass symbolMetaClass = symbolClass.getMetaClass();
        symbolClass.index = ClassIndex.SYMBOL;
        symbolClass.kindOf = new RubyModule.KindOf() {
            public boolean isKindOf(IRubyObject obj, RubyModule type) {
                return obj instanceof RubySymbol;
            }
        };

        symbolClass.defineAnnotatedMethods(RubySymbol.class);
        symbolMetaClass.undefineMethod("new");
        
        return symbolClass;
    }
    
    @Override
    public int getNativeTypeIndex() {
        return ClassIndex.SYMBOL;
    }

    /** rb_to_id
     * 
     * @return a String representation of the symbol 
     */
    @Override
    public String asJavaString() {
        return symbol;
    }
    
    /** short circuit for Symbol key comparison
     * 
     */
    @Override
    public final boolean eql(IRubyObject other) {
        return other == this;
    }

    @Override
    public boolean isImmediate() {
    	return true;
    }

    @Override
    public RubyClass getSingletonClass() {
        throw getRuntime().newTypeError("can't define singleton");
    }

    public static RubySymbol getSymbolLong(Ruby runtime, long id) {
        return runtime.getSymbolTable().lookup(id);
    }

    /* Symbol class methods.
     * 
     */

    public static RubySymbol newSymbol(Ruby runtime, String name) {
        return runtime.getSymbolTable().getSymbol(name);
    }

    @JRubyMethod(name = "to_i")
    public RubyFixnum to_i() {
        return getRuntime().newFixnum(id);
    }

    @JRubyMethod(name = "to_int")
    public RubyFixnum to_int() {
        if (getRuntime().getVerbose().isTrue()) {
            getRuntime().getWarnings().warn(ID.SYMBOL_AS_INTEGER, "treating Symbol as an integer");
	}
        return to_i();
    }

    @JRubyMethod(name = "inspect")
    @Override
    public IRubyObject inspect() {
        Ruby runtime = getRuntime();
        return runtime.newString(":" + 
            (isSymbolName(symbol) ? symbol : RubyString.newStringShared(runtime, symbolBytes).dump().toString())); 
    }

    @JRubyMethod(name = "to_s")
    @Override
    public IRubyObject to_s() {
        return RubyString.newStringShared(getRuntime(), symbolBytes);
    }

    @JRubyMethod(name = "id2name")
    public IRubyObject id2name() {
        return to_s();
    }

    @JRubyMethod(name = "===", required = 1)
    @Override
    public IRubyObject op_eqq(ThreadContext context, IRubyObject other) {
        return super.op_equal(context, other);
    }

    @Override
    public RubyFixnum hash() {
        return getRuntime().newFixnum(hashCode());
    }
    
    @Override
    public int hashCode() {
        return id;
    }

    public int getId() {
        return id;
    }
    
    @Override
    public boolean equals(Object other) {
        return other == this;
    }
    
    @JRubyMethod(name = "to_sym")
    public IRubyObject to_sym() {
        return this;
    }

    @Override
    public IRubyObject freeze(ThreadContext context) {
        return this;
    }

    @Override
    public IRubyObject taint(ThreadContext context) {
        return this;
    }

    private static class ToProcCallback implements BlockCallback {
        private RubySymbol symbol;
        public ToProcCallback(RubySymbol symbol) {
            this.symbol = symbol;
        }

        public IRubyObject call(ThreadContext ctx, IRubyObject[] args, Block blk) {
            IRubyObject[] currentArgs = args;
            switch(currentArgs.length) {
            case 0: throw symbol.getRuntime().newArgumentError("no receiver given");
            case 1: {
                if((currentArgs[0] instanceof RubyArray) && ((RubyArray)currentArgs[0]).getLength() != 0) {
                    // This is needed to unpack stuff
                    currentArgs = ((RubyArray)currentArgs[0]).toJavaArrayMaybeUnsafe();
                    IRubyObject[] args2 = new IRubyObject[currentArgs.length-1];
                    System.arraycopy(currentArgs, 1, args2, 0, args2.length);
                    return RuntimeHelpers.invoke(ctx, currentArgs[0], symbol.symbol, args2);
                } else {
                    return RuntimeHelpers.invoke(ctx, currentArgs[0], symbol.symbol);
                }
            }
            default: {
                IRubyObject[] args2 = new IRubyObject[currentArgs.length-1];
                System.arraycopy(currentArgs, 1, args2, 0, args2.length);
                return RuntimeHelpers.invoke(ctx, currentArgs[0], symbol.symbol, args2);
            }
            }
        }
    }
    /*
    @JRubyMethod
    public IRubyObject to_proc() {
        return RubyProc.newProc(getRuntime(),
                                CallBlock.newCallClosure(this, getRuntime().getSymbol(), Arity.noArguments(), new ToProcCallback(this), getRuntime().getCurrentContext()),
                                Block.Type.PROC);
    }
    */
    private static boolean isIdentStart(char c) {
        return ((c >= 'a' && c <= 'z')|| (c >= 'A' && c <= 'Z')
                || c == '_');
    }
    private static boolean isIdentChar(char c) {
        return ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z')
                || c == '_');
    }
    
    private static boolean isIdentifier(String s) {
        if (s == null || s.length() <= 0) {
            return false;
        } 
        
        if (!isIdentStart(s.charAt(0))) {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            if (!isIdentChar(s.charAt(i))) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * is_special_global_name from parse.c.  
     * @param s
     * @return
     */
    private static boolean isSpecialGlobalName(String s) {
        if (s == null || s.length() <= 0) {
            return false;
        }

        int length = s.length();
           
        switch (s.charAt(0)) {        
        case '~': case '*': case '$': case '?': case '!': case '@': case '/': case '\\':        
        case ';': case ',': case '.': case '=': case ':': case '<': case '>': case '\"':        
        case '&': case '`': case '\'': case '+': case '0':
            return length == 1;            
        case '-':
            return (length == 1 || (length == 2 && isIdentChar(s.charAt(1))));
            
        default:
            // we already confirmed above that length > 0
            for (int i = 0; i < length; i++) {
                if (!Character.isDigit(s.charAt(i))) {
                    return false;
                }
            }
        }
        return true;
    }
    
    private static boolean isSymbolName(String s) {
        if (s == null || s.length() < 1) {
            return false;
        }

        int length = s.length();

        char c = s.charAt(0);
        switch (c) {
        case '$':
            if (length > 1 && isSpecialGlobalName(s.substring(1))) {
                return true;
            }
            return isIdentifier(s.substring(1));
        case '@':
            int offset = 1;
            if (length >= 2 && s.charAt(1) == '@') {
                offset++;
            }

            return isIdentifier(s.substring(offset));
        case '<':
            return (length == 1 || (length == 2 && (s.equals("<<") || s.equals("<="))) || 
                    (length == 3 && s.equals("<=>")));
        case '>':
            return (length == 1) || (length == 2 && (s.equals(">>") || s.equals(">=")));
        case '=':
            return ((length == 2 && (s.equals("==") || s.equals("=~"))) || 
                    (length == 3 && s.equals("===")));
        case '*':
            return (length == 1 || (length == 2 && s.equals("**")));
        case '+':
            return (length == 1 || (length == 2 && s.equals("+@")));
        case '-':
            return (length == 1 || (length == 2 && s.equals("-@")));
        case '|': case '^': case '&': case '/': case '%': case '~': case '`':
            return length == 1;
        case '[':
            return s.equals("[]") || s.equals("[]=");
        }
        
        if (!isIdentStart(c)) {
            return false;
        }

        boolean localID = (c >= 'a' && c <= 'z');
        int last = 1;
        
        for (; last < length; last++) {
            char d = s.charAt(last);
            
            if (!isIdentChar(d)) {
                break;
            }
        }
                    
        if (last == length) {
            return true;
        } else if (localID && last == length - 1) {
            char d = s.charAt(last);
            
            return d == '!' || d == '?' || d == '=';
        }
        
        return false;
    }
    
    @JRubyMethod(name = "all_symbols", meta = true)
    public static IRubyObject all_symbols(IRubyObject recv) {
        return recv.getRuntime().getSymbolTable().all_symbols();
    }

    public static RubySymbol unmarshalFrom(UnmarshalStream input) throws java.io.IOException {
        RubySymbol result = newSymbol(input.getRuntime(), RubyString.byteListToString(input.unmarshalString()));
        input.registerLinkTarget(result);
        return result;
    }

    public static class SymbolTable {
        static final int DEFAULT_INITIAL_CAPACITY = 2048; // *must* be power of 2!
        static final int MAXIMUM_CAPACITY = 1 << 30;
        static final float DEFAULT_LOAD_FACTOR = 0.75f;
        
        private final ReentrantLock tableLock = new ReentrantLock();
        private volatile SymbolEntry[] symbolTable;
        private int size;
        private int threshold;
        private final float loadFactor;
        private final Ruby runtime;
        
        public SymbolTable(Ruby runtime) {
            this.runtime = runtime;
            this.loadFactor = DEFAULT_LOAD_FACTOR;
            this.threshold = (int)(DEFAULT_INITIAL_CAPACITY * DEFAULT_LOAD_FACTOR);
            this.symbolTable = new SymbolEntry[DEFAULT_INITIAL_CAPACITY];
        }
        
        // note all fields are final -- rehash creates new entries when necessary.
        // as documented in java.util.concurrent.ConcurrentHashMap.java, that will
        // statistically affect only a small percentage (< 20%) of entries for a given rehash.
        static class SymbolEntry {
            final int hash;
            final String name;
            final RubySymbol symbol;
            final SymbolEntry next;
            
            SymbolEntry(int hash, String name, RubySymbol symbol, SymbolEntry next) {
                this.hash = hash;
                this.name = name;
                this.symbol = symbol;
                this.next = next;
            }
        }

        public RubySymbol getSymbol(String name) {
            int hash = name.hashCode();
            SymbolEntry[] table;
            for (SymbolEntry e = (table = symbolTable)[hash & (table.length - 1)]; e != null; e = e.next) {
                if (hash == e.hash && name.equals(e.name)) {
                    return e.symbol;
                }
            }
            ReentrantLock lock;
            (lock = tableLock).lock();
            try {
                int potentialNewSize;
                if ((potentialNewSize = size + 1) > threshold) {
                    table = rehash(); 
                } else {
                    table = symbolTable;
                }
                int index;
                // try lookup again under lock
                for (SymbolEntry e = table[index = hash & (table.length - 1)]; e != null; e = e.next) {
                    if (hash == e.hash && name.equals(e.name)) {
                        return e.symbol;
                    }
                }
                String internedName;
                RubySymbol symbol = new RubySymbol(runtime, internedName = name.intern());
                table[index] = new SymbolEntry(hash, internedName, symbol, table[index]);
                size = potentialNewSize;
                // write-volatile
                symbolTable = table;
                return symbol;
            } finally {
                lock.unlock();
            }
        }
        
        public RubySymbol fastGetSymbol(String internedName) {
            assert internedName == internedName.intern() : internedName + " is not interned";
            SymbolEntry[] table;
            for (SymbolEntry e = (table = symbolTable)[internedName.hashCode() & (table.length - 1)]; e != null; e = e.next) {
                if (internedName == e.name) {
                    return e.symbol;
                }
            }
            ReentrantLock lock;
            (lock = tableLock).lock();
            try {
                int potentialNewSize;
                if ((potentialNewSize = size + 1) > threshold) {
                    table = rehash();
                } else {
                    table = symbolTable;
                }
                int index;
                int hash;
                // try lookup again under lock
                for (SymbolEntry e = table[index = (hash = internedName.hashCode()) & (table.length - 1)]; e != null; e = e.next) {
                    if (internedName == e.name) {
                        return e.symbol;
                    }
                }
                RubySymbol symbol = new RubySymbol(runtime, internedName);
                table[index] = new SymbolEntry(hash, internedName, symbol, table[index]);
                size = potentialNewSize;
                // write-volatile
                symbolTable = table;
                return symbol;
            } finally {
                lock.unlock();
            }
        }
        
        // backwards-compatibility, but threadsafe now
        public RubySymbol lookup(String name) {
            int hash = name.hashCode();
            SymbolEntry[] table;
            for (SymbolEntry e = (table = symbolTable)[hash & (table.length - 1)]; e != null; e = e.next) {
                if (hash == e.hash && name.equals(e.name)) {
                    return e.symbol;
                }
            }
            return null;
        }
        
        public RubySymbol lookup(long id) {
            SymbolEntry[] table = symbolTable;
            for (int i = table.length; --i >= 0; ) {
                for (SymbolEntry e = table[i]; e != null; e = e.next) {
                    if (id == e.symbol.id) {
                        return e.symbol;
                    }
                }
            }
            return null;
        }
        
        public RubyArray all_symbols() {
            SymbolEntry[] table = this.symbolTable;
            RubyArray array = runtime.newArray(this.size);
            for (int i = table.length; --i >= 0; ) {
                for (SymbolEntry e = table[i]; e != null; e = e.next) {
                    array.append(e.symbol);
                }
            }
            return array;
        }
        
        // not so backwards-compatible here, but no one should have been
        // calling this anyway.
        @Deprecated
        public void store(RubySymbol symbol) {
            throw new UnsupportedOperationException();
        }
        
        private SymbolEntry[] rehash() {
            SymbolEntry[] oldTable = symbolTable;
            int oldCapacity;
            if ((oldCapacity = oldTable.length) >= MAXIMUM_CAPACITY) {
                return oldTable;
            }
            
            int newCapacity = oldCapacity << 1;
            SymbolEntry[] newTable = new SymbolEntry[newCapacity];
            threshold = (int)(newCapacity * loadFactor);
            int sizeMask = newCapacity - 1;
            SymbolEntry e;
            for (int i = oldCapacity; --i >= 0; ) {
                // We need to guarantee that any existing reads of old Map can
                //  proceed. So we cannot yet null out each bin.
                e = oldTable[i];

                if (e != null) {
                    SymbolEntry next = e.next;
                    int idx = e.hash & sizeMask;

                    //  Single node on list
                    if (next == null)
                        newTable[idx] = e;

                    else {
                        // Reuse trailing consecutive sequence at same slot
                        SymbolEntry lastRun = e;
                        int lastIdx = idx;
                        for (SymbolEntry last = next;
                             last != null;
                             last = last.next) {
                            int k = last.hash & sizeMask;
                            if (k != lastIdx) {
                                lastIdx = k;
                                lastRun = last;
                            }
                        }
                        newTable[lastIdx] = lastRun;

                        // Clone all remaining nodes
                        for (SymbolEntry p = e; p != lastRun; p = p.next) {
                            int k = p.hash & sizeMask;
                            SymbolEntry n = newTable[k];
                            newTable[k] = new SymbolEntry(p.hash, p.name, p.symbol, n);
                        }
                    }
                }
            }
            symbolTable = newTable;
            return newTable;
        }
        
    }
}
