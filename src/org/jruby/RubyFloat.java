/*
 * RubyFloat.java - No description
 * Created on 04. Juli 2001, 22:53
 * 
 * Copyright (C) 2001, 2002 Jan Arne Petersen, Alan Moore, Benoit Cerrina
 * Copyright (C) 2002-2004 Thomas E Enebo
 * Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Alan Moore <alan_moore@gmx.net>
 * Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Thomas E Enebo <enebo@acm.org>
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

import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.marshal.MarshalStream;
import org.jruby.runtime.marshal.UnmarshalStream;

/**
 *
 * @author  jpetersen
 */
public class RubyFloat extends RubyNumeric {
    private final double value;

    public RubyFloat(Ruby runtime) {
        this(runtime, 0.0);
    }

    public RubyFloat(Ruby runtime, double value) {
        super(runtime, runtime.getClass("Float"));
        this.value = value;
    }

    public Class getJavaClass() {
        return Double.TYPE;
    }

    /** Getter for property value.
     * @return Value of property value.
     */
    public double getValue() {
        return this.value;
    }

    public double getDoubleValue() {
        return value;
    }

    public long getLongValue() {
        return (long) value;
    }

    public static RubyClass createFloatClass(Ruby runtime) {
        RubyClass result = runtime.defineClass("Float", runtime.getClasses().getNumericClass());
        CallbackFactory callbackFactory = runtime.callbackFactory(RubyFloat.class);
        
        result.defineMethod("+", callbackFactory.getMethod("op_plus", RubyNumeric.class));
        result.defineMethod("-", callbackFactory.getMethod("op_minus", RubyNumeric.class));
        result.defineMethod("*", callbackFactory.getMethod("op_mul", IRubyObject.class));
        result.defineMethod("/", callbackFactory.getMethod("op_div", RubyNumeric.class));
        result.defineMethod("%", callbackFactory.getMethod("op_mod", RubyNumeric.class));
        result.defineMethod("**", callbackFactory.getMethod("op_pow", RubyNumeric.class));
        result.defineMethod("ceil", callbackFactory.getMethod("ceil"));
        result.defineMethod("finite?", callbackFactory.getMethod("finite_p"));
        result.defineMethod("floor", callbackFactory.getMethod("floor"));
        result.defineMethod("hash", callbackFactory.getMethod("hash"));
        result.defineMethod("infinite?", callbackFactory.getMethod("infinite_p"));
        result.defineMethod("nan?", callbackFactory.getMethod("nan_p"));
        result.defineMethod("round", callbackFactory.getMethod("round"));
        result.defineMethod("to_i", callbackFactory.getMethod("to_i"));
        result.defineMethod("to_f", callbackFactory.getMethod("to_f"));
        result.defineMethod("to_s", callbackFactory.getMethod("to_s"));
        result.defineMethod("truncate", callbackFactory.getMethod("truncate"));

        result.getMetaClass().undefineMethod("new");
        result.defineSingletonMethod("induced_from", callbackFactory.getSingletonMethod("induced_from", IRubyObject.class));
        return result;
    }

    protected int compareValue(RubyNumeric other) {
        double otherVal = other.getDoubleValue();
        return getValue() > otherVal ? 1 : getValue() < otherVal ? -1 : 0;
    }

    public RubyFixnum hash() {
        return getRuntime().newFixnum(new Double(value).hashCode());
    }

    // Float methods (flo_*)

    public static RubyFloat newFloat(Ruby runtime, double value) {
        return new RubyFloat(runtime, value);
    }

    public static RubyFloat induced_from(IRubyObject recv, IRubyObject number) {
        if (number instanceof RubyFloat) {
            return (RubyFloat) number;
        } else if (number instanceof RubyInteger) {
            return (RubyFloat) number.callMethod("to_f");
        } else {
            throw recv.getRuntime().newTypeError("failed to convert " + number.getMetaClass() + " into Float");
        }
    }

    public RubyArray coerce(RubyNumeric other) {
        return getRuntime().newArray(newFloat(getRuntime(), other.getDoubleValue()), this);
    }

    public RubyInteger ceil() {
        double val = Math.ceil(getDoubleValue());

        if (val < RubyFixnum.MIN || val > RubyFixnum.MAX) {
            return RubyBignum.newBignum(getRuntime(), val);
        }
		return getRuntime().newFixnum((long) val);
    }

    public RubyInteger floor() {
        double val = Math.floor(getDoubleValue());

        if (val < Long.MIN_VALUE || val > Long.MAX_VALUE) {
            return RubyBignum.newBignum(getRuntime(), val);
        }
		return getRuntime().newFixnum((long) val);
    }

