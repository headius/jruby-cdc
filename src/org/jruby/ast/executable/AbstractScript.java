/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jruby.ast.executable;

import org.jruby.Ruby;
import org.jruby.RubyFixnum;
import org.jruby.RubySymbol;
import org.jruby.runtime.Block;
import org.jruby.runtime.CallSite;
import org.jruby.runtime.MethodIndex;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

/**
 *
 * @author headius
 */
public abstract class AbstractScript implements Script {
    public AbstractScript() {
    }
    
    public IRubyObject __file__(ThreadContext context, IRubyObject self, Block block) {
        return __file__(context, self, IRubyObject.NULL_ARRAY, block);
    }
    
    public IRubyObject __file__(ThreadContext context, IRubyObject self, IRubyObject arg, Block block) {
        return __file__(context, self, new IRubyObject[] {arg}, block);
    }
    
    public IRubyObject __file__(ThreadContext context, IRubyObject self, IRubyObject arg1, IRubyObject arg2, Block block) {
        return __file__(context, self, new IRubyObject[] {arg1, arg2}, block);
    }
    
    public IRubyObject __file__(ThreadContext context, IRubyObject self, IRubyObject arg1, IRubyObject arg2, IRubyObject arg3, Block block) {
        return __file__(context, self, new IRubyObject[] {arg1, arg2, arg3}, block);
    }
    
    public IRubyObject load(ThreadContext context, IRubyObject self, IRubyObject[] args, Block block) {
        return null;
    }
    
    public IRubyObject run(ThreadContext context, IRubyObject self, IRubyObject[] args, Block block) {
        return __file__(context, self, args, block);
    }

    public final CallSite getCallSite(int index) {
        return callSites[index];
    }

    public final RubySymbol getSymbol(Ruby runtime, int index, String name) {
        RubySymbol symbol = symbols[index];
        if (symbol == null) return symbols[index] = runtime.newSymbol(name);
        return symbol;
    }

    public final RubyFixnum getFixnum(Ruby runtime, int index, int value) {
        RubyFixnum fixnum = fixnums[index];
        if (fixnum == null) return fixnums[index] = RubyFixnum.newFixnum(runtime, value);
        return fixnum;
    }

    public final RubyFixnum getFixnum(Ruby runtime, int index, long value) {
        RubyFixnum fixnum = fixnums[index];
        if (fixnum == null) return fixnums[index] = RubyFixnum.newFixnum(runtime, value);
        return fixnum;
    }

    public final void initCallSites(int size) {
        callSites = new CallSite[size];
    }

    public final void initSymbols(int size) {
        symbols = new RubySymbol[size];
    }

    public final void initFixnums(int size) {
        fixnums = new RubyFixnum[size];
    }

    public static CallSite[] setCallSite(CallSite[] callSites, int index, String name) {
        callSites[index] = MethodIndex.getCallSite(name);
        return callSites;
    }

    public static CallSite[] setFunctionalCallSite(CallSite[] callSites, int index, String name) {
        callSites[index] = MethodIndex.getFunctionalCallSite(name);
        return callSites;
    }

    public static CallSite[] setVariableCallSite(CallSite[] callSites, int index, String name) {
        callSites[index] = MethodIndex.getVariableCallSite(name);
        return callSites;
    }
    
    public final void setFilename(String filename) {
        this.filename = filename;
    }

    public CallSite[] callSites;
    public RubySymbol[] symbols;
    public RubyFixnum[] fixnums;
    public String filename;
}
