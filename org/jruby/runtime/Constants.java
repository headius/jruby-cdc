/*
 * Constants.java - No description
 * Created on 02. November 2001, 01:25
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

package org.jruby.runtime;

public final class Constants {
    public static final int SCOPE_PUBLIC = 0;
    public static final int SCOPE_PRIVATE = 1;
    public static final int SCOPE_PROTECTED = 2;
    public static final int SCOPE_MODFUNC = 5;

    public static final int NOEX_PUBLIC = 0;
    public static final int NOEX_UNDEF = 1;
    public static final int NOEX_CFUNC = 1;
    public static final int NOEX_PRIVATE = 2;
    public static final int NOEX_PROTECTED = 4;
}