/*
 * BackRefNode.java - No description
 * Created on 01. November 2001, 16:27
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

package org.jruby.nodes;

import org.jruby.*;
import org.jruby.exceptions.*;
import org.jruby.runtime.*;

/**
 *
 * @author  jpetersen
 * @version $Revision$
 */
public class BackRefNode extends Node {
    public BackRefNode(int nth) {
        super(Constants.NODE_BACK_REF, null, nth, null);
    }
    
 	public RubyObject eval(Ruby ruby, RubyObject self) {
        switch ((char)getNth()) {
            case '&':
                return RubyRegexp.last_match(ruby.getParserHelper().getBackref());
                
            case '`':
                return RubyRegexp.match_pre(ruby.getParserHelper().getBackref());
                
            case '\'':
                return RubyRegexp.match_post(ruby.getParserHelper().getBackref());
                
            case '+':
                return RubyRegexp.match_last(ruby.getParserHelper().getBackref());
                
            default:
                throw new RubyBugException("unexpected back-ref");
        }
    }
	/**
	 * Accept for the visitor pattern.
	 * @param iVisitor the visitor
	 **/
	public void accept(NodeVisitor iVisitor)	
	{
		iVisitor.visitBackRefNode(this);
	}

}
