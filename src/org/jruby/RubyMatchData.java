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
 * Copyright (C) 2004 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
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

import jregex.Matcher;
import org.jruby.runtime.Arity;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.builtin.IRubyObject;

/**
 *
 * @author  amoore
 */
public class RubyMatchData extends RubyObject {
    private String original;
    private Matcher matcher;

    public RubyMatchData(Ruby runtime, String original, Matcher matcher) {
        super(runtime, runtime.getClass("MatchData"));
        this.matcher = matcher;
        this.original = original;
    }

    public static RubyClass createMatchDataClass(Ruby runtime) {
        // TODO: Is NOT_ALLOCATABLE_ALLOCATOR ok here, since you can't actually instanriate MatchData directly?
        RubyClass matchDataClass = runtime.defineClass("MatchData", runtime.getObject(), ObjectAllocator.NOT_ALLOCATABLE_ALLOCATOR);
        runtime.defineGlobalConstant("MatchingData", matchDataClass);

        CallbackFactory callbackFactory = runtime.callbackFactory(RubyMatchData.class);

        matchDataClass.defineFastMethod("captures", callbackFactory.getFastMethod("captures"));
        matchDataClass.defineFastMethod("inspect", callbackFactory.getFastMethod("inspect"));
        matchDataClass.defineFastMethod("size", callbackFactory.getFastMethod("size"));
        matchDataClass.defineFastMethod("length", callbackFactory.getFastMethod("size"));
        matchDataClass.defineFastMethod("offset", callbackFactory.getFastMethod("offset", RubyFixnum.class));
        matchDataClass.defineFastMethod("begin", callbackFactory.getFastMethod("begin", RubyFixnum.class));
        matchDataClass.defineFastMethod("end", callbackFactory.getFastMethod("end", RubyFixnum.class));
        matchDataClass.defineFastMethod("to_a", callbackFactory.getFastMethod("to_a"));
        matchDataClass.defineFastMethod("[]", callbackFactory.getFastOptMethod("aref"));
        matchDataClass.defineFastMethod("pre_match", callbackFactory.getFastMethod("pre_match"));
        matchDataClass.defineFastMethod("post_match", callbackFactory.getFastMethod("post_match"));
        matchDataClass.defineFastMethod("to_s", callbackFactory.getFastMethod("to_s"));
        matchDataClass.defineFastMethod("string", callbackFactory.getFastMethod("string"));

        matchDataClass.getMetaClass().undefineMethod("new");

        return matchDataClass;
    }
    
    public IRubyObject captures() {
        RubyArray arr = getRuntime().newArray(matcher.groupCount());
        
        for (int i = 1; i < matcher.groupCount(); i++) {
            if (matcher.group(i) == null) {
                arr.append(getRuntime().getNil());
            } else {
                arr.append(RubyString.newString(getRuntime(), matcher.group(i)));
            }
        }
        
        return arr;
    }

    public IRubyObject subseq(long beg, long len) {
    	// Subsequence begins at a valid index and a positive length
        if (beg < 0 || beg > getSize() || len < 0) {
            getRuntime().getNil();
        }

        if (beg + len > getSize()) {
            len = getSize() - beg;
        }
        if (len < 0) {
            len = 0;
        }
        if (len == 0) {
            return getRuntime().newArray();
        }
        
        RubyArray arr = getRuntime().newArray(0);
        for (long i = beg; i < beg + len; i++) {
            arr.append(group(i));
        }
        return arr;
    }

    public long getSize() {
        return matcher.groupCount();
    }
    
    public boolean proceed() {
        return matcher.proceed();
    }
    
    public boolean find() {
        return matcher.find();
    }

    public IRubyObject group(long n) {
    	// Request an invalid group OR group is an empty match
        if (n < 0 || n >= getSize() || matcher.group((int)n) == null) {
            return getRuntime().getNil();
        }
        // Fix for JRUBY-97: Temporary fix pending 
        // decision on UTF8-based string implementation.
        // String#substring reuses the storage of the original string
        // <http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4513622> 
        // Wrapping the String#substring in new String prevents this.
        // This wrapping alone was enough to fix the failing test cases in
        // JRUBY-97, but at the same time the testcase remained very slow
        // The additional minor optimizations to RubyString as part of the fix
        // dramatically improve the performance. 
        return getRuntime().newString(matcher.group((int)n));
    }

