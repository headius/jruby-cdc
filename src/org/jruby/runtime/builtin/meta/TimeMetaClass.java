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
 * Copyright (C) 2001 Chad Fowler <chadfowler@chadfowler.com>
 * Copyright (C) 2001-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004 Joey Gibson <joey@joeygibson.com>
 * Copyright (C) 2004 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2006 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2006 Ola Bini <ola.bini@ki.se>
 * Copyright (C) 2006 Miguel Covarrubias <mlcovarrubias@gmail.com>
 * Copyright (C) 2007 Nick Sieger <nicksieger@gmail.com>
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
package org.jruby.runtime.builtin.meta;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyFloat;
import org.jruby.RubyNumeric;
import org.jruby.RubyString;
import org.jruby.RubyTime;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.collections.SinglyLinkedList;

public class TimeMetaClass extends ObjectMetaClass {
    public TimeMetaClass(Ruby runtime) {
        super("Time", RubyTime.class, runtime.getObject(), TIME_ALLOCATOR);
    }

    public TimeMetaClass(String name, RubyClass superClass, ObjectAllocator allocator, SinglyLinkedList parentCRef) {
        super(name, RubyTime.class, superClass, allocator, parentCRef);
    }
    
    protected class TimeMeta extends Meta {
        protected void initializeClass() {
            includeModule(getRuntime().getModule("Comparable"));
    
            defineSingletonMethod("new", Arity.noArguments(), "s_new"); 
            defineSingletonMethod("now", Arity.noArguments(), "s_new"); 
            defineFastSingletonMethod("at", Arity.optional(), "new_at"); 
            defineFastSingletonMethod("local", Arity.optional(), "new_local"); 
            defineFastSingletonMethod("mktime", Arity.optional(), "new_local"); 
            defineFastSingletonMethod("utc", Arity.optional(), "new_utc"); 
            defineFastSingletonMethod("gm", Arity.optional(), "new_utc"); 
            defineSingletonMethod("_load", Arity.singleArgument(), "s_load"); 
            
            // To override Comparable with faster String ones
            defineFastMethod(">=", Arity.singleArgument(), "op_ge");
            defineFastMethod(">", Arity.singleArgument(), "op_gt");
            defineFastMethod("<=", Arity.singleArgument(), "op_le");
            defineFastMethod("<", Arity.singleArgument(), "op_lt");
            
            defineFastMethod("===", Arity.singleArgument(), "same2");
            defineFastMethod("+", Arity.singleArgument(), "op_plus"); 
            defineFastMethod("-", Arity.singleArgument(), "op_minus"); 
            defineFastMethod("<=>", Arity.singleArgument(), "op_cmp");
            defineFastMethod("asctime", Arity.noArguments()); 
            defineFastMethod("mday", Arity.noArguments()); 
            defineAlias("day", "mday"); 
            defineAlias("ctime", "asctime");
            defineFastMethod("sec", Arity.noArguments()); 
            defineFastMethod("min", Arity.noArguments()); 
            defineFastMethod("hour", Arity.noArguments()); 
            defineFastMethod("month", Arity.noArguments());
            defineAlias("mon", "month"); 
            defineFastMethod("year", Arity.noArguments()); 
            defineFastMethod("wday", Arity.noArguments()); 
            defineFastMethod("yday", Arity.noArguments());
            defineFastMethod("isdst", Arity.noArguments());
            defineAlias("dst?", "isdst");
            defineFastMethod("zone", Arity.noArguments()); 
            defineFastMethod("to_a", Arity.noArguments()); 
            defineFastMethod("to_f", Arity.noArguments()); 
            defineFastMethod("succ", Arity.noArguments()); 
            defineFastMethod("to_i", Arity.noArguments());
            defineFastMethod("to_s", Arity.noArguments()); 
            defineFastMethod("inspect", Arity.noArguments()); 
            defineFastMethod("strftime", Arity.singleArgument()); 
            defineFastMethod("usec",  Arity.noArguments());
            defineAlias("tv_usec", "usec"); 
            defineAlias("tv_sec", "to_i"); 
            defineFastMethod("gmtime", Arity.noArguments());   
            defineAlias("utc", "gmtime"); 
            defineFastMethod("gmt?", Arity.noArguments(), "gmt");
            defineAlias("utc?", "gmt?");
            defineAlias("gmtime?", "gmt?");
            defineFastMethod("localtime", Arity.noArguments()); 
            defineFastMethod("hash", Arity.noArguments()); 
            defineFastMethod("initialize_copy", Arity.singleArgument()); 
            defineMethod("_dump", Arity.optional(),"dump"); 
            defineFastMethod("gmt_offset", Arity.noArguments());
            defineAlias("gmtoff", "gmt_offset");
            defineAlias("utc_offset", "gmt_offset");
            defineFastMethod("getgm", Arity.noArguments());
            defineFastMethod("getlocal", Arity.noArguments());
            defineAlias("getutc", "getgm");
        }
    };
    
