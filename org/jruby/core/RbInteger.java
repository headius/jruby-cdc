/*
 * RbNumeric.java - No description
 * Created on 10. September 2001, 17:56
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

package org.jruby.core;

import org.jruby.*;
import org.jruby.exceptions.*;

/**
 *
 * @author  jpetersen
 * @version 
 */
public class RbInteger {
    private static RubyCallbackMethod methodIntP = null;

    private static RubyCallbackMethod methodTimes = null;
    
    public static RubyClass createIntegerClass(Ruby ruby) {
        RubyClass integerClass = ruby.defineClass("Integer", ruby.getNumericClass());
     
        integerClass.defineMethod("integer?", getMethodIntP());
        
        integerClass.defineMethod("times", getMethodTimes());
        
        return integerClass;
    }

    public static RubyCallbackMethod getMethodIntP() {
        if (methodIntP == null) {
            methodIntP = new RubyCallbackMethod() {
                public RubyObject execute(RubyObject recv, RubyObject[] args, Ruby ruby) {
                    return ((RubyNumeric)recv).m_int_p();
                }
            };
        }
        
        return methodIntP;
    }

    public static RubyCallbackMethod getMethodTimes() {
        if (methodTimes == null) {
            methodTimes = new RubyCallbackMethod() {
                public RubyObject execute(RubyObject recv, RubyObject[] args, Ruby ruby) {
                    return ((RubyInteger)recv).m_times();
                }
            };
        }
        
        return methodTimes;
    }
}