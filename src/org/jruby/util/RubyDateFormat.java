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
 * Copyright (C) 2002 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2004 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
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
package org.jruby.util;

import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import org.joda.time.DateTime;

public class RubyDateFormat extends DateFormat {
    private static final long serialVersionUID = -250429218019023997L;

    private List<Token> compiledPattern;

    private final DateFormatSymbols formatSymbols;

    private static final int FORMAT_STRING = 0;
    private static final int FORMAT_WEEK_LONG = 1;
    private static final int FORMAT_WEEK_SHORT = 2;
    private static final int FORMAT_MONTH_LONG = 3;
    private static final int FORMAT_MONTH_SHORT = 4;
    private static final int FORMAT_DAY = 5;
    private static final int FORMAT_DAY_S = 6;
    private static final int FORMAT_HOUR = 7;
    private static final int FORMAT_HOUR_M = 8;
    private static final int FORMAT_DAY_YEAR = 9;
    private static final int FORMAT_MINUTES = 10;
    private static final int FORMAT_MONTH = 11;
    private static final int FORMAT_MERIDIAN = 12;
    private static final int FORMAT_SECONDS = 13;
    private static final int FORMAT_WEEK_YEAR_S = 14;
    private static final int FORMAT_WEEK_YEAR_M = 15;
    private static final int FORMAT_DAY_WEEK = 16;
    private static final int FORMAT_YEAR_LONG = 17;
    private static final int FORMAT_YEAR_SHORT = 18;
    private static final int FORMAT_ZONE_OFF = 19;
    private static final int FORMAT_ZONE_ID = 20;

    private static class Token {
        private int format;
        private Object data;
        
        public Token(int format) {
            this(format, null);
        }

        public Token(int format, Object data) {
            this.format = format;
            this.data = data;
        }
        
        /**
         * Gets the data.
         * @return Returns a Object
         */
        public Object getData() {
            return data;
        }

        /**
         * Gets the format.
         * @return Returns a int
         */
        public int getFormat() {
            return format;
        }
    }

    /**
     * Constructor for RubyDateFormat.
     */
    public RubyDateFormat() {
        this("", new DateFormatSymbols());
    }

    public RubyDateFormat(String pattern, Locale aLocale) {
        this(pattern, new DateFormatSymbols(aLocale));
    }
    
    public RubyDateFormat(String pattern, DateFormatSymbols formatSymbols) {
        super();

        this.formatSymbols = formatSymbols;
        applyPattern(pattern);
    }
    
    public void applyPattern(String pattern) {
        compilePattern(pattern);
    }

    private void compilePattern(String pattern) {
        compiledPattern = new LinkedList<Token>();
        
        int len = pattern.length();
        for (int i = 0; i < len;) {
            if (pattern.charAt(i) == '%') {
                i++;
                switch (pattern.charAt(i)) {
                    case 'A' :
                        compiledPattern.add(new Token(FORMAT_WEEK_LONG));
                        break;
                    case 'a' :
                        compiledPattern.add(new Token(FORMAT_WEEK_SHORT));
                        break;
                    case 'B' :
                        compiledPattern.add(new Token(FORMAT_MONTH_LONG));
                        break;
                    case 'b' :
                        compiledPattern.add(new Token(FORMAT_MONTH_SHORT));
                        break;
                    case 'c' :
                        compiledPattern.add(new Token(FORMAT_WEEK_SHORT));
                        compiledPattern.add(new Token(FORMAT_STRING, " "));
                        compiledPattern.add(new Token(FORMAT_MONTH_SHORT));
                        compiledPattern.add(new Token(FORMAT_STRING, " "));
                        compiledPattern.add(new Token(FORMAT_DAY));
                        compiledPattern.add(new Token(FORMAT_STRING, " "));
                        compiledPattern.add(new Token(FORMAT_HOUR));
                        compiledPattern.add(new Token(FORMAT_STRING, ":"));
                        compiledPattern.add(new Token(FORMAT_MINUTES));
                        compiledPattern.add(new Token(FORMAT_STRING, ":"));
                        compiledPattern.add(new Token(FORMAT_SECONDS));
                        compiledPattern.add(new Token(FORMAT_STRING, " "));
                        compiledPattern.add(new Token(FORMAT_YEAR_LONG));
                        break;
                    case 'D':
                        compiledPattern.add(new Token(FORMAT_MONTH));
                        compiledPattern.add(new Token(FORMAT_STRING, "/"));
                        compiledPattern.add(new Token(FORMAT_DAY));
                        compiledPattern.add(new Token(FORMAT_STRING, "/"));
                        compiledPattern.add(new Token(FORMAT_YEAR_SHORT));
                        break;
                    case 'd':
                        compiledPattern.add(new Token(FORMAT_DAY));
                        break;
                    case 'e':
                        compiledPattern.add(new Token(FORMAT_DAY_S));
                        break;
                    case 'F':
                        compiledPattern.add(new Token(FORMAT_YEAR_LONG));
                        compiledPattern.add(new Token(FORMAT_STRING, "-"));
                        compiledPattern.add(new Token(FORMAT_MONTH));
                        compiledPattern.add(new Token(FORMAT_STRING, "-"));
                        compiledPattern.add(new Token(FORMAT_DAY));
                        break;
                    case 'H':
                        compiledPattern.add(new Token(FORMAT_HOUR));
                        break;
                    case 'I':
                        compiledPattern.add(new Token(FORMAT_HOUR_M));
                        break;
                    case 'j':
                        compiledPattern.add(new Token(FORMAT_DAY_YEAR));
                        break;
                    case 'M':
                        compiledPattern.add(new Token(FORMAT_MINUTES));
                        break;
                    case 'm':
                        compiledPattern.add(new Token(FORMAT_MONTH));
                        break;
                    case 'p':
                        compiledPattern.add(new Token(FORMAT_MERIDIAN));
                        break;
                    case 'S':
                        compiledPattern.add(new Token(FORMAT_SECONDS));
                        break;
                    case 'T':
                        compiledPattern.add(new Token(FORMAT_HOUR));
                        compiledPattern.add(new Token(FORMAT_STRING, ":"));
                        compiledPattern.add(new Token(FORMAT_MINUTES));
                        compiledPattern.add(new Token(FORMAT_STRING, ":"));
                        compiledPattern.add(new Token(FORMAT_SECONDS));
                        break;
                    case 'U':
                        compiledPattern.add(new Token(FORMAT_WEEK_YEAR_S));
                        break;
                    case 'W':
                        compiledPattern.add(new Token(FORMAT_WEEK_YEAR_M));
                        break;
                    case 'w':
                        compiledPattern.add(new Token(FORMAT_DAY_WEEK));
                        break;
                    case 'X':
                        compiledPattern.add(new Token(FORMAT_HOUR));
                        compiledPattern.add(new Token(FORMAT_STRING, ":"));
                        compiledPattern.add(new Token(FORMAT_MINUTES));
                        compiledPattern.add(new Token(FORMAT_STRING, ":"));
                        compiledPattern.add(new Token(FORMAT_SECONDS));
                        break;
                    case 'x':
                        compiledPattern.add(new Token(FORMAT_MONTH));
                        compiledPattern.add(new Token(FORMAT_STRING, "/"));
                        compiledPattern.add(new Token(FORMAT_DAY));
                        compiledPattern.add(new Token(FORMAT_STRING, "/"));
                        compiledPattern.add(new Token(FORMAT_YEAR_SHORT));
                        break;
                    case 'Y':
                        compiledPattern.add(new Token(FORMAT_YEAR_LONG));
                        break;
                    case 'y':
                        compiledPattern.add(new Token(FORMAT_YEAR_SHORT));
                        break;
                    case 'Z':
                        compiledPattern.add(new Token(FORMAT_ZONE_ID));
                        break;
                    case 'z':
                        compiledPattern.add(new Token(FORMAT_ZONE_OFF));
                        break;
                    case '%':
                        compiledPattern.add(new Token(FORMAT_STRING, "%"));
                        break;
                    default:
                        compiledPattern.add(new Token(FORMAT_STRING, "%" + pattern.charAt(i)));
                }
                i++;
            } else {
                StringBuilder sb = new StringBuilder();
                for (;i < len && pattern.charAt(i) != '%'; i++) {
                    sb.append(pattern.charAt(i));
                }
                compiledPattern.add(new Token(FORMAT_STRING, sb.toString()));
            }
        }
    }

    private DateTime dt;

    public void setDateTime(final DateTime dt) {
        this.dt = dt;
    }

    /**
     * @see DateFormat#format(Date, StringBuffer, FieldPosition)
     */
    public StringBuffer format(Date ignored, StringBuffer toAppendTo, FieldPosition fieldPosition) {
        for (Token token: compiledPattern) {
            switch (token.getFormat()) {
                case FORMAT_STRING:
                    toAppendTo.append(token.getData());
                    break;
                case FORMAT_WEEK_LONG:
                    // This is GROSS, but Java API's aren't ISO 8601 compliant at all
                    int v = (dt.getDayOfWeek()+1)%8;
                    if(v == 0) {
                        v++;
                    }
                    toAppendTo.append(formatSymbols.getWeekdays()[v]);
                    break;
                case FORMAT_WEEK_SHORT:
                    // This is GROSS, but Java API's aren't ISO 8601 compliant at all
                    v = (dt.getDayOfWeek()+1)%8;
                    if(v == 0) {
                        v++;
                    }
                    toAppendTo.append(formatSymbols.getShortWeekdays()[v]);
                    break;
                case FORMAT_MONTH_LONG:
                    toAppendTo.append(formatSymbols.getMonths()[dt.getMonthOfYear()-1]);
                    break;
                case FORMAT_MONTH_SHORT:
                    toAppendTo.append(formatSymbols.getShortMonths()[dt.getMonthOfYear()-1]);
                    break;
                case FORMAT_DAY:
                    int value = dt.getDayOfMonth();
                    if (value < 10) {
                        toAppendTo.append('0');
                    }
                    toAppendTo.append(value);
                    break;
                case FORMAT_DAY_S: 
                    value = dt.getDayOfMonth();
                    if (value < 10) {
                        toAppendTo.append(' ');
                    }
                    toAppendTo.append(value);
                    break;
                case FORMAT_HOUR:
                    value = dt.getHourOfDay();
                    if (value < 10) {
                        toAppendTo.append('0');
                    }
                    toAppendTo.append(value);
                    break;
                case FORMAT_HOUR_M:
                    value = dt.getHourOfDay();

                    if(value > 12) {
                        value-=12;
                    }

                    if(value == 0) {
                        toAppendTo.append("12");
                    } else {
                        if (value < 10) {
                            toAppendTo.append('0');
                        }
                        toAppendTo.append(value);
                    }
                    break;
                case FORMAT_DAY_YEAR:
                    value = dt.getDayOfYear();
                    if (value < 10) {
                        toAppendTo.append("00");
                    } else if (value < 100) {
                        toAppendTo.append('0');
                    }
                    toAppendTo.append(value);
                    break;
                case FORMAT_MINUTES:
                    value = dt.getMinuteOfHour();
                    if (value < 10) {
                        toAppendTo.append('0');
                    }
                    toAppendTo.append(value);
                    break;
                case FORMAT_MONTH:
                    value = dt.getMonthOfYear();
                    if (value < 10) {
                        toAppendTo.append('0');
                    }
                    toAppendTo.append(value);
                    break;
                case FORMAT_MERIDIAN:
                    if (dt.getHourOfDay() < 12) {
                        toAppendTo.append("AM");
                    } else {
                        toAppendTo.append("PM");
                    }
                    break;
                case FORMAT_SECONDS:
                    value = dt.getSecondOfMinute();
                    if (value < 10) {
                        toAppendTo.append('0');
                    }
                    toAppendTo.append(value);
                    break;
                case FORMAT_WEEK_YEAR_M:
                	formatWeekYear(java.util.Calendar.MONDAY, toAppendTo);
                    break;
                	// intentional fall-through
                case FORMAT_WEEK_YEAR_S:
                	formatWeekYear(java.util.Calendar.SUNDAY, toAppendTo);
                    break;
                case FORMAT_DAY_WEEK:
                    value = dt.getDayOfWeek() ;
                    toAppendTo.append(value);
                    break;
                case FORMAT_YEAR_LONG:
                    value = dt.getYear();
                    if (value < 10) {
                        toAppendTo.append("000");
                    } else if (value < 100) {
                        toAppendTo.append("00");
                    } else if (value < 1000) {
                        toAppendTo.append('0');
                    }
                    toAppendTo.append(value);
                    break;
                case FORMAT_YEAR_SHORT:
                    value = dt.getYear() % 100;
                    if (value < 10) {
                        toAppendTo.append('0');
                    }
                    toAppendTo.append(value);
                    break;
                case FORMAT_ZONE_OFF:
                    value = dt.getZone().getOffset(dt.getMillis());
                    if (value <= 0) {
                        toAppendTo.append('-');
                    } else {
                        toAppendTo.append('+');
                    }
                    value = Math.abs(value);
                    if (value / 3600000 < 10) {
                        toAppendTo.append('0');
                    }
                    toAppendTo.append(value / 3600000);
                    value = value % 3600000 / 60000;
                    if (value < 10) {
                        toAppendTo.append('0');
                    }
                    toAppendTo.append(value);
                    break;
                case FORMAT_ZONE_ID:
                    toAppendTo.append(dt.getZone().getShortName(dt.getMillis()));
                    break;
            }
        }

        return toAppendTo;
    }

	private void formatWeekYear(int firstDayOfWeek, StringBuffer toAppendTo) {
        java.util.Calendar calendar = dt.toGregorianCalendar();
		calendar.setFirstDayOfWeek(firstDayOfWeek);
		calendar.setMinimalDaysInFirstWeek(7);
		int value = calendar.get(java.util.Calendar.WEEK_OF_YEAR);
		if ((value == 52 || value == 53)
		        && (calendar.get(Calendar.MONTH) == Calendar.JANUARY )) {
		    // MRI behavior: Week values are monotonous.
		    // So, weeks that effectively belong to previous year,
		    // will get the value of 0, not 52 or 53, as in Java.
		    value = 0;
		}
		if (value < 10) {
		    toAppendTo.append('0');
		}
		toAppendTo.append(value);
	}

    /**
     * @see DateFormat#parse(String, ParsePosition)
     */
    public Date parse(String source, ParsePosition pos) {
        throw new UnsupportedOperationException();
    }
}