    protected Meta getMeta() {
        return new TimeMeta();
    }
        
    public RubyClass newSubClass(String name, SinglyLinkedList parentCRef) {
        return new TimeMetaClass(name, this, TIME_ALLOCATOR, parentCRef);
    }

    private static ObjectAllocator TIME_ALLOCATOR = new ObjectAllocator() {
        public IRubyObject allocate(Ruby runtime, RubyClass klass) {
            RubyTime instance = new RubyTime(runtime, klass);

            instance.setMetaClass(klass);

            return instance;
        }
    };
    
    public IRubyObject s_new(Block block) {
        RubyTime time = new RubyTime(getRuntime(), this);
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTime(new Date());
        time.setJavaCalendar(cal);
        return time;
    }

    public IRubyObject new_at(IRubyObject[] args) {
        int len = checkArgumentCount(args, 1, 2);

        Calendar cal = Calendar.getInstance(); 
        RubyTime time = new RubyTime(getRuntime(), this, cal);

        if (args[0] instanceof RubyTime) {
            ((RubyTime) args[0]).updateCal(cal);
        } else {
            long seconds = RubyNumeric.num2long(args[0]);
            long millisecs = 0;
            long microsecs = 0;
            if (len > 1) {
                long tmp = RubyNumeric.num2long(args[1]);
                millisecs = tmp / 1000;
                microsecs = tmp % 1000;
            }
            else {
                // In the case of two arguments, MRI will discard the portion of
                // the first argument after a decimal point (i.e., "floor").
                // However in the case of a single argument, any portion after
                // the decimal point is honored.
                if (args[0] instanceof RubyFloat) {
                    double dbl = ((RubyFloat) args[0]).getDoubleValue();
                    long micro = (long) ((dbl - seconds) * 1000000);
                    millisecs = micro / 1000;
                    microsecs = micro % 1000;
                }
            }
            time.setUSec(microsecs);
            cal.setTimeInMillis(seconds * 1000 + millisecs);
        }

        time.callInit(args, Block.NULL_BLOCK);

        return time;
    }

    public RubyTime new_local(IRubyObject[] args) {
        return createTime(args, false);
    }

    public RubyTime new_utc(IRubyObject[] args) {
        return createTime(args, true);
    }

    public RubyTime s_load(IRubyObject from, Block block) {
        return s_mload((RubyTime) s_new(block), from);
    }

    protected RubyTime s_mload(RubyTime time, IRubyObject from) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.setTimeZone(TimeZone.getTimeZone(RubyTime.UTC));
        byte[] fromAsBytes = null;
        fromAsBytes = from.convertToString().getBytes();
        if(fromAsBytes.length != 8) {
            throw getRuntime().newTypeError("marshaled time format differ");
        }
        int p=0;
        int s=0;
        for(int i = 0; i < 4; i++) {
            p |= ((int)fromAsBytes[i] & 0xFF) << (8*i);
        }
        for(int i = 4; i < 8; i++) {
            s |= ((int)fromAsBytes[i] & 0xFF) << (8*(i-4));
        }
        if((p & (1<<31)) == 0) {
            calendar.setTimeInMillis(p * 1000L + s);
        } else {
            p &= ~(1<<31);
            calendar.set(Calendar.YEAR,((p >>> 14) & 0xFFFF)+1900);
            calendar.set(Calendar.MONTH,((p >>> 10) & 0xF));
            calendar.set(Calendar.DAY_OF_MONTH,((p >>> 5)  & 0x1F));
            calendar.set(Calendar.HOUR_OF_DAY,(p & 0x1F));
            calendar.set(Calendar.MINUTE,((s >>> 26) & 0x3F));
            calendar.set(Calendar.SECOND,((s >>> 20) & 0x3F));
            calendar.set(Calendar.MILLISECOND,(s & 0xFFFFF));
        }
        time.setJavaCalendar(calendar);
        return time;
    }
    
    private static final String[] months = {"jan", "feb", "mar", "apr", "may", "jun",
                                            "jul", "aug", "sep", "oct", "nov", "dec"};
    private static final long[] time_min = {1, 0, 0, 0, 0};
    private static final long[] time_max = {31, 23, 59, 60, Long.MAX_VALUE};

    private RubyTime createTime(IRubyObject[] args, boolean gmt) {
        int len = 6;
        if (args.length == 10) {
            args = new IRubyObject[] { args[5], args[4], args[3], args[2], args[1], args[0] };
        } else {
            len = checkArgumentCount(args, 1, 7);
        }
        ThreadContext tc = getRuntime().getCurrentContext();
        if(!(args[0] instanceof RubyNumeric)) {
            args[0] = args[0].callMethod(tc,"to_i");
        }
        int year = (int)RubyNumeric.num2long(args[0]);
        int month = 0;
        
        if (len > 1) {
            if (!args[1].isNil()) {
                if (args[1] instanceof RubyString) {
                    month = -1;
                    for (int i = 0; i < 12; i++) {
                        if (months[i].equalsIgnoreCase(args[1].toString())) {
                            month = i;
                        }
                    }
                    if (month == -1) {
                        try {
                            month = Integer.parseInt(args[1].toString()) - 1;
                        } catch (NumberFormatException nfExcptn) {
                            throw getRuntime().newArgumentError("Argument out of range.");
                        }
                    }
                } else {
                    month = (int)RubyNumeric.num2long(args[1]) - 1;
                }
            }
            if (0 > month || month > 11) {
                throw getRuntime().newArgumentError("Argument out of range.");
            }
        }

        int[] int_args = { 1, 0, 0, 0, 0 };

        for (int i = 0; len > i + 2; i++) {
            if (!args[i + 2].isNil()) {
                if(!(args[i+2] instanceof RubyNumeric)) {
                    args[i+2] = args[i+2].callMethod(tc,"to_i");
                }
                int_args[i] = (int)RubyNumeric.num2long(args[i + 2]);
                if (time_min[i] > int_args[i] || int_args[i] > time_max[i]) {
                    throw getRuntime().newArgumentError("Argument out of range.");
                }
            }
        }

        Calendar cal = gmt ? Calendar.getInstance(TimeZone.getTimeZone(RubyTime.UTC)) : 
            Calendar.getInstance(); 
        cal.set(year, month, int_args[0], int_args[1], int_args[2], int_args[3]);
        cal.set(Calendar.MILLISECOND, int_args[4] / 1000);
        if (cal.getTimeInMillis() < 0) {
            throw getRuntime().newArgumentError("time out of range");
        }
        RubyTime time = new RubyTime(getRuntime(), (RubyClass) this, cal);
        time.setUSec(int_args[4] % 1000);

        time.callInit(args, Block.NULL_BLOCK);

        return time;
    }
}
