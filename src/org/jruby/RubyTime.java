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

import java.lang.ref.SoftReference;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.Block;
import org.jruby.runtime.ClassIndex;
import org.jruby.runtime.MethodIndex;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;
import org.jruby.util.RubyDateFormat;

/** The Time class.
 * 
 * @author chadfowler, jpetersen
 */
@JRubyClass(name="Time", include="Comparable")
public class RubyTime extends RubyObject {
    public static final String UTC = "UTC";
    private DateTime dt;
    private long usec;
    
    private final static DateTimeFormatter ONE_DAY_CTIME_FORMATTER = DateTimeFormat.forPattern("EEE MMM  d HH:mm:ss yyyy").withLocale(Locale.ENGLISH);
    private final static DateTimeFormatter TWO_DAY_CTIME_FORMATTER = DateTimeFormat.forPattern("EEE MMM dd HH:mm:ss yyyy").withLocale(Locale.ENGLISH);

    private final static DateTimeFormatter TO_S_FORMATTER = DateTimeFormat.forPattern("EEE MMM dd HH:mm:ss Z yyyy").withLocale(Locale.ENGLISH);
    private final static DateTimeFormatter TO_S_UTC_FORMATTER = DateTimeFormat.forPattern("EEE MMM dd HH:mm:ss 'UTC' yyyy").withLocale(Locale.ENGLISH);

    // There are two different popular TZ formats: legacy (AST+3:00:00, GMT-3), and
    // newer one (US/Pacific, America/Los_Angeles). This pattern is to detect
    // the legacy TZ format in order to convert it to the newer format
    // understood by Java API.
    private static final Pattern TZ_PATTERN
            = Pattern.compile("(\\D+?)([\\+-]?)(\\d+)(:\\d+)?(:\\d+)?");
    
    private static final ByteList TZ_STRING = ByteList.create("TZ");
     
    public static DateTimeZone getLocalTimeZone(Ruby runtime) {
        RubyString tzVar = runtime.newString(TZ_STRING);
        RubyHash h = ((RubyHash)runtime.getObject().fastGetConstant("ENV"));
        IRubyObject tz = h.op_aref(runtime.getCurrentContext(), tzVar);
        if (tz == null || ! (tz instanceof RubyString)) {
            return DateTimeZone.getDefault();
        } else {
            String zone = tz.toString();
            DateTimeZone cachedZone = runtime.getLocalTimezoneCache().get(zone);

            if (cachedZone != null) return cachedZone;

            String originalZone = zone;

            // Value of "TZ" property is of a bit different format,
            // which confuses the Java's TimeZone.getTimeZone(id) method,
            // and so, we need to convert it.

            Matcher tzMatcher = TZ_PATTERN.matcher(zone);
            if (tzMatcher.matches()) {                    
                String sign = tzMatcher.group(2);
                String hours = tzMatcher.group(3);
                String minutes = tzMatcher.group(4);
                
                // GMT+00:00 --> Etc/GMT, see "MRI behavior"
                // comment below.
                if (("00".equals(hours) || "0".equals(hours))
                        && (minutes == null || ":00".equals(minutes) || ":0".equals(minutes))) {
                    zone = "Etc/GMT";
                } else {
                    // Invert the sign, since TZ format and Java format
                    // use opposite signs, sigh... Also, Java API requires
                    // the sign to be always present, be it "+" or "-".
                    sign = ("-".equals(sign)? "+" : "-");

                    // Always use "GMT" since that's required by Java API.
                    zone = "GMT" + sign + hours;

                    if (minutes != null) {
                        zone += minutes;
                    }
                }
            }

            // MRI behavior: With TZ equal to "GMT" or "UTC", Time.now
            // is *NOT* considered as a proper GMT/UTC time:
            //   ENV['TZ']="GMT"
            //   Time.now.gmt? ==> false
            //   ENV['TZ']="UTC"
            //   Time.now.utc? ==> false
            // Hence, we need to adjust for that.
            if ("GMT".equalsIgnoreCase(zone) || "UTC".equalsIgnoreCase(zone)) {
                zone = "Etc/" + zone;
            }

            DateTimeZone dtz = DateTimeZone.forTimeZone(TimeZone.getTimeZone(zone));
            runtime.getLocalTimezoneCache().put(originalZone, dtz);
            return dtz;
        }
    }
    
