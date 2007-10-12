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
 * Copyright (C) 2006 Ola Bini <ola@ologix.com>
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

import java.math.BigDecimal;
import org.jruby.anno.JRubyMethod;

import org.jruby.runtime.Arity;

import org.jruby.runtime.Block;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.MethodIndex;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * @author <a href="mailto:ola.bini@ki.se">Ola Bini</a>
 */
public class RubyBigDecimal extends RubyNumeric {
    private static final ObjectAllocator BIGDECIMAL_ALLOCATOR = new ObjectAllocator() {
        public IRubyObject allocate(Ruby runtime, RubyClass klass) {
            return new RubyBigDecimal(runtime, klass);
        }
    };
    
    public static RubyClass createBigDecimal(Ruby runtime) {
        RubyClass result = runtime.defineClass("BigDecimal",runtime.getNumeric(), BIGDECIMAL_ALLOCATOR);

        result.setConstant("ROUND_DOWN",RubyNumeric.int2fix(runtime,BigDecimal.ROUND_DOWN));
        result.setConstant("SIGN_POSITIVE_INFINITE",RubyNumeric.int2fix(runtime,3));
        result.setConstant("EXCEPTION_OVERFLOW",RubyNumeric.int2fix(runtime,1));
        result.setConstant("SIGN_POSITIVE_ZERO",RubyNumeric.int2fix(runtime,1));
        result.setConstant("EXCEPTION_ALL",RubyNumeric.int2fix(runtime,255));
        result.setConstant("ROUND_CEILING",RubyNumeric.int2fix(runtime,BigDecimal.ROUND_CEILING));
        result.setConstant("ROUND_UP",RubyNumeric.int2fix(runtime,BigDecimal.ROUND_UP));
        result.setConstant("SIGN_NEGATIVE_FINITE",RubyNumeric.int2fix(runtime,-2));
        result.setConstant("EXCEPTION_UNDERFLOW",RubyNumeric.int2fix(runtime, 4));
        result.setConstant("SIGN_NaN",RubyNumeric.int2fix(runtime, 0));
        result.setConstant("BASE",RubyNumeric.int2fix(runtime,10000));
        result.setConstant("ROUND_HALF_DOWN",RubyNumeric.int2fix(runtime,BigDecimal.ROUND_HALF_DOWN));
        result.setConstant("ROUND_MODE",RubyNumeric.int2fix(runtime,256));
        result.setConstant("SIGN_POSITIVE_FINITE",RubyNumeric.int2fix(runtime,2));
        result.setConstant("EXCEPTION_INFINITY",RubyNumeric.int2fix(runtime,1));
        result.setConstant("ROUND_HALF_EVEN",RubyNumeric.int2fix(runtime,BigDecimal.ROUND_HALF_EVEN));
        result.setConstant("ROUND_HALF_UP",RubyNumeric.int2fix(runtime,BigDecimal.ROUND_HALF_UP));
        result.setConstant("SIGN_NEGATIVE_INFINITE",RubyNumeric.int2fix(runtime,-3));
        result.setConstant("EXCEPTION_ZERODIVIDE",RubyNumeric.int2fix(runtime,1));
        result.setConstant("SIGN_NEGATIVE_ZERO",RubyNumeric.int2fix(runtime,-1));
        result.setConstant("EXCEPTION_NaN",RubyNumeric.int2fix(runtime,2));
        result.setConstant("ROUND_FLOOR",RubyNumeric.int2fix(runtime,BigDecimal.ROUND_FLOOR));

        CallbackFactory callbackFactory = runtime.callbackFactory(RubyBigDecimal.class);

        runtime.getKernel().defineModuleFunction("BigDecimal",callbackFactory.getOptSingletonMethod("newBigDecimal"));

        result.setClassVar("VpPrecLimit", RubyFixnum.zero(runtime));
        result.setClassVar("VpExceptionMode", RubyFixnum.zero(runtime));
        result.setClassVar("VpRoundingMode", RubyFixnum.zero(runtime));
        
        result.defineAnnotatedMethods(RubyBigDecimal.class);
        result.dispatcher = callbackFactory.createDispatcher(result);

        return result;
    }

    private BigDecimal value;

    public RubyBigDecimal(Ruby runtime, RubyClass klass) {
        super(runtime, klass);
    }

