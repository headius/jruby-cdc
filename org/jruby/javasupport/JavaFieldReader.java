/*
 * JavaFieldReader.java - No description
 * Created on 21.01.2002, 15:07:09
 * 
 * Copyright (C) 2001, 2002 Jan Arne Petersen
 * Jan Arne Petersen <jpetersen@uni-bonn.de>
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
package org.jruby.javasupport;

import java.lang.reflect.*;

import org.jruby.*;
import org.jruby.exceptions.*;
import org.jruby.runtime.*;

/**
 *
 * @author  jpetersen
 * @version $Revision$
 */
public class JavaFieldReader implements Callback {
    private Field field;

    public JavaFieldReader(Field field) {
        this.field = field;
    }
    
    public int getArity() {
        return 0;
    }

    /**
     * @see Callback#execute(RubyObject, RubyObject[], Ruby)
     */
    public RubyObject execute(RubyObject recv, RubyObject[] args, Ruby ruby) {
        try {
			return JavaUtil.convertJavaToRuby(ruby, field.get(((RubyJavaObject)recv).getValue()));
        } catch (IllegalAccessException iaExcptn) {
            throw new RubySecurityException(ruby, iaExcptn.getMessage());
        }
    }
}