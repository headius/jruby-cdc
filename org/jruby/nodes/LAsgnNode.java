/*
 * LAsgnNode.java - No description
 * Created on 05. November 2001, 21:45
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
import org.jruby.nodes.types.*;
import org.jruby.runtime.*;

/**
 * local variable assignment node.
 * @author  jpetersen
 * @version $Revision$
 */
public class LAsgnNode extends Node implements AssignableNode {
    public LAsgnNode(Node valueNode, int count) {
        super(Constants.NODE_LASGN, null, valueNode, count);
    }

    public RubyObject eval(Ruby ruby, RubyObject self) {
        if (ruby.getScope().getLocalValues() == null) {
        	ruby.getRuntime().printBug("unexpected local variable assignment");
        }

        RubyObject result = getValueNode().eval(ruby, self);
        ruby.getScope().setValue(getCount(), result);
        return result;
    }

    public void assign(Ruby ruby, RubyObject self, RubyObject value, boolean check) {
        if (ruby.getScope().getLocalValues() == null) {
        	ruby.getRuntime().printBug("unexpected local variable assignment");
        }

        ruby.getScope().setValue(getCount(), value);
    }
	/**
	 * Accept for the visitor pattern.
	 * @param iVisitor the visitor
	 **/
	public void accept(NodeVisitor iVisitor)	
	{
		iVisitor.visitLAsgnNode(this);
	}
}
