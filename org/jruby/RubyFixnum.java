/*
 * RubyFixnum.java - No description
 * Created on 04. Juli 2001, 22:53
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

import org.jruby.runtime.*;

/**
 *
 * @author  jpetersen
 */
public class RubyFixnum extends RubyInteger {
    private long value;

    public RubyFixnum(Ruby ruby) {
        this(ruby, 0);
    }

    public RubyFixnum(Ruby ruby, long value) {
        super(ruby, ruby.getClasses().getFixnumClass());
        this.value = value;
    }

    public Class getJavaClass() {
        return Long.TYPE;
    }

    /** Getter for property value.
     * @return Value of property value.
     */
    public long getValue() {
        return this.value;
    }

    /** Setter for property value.
     * @param value New value of property value.
     */
    public void setValue(long value) {
        this.value = value;
    }

    public double getDoubleValue() {
        return (double) value;
    }

    public long getLongValue() {
        return value;
    }
    
	public static RubyClass createFixnumClass(Ruby ruby) {
        RubyClass fixnumClass = ruby.defineClass("Fixnum", ruby.getClasses().getIntegerClass());

        fixnumClass.defineMethod("to_s", CallbackFactory.getMethod(RubyFixnum.class, "to_s"));
        fixnumClass.defineMethod("to_str", CallbackFactory.getMethod(RubyFixnum.class, "to_s"));
        fixnumClass.defineMethod("hash", CallbackFactory.getMethod(RubyFixnum.class, "hash"));

        fixnumClass.defineMethod("+", CallbackFactory.getMethod(RubyFixnum.class, "op_plus", RubyObject.class));
        fixnumClass.defineMethod("-", CallbackFactory.getMethod(RubyFixnum.class, "op_minus", RubyObject.class));
        fixnumClass.defineMethod("*", CallbackFactory.getMethod(RubyFixnum.class, "op_mul", RubyObject.class));
        fixnumClass.defineMethod("/", CallbackFactory.getMethod(RubyFixnum.class, "op_div", RubyObject.class));
        fixnumClass.defineMethod("%", CallbackFactory.getMethod(RubyFixnum.class, "op_mod", RubyObject.class));
        fixnumClass.defineMethod("**", CallbackFactory.getMethod(RubyFixnum.class, "op_pow", RubyObject.class));

        fixnumClass.defineMethod("==", CallbackFactory.getMethod(RubyFixnum.class, "op_equal", RubyObject.class));
        fixnumClass.defineMethod("<=>", CallbackFactory.getMethod(RubyFixnum.class, "op_cmp", RubyObject.class));
        fixnumClass.defineMethod(">", CallbackFactory.getMethod(RubyFixnum.class, "op_gt", RubyObject.class));
        fixnumClass.defineMethod(">=", CallbackFactory.getMethod(RubyFixnum.class, "op_ge", RubyObject.class));
        fixnumClass.defineMethod("<", CallbackFactory.getMethod(RubyFixnum.class, "op_lt", RubyObject.class));
        fixnumClass.defineMethod("<=", CallbackFactory.getMethod(RubyFixnum.class, "op_le", RubyObject.class));

        return fixnumClass;
    }

    public static RubyFixnum zero(Ruby ruby) {
        return newFixnum(ruby, 0);
    }

    public static RubyFixnum one(Ruby ruby) {
        return newFixnum(ruby, 1);
    }

    public static RubyFixnum minus_one(Ruby ruby) {
        return newFixnum(ruby, -1);
    }

    protected int compareValue(RubyNumeric other) {
        if (other instanceof RubyBignum) {
            return ((RubyBignum)other).compareValue(this) * -1;
        } else if (other instanceof RubyFloat) {
            double otherVal = other.getDoubleValue();
            double thisVal = getDoubleValue();
            return thisVal > otherVal ? 1 : thisVal < otherVal ? -1 : 0;
        } else {
            long otherVal = other.getLongValue();
            return getValue() > otherVal ? 1 : getValue() < otherVal ? -1 : 0;
        }
    }

    // Methods of the Fixnum Class (fix_*):

    public static RubyFixnum newFixnum(Ruby ruby, long value) {
        // Cache for Fixnums (Performance)
        if ((value & ~Ruby.FIXNUM_CACHE_MAX) == 0) {
            return ruby.fixnumCache[(int) value];
        }

        return new RubyFixnum(ruby, value);
    }

    public RubyFixnum newFixnum(long value) {
        // Cache for Fixnums (Performance)
        if ((value & ~Ruby.FIXNUM_CACHE_MAX) == 0) {
            return getRuby().fixnumCache[(int) value];
        }

        return new RubyFixnum(getRuby(), value);
    }

    public RubyFixnum hash() {
        // +++ jpetersen I don't think we need to use the hashCode() method.
        // return new RubyFixnum(getRuby(), new Long(value).hashCode());
        // +++
        return this;
    }