    public int matchStartPosition() {
        return matcher.start();
    }

    public int matchEndPosition() {
        return matcher.end();
    }

    private boolean outOfBounds(RubyFixnum index) {
        return outOfBounds(index.getLongValue());
    }
    
    // version to work with Java primitives for efficiency
    private boolean outOfBounds(long n) {
        return n < 0 || n >= getSize();
    }

    //
    // Methods of the MatchData Class:
    //

    /** match_aref
     *
     */
    public IRubyObject aref(IRubyObject[] args) {
        int argc = Arity.checkArgumentCount(getRuntime(), args, 1, 2);
        if (argc == 2) {
            int beg = RubyNumeric.fix2int(args[0]);
            int len = RubyNumeric.fix2int(args[1]);
            if (beg < 0) {
                beg += getSize();
            }
            return subseq(beg, len);
        }
        if (args[0] instanceof RubyFixnum) {
            return group(RubyNumeric.fix2int(args[0]));
        }
        if (args[0] instanceof RubyBignum) {
            throw getRuntime().newIndexError("index too big");
        }
        if (args[0] instanceof RubyRange) {
            long[] begLen = ((RubyRange) args[0]).getBeginLength(getSize(), true, false);
            if (begLen == null) {
                return getRuntime().getNil();
            }
            return subseq(begLen[0], begLen[1]);
        }
        return group(RubyNumeric.num2long(args[0]));
    }

    /** match_begin
     *
     */
    public IRubyObject begin(RubyFixnum index) {
        long lIndex = index.getLongValue();
        long answer = begin((int)lIndex);
        
        return answer == -1 ? getRuntime().getNil() : getRuntime().newFixnum(answer);
    }
    
    public int begin(int index) {
        return outOfBounds(index) ? -1 : matcher.start((int)index);
    }

    /** match_end
     *
     */
    public IRubyObject end(RubyFixnum index) {
        int lIndex = RubyNumeric.fix2int(index);
        long answer = end(lIndex);

        return answer == -1 ? getRuntime().getNil() : getRuntime().newFixnum(answer);
    }
    
    public int end(int index) {
        return outOfBounds(index) ? -1 : matcher.end((int)index); 
    }
    
    public IRubyObject inspect() {
    	return anyToString();
    }

    /** match_size
     *
     */
    public RubyFixnum size() {
        return getRuntime().newFixnum(getSize());
    }

    /** match_offset
     *
     */
    public IRubyObject offset(RubyFixnum index) {
        if (outOfBounds(index)) {
            return getRuntime().getNil();
        }
        return getRuntime().newArrayNoCopy(new IRubyObject[] { begin(index), end(index)});
    }

    /** match_pre_match
     *
     */
    public RubyString pre_match() {
        return getRuntime().newString(matcher.prefix());
    }

    /** match_post_match
     *
     */
    public RubyString post_match() {
        return getRuntime().newString(matcher.suffix());
    }

    /** match_string
     *
     */
    public RubyString string() {
        RubyString frozenString = getRuntime().newString(original);
        frozenString.freeze();
        return frozenString;
    }

    /** match_to_a
     *
     */
    public RubyArray to_a() {
        RubyArray arr = getRuntime().newArray(matcher.groupCount());
        
        for (int i = 0; i < matcher.groupCount(); i++) {
            if (matcher.group(i) == null) {
                arr.append(RubyString.newString(getRuntime(), ""));
            } else {
                arr.append(RubyString.newString(getRuntime(), matcher.group(i)));
            }
        }
        
        return arr;
    }

    /** match_to_s
     *
     */
    public IRubyObject to_s() {
        return getRuntime().newString(matcher.group(0));
    }

    public IRubyObject doClone() {
        return new RubyMatchData(getRuntime(), original, matcher);
    }
}