    public RubyInteger round() {
        double value = getDoubleValue();
        double decimal = value % 1;
        double round = Math.round(value);

        // Ruby rounds differently than java for negative numbers.
        if (value < 0 && decimal == -0.5) {
            round -= 1;
        }

        if (value < RubyFixnum.MIN || value > RubyFixnum.MAX) {
            return RubyBignum.newBignum(getRuntime(), round);
        }
        return getRuntime().newFixnum((long) round);
    }

    public RubyInteger truncate() {
        if (getDoubleValue() > 0.0) {
            return floor();
        } else if (getDoubleValue() < 0.0) {
            return ceil();
        } else {
            return RubyFixnum.zero(getRuntime());
        }
    }

    public RubyNumeric op_uminus() {
        return RubyFloat.newFloat(getRuntime(), -value);
    }

    public RubyNumeric op_plus(RubyNumeric other) {
        return RubyFloat.newFloat(getRuntime(), getDoubleValue() + other.getDoubleValue());
    }

    public RubyNumeric op_minus(RubyNumeric other) {
        return RubyFloat.newFloat(getRuntime(), getDoubleValue() - other.getDoubleValue());
    }

    // TODO: Coercion messages needed for all ops...Does this sink Anders
    // dispatching optimization?
    public RubyNumeric op_mul(IRubyObject other) {
    	if ((other instanceof RubyNumeric) == false) {
    		throw getRuntime().newTypeError(other.getMetaClass().getName() +
    			" can't be coerced into Float");
    	}
    	
        return ((RubyNumeric) other).multiplyWith(this);
    }

    public RubyNumeric multiplyWith(RubyNumeric other) {
        return other.multiplyWith(this);
    }

    public RubyNumeric multiplyWith(RubyFloat other) {
        return RubyFloat.newFloat(getRuntime(), getDoubleValue() * other.getDoubleValue());
    }

    public RubyNumeric multiplyWith(RubyInteger other) {
        return other.multiplyWith(this);
    }

    public RubyNumeric multiplyWith(RubyBignum other) {
        return other.multiplyWith(this);
    }
    
    public RubyNumeric op_div(RubyNumeric other) {
        return RubyFloat.newFloat(getRuntime(), getDoubleValue() / other.getDoubleValue());
    }

    public RubyNumeric op_mod(RubyNumeric other) {
        // Modelled after c ruby implementation (java /,% not same as ruby)
        double x = getDoubleValue();
        double y = other.getDoubleValue();
        double mod = x % y;

        if (mod < 0 && y > 0 || mod > 0 && y < 0) {
            mod += y;
        }

        return RubyFloat.newFloat(getRuntime(), mod);
    }

    public RubyNumeric op_pow(RubyNumeric other) {
        return RubyFloat.newFloat(getRuntime(), 
                Math.pow(getDoubleValue(), other.getDoubleValue()));
    }

    public RubyString to_s() {
        return getRuntime().newString("" + getValue());
    }

    public RubyFloat to_f() {
        return this;
    }

    public RubyInteger to_i() {
    	if (value > Integer.MAX_VALUE) {
    		return RubyBignum.newBignum(getRuntime(), getValue());
    	}
        return getRuntime().newFixnum(getLongValue());
    }

    public IRubyObject infinite_p() {
        if (getValue() == Double.POSITIVE_INFINITY) {
            return getRuntime().newFixnum(1);
        } else if (getValue() == Double.NEGATIVE_INFINITY) {
            return getRuntime().newFixnum(-1);
        } else {
            return getRuntime().getNil();
        }
    }

    public RubyBoolean finite_p() {
        if (! infinite_p().isNil()) {
            return getRuntime().getFalse();
        }
        if (nan_p().isTrue()) {
            return getRuntime().getFalse();
        }
        return getRuntime().getTrue();
    }

    public RubyBoolean nan_p() {
        return getRuntime().newBoolean(Double.isNaN(getValue()));
    }

    public RubyBoolean zero_p() {
        return getRuntime().newBoolean(getValue() == 0);
    }

	public void marshalTo(MarshalStream output) throws java.io.IOException {
		output.write('f');
		String strValue = this.toString();
		double value = getValue();
		if (Double.isInfinite(value)) {
			strValue = value < 0 ? "-inf" : "inf";
		} else if (Double.isNaN(value)) {
			strValue = "nan";
		}
		output.dumpString(strValue);
	}
	
    public static RubyFloat unmarshalFrom(UnmarshalStream input) throws java.io.IOException {
        return RubyFloat.newFloat(input.getRuntime(),
                                    Double.parseDouble(input.unmarshalString()));
    }
}
