/*
 * Copyright (C) 2002 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
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
package org.jruby.common;

import org.jruby.lexer.yacc.SourcePosition;

/**
 *
 * @author  jpetersen
 * @version $Revision$
 */
public class NullErrorHandler implements IRubyErrorHandler {

    /**
     * Constructor for NullErrorHandler.
     */
    public NullErrorHandler() {
        super();
    }

    /**
     * @see org.jruby.common.IRubyErrorHandler#handleError(int, ISourcePosition, String)
     */
    public void handleError(int type, SourcePosition position, String message) {
    }

    /**
     * @see org.jruby.common.IRubyErrorHandler#handleError(int, ISourcePosition, String, Object)
     */
    public void handleError(int type, SourcePosition position, String message, Object args) {
    }

	/**
	 * @see org.jruby.common.IRubyErrorHandler#isVerbose()
	 */
	public boolean isVerbose() {
		return false;
	}
}