    public RubyTime(Ruby runtime, RubyClass rubyClass) {
        super(runtime, rubyClass);
    }
    
    public RubyTime(Ruby runtime, RubyClass rubyClass, DateTime dt) {
        super(runtime, rubyClass);
        this.dt = dt;
    }

    // We assume that these two time instances
    // occurred at the same time.
    private static final long BASE_TIME_MILLIS = System.currentTimeMillis();
    private static final long BASE_TIME_NANOS = System.nanoTime();

    private static ObjectAllocator TIME_ALLOCATOR = new ObjectAllocator() {
        public IRubyObject allocate(Ruby runtime, RubyClass klass) {
            long usecsPassed = (System.nanoTime() - BASE_TIME_NANOS) / 1000L;
            long millisTime = BASE_TIME_MILLIS + usecsPassed / 1000L;
            long usecs = usecsPassed % 1000L;

            DateTimeZone dtz = getLocalTimeZone(runtime);
            DateTime dt = new DateTime(millisTime, dtz);
            RubyTime rt =  new RubyTime(runtime, klass, dt);
            rt.setUSec(usecs);

            return rt;
        }
    };

    public static RubyClass createTimeClass(Ruby runtime) {
        RubyClass timeClass = runtime.defineClass("Time", runtime.getObject(), TIME_ALLOCATOR);
        timeClass.index = ClassIndex.TIME;
        runtime.setTime(timeClass);
        
        timeClass.includeModule(runtime.getComparable());
        
        timeClass.defineAnnotatedMethods(RubyTime.class);
        
        return timeClass;
    }
    
    public void setUSec(long usec) {
        this.usec = usec;
    }
    
    public long getUSec() {
        return usec;
    }
    
    public void updateCal(DateTime dt) {
        this.dt = dt;
    }
    
    protected long getTimeInMillis() {
        return dt.getMillis();  // For JDK 1.4 we can use "cal.getTimeInMillis()"
    }
    
    public static RubyTime newTime(Ruby runtime, long milliseconds) {
        return newTime(runtime, new DateTime(milliseconds));
    }
    
    public static RubyTime newTime(Ruby runtime, DateTime dt) {
        return new RubyTime(runtime, runtime.getTime(), dt);
    }
    
    public static RubyTime newTime(Ruby runtime, DateTime dt, long usec) {
        RubyTime t = new RubyTime(runtime, runtime.getTime(), dt);
        t.setUSec(usec);
        return t;
    }
    
    @Override
    public Class<?> getJavaClass() {
        return Date.class;
    }

    @JRubyMethod(name = "initialize_copy", required = 1)
    @Override
    public IRubyObject initialize_copy(IRubyObject original) {
        if (!(original instanceof RubyTime)) {
            throw getRuntime().newTypeError("Expecting an instance of class Time");
        }
        
        RubyTime originalTime = (RubyTime) original;
        
        // We can just use dt, since it is immutable
        dt = originalTime.dt;
        usec = originalTime.usec;
        
        return this;
    }

    @JRubyMethod(name = "succ")
    public RubyTime succ() {
        return newTime(getRuntime(),dt.plusSeconds(1));
    }

    @JRubyMethod(name = {"gmtime", "utc"})
    public RubyTime gmtime() {
        dt = dt.withZone(DateTimeZone.UTC);
        return this;
    }

    @JRubyMethod(name = "localtime")
    public RubyTime localtime() {
        dt = dt.withZone(getLocalTimeZone(getRuntime()));
        return this;
    }
    
    @JRubyMethod(name = {"gmt?", "utc?", "gmtime?"})
    public RubyBoolean gmt() {
        return getRuntime().newBoolean(dt.getZone().getID().equals("UTC"));
    }
    
    @JRubyMethod(name = {"getgm", "getutc"})
    public RubyTime getgm() {
        return newTime(getRuntime(), dt.withZone(DateTimeZone.UTC), getUSec());
    }

    @JRubyMethod(name = "getlocal")
    public RubyTime getlocal() {
        return newTime(getRuntime(), dt.withZone(getLocalTimeZone(getRuntime())), getUSec());
    }