    public RubyBigDecimal(Ruby runtime, BigDecimal value) {
        super(runtime, runtime.getClass("BigDecimal"));
        this.value = value;
    }

    public static RubyBigDecimal newBigDecimal(IRubyObject recv, IRubyObject[] args, Block unusedBlock) {
        return newInstance(recv.getRuntime().getClass("BigDecimal"), args);
    }

    @JRubyMethod(name = "ver", meta = true)
    public static IRubyObject ver(IRubyObject recv) {
        return recv.getRuntime().newString("1.0.1");
    }

    @JRubyMethod(name = "_load", required = 1, frame = true, meta = true)
    public static IRubyObject _load(IRubyObject recv, IRubyObject p1, Block block) {
        // TODO: implement
        return recv.getRuntime().getNil();
    }

    @JRubyMethod(name = "double_fig", meta = true)
    public static IRubyObject double_fig(IRubyObject recv) {
        return recv.getRuntime().newFixnum(20);
    }
    
    @JRubyMethod(name = "limit", required = 1, meta = true)
    public static IRubyObject limit(IRubyObject recv, IRubyObject arg1) {
        RubyModule c = (RubyModule)recv;
        IRubyObject nCur = c.getClassVar("VpPrecLimit");

        if (arg1.isNil()) {
            return nCur;
        }

        c.setClassVar("VpPrecLimit",arg1);

        return nCur;
    }

    @JRubyMethod(name = "mode", required = 1, optional = 1, meta = true)
    public static IRubyObject mode(IRubyObject recv, IRubyObject[] args) {
        RubyModule c = (RubyModule)recv;
        
        args = Arity.scanArgs(recv.getRuntime(), args, 1, 1);
        
        IRubyObject mode = args[0];
        IRubyObject value = args[1];
        
        if (!(mode instanceof RubyFixnum)) {
            throw recv.getRuntime().newTypeError("wrong argument type " + mode.getMetaClass() + " (expected Fixnum)");
        }
        
        long longMode = ((RubyFixnum)mode).getLongValue();
        long EXCEPTION_ALL = ((RubyFixnum)recv.getRuntime().getModule("BigDecimal").getConstant("EXCEPTION_ALL")).getLongValue();
        if ((longMode & EXCEPTION_ALL) != 0) {     
            if (value.isNil()) {
                return c.getClassVar("VpExceptionMode");
            }
            if (!(value.isNil()) && !(value instanceof RubyBoolean)) {
                throw recv.getRuntime().newTypeError("second argument must be true or false");
            }

            RubyFixnum currentExceptionMode = (RubyFixnum)c.getClassVar("VpExceptionMode");
            RubyFixnum newExceptionMode = new RubyFixnum(recv.getRuntime(), currentExceptionMode.getLongValue());
            
            RubyFixnum EXCEPTION_INFINITY = (RubyFixnum)recv.getRuntime().getModule("BigDecimal").getConstant("EXCEPTION_INFINITY");
            if ((longMode & EXCEPTION_INFINITY.getLongValue()) != 0) {
                newExceptionMode = (value.isTrue()) ? (RubyFixnum)currentExceptionMode.callCoerced("|", EXCEPTION_INFINITY)
                        : (RubyFixnum)currentExceptionMode.callCoerced("&", new RubyFixnum(recv.getRuntime(), ~(EXCEPTION_INFINITY).getLongValue()));
            }
            
            RubyFixnum EXCEPTION_NaN = (RubyFixnum)recv.getRuntime().getModule("BigDecimal").getConstant("EXCEPTION_NaN");
            if ((longMode & EXCEPTION_NaN.getLongValue()) != 0) {
                newExceptionMode = (value.isTrue()) ? (RubyFixnum)currentExceptionMode.callCoerced("|", EXCEPTION_NaN)
                        : (RubyFixnum)currentExceptionMode.callCoerced("&", new RubyFixnum(recv.getRuntime(), ~(EXCEPTION_NaN).getLongValue()));
            }
            c.setClassVar("VpExceptionMode", newExceptionMode);
            return newExceptionMode;
        }
        
        long ROUND_MODE = ((RubyFixnum)recv.getRuntime().getModule("BigDecimal").getConstant("ROUND_MODE")).getLongValue();
        if (longMode == ROUND_MODE) {
            if (value.isNil()) {
                return c.getClassVar("VpRoundingMode");
            }
            if (!(value instanceof RubyFixnum)) {
                throw recv.getRuntime().newTypeError("wrong argument type " + mode.getMetaClass() + " (expected Fixnum)");
            }
            
            RubyFixnum roundingMode = (RubyFixnum)value;
            if (roundingMode == recv.getRuntime().getModule("BigDecimal").getConstant("ROUND_UP") ||
                    roundingMode == recv.getRuntime().getModule("BigDecimal").getConstant("ROUND_DOWN") ||
                    roundingMode == recv.getRuntime().getModule("BigDecimal").getConstant("ROUND_FLOOR") ||
                    roundingMode == recv.getRuntime().getModule("BigDecimal").getConstant("ROUND_CEILING") ||
                    roundingMode == recv.getRuntime().getModule("BigDecimal").getConstant("ROUND_HALF_UP") ||
                    roundingMode == recv.getRuntime().getModule("BigDecimal").getConstant("ROUND_HALF_DOWN") ||
                    roundingMode == recv.getRuntime().getModule("BigDecimal").getConstant("ROUND_HALF_EVEN")) {
                c.setClassVar("VpRoundingMode", roundingMode);
            } else {
                throw recv.getRuntime().newTypeError("invalid rounding mode");
            }
            return c.getClassVar("VpRoundingMode");
        }
        throw recv.getRuntime().newTypeError("first argument for BigDecimal#mode invalid");
    }

