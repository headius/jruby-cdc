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
 * Copyright (C) 2006-2007 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2006-2007 Charles Nutter <headius@headius.com>
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
package org.jruby.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jruby.RubyInstanceConfig;

/**
 *
 * @author headius
 */
public class MethodIndex {
    public static final List<String> NAMES = new ArrayList<String>();
    private static final Map<String, Integer> NUMBERS = new HashMap<String, Integer>();
    private static final Map<Integer, CallSite> CALL_SITES = new HashMap<Integer, CallSite>();
    private static final Map<Integer, CallSite> FUNCTIONAL_CALL_SITES = new HashMap<Integer, CallSite>();
    private static final Map<Integer, CallSite> VARIABLE_CALL_SITES = new HashMap<Integer, CallSite>();
    
    // ensure zero is devoted to no method name
    public static final int NO_INDEX = getIndex("");
    
    // predefine a few other methods we invoke directly elsewhere
    public static final int OP_PLUS = getIndex("+");
    public static final int OP_MINUS = getIndex("-");
    public static final int OP_LT = getIndex("<");
    public static final int AREF = getIndex("[]");
    public static final int ASET = getIndex("[]=");
    public static final int EQUALEQUAL = getIndex("==");
    public static final int OP_LSHIFT = getIndex("<<");
    public static final int EMPTY_P = getIndex("empty?");
    public static final int TO_S = getIndex("to_s");
    public static final int TO_I = getIndex("to_i");
    public static final int TO_STR = getIndex("to_str");
    public static final int TO_ARY = getIndex("to_ary");
    public static final int TO_INT = getIndex("to_int");
    public static final int TO_F = getIndex("to_f");
    public static final int TO_A = getIndex("to_a");
    public static final int TO_IO = getIndex("to_io");
    public static final int HASH = getIndex("hash");
    public static final int OP_GT = getIndex(">");
    public static final int OP_TIMES = getIndex("*");
    public static final int OP_LE = getIndex("<=");
    public static final int OP_SPACESHIP = getIndex("<=>");
    public static final int OP_EQQ = getIndex("===");
    public static final int EQL_P = getIndex("eql?");
    public static final int TO_HASH = getIndex("to_hash");
    public static final int METHOD_MISSING = getIndex("method_missing");
    public static final int DEFAULT = getIndex("default");
    
    public synchronized static int getIndex(String methodName) {
        Integer index = NUMBERS.get(methodName);
        
        if (index == null) {
            index = new Integer(NAMES.size());
            NUMBERS.put(methodName, index);
            NAMES.add(methodName);
        }
        
        return index;
    }
    
    public synchronized static CallSite getCallSite(String name) {
        if (!RubyInstanceConfig.FASTOPS_COMPILE_ENABLED) {
            return new CallSite.InlineCachingCallSite(name, CallType.NORMAL);
        } else {
            if (name.equals("+")) {
                return new CallSite.PlusCallSite();
            } else if (name.equals("-")) {
                return new CallSite.MinusCallSite();
            } else if (name.equals("*")) {
                return new CallSite.MulCallSite();
            } else if (name.equals("/")) {
                return new CallSite.DivCallSite();
            } else if (name.equals("<")) {
                return new CallSite.LtCallSite();
            } else if (name.equals("<-")) {
                return new CallSite.LeCallSite();
            } else if (name.equals(">")) {
                return new CallSite.GtCallSite();
            } else if (name.equals(">=")) {
                return new CallSite.GeCallSite();
            } else {
                return new CallSite.InlineCachingCallSite(name, CallType.NORMAL);
            }
        }
    }
    
    public synchronized static CallSite getFunctionalCallSite(String name) {
        return new CallSite.InlineCachingCallSite(name, CallType.FUNCTIONAL);
    }
    
    public synchronized static CallSite getVariableCallSite(String name) {
        return new CallSite.InlineCachingCallSite(name, CallType.VARIABLE);
    }
}
