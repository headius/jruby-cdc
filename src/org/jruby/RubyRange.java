/*
 * RubyRange.java - No description
 * Created on 26. Juli 2001, 00:01
 * 
 * Copyright (C) 2001, 2002 Jan Arne Petersen, Alan Moore, Benoit Cerrina
 * Copyright (C) 2002-2004 Thomas E. Enebo
 * Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Alan Moore <alan_moore@gmx.net>
 * Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Thomas E. Enebo <enebo@acm.org>
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

import org.jruby.exceptions.ArgumentError;
import org.jruby.exceptions.RaiseException;
import org.jruby.exceptions.RangeError;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * @author jpetersen
 * @version $Revision$
 */
public class RubyRange extends RubyObject {

    private IRubyObject begin;
    private IRubyObject end;
    private boolean isExclusive;

    public RubyRange(Ruby runtime) {
        super(runtime, runtime.getClass("Range"));
    }

    public void init(IRubyObject begin, IRubyObject end, RubyBoolean isExclusive) {
        if (!(begin instanceof RubyFixnum && end instanceof RubyFixnum)) {
            try {
                begin.callMethod("<=>", end);
            } catch (RaiseException rExcptn) {
                throw new ArgumentError(getRuntime(), "bad value for range");
            }
        }

        this.begin = begin;
        this.end = end;
        this.isExclusive = isExclusive.isTrue();
    }

    public static RubyClass createRangeClass(Ruby runtime) {
        RubyClass result = runtime.defineClass("Range", 
                runtime.getClasses().getObjectClass());
        CallbackFactory callbackFactory = runtime.callbackFactory();
        
        result.includeModule(runtime.getClasses().getEnumerableModule());

        result.defineMethod("==", callbackFactory.getMethod(RubyRange.class, "equal", IRubyObject.class));
        result.defineMethod("===", callbackFactory.getMethod(RubyRange.class, "op_eqq", IRubyObject.class));
        result.defineMethod("begin", callbackFactory.getMethod(RubyRange.class, "first"));
        result.defineMethod("each", callbackFactory.getMethod(RubyRange.class, "each"));
        result.defineMethod("end", callbackFactory.getMethod(RubyRange.class, "last"));
        result.defineMethod("exclude_end?", callbackFactory.getMethod(RubyRange.class, "exclude_end_p"));
        result.defineMethod("first", callbackFactory.getMethod(RubyRange.class, "first"));
        result.defineMethod("initialize", callbackFactory.getOptMethod(RubyRange.class, "initialize"));
        result.defineMethod("inspect", callbackFactory.getMethod(RubyRange.class, "inspect"));
        result.defineMethod("last", callbackFactory.getMethod(RubyRange.class, "last"));
        result.defineMethod("length", callbackFactory.getMethod(RubyRange.class, "length"));
        result.defineMethod("size", callbackFactory.getMethod(RubyRange.class, "length"));
        result.defineMethod("to_s", callbackFactory.getMethod(RubyRange.class, "inspect"));

        result.defineMethod("to_a", callbackFactory.getMethod(RubyRange.class, "to_a"));

        return result;
    }

    /**
     * Converts this Range to a pair of integers representing a start position 
     * and length.  If either of the range's endpoints is negative, it is added to 
     * the <code>limit</code> parameter in an attempt to arrive at a position 
     * <i>p</i> such that <i>0&nbsp;&lt;=&nbsp;p&nbsp;&lt;=&nbsp;limit</i>. If 
     * <code>truncate</code> is true, the result will be adjusted, if possible, so 
     * that <i>begin&nbsp;+&nbsp;length&nbsp;&lt;=&nbsp;limit</i>.  If <code>strict</code> 
     * is true, an exception will be raised if the range can't be converted as 
     * described above; otherwise it just returns <b>null</b>. 
     * 
     * @param limit    the size of the object (e.g., a String or Array) that 
     *                 this range is being evaluated against.
     * @param truncate if true, result must fit within the range <i>(0..limit)</i>.
     * @param isStrict   if true, raises an exception if the range can't be converted.
     * @return         a two-element array representing a start value and a length, 
     *                 or <b>null</b> if the conversion failed.
     */
    public long[] getBeginLength(long limit, boolean truncate, boolean isStrict) {
        long beginLong = RubyNumeric.num2long(begin);
        long endLong = RubyNumeric.num2long(end);

        if (! isExclusive) {
            endLong++;
        }

        if (beginLong < 0) {
            beginLong += limit;
            if (beginLong < 0) {
                if (isStrict) {
                    throw new RangeError(runtime, inspect().toString() + " out of range.");
                }
                return null;
            }
        }

        if (truncate && beginLong > limit) {
            if (isStrict) {
                throw new RangeError(runtime, inspect().toString() + " out of range.");
            }
            return null;
        }

        if (truncate && endLong > limit) {
            endLong = limit;
        }

		if (endLong < 0  || (!isExclusive && endLong == 0)) {
			endLong += limit;
			if (endLong < 0) {
				if (isStrict) {
					throw new RangeError(runtime, inspect().toString() + " out of range.");
				}
				return null;
			}
		}

        if (beginLong > endLong) {
            if (isStrict) {
                throw new RangeError(runtime, inspect().toString() + " out of range.");
            }
			return null;
        }

        return new long[] { beginLong, endLong - beginLong };
    }

    // public Range methods

