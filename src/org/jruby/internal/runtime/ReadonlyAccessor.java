/*
 * Copyright (C) 2002 Jan Arne Petersen <jpetersen@uni-bonn.de>
 *
 * JRuby - http://jruby.sourceforge.net
 *
 * This file is part of JRuby
 *
 * JRuby is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * JRuby is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with JRuby; if not, write to
 * the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA  02111-1307 USA
 */
package org.jruby.internal.runtime;

import org.jruby.exceptions.NameError;
import org.jruby.runtime.IAccessor;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * 
 * @author jpetersen
 * @version $Revision$
 */
public class ReadonlyAccessor implements IAccessor {
    private String name;
    private IAccessor accessor;

    public ReadonlyAccessor(String name, IAccessor accessor) {
        assert name != null;
        assert accessor != null;

        this.name = name;
        this.accessor = accessor;
    }

    public IRubyObject getValue() {
        return accessor.getValue();
    }

    public IRubyObject setValue(IRubyObject newValue) {
        assert newValue != null;

        throw new NameError(newValue.getRuntime(), "can't set variable " + name);
    }
}