    @JRubyMethod(name = "strftime", required = 1)
    public RubyString strftime(IRubyObject format) {
        final RubyDateFormat rubyDateFormat = new RubyDateFormat("-", Locale.US);
        rubyDateFormat.applyPattern(format.toString());
        rubyDateFormat.setDateTime(dt);
        String result = rubyDateFormat.format(null);
        return getRuntime().newString(result);
    }
    
    @JRubyMethod(name = ">=", required = 1)
    public IRubyObject op_ge(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyTime) {
            return getRuntime().newBoolean(cmp((RubyTime) other) >= 0);
        }
        
        return RubyComparable.op_ge(context, this, other);
    }
    
    @JRubyMethod(name = ">", required = 1)
    public IRubyObject op_gt(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyTime) {
            return getRuntime().newBoolean(cmp((RubyTime) other) > 0);
        }
        
        return RubyComparable.op_gt(context, this, other);
    }
    
    @JRubyMethod(name = "<=", required = 1)
    public IRubyObject op_le(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyTime) {
            return getRuntime().newBoolean(cmp((RubyTime) other) <= 0);
        }
        
        return RubyComparable.op_le(context, this, other);
    }
    
    @JRubyMethod(name = "<", required = 1)
    public IRubyObject op_lt(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyTime) {
            return getRuntime().newBoolean(cmp((RubyTime) other) < 0);
        }
        
        return RubyComparable.op_lt(context, this, other);
    }
    
    private int cmp(RubyTime other) {
        long millis = getTimeInMillis();
		long millis_other = other.getTimeInMillis();
        long usec_other = other.usec;
        
		if (millis > millis_other || (millis == millis_other && usec > usec_other)) {
		    return 1;
		} else if (millis < millis_other || (millis == millis_other && usec < usec_other)) {
		    return -1;
		} 

        return 0;
    }
    
    @JRubyMethod(name = "+", required = 1)
    public IRubyObject op_plus(IRubyObject other) {
        long time = getTimeInMillis();

        if (other instanceof RubyTime) {
            throw getRuntime().newTypeError("time + time ?");
        }
        long adjustment = (long) (RubyNumeric.num2dbl(other) * 1000000);
        int micro = (int) (adjustment % 1000);
        adjustment = adjustment / 1000;

        time += adjustment;

        RubyTime newTime = new RubyTime(getRuntime(), getMetaClass());
        newTime.dt = new DateTime(time).withZone(dt.getZone());
        newTime.setUSec(micro);

        return newTime;
    }
    
    private IRubyObject opMinus(RubyTime other) {
        long time = getTimeInMillis() * 1000 + getUSec();

        time -= other.getTimeInMillis() * 1000 + other.getUSec();
        
        return RubyFloat.newFloat(getRuntime(), time / 1000000.0); // float number of seconds
    }

    @JRubyMethod(name = "-", required = 1)
    public IRubyObject op_minus(IRubyObject other) {
        if (other instanceof RubyTime) return opMinus((RubyTime) other);
        
        long time = getTimeInMillis();
        long adjustment = (long) (RubyNumeric.num2dbl(other) * 1000000);
        int micro = (int) (adjustment % 1000);
        adjustment = adjustment / 1000;

        time -= adjustment;

        RubyTime newTime = new RubyTime(getRuntime(), getMetaClass());
        newTime.dt = new DateTime(time).withZone(dt.getZone());
        newTime.setUSec(micro);

        return newTime;
    }

    @JRubyMethod(name = "===", required = 1)
    @Override
    public IRubyObject op_eqq(ThreadContext context, IRubyObject other) {
        return (RubyNumeric.fix2int(callMethod(context, MethodIndex.OP_SPACESHIP, "<=>", other)) == 0) ? getRuntime().getTrue() : getRuntime().getFalse();
    }

    @JRubyMethod(name = "<=>", required = 1)
    public IRubyObject op_cmp(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyTime) {
            return context.getRuntime().newFixnum(cmp((RubyTime) other));
        }

        return context.getRuntime().getNil();
    }
    
    @JRubyMethod(name = "eql?", required = 1)
    @Override
    public IRubyObject eql_p(IRubyObject other) {
        if (other instanceof RubyTime) {
            RubyTime otherTime = (RubyTime)other; 
            return (usec == otherTime.usec && getTimeInMillis() == otherTime.getTimeInMillis()) ? getRuntime().getTrue() : getRuntime().getFalse();
        }
        return getRuntime().getFalse();
    }

    @JRubyMethod(name = {"asctime", "ctime"})
    public RubyString asctime() {
        DateTimeFormatter simpleDateFormat;

        if (dt.getDayOfMonth() < 10) {
            simpleDateFormat = ONE_DAY_CTIME_FORMATTER;
        } else {
            simpleDateFormat = TWO_DAY_CTIME_FORMATTER;
        }
        String result = simpleDateFormat.print(dt);
        return getRuntime().newString(result);
    }

    @JRubyMethod(name = {"to_s", "inspect"})
    @Override
    public IRubyObject to_s() {
        DateTimeFormatter simpleDateFormat;
        if (dt.getZone() == DateTimeZone.UTC) {
            simpleDateFormat = TO_S_UTC_FORMATTER;
        } else {
            simpleDateFormat = TO_S_FORMATTER;
        }

        String result = simpleDateFormat.print(dt);

        return getRuntime().newString(result);
    }

    @JRubyMethod(name = "to_a")
    @Override
    public RubyArray to_a() {
        return getRuntime().newArrayNoCopy(new IRubyObject[] { sec(), min(), hour(), mday(), month(), 
                year(), wday(), yday(), isdst(), zone() });
    }

    @JRubyMethod(name = "to_f")
    public RubyFloat to_f() {
        long time = getTimeInMillis();
        time = time * 1000 + usec;
        return RubyFloat.newFloat(getRuntime(), time / 1000000.0);
    }

    @JRubyMethod(name = {"to_i", "tv_sec"})
    public RubyInteger to_i() {
        return getRuntime().newFixnum(getTimeInMillis() / 1000);
    }

    @JRubyMethod(name = {"usec", "tv_usec"})
    public RubyInteger usec() {
        return getRuntime().newFixnum(dt.getMillisOfSecond() * 1000 + getUSec());
    }

    public void setMicroseconds(long mic) {
        long millis = getTimeInMillis() % 1000;
        long withoutMillis = getTimeInMillis() - millis;
        withoutMillis += (mic / 1000);
        dt = dt.withMillis(withoutMillis);
        usec = mic % 1000;
    }
    
    public long microseconds() {
    	return getTimeInMillis() % 1000 * 1000 + usec;
    }

    @JRubyMethod(name = "sec")
    public RubyInteger sec() {
        return getRuntime().newFixnum(dt.getSecondOfMinute());
    }

    @JRubyMethod(name = "min")
    public RubyInteger min() {
        return getRuntime().newFixnum(dt.getMinuteOfHour());
    }

    @JRubyMethod(name = "hour")
    public RubyInteger hour() {
        return getRuntime().newFixnum(dt.getHourOfDay());
    }

    @JRubyMethod(name = {"mday", "day"})
    public RubyInteger mday() {
        return getRuntime().newFixnum(dt.getDayOfMonth());
    }

    @JRubyMethod(name = {"month", "mon"})
    public RubyInteger month() {
        return getRuntime().newFixnum(dt.getMonthOfYear());
    }

    @JRubyMethod(name = "year")
    public RubyInteger year() {
        return getRuntime().newFixnum(dt.getYear());
    }

    @JRubyMethod(name = "wday")
    public RubyInteger wday() {
        return getRuntime().newFixnum((dt.getDayOfWeek()%7));
    }

    @JRubyMethod(name = "yday")
    public RubyInteger yday() {
        return getRuntime().newFixnum(dt.getDayOfYear());
    }

    @JRubyMethod(name = {"gmt_offset", "gmtoff", "utc_offset"})
    public RubyInteger gmt_offset() {
        int offset = dt.getZone().getOffsetFromLocal(dt.getMillis());
        
        return getRuntime().newFixnum((int)(offset/1000));
    }

    @JRubyMethod(name = {"isdst", "dst?"})
    public RubyBoolean isdst() {
        return getRuntime().newBoolean(!dt.getZone().isStandardOffset(dt.getMillis()));
    }

    @JRubyMethod(name = "zone")
    public RubyString zone() {
        String zone = dt.getZone().getShortName(dt.getMillis());
        if(zone.equals("+00:00")) {
            zone = "GMT";
        }
        return getRuntime().newString(zone);
    }

    public void setDateTime(DateTime dt) {
        this.dt = dt;
    }

    public DateTime getDateTime() {
        return this.dt;
    }

    public Date getJavaDate() {
        return this.dt.toDate();
    }

    @JRubyMethod(name = "hash")
    @Override
    public RubyFixnum hash() {
    	// modified to match how hash is calculated in 1.8.2
        return getRuntime().newFixnum((int)(((dt.getMillis() / 1000) ^ microseconds()) << 1) >> 1);
    }    

    @JRubyMethod(name = "_dump", optional = 1, frame = true)
    public RubyString dump(IRubyObject[] args, Block unusedBlock) {
        RubyString str = (RubyString) mdump(new IRubyObject[] { this });
        str.syncVariables(this.getVariableList());
        return str;
    }    

    public RubyObject mdump(final IRubyObject[] args) {
        RubyTime obj = (RubyTime)args[0];
        DateTime dateTime = obj.dt.withZone(DateTimeZone.UTC);
        byte dumpValue[] = new byte[8];
        int pe = 
            0x1                                 << 31 |
            (dateTime.getYear()-1900)           << 14 |
            (dateTime.getMonthOfYear()-1)       << 10 |
            dateTime.getDayOfMonth()            << 5  |
            dateTime.getHourOfDay();
        int se =
            dateTime.getMinuteOfHour()          << 26 |
            dateTime.getSecondOfMinute()        << 20 |
            (dateTime.getMillisOfSecond() * 1000 + (int)usec); // dump usec, not msec

        for(int i = 0; i < 4; i++) {
            dumpValue[i] = (byte)(pe & 0xFF);
            pe >>>= 8;
        }
        for(int i = 4; i < 8 ;i++) {
            dumpValue[i] = (byte)(se & 0xFF);
            se >>>= 8;
        }
        return RubyString.newString(obj.getRuntime(), new ByteList(dumpValue,false));
    }

    @JRubyMethod(name = "initialize", frame = true, visibility = Visibility.PRIVATE)
    public IRubyObject initialize(Block block) {
        return this;
    }
    
    /* Time class methods */
    
    public static IRubyObject s_new(IRubyObject recv, IRubyObject[] args, Block block) {
        Ruby runtime = recv.getRuntime();
        RubyTime time = new RubyTime(runtime, (RubyClass) recv, new DateTime(getLocalTimeZone(runtime)));
        time.callInit(args,block);
        return time;
    }

    /**
     * @deprecated Use {@link #newInstance(ThreadContext, IRubyObject)}
     */
    @Deprecated
    public static IRubyObject newInstance(ThreadContext context, IRubyObject recv, IRubyObject[] args, Block block) {
        return newInstance(context, recv);
    }

    @JRubyMethod(name = "now", backtrace = true, meta = true)
    public static IRubyObject newInstance(ThreadContext context, IRubyObject recv) {
        IRubyObject obj = ((RubyClass) recv).allocate();
        obj.getMetaClass().getBaseCallSites()[RubyClass.CS_IDX_INITIALIZE].call(context, recv, obj);
        return obj;
    }

    @JRubyMethod(name = "at",  meta = true)
    public static IRubyObject at(ThreadContext context, IRubyObject recv, IRubyObject arg) {
        Ruby runtime = context.getRuntime();
        final RubyTime time;

        if (arg instanceof RubyTime) {
            RubyTime other = (RubyTime) arg;
            time = new RubyTime(runtime, (RubyClass) recv, other.dt);
            time.setUSec(other.getUSec());
        } else {
            time = new RubyTime(runtime, (RubyClass) recv,
                    new DateTime(0L, getLocalTimeZone(runtime)));

            long seconds = RubyNumeric.num2long(arg);
            long millisecs = 0;
            long microsecs = 0;

            // In the case of two arguments, MRI will discard the portion of
            // the first argument after a decimal point (i.e., "floor").
            // However in the case of a single argument, any portion after
            // the decimal point is honored.
            if (arg instanceof RubyFloat) {
                double dbl = ((RubyFloat) arg).getDoubleValue();
                long micro = (long) ((dbl - seconds) * 1000000);
                millisecs = micro / 1000;
                microsecs = micro % 1000;
            }
            time.setUSec(microsecs);
            time.dt = time.dt.withMillis(seconds * 1000 + millisecs);
        }

        time.getMetaClass().getBaseCallSites()[RubyClass.CS_IDX_INITIALIZE].call(context, recv, time);

        return time;
    }

    @JRubyMethod(name = "at", meta = true)
    public static IRubyObject at(ThreadContext context, IRubyObject recv, IRubyObject arg1, IRubyObject arg2) {
        Ruby runtime = context.getRuntime();

        RubyTime time = new RubyTime(runtime, (RubyClass) recv,
                new DateTime(0L, getLocalTimeZone(runtime)));

            long seconds = RubyNumeric.num2long(arg1);
            long millisecs = 0;
            long microsecs = 0;

            long tmp = RubyNumeric.num2long(arg2);
            millisecs = tmp / 1000;
            microsecs = tmp % 1000;

            time.setUSec(microsecs);
            time.dt = time.dt.withMillis(seconds * 1000 + millisecs);

            time.getMetaClass().getBaseCallSites()[RubyClass.CS_IDX_INITIALIZE].call(context, recv, time);

        return time;
    }

    @JRubyMethod(name = {"local", "mktime"}, required = 1, optional = 9, meta = true)
    public static RubyTime new_local(IRubyObject recv, IRubyObject[] args) {
        return createTime(recv, args, false);
    }

    @JRubyMethod(name = {"utc", "gm"}, required = 1, optional = 9, meta = true)
    public static RubyTime new_utc(IRubyObject recv, IRubyObject[] args) {
        return createTime(recv, args, true);
    }

    @JRubyMethod(name = "_load", required = 1, frame = true, meta = true)
    public static RubyTime load(IRubyObject recv, IRubyObject from, Block block) {
        return s_mload(recv, (RubyTime)(((RubyClass)recv).allocate()), from);
    }

    protected static RubyTime s_mload(IRubyObject recv, RubyTime time, IRubyObject from) {
        Ruby runtime = recv.getRuntime();

        DateTime dt = new DateTime(DateTimeZone.UTC);

        byte[] fromAsBytes = null;
        fromAsBytes = from.convertToString().getBytes();
        if(fromAsBytes.length != 8) {
            throw runtime.newTypeError("marshaled time format differ");
        }
        int p=0;
        int s=0;
        for (int i = 0; i < 4; i++) {
            p |= ((int)fromAsBytes[i] & 0xFF) << (8 * i);
        }
        for (int i = 4; i < 8; i++) {
            s |= ((int)fromAsBytes[i] & 0xFF) << (8 * (i - 4));
        }
        if ((p & (1<<31)) == 0) {
            dt = dt.withMillis(p * 1000L + s);
        } else {
            p &= ~(1<<31);
            dt = dt.withYear(((p >>> 14) & 0xFFFF) + 1900);
            dt = dt.withMonthOfYear(((p >>> 10) & 0xF) + 1);
            dt = dt.withDayOfMonth(((p >>> 5)  & 0x1F));
            dt = dt.withHourOfDay((p & 0x1F));
            dt = dt.withMinuteOfHour(((s >>> 26) & 0x3F));
            dt = dt.withSecondOfMinute(((s >>> 20) & 0x3F));
            // marsaling dumps usec, not msec
            dt = dt.withMillisOfSecond((s & 0xFFFFF) / 1000);
            dt = dt.withZone(getLocalTimeZone(runtime));
            time.setUSec((s & 0xFFFFF) % 1000);
        }
        time.setDateTime(dt);

        from.getInstanceVariables().copyInstanceVariablesInto(time);
        return time;
    }

    private static final String[] MONTHS = {"jan", "feb", "mar", "apr", "may", "jun",
                                            "jul", "aug", "sep", "oct", "nov", "dec"};

    private static final Map<String, Integer> MONTHS_MAP = new HashMap<String, Integer>();
    static {
        for (int i = 0; i < MONTHS.length; i++) {
            MONTHS_MAP.put(MONTHS[i], i + 1);
        }
    }

    private static final int[] time_min = {1, 0, 0, 0, Integer.MIN_VALUE};
    private static final int[] time_max = {31, 23, 59, 60, Integer.MAX_VALUE};

    private static final int ARG_SIZE = 7;

    private static RubyTime createTime(IRubyObject recv, IRubyObject[] args, boolean gmt) {
        Ruby runtime = recv.getRuntime();
        int len = ARG_SIZE;

        if (args.length == 10) {
            args = new IRubyObject[] { args[5], args[4], args[3], args[2], args[1], args[0], runtime.getNil() };
        } else {
            // MRI accepts additional wday argument which appears to be ignored.
            len = args.length;

            if (len < ARG_SIZE) {
                IRubyObject[] newArgs = new IRubyObject[ARG_SIZE];
                System.arraycopy(args, 0, newArgs, 0, args.length);
                for (int i = len; i < ARG_SIZE; i++) {
                    newArgs[i] = runtime.getNil();
                }
                args = newArgs;
                len = ARG_SIZE;
            }
        }

        if (args[0] instanceof RubyString) {
            args[0] = RubyNumeric.str2inum(runtime, (RubyString) args[0], 10, false);
        }

        int year = (int) RubyNumeric.num2long(args[0]);
        int month = 1;

        if (len > 1) {
            if (!args[1].isNil()) {
                IRubyObject tmp = args[1].checkStringType();
                if (!tmp.isNil()) {
                    String monthString = tmp.toString().toLowerCase();
                    Integer monthInt = MONTHS_MAP.get(monthString);

                    if (monthInt != null) {
                        month = monthInt;
                    } else {
                        try {
                            month = Integer.parseInt(monthString);
                        } catch (NumberFormatException nfExcptn) {
                            throw runtime.newArgumentError("Argument out of range.");
                        }
                    }
                } else {
                    month = (int) RubyNumeric.num2long(args[1]);
                }
            }
            if (1 > month || month > 12) {
                throw runtime.newArgumentError("Argument out of range: for month: " + month);
            }
        }

        int[] int_args = { 1, 0, 0, 0, 0, 0 };

        for (int i = 0; int_args.length >= i + 2; i++) {
            if (!args[i + 2].isNil()) {
                if (!(args[i + 2] instanceof RubyNumeric)) {
                    args[i + 2] = args[i + 2].callMethod(
                            runtime.getCurrentContext(), "to_i");
                }

                long value = RubyNumeric.num2long(args[i + 2]);
                if (time_min[i] > value || value > time_max[i]) {
                    throw runtime.newArgumentError("argument out of range.");
                }
                int_args[i] = (int) value;
            }
        }

        if (0 <= year && year < 39) {
            year += 2000;
        } else if (69 <= year && year < 139) {
            year += 1900;
        }

        DateTimeZone dtz;
        if (gmt) {
            dtz = DateTimeZone.UTC;
        } else {
            dtz = getLocalTimeZone(runtime);
        }

        DateTime dt;
        // set up with min values and then add to allow rolling over
        try {
            dt = new DateTime(year, 1, 1, 0, 0 , 0, 0, dtz);

            dt = dt.plusMonths(month - 1)
                    .plusDays(int_args[0] - 1)
                    .plusHours(int_args[1])
                    .plusMinutes(int_args[2])
                    .plusSeconds(int_args[3]);
        } catch (org.joda.time.IllegalFieldValueException e) {
            throw runtime.newArgumentError("time out of range");
        }

        RubyTime time = new RubyTime(runtime, (RubyClass) recv, dt);
        // Ignores usec if 8 args (for compatibility with parsedate) or if not supplied.
        if (args.length != 8 && !args[6].isNil()) {
            int usec = int_args[4] % 1000;
            int msec = int_args[4] / 1000;

            if (int_args[4] < 0) {
                msec -= 1;
                usec += 1000;
            }
            time.dt = dt.withMillis(dt.getMillis() + msec);
            time.setUSec(usec);
        }

        time.callInit(IRubyObject.NULL_ARRAY, Block.NULL_BLOCK);
        return time;
    }
}