    private RubyBigDecimal getVpValue(IRubyObject v, boolean must) {
        if(v instanceof RubyBigDecimal) {
            return (RubyBigDecimal)v;
        } else if(v instanceof RubyFixnum || v instanceof RubyBignum) {
            String s = v.toString();
            return newInstance(getRuntime().getClass("BigDecimal"),new IRubyObject[]{getRuntime().newString(s)});
        }
        if(must) {
            String err;
            if (isImmediate()) {
                err = RubyString.objAsString(callMethod(getRuntime().getCurrentContext(), "inspect")).toString();
            } else {
                err = getMetaClass().getBaseName();
            }
            throw getRuntime().newTypeError(err + " can't be coerced into BigDecimal");
        }
        return null;
    }

    @JRubyMethod(name = "new", required = 1, optional = 1, meta = true)
    public static RubyBigDecimal newInstance(IRubyObject recv, IRubyObject[] args) {
        BigDecimal decimal;
        if (Arity.checkArgumentCount(recv.getRuntime(), args, 1, 2) == 0) { 
            decimal = new BigDecimal(0);
        } else {
            try {
                decimal = new BigDecimal(args[0].convertToString().toString());
            } catch(NumberFormatException e) {
                decimal = new BigDecimal(0);
            }
        }
        return new RubyBigDecimal(recv.getRuntime(), decimal);
    }

    private RubyBigDecimal setResult() {
        return setResult(0);
    }

    private RubyBigDecimal setResult(int scale) {
        int prec = RubyFixnum.fix2int(getRuntime().getClass("BigDecimal").getClassVar("VpPrecLimit"));
        int prec2 = Math.max(scale,prec);
        if(prec2 > 0 && this.value.scale() > (prec2-exp())) {
            this.value = this.value.setScale(prec2-exp(),BigDecimal.ROUND_HALF_UP);
        }
        return this;
    }
    
    @JRubyMethod(name = "hash")
    public RubyFixnum hash() {
        return getRuntime().newFixnum(value.hashCode());
    }

    @JRubyMethod(name = "%", name2 = "modulo", required = 1)
    public IRubyObject op_mod(IRubyObject arg) {
        RubyBigDecimal val = getVpValue(arg,false);
        return new RubyBigDecimal(getRuntime(),this.value.divideAndRemainder(val.value)[1]).setResult();
    }

    @JRubyMethod(name = "*", required = 1)
    public IRubyObject op_mul(IRubyObject arg) {
        RubyBigDecimal val = getVpValue(arg, false);
        if(val == null) {
            return callCoerced("*", arg);
        }

        return new RubyBigDecimal(getRuntime(),value.multiply(val.value)).setResult();
    }

