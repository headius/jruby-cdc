/*
 * RubyFixnum.java - No description
 * Created on 04. Juli 2001, 22:53
 * 
 * Copyright (C) 2001 Jan Arne Petersen, Stefan Matthias Aust
 * Jan Arne Petersen <japetersen@web.de>
 * Stefan Matthias Aust <sma@3plus4.de>
 * 
 * JRuby - http://jruby.sourceforge.net
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 */

package org.jruby;

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
        super(ruby, ruby.getFixnumClass());
        this.value = value;
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
        return (double)value;
    }
    
    public long getLongValue() {
        return value;
    }
    
    protected boolean needBignumAdd(long value) {
        if ((getValue() < 0) && (value < 0)) {
            return (getValue() + value) >= 0;
        } else if ((getValue() > 0) && (value > 0)) {
            return (getValue() + value) < 0;
        }
        return false;
    }
    
    protected boolean needBignumMul(long value) {
        long product = getValue() * value;
        return (product / value) != getValue();
    }
    
    // Methods of the Fixnum Class (fix_*):
    
    public static RubyFixnum m_newFixnum(Ruby ruby, long value) {
        // Cache for Fixnums (Performance)
        
        return new RubyFixnum(ruby, value);
    }
    
    public RubyNumeric op_plus(RubyNumeric other) {
        if (other instanceof RubyFloat) {
            return RubyFloat.m_newFloat(getRuby(), getDoubleValue()).op_plus(other);
        } else if (other instanceof RubyBignum || needBignumAdd(other.getLongValue())) {
            return RubyBignum.m_newBignum(getRuby(), getLongValue()).op_plus(other);
        } else {
            return m_newFixnum(getRuby(), getValue() + other.getLongValue());
        }
    }
    
    public RubyNumeric op_minus(RubyNumeric other) {
        if (other instanceof RubyFloat) {
            return RubyFloat.m_newFloat(getRuby(), getDoubleValue()).op_minus(other);
        } else if (other instanceof RubyBignum  || needBignumAdd(other.getLongValue())) {
            return RubyBignum.m_newBignum(getRuby(), getLongValue()).op_minus(other);
        } else {
            return m_newFixnum(getRuby(), getValue() - other.getLongValue());
        }
    }
    
    public RubyNumeric op_mul(RubyNumeric other) {
        if (other instanceof RubyFloat) {
            return RubyFloat.m_newFloat(getRuby(), getDoubleValue()).op_mul(other);
        } else if (other instanceof RubyBignum  || needBignumMul(other.getLongValue())) {
            return RubyBignum.m_newBignum(getRuby(), getLongValue()).op_mul(other);
        } else {
            return m_newFixnum(getRuby(), getValue() * other.getLongValue());
        }
    }
    
    public RubyNumeric op_div(RubyNumeric other) {
        if (other instanceof RubyFloat) {
            return RubyFloat.m_newFloat(getRuby(), getDoubleValue()).op_div(other);
        } else if (other instanceof RubyBignum) {
            return RubyBignum.m_newBignum(getRuby(), getLongValue()).op_div(other);
        } else {
            return m_newFixnum(getRuby(), getValue() / other.getLongValue());
        }
    }
    
    public RubyNumeric op_mod(RubyNumeric other) {
        if (other instanceof RubyFloat) {
            return RubyFloat.m_newFloat(getRuby(), getDoubleValue()).op_mod(other);
        } else if (other instanceof RubyBignum) {
            return RubyBignum.m_newBignum(getRuby(), getLongValue()).op_mod(other);
        } else {
            return m_newFixnum(getRuby(), getValue() % other.getLongValue());
        }
    }
    
    public RubyNumeric op_pow(RubyNumeric other) {
        if (other instanceof RubyFloat) {
            return RubyFloat.m_newFloat(getRuby(), getDoubleValue()).op_pow(other);
        } else {
            if (other.getLongValue() == 0) {
                return m_newFixnum(getRuby(), 1);
            } else if (other.getLongValue() == 1) {
                return this;
            } else if (other.getLongValue() > 1) {
                return RubyBignum.m_newBignum(getRuby(), getLongValue()).op_pow(other);
            } else {
                return RubyFloat.m_newFloat(getRuby(), getDoubleValue()).op_pow(other);
            }
        }
    }
    
    public RubyBoolean op_equal(RubyNumeric other) {
        if (other instanceof RubyFloat) {
            return RubyBoolean.m_newBoolean(getRuby(), getDoubleValue() == other.getDoubleValue());
        } else {
            return RubyBoolean.m_newBoolean(getRuby(), getLongValue() == other.getLongValue());
        }
    }
    
    public RubyNumeric op_cmp(RubyNumeric other) {
        if (other instanceof RubyFloat) {
            return RubyFloat.m_newFloat(getRuby(), getDoubleValue()).op_cmp(other);
        } else if (getLongValue() == other.getLongValue()) {
            return m_newFixnum(getRuby(), 0);
        } else if (getLongValue() > other.getLongValue()) {
            return m_newFixnum(getRuby(), 1);
        } else {
            return m_newFixnum(getRuby(), -1);
        }
    }
    
    public RubyBoolean op_gt(RubyNumeric other) {
        if (other instanceof RubyFloat) {
            return RubyFloat.m_newFloat(getRuby(), getDoubleValue()).op_gt(other);
        } else {
            return getLongValue() > other.getLongValue() ? getRuby().getTrue() : getRuby().getFalse();
        }
    }
    
    public RubyBoolean op_ge(RubyNumeric other) {
        if (other instanceof RubyFloat) {
            return RubyFloat.m_newFloat(getRuby(), getDoubleValue()).op_ge(other);
        } else {
            return getLongValue() >= other.getLongValue() ? getRuby().getTrue() : getRuby().getFalse();
        }
    }
    
    public RubyBoolean op_lt(RubyNumeric other) {
        if (other instanceof RubyFloat) {
            return RubyFloat.m_newFloat(getRuby(), getDoubleValue()).op_lt(other);
        } else {
            return getLongValue() < other.getLongValue() ? getRuby().getTrue() : getRuby().getFalse();
        }
    }
    
    public RubyBoolean op_le(RubyNumeric other) {
        if (other instanceof RubyFloat) {
            return RubyFloat.m_newFloat(getRuby(), getDoubleValue()).op_le(other);
        } else {
            return getLongValue() <= other.getLongValue() ? getRuby().getTrue() : getRuby().getFalse();
        }
    }
    
    public RubyString m_to_s() {
        return RubyString.m_newString(getRuby(), String.valueOf(getValue()));
    }
}