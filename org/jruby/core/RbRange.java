/*
 * RbRange.java - No description
 * Created on 10. September 2001, 17:56
 * 
 * Copyright (C) 2001 Jan Arne Petersen, Stefan Matthias Aust, Alan Moore, Benoit Cerrina
 * Jan Arne Petersen <japetersen@web.de>
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

package org.jruby.core;

import org.jruby.*;
import org.jruby.exceptions.*;

/**
 *
 * @author  jpetersen
 * @version 
 */
public class RbRange {
    
    public static RubyClass createRangeClass(Ruby ruby) {
        RubyClass rangeClass = ruby.defineClass("Range", ruby.getClasses().getObjectClass());

        rangeClass.includeModule(ruby.getRubyModule("Enumerable"));

        rangeClass.defineMethod("==", getMethod("m_eq", RubyObject.class));
        rangeClass.defineMethod("===", getMethod("m_eqq", RubyObject.class));
        rangeClass.defineMethod("first", getMethod("m_first"));
        rangeClass.defineMethod("begin", getMethod("m_first"));
        rangeClass.defineMethod("last", getMethod("m_last"));
        rangeClass.defineMethod("end", getMethod("m_last"));
        rangeClass.defineMethod("to_s", getMethod("m_inspect"));
        rangeClass.defineMethod("inspect", getMethod("m_inspect"));
        rangeClass.defineMethod("exclude_end?", getMethod("m_exclude_end_p"));
        rangeClass.defineMethod("length", getMethod("m_length"));
        rangeClass.defineMethod("size", getMethod("m_length"));
        rangeClass.defineMethod("each", getMethod("m_each"));
        rangeClass.defineMethod("initialize", getRestArgsMethod("m_initialize"));

        return rangeClass;
    }
    
    public static Callback getRestArgsMethod(String methodName) {
        return new ReflectionCallbackMethod(RubyRange.class, methodName, true);
    }
    
    public static Callback getMethod(String methodName) {
        return new ReflectionCallbackMethod(RubyRange.class, methodName);
    }

    public static Callback getMethod(String methodName, Class arg1)
    {
        return new ReflectionCallbackMethod(RubyRange.class, methodName, arg1);
    }    
}