    @JRubyMethod(name = "mult", required = 2)
    public IRubyObject mult2(IRubyObject b, IRubyObject n) {
        RubyBigDecimal val = getVpValue(b,false);
        if(val == null) {
            return callCoerced("*",b);
        }

        return new RubyBigDecimal(getRuntime(),value.multiply(val.value)).setResult();
    }
    
    @JRubyMethod(name = "**", name2 = "power", required = 1)
    public IRubyObject op_pow(IRubyObject arg) {
        if (!(arg instanceof RubyFixnum)) {
            throw getRuntime().newTypeError("wrong argument type " + arg.getMetaClass() + " (expected Fixnum)");
        }
        
        BigDecimal val = value;
        
        int times = RubyNumeric.fix2int(arg.convertToInteger());
        int sign = 0;
        if (times < 0) {
            sign = -1;
            times = -times;
        }
        
        BigDecimal result = BigDecimal.ONE;
        while (times > 0) {
            if (times % 2 != 0) {
                result = result.multiply(val);
                times -= 1;
            }
            val = val.multiply(val);
            times /= 2;
        }
        
        if (sign == -1) {
            result = BigDecimal.ONE.divide(result);
        }
        
        return new RubyBigDecimal(getRuntime(),result).setResult();
    }

    @JRubyMethod(name = "+", required = 1)
    public IRubyObject op_plus(IRubyObject arg) {
        RubyBigDecimal val = getVpValue(arg, false);
        if(val == null) {
            return callCoerced("+", arg);
        }
        return new RubyBigDecimal(getRuntime(),value.add(val.value)).setResult();
    }

    @JRubyMethod(name = "add", required = 2)
    public IRubyObject add2(IRubyObject b, IRubyObject n) {
        RubyBigDecimal val = getVpValue(b, false);
        if(val == null) {
            return callCoerced("+", b);
        }
        return new RubyBigDecimal(getRuntime(),value.add(val.value)).setResult();
    }
    
    @JRubyMethod(name = "+@")
    public IRubyObject op_uplus() {
        return this;
    }
    
    @JRubyMethod(name = "-", required = 1)
    public IRubyObject op_minus(IRubyObject arg) {
        RubyBigDecimal val = getVpValue(arg, false);
        if(val == null) {
            return callCoerced("-", arg);
        }
        return new RubyBigDecimal(getRuntime(),value.subtract(val.value)).setResult();
    }

    @JRubyMethod(name = "sub", required = 2)
    public IRubyObject sub2(IRubyObject b, IRubyObject n) {
        RubyBigDecimal val = getVpValue(b, false);
        if(val == null) {
            return callCoerced("-", b);
        }
        return new RubyBigDecimal(getRuntime(),value.subtract(val.value)).setResult();
    }
    
    @JRubyMethod(name = "-@")
    public IRubyObject op_uminus() {
        return new RubyBigDecimal(getRuntime(), value.negate());
    }
    
    @JRubyMethod(name = "/", name2 = "div", name3 = "quo", required = 1, optional = 1)
    public IRubyObject op_div(IRubyObject[] args) {
        int scale = 0;
        if(Arity.checkArgumentCount(getRuntime(), args,1,2) == 2) {
            scale = RubyNumeric.fix2int(args[1]);
        }

        RubyBigDecimal val = getVpValue(args[0],false);
        if(val == null) {
            return callCoerced("/",args[0]);
        }

        if(scale == 0) {
            return new RubyBigDecimal(getRuntime(),value.divide(val.value,200,BigDecimal.ROUND_HALF_UP)).setResult();
        } else {
            return new RubyBigDecimal(getRuntime(),value.divide(val.value,200,BigDecimal.ROUND_HALF_UP)).setResult(scale);
        }
    }
    
    private IRubyObject cmp(IRubyObject r, char op) {
        int e = 0;
        RubyBigDecimal rb = getVpValue(r,false);
        if(rb == null) {
            IRubyObject ee = callCoerced("<=>",r);
            if(ee.isNil()) {
                return getRuntime().getNil();
            }
            e = RubyNumeric.fix2int(ee);
        } else {
            e = value.compareTo(rb.value);
        }
        switch(op) {
        case '*': return getRuntime().newFixnum(e);
        case '=': return (e==0)?getRuntime().getTrue():getRuntime().getFalse();
        case '!': return (e!=0)?getRuntime().getTrue():getRuntime().getFalse();
        case 'G': return (e>=0)?getRuntime().getTrue():getRuntime().getFalse();
        case '>': return (e> 0)?getRuntime().getTrue():getRuntime().getFalse();
        case 'L': return (e<=0)?getRuntime().getTrue():getRuntime().getFalse();
        case '<': return (e< 0)?getRuntime().getTrue():getRuntime().getFalse();
        }
        return getRuntime().getNil();
    }