    public static RubyRange newRange(Ruby runtime, IRubyObject begin, IRubyObject end, boolean isExclusive) {
        RubyRange range = new RubyRange(runtime);
        range.init(begin, end, isExclusive ? runtime.getTrue() : runtime.getFalse());
        return range;
    }

    public IRubyObject initialize(IRubyObject[] args) {
        if (args.length == 3) {
            init(args[0], args[1], (RubyBoolean) args[2]);
        } else if (args.length == 2) {
            init(args[0], args[1], getRuntime().getFalse());
        } else {
            throw new ArgumentError(getRuntime(), "Wrong arguments. (anObject, anObject, aBoolean = false) expected");
        }
        return getRuntime().getNil();
    }

    public IRubyObject first() {
        return begin;
    }

    public IRubyObject last() {
        return end;
    }

    public RubyString inspect() {
        RubyString begStr = (RubyString) begin.callMethod("to_s");
        RubyString endStr = (RubyString) end.callMethod("to_s");

        begStr.cat(isExclusive ? "..." : "..");
        begStr.concat(endStr);
        return begStr;
    }

    public RubyBoolean exclude_end_p() {
        return RubyBoolean.newBoolean(getRuntime(), isExclusive);
    }

    public RubyFixnum length() {
        long size = 0;

        if (begin.callMethod(">", end).isTrue()) {
            return RubyFixnum.newFixnum(getRuntime(), 0);
        }

        if (begin instanceof RubyFixnum && end instanceof RubyFixnum) {
            size = ((RubyNumeric) end).getLongValue() - ((RubyNumeric) begin).getLongValue();
            if (!isExclusive) {
                size++;
            }
        } else { // Support length for arbitrary classes
            IRubyObject currentObject = begin;
	    String compareMethod = isExclusive ? "<" : "<=";

	    while (currentObject.callMethod(compareMethod, end).isTrue()) {
		size++;
		if (currentObject.equals(end)) {
		    break;
		}
		currentObject = currentObject.callMethod("succ");
	    }
	}
        return RubyFixnum.newFixnum(getRuntime(), size);
    }

    public IRubyObject equal(IRubyObject obj) {
        if (!(obj instanceof RubyRange)) {
            return getRuntime().getFalse();
        }
        RubyRange otherRange = (RubyRange) obj;
        boolean result =
            begin.equals(otherRange.begin) &&
            end.equals(otherRange.end) &&
            isExclusive == otherRange.isExclusive;
        return RubyBoolean.newBoolean(getRuntime(), result);
    }

    public RubyBoolean op_eqq(IRubyObject obj) {
        if (begin instanceof RubyFixnum && obj instanceof RubyFixnum && end instanceof RubyFixnum) {
            long b = RubyNumeric.fix2long(begin);
            long o = RubyNumeric.fix2long(obj);

            if (b <= o) {
                long e =  RubyNumeric.fix2long(end);
                if (isExclusive) {
                    if (o < e) {
                        return getRuntime().getTrue();
                    }
                } else {
                    if (o <= e) {
                        return getRuntime().getTrue();
                    }
                }
            }
            return getRuntime().getFalse();
        } else if (begin.callMethod("<=", obj).isTrue()) {
            if (isExclusive) {
                if (end.callMethod(">", obj).isTrue()) {
                    return getRuntime().getTrue();
                }
            } else {
                if (end.callMethod(">=", obj).isTrue()) {
                    return getRuntime().getTrue();
                }
            }
        }
        return getRuntime().getFalse();
    }

    public IRubyObject each() {
        if (begin instanceof RubyFixnum && end instanceof RubyFixnum) {
            long endLong = ((RubyNumeric) end).getLongValue();
            long i = ((RubyNumeric) begin).getLongValue();

            if (!isExclusive) {
                endLong += 1;
            }

            for (; i < endLong; i++) {
                getRuntime().yield(RubyFixnum.newFixnum(getRuntime(), i));
            }
        } else if (begin instanceof RubyString) {
            ((RubyString) begin).upto(end, isExclusive);
        } else if (begin.isKindOf(getRuntime().getClasses().getNumericClass())) {
            if (!isExclusive) {
                end = end.callMethod("+", RubyFixnum.one(getRuntime()));
            }
            while (begin.callMethod("<", end).isTrue()) {
                getRuntime().yield(begin);
                begin = begin.callMethod("+", RubyFixnum.one(getRuntime()));
            }
        } else {
            IRubyObject v = begin;

            if (isExclusive) {
                while (v.callMethod("<", end).isTrue()) {
                    if (v.equals(end)) {
                        break;
                    }
                    getRuntime().yield(v);
                    v = v.callMethod("succ");
                }
            } else {
                while (v.callMethod("<=", end).isTrue()) {
                    getRuntime().yield(v);
                    if (v.equals(end)) {
                        break;
                    }
                    v = v.callMethod("succ");
                }
            }
        }

        return this;
    }
    
    public RubyArray to_a() {
        IRubyObject currentObject = begin;
	    String compareMethod = isExclusive ? "<" : "<=";
	    RubyArray array = RubyArray.newArray(getRuntime());
        
	    while (currentObject.callMethod(compareMethod, end).isTrue()) {
	        array.append(currentObject);
	        
			if (currentObject.equals(end)) {
			    break;
			}
			
			currentObject = currentObject.callMethod("succ");
	    }
	    
	    return array;
    }
}