    public RubyNumeric op_plus(RubyObject num) {
        RubyNumeric other = numericValue(num);
        if (other instanceof RubyFloat) {
            return RubyFloat.newFloat(getRuby(), getDoubleValue()).op_plus(other);
        } else if (other instanceof RubyBignum) {
            return RubyBignum.newBignum(getRuby(), value).op_plus(other);
        } else {
            long otherValue = other.getLongValue();
            long result = value + otherValue;
			if ((value < 0 && otherValue < 0 && result > 0) || (value > 0 && otherValue > 0 && result < 0)) {
			    return RubyBignum.newBignum(getRuby(), value).op_plus(other);
        	}
            return newFixnum(result);
        }
    }

    public RubyNumeric op_minus(RubyObject num) {
        RubyNumeric other = numericValue(num);
        if (other instanceof RubyFloat) {
            return RubyFloat.newFloat(getRuby(), getDoubleValue()).op_minus(other);
        } else if (other instanceof RubyBignum) {
            return RubyBignum.newBignum(getRuby(), value).op_minus(other);
        } else {
            long otherValue = other.getLongValue();
            long result = value - otherValue;
			if ((value < 0 && otherValue > 0 && result > 0) || (value > 0 && otherValue < 0 && result < 0)) {
			    return RubyBignum.newBignum(getRuby(), value).op_minus(other);
        	}
            return newFixnum(result);
        }
    }

    public RubyNumeric op_mul(RubyObject num) {
        RubyNumeric other = numericValue(num);
        if (other instanceof RubyFloat) {
            return RubyFloat.newFloat(getRuby(), getDoubleValue()).op_mul(other);
        } else if (other instanceof RubyBignum) {
            return RubyBignum.newBignum(getRuby(), getLongValue()).op_mul(other);
        } else {
            long otherValue = other.getLongValue();
            long result = value * otherValue;
			if (result / otherValue == value) {
            	return newFixnum(result);
			} else {
			    return RubyBignum.newBignum(getRuby(), getLongValue()).op_mul(other);
			}			
        }
    }

    public RubyNumeric op_div(RubyObject num) {
        RubyNumeric other = numericValue(num);
        if (other instanceof RubyFloat) {
            return RubyFloat.newFloat(getRuby(), getDoubleValue()).op_div(other);
        } else if (other instanceof RubyBignum) {
            return RubyBignum.newBignum(getRuby(), getLongValue()).op_div(other);
        } else {
            return newFixnum(getRuby(), getValue() / other.getLongValue());
        }
    }

    public RubyNumeric op_mod(RubyObject num) {
        RubyNumeric other = numericValue(num);
        if (other instanceof RubyFloat) {
            return RubyFloat.newFloat(getRuby(), getDoubleValue()).op_mod(other);
        } else if (other instanceof RubyBignum) {
            return RubyBignum.newBignum(getRuby(), getLongValue()).op_mod(other);
        } else {
            return newFixnum(getRuby(), getValue() % other.getLongValue());
        }
    }

    public RubyNumeric op_pow(RubyObject num) {
        RubyNumeric other = numericValue(num);
        if (other instanceof RubyFloat) {
            return RubyFloat.newFloat(getRuby(), getDoubleValue()).op_pow(other);
        } else {
            if (other.getLongValue() == 0) {
                return newFixnum(getRuby(), 1);
            } else if (other.getLongValue() == 1) {
                return this;
            } else if (other.getLongValue() > 1) {
                return RubyBignum.newBignum(getRuby(), getLongValue()).op_pow(other);
            } else {
                return RubyFloat.newFloat(getRuby(), getDoubleValue()).op_pow(other);
            }
        }
    }

    public RubyBoolean op_equal(RubyObject other) {
        if (!(other instanceof RubyNumeric)) {
            return getRuby().getFalse();
        } else {
            return RubyBoolean.newBoolean(getRuby(),
                compareValue((RubyNumeric)other) == 0);
        }
    }

    public RubyNumeric op_cmp(RubyObject num) {
        RubyNumeric other = numericValue(num);
        return RubyFixnum.newFixnum(getRuby(), compareValue(other));
    }

    public RubyBoolean op_gt(RubyObject num) {
        RubyNumeric other = numericValue(num);
        return RubyBoolean.newBoolean(getRuby(), compareValue(other) > 0);
    }

    public RubyBoolean op_ge(RubyObject num) {
        RubyNumeric other = numericValue(num);
        return RubyBoolean.newBoolean(getRuby(), compareValue(other) >= 0);
    }

    public RubyBoolean op_lt(RubyObject num) {
        RubyNumeric other = numericValue(num);
        return RubyBoolean.newBoolean(getRuby(), compareValue(other) < 0);
    }

    public RubyBoolean op_le(RubyObject num) {
        RubyNumeric other = numericValue(num);
        return RubyBoolean.newBoolean(getRuby(), compareValue(other) <= 0);
    }

    public RubyString to_s() {
        return RubyString.newString(getRuby(), String.valueOf(getValue()));
    }
}