    @JRubyMethod(name = "<=>", required = 1)
    public IRubyObject op_cmp(IRubyObject arg) {
        return cmp(arg,'*');
    }

    @JRubyMethod(name = "eql?", required = 1)
    public IRubyObject eql_p(IRubyObject arg) {
        return cmp(arg,'=');
    }

    @JRubyMethod(name = "<", required = 1)
    public IRubyObject op_lt(IRubyObject arg) {
        return cmp(arg,'<');
    }

    @JRubyMethod(name = "<=", required = 1)
    public IRubyObject op_le(IRubyObject arg) {
        return cmp(arg,'L');
    }

    @JRubyMethod(name = ">", required = 1)
    public IRubyObject op_gt(IRubyObject arg) {
        return cmp(arg,'>');
    }

    @JRubyMethod(name = ">=", required = 1)
    public IRubyObject op_ge(IRubyObject arg) {
        return cmp(arg,'G');
    }

    @JRubyMethod(name = "abs")
    public IRubyObject abs() {
        return new RubyBigDecimal(getRuntime(),value.abs()).setResult();
    }

    @JRubyMethod(name = "ceil", optional = 1)
    public IRubyObject ceil(IRubyObject[] args) {
        throw new RuntimeException("BigDecimal#ceil is not implemented");
    }

    @JRubyMethod(name = "coerce", required = 1)
    public IRubyObject coerce(IRubyObject other) {
        IRubyObject obj;
        if(other instanceof RubyFloat) {
            obj = getRuntime().newArray(other,to_f());
        } else {
            obj = getRuntime().newArray(getVpValue(other, true),this);
        }
        return obj;
    }

    public double getDoubleValue() { return value.doubleValue(); }
    public long getLongValue() { return value.longValue(); }

    public RubyNumeric multiplyWith(RubyInteger value) { 
        return (RubyNumeric)op_mul(value);
    }

    public RubyNumeric multiplyWith(RubyFloat value) { 
        return (RubyNumeric)op_mul(value);
    }

    public RubyNumeric multiplyWith(RubyBignum value) { 
        return (RubyNumeric)op_mul(value);
    }

    @JRubyMethod(name = "divmod", required = 1)
    public IRubyObject divmod(IRubyObject arg) {
        System.err.println("unimplemented: divmod");
        // TODO: implement
        return getRuntime().getNil();
    }

    @JRubyMethod(name = "exponent")
    public IRubyObject exponent() {
        return getRuntime().newFixnum(exp());
    }

    private int exp() {
        return value.abs().unscaledValue().toString().length() - value.abs().scale();
    }

    @JRubyMethod(name = "finite?")
    public IRubyObject finite_p() {
        System.err.println("unimplemented: finite?");
        // TODO: implement correctly
        return getRuntime().getTrue();
    }

    @JRubyMethod(name = "fix")
    public IRubyObject fix() {
        System.err.println("unimplemented: fix");
        // TODO: implement correctly
        return this;
    }

    @JRubyMethod(name = "floor", optional = 1)
    public IRubyObject floor(IRubyObject[]args) {
        throw new RuntimeException("BigDecimal#floor is not implemented");
    }
 
    @JRubyMethod(name = "frac")
    public IRubyObject frac() {
        System.err.println("unimplemented: frac");
        // TODO: implement correctly
        return this;
    }

    @JRubyMethod(name = "infinite?")
    public IRubyObject infinite_p() {
        System.err.println("unimplemented: infinite?");
        // TODO: implement correctly
        return getRuntime().getFalse();
    }

    @JRubyMethod(name = "inspect")
    public IRubyObject inspect() {
        StringBuffer val = new StringBuffer("#<BigDecimal:").append(Integer.toHexString(System.identityHashCode(this))).append(",");
        val.append("'").append(this.callMethod(getRuntime().getCurrentContext(), MethodIndex.TO_S, "to_s")).append("'").append(",");
        int len = value.abs().unscaledValue().toString().length();
        int pow = len/4;
        val.append(len).append("(").append((pow+1)*4).append(")").append(">");
        return getRuntime().newString(val.toString());
    }

    @JRubyMethod(name = "nan?")
    public IRubyObject nan_p() {
        System.err.println("unimplemented: nan?");
        // TODO: implement correctly
        return getRuntime().getFalse();
    }

    @JRubyMethod(name = "nonzero?")
    public IRubyObject nonzero_p() {
        return value.signum() != 0 ? getRuntime().getTrue() : getRuntime().getFalse();
    }
 
    @JRubyMethod(name = "precs")
    public IRubyObject precs() {
        System.err.println("unimplemented: precs");
        // TODO: implement
        return getRuntime().getNil();
    }

    @JRubyMethod(name = "remainder", required = 1)
    public IRubyObject remainder(IRubyObject arg) {
        System.err.println("unimplemented: remainder");
        // TODO: implement
        return this;
    }

    @JRubyMethod(name = "round", optional = 2)
    public IRubyObject round(IRubyObject[] args) {
        int scale = args.length > 0 ? num2int(args[0]) : 0;
        int mode = (args.length > 1) ? javaRoundingModeFromRubyRoundingMode(args[1]) : BigDecimal.ROUND_HALF_UP;
        // JRUBY-914: Java 1.4 BigDecimal does not allow a negative scale, so we have to simulate it
        if (scale < 0) {
          // shift the decimal point just to the right of the digit to be rounded to (divide by 10**(abs(scale)))
          // -1 -> 10's digit, -2 -> 100's digit, etc.
          BigDecimal normalized = value.movePointRight(scale);
          // ...round to that digit
          BigDecimal rounded = normalized.setScale(0,mode);
          // ...and shift the result back to the left (multiply by 10**(abs(scale)))
          return new RubyBigDecimal(getRuntime(), rounded.movePointLeft(scale));
        } else {
          return new RubyBigDecimal(getRuntime(), value.setScale(scale, mode));
        }
    }

    //this relies on the Ruby rounding enumerations == Java ones, which they (currently) all are
    private int javaRoundingModeFromRubyRoundingMode(IRubyObject arg) {
      return num2int(arg);
    }
    
    @JRubyMethod(name = "sign")
    public IRubyObject sign() {
        System.err.println("unimplemented: sign");
        // TODO: implement correctly
        return getRuntime().newFixnum(value.signum());
    }

    @JRubyMethod(name = "split")
    public IRubyObject split() {
        System.err.println("unimplemented: split");
        // TODO: implement
        return getRuntime().getNil();
    }

    @JRubyMethod(name = "sqrt", required = 1)
    public IRubyObject sqrt(IRubyObject arg) {
        System.err.println("unimplemented: sqrt");
        // TODO: implement correctly
        return new RubyBigDecimal(getRuntime(),new BigDecimal(Math.sqrt(value.doubleValue()))).setResult();
    }

    @JRubyMethod(name = "to_f")
    public IRubyObject to_f() {
        return RubyFloat.newFloat(getRuntime(),value.doubleValue());
    }

    @JRubyMethod(name = "to_i")
    public IRubyObject to_i() {
        return RubyNumeric.int2fix(getRuntime(),value.longValue());
    }

    @JRubyMethod(name = "to_int")
    public IRubyObject to_int() {
        // TODO: implement to handle infinity and stuff
        return RubyNumeric.int2fix(getRuntime(),value.longValue());
    }

    private String removeTrailingZeroes(String in) {
        while(in.length() > 0 && in.charAt(in.length()-1)=='0') {
            in = in.substring(0,in.length()-1);
        }
        return in;
    }

    private String toSpecialString(BigDecimal abs) {
      if (abs.compareTo(BigDecimal.valueOf(0))== 0) {
        return "0.0";
      }
      //TODO: match the MRI code below!
      //TODO: refactor the overly-long branches in to_s so we can reuse the sign processing here
      return null;
//      if(VpIsNaN(a)) {
//          sprintf(psz,SZ_NaN);
//          return 1;
//      }
//
//      if(VpIsPosInf(a)) {
//          if(fPlus==1) {
//             *psz++ = ' ';
//          } else if(fPlus==2) {
//             *psz++ = '+';
//          }
//          sprintf(psz,SZ_INF);
//          return 1;
//      }
//      if(VpIsNegInf(a)) {
//          sprintf(psz,SZ_NINF);
//          return 1;
//      }
//      if(VpIsZero(a)) {
//          if(VpIsPosZero(a)) {
//              if(fPlus==1)      sprintf(psz, " 0.0");
//              else if(fPlus==2) sprintf(psz, "+0.0");
//              else              sprintf(psz, "0.0");
//          } else    sprintf(psz, "-0.0");
//          return 1;
//      }
//      return 0;

    }

    @JRubyMethod(name = "to_s", optional = 1)
    public IRubyObject to_s(IRubyObject[] args) {
        boolean engineering = true;
        boolean pos_sign = false;
        boolean pos_space = false;
        int groups = 0;

        if(args.length != 0 && !args[0].isNil()) {
            String format = args[0].toString();
            int start = 0;
            int end = format.length();
            if(format.length() > 0 && format.charAt(0) == '+') {
                pos_sign = true;
                start++;
            } else if(format.length() > 0 && format.charAt(0) == ' ') {
                pos_sign = true;
                pos_space = true;
                start++;
            }
            if(format.length() > 0 && format.charAt(format.length()-1) == 'F') {
                engineering = false;
                end--;
            } else if(format.length() > 0 && format.charAt(format.length()-1) == 'E') {
                engineering = true;
                end--;
            }
            String nums = format.substring(start,end);
            if(nums.length()>0) {
                groups = Integer.parseInt(nums);
            }
        }

        String out = null;
        BigDecimal abs = value.abs();
        String unscaled = abs.unscaledValue().toString();

        //not beautiful, but parallel to MRI's VpToSpecialString
        if (null != (out = toSpecialString(abs))) {
          return getRuntime().newString(out);
        }
        if(engineering) {
            int exponent = exp();
            int signum = value.signum();
            StringBuffer build = new StringBuffer();
            build.append(signum == -1 ? "-" : (signum == 1 ? (pos_sign ? (pos_space ? " " : "+" ) : "") : ""));
            build.append("0.");
            if(0 == groups) {
                String s = removeTrailingZeroes(unscaled);
                if("".equals(s)) {
                    build.append("0");
            } else {
                    build.append(s);
                }
            } else {
                int index = 0;
                String sep = "";
                while(index < unscaled.length()) {
                    int next = index+groups;
                    if(next > unscaled.length()) {
                        next = unscaled.length();
                    }
                    build.append(sep).append(unscaled.substring(index,next));
                    sep = " ";
                    index += groups;
                }
            }
            build.append("E").append(exponent);
            out = build.toString();
        } else {
            int ix = abs.toString().indexOf('.');
            String whole = unscaled;
            String after = null;
            if(ix != -1) {
                whole = unscaled.substring(0,ix);
                after = unscaled.substring(ix);
            }
            int signum = value.signum();
            StringBuffer build = new StringBuffer();
            build.append(signum == -1 ? "-" : (signum == 1 ? (pos_sign ? (pos_space ? " " : "+" ) : "") : ""));
            if(0 == groups) {
                build.append(whole);
                if(null != after) {
                    build.append(".").append(after);
                }
            } else {
                int index = 0;
                String sep = "";
                while(index < whole.length()) {
                    int next = index+groups;
                    if(next > whole.length()) {
                        next = whole.length();
                    }
                    build.append(sep).append(whole.substring(index,next));
                    sep = " ";
                    index += groups;
                }
                if(null != after) {
                    System.out.println("AFTER: " + after);
                    build.append(".");
                    index = 0;
                    sep = "";
                    while(index < after.length()) {
                        int next = index+groups;
                        if(next > after.length()) {
                            next = after.length();
                        }
                        build.append(sep).append(after.substring(index,next));
                        sep = " ";
                        index += groups;
                    }
                }
            }
            out = build.toString();
        }

        return getRuntime().newString(out);
    }

    @JRubyMethod(name = "truncate", optional = 1)
    public IRubyObject truncate(IRubyObject[] args) {
        throw new RuntimeException("BigDecimal#truncate is not implemented");
    }

    @JRubyMethod(name = "zero?")
    public IRubyObject zero_p() {
        return value.signum() == 0 ? getRuntime().getTrue() : getRuntime().getFalse();
    }
}// RubyBigdecimal
