/*
 * NodeVisitorAdapter.java - a default implementation of the NodeVisitor interface
 * Created on 05. November 2001, 21:46
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

package org.jruby.nodes.visitor;
import org.jruby.nodes.*;
import org.jruby.runtime.Constants;
/**
 * Adapter for the NodeVisitor interface.
 * each visit method is implemented by calling
 * the #visit(Node) method which can be overriden.
 * @see NodeVisitor
 * @author Benoit Cerrina
 * @version $Revision: 1.3 $
 **/
public class DumpVisitor extends NodeVisitorAdapter
{
	StringBuffer _buffer = new StringBuffer();
	StringBuffer _Indent = new StringBuffer();
	/**
	 *	Increases the indentation level by 1.
	 */
	private void indent()
	{
		_Indent.append('\t');
	}

	/**
	 *	Decreases the indentation level by 1.
	 */
	private void undent()
	{
		final int lTabDepth = _Indent.length();
		if (lTabDepth == 0)
		{
			_Indent.setLength(0);
		}
		else
		{
			_Indent.setLength(lTabDepth - 1 );
		}
	}

	public String dump()
	{
		return _buffer.toString();
	}
	protected void visit(Node iVisited)
	{
		visit(iVisited, true);
	}
	protected void visit(Node iVisited, boolean mayLeave) 
	{	
		_buffer.append(_Indent.toString()).append("<")
			.append( Constants.NODE_TRANSLATOR[iVisited.getType()]);

		switch(iVisited.getType())
		{
			case Constants.NODE_NEWLINE:
				_buffer.append(" file='")
					.append(iVisited.getFile())
					.append("' line='")
					.append(iVisited.getLine())
					.append("'");
				break;
			case Constants.NODE_CVASGN:
			case Constants.NODE_DASGN:
			case Constants.NODE_DASGN_CURR:
			case Constants.NODE_IASGN:
				_buffer.append(" id='")
					.append(iVisited.getVId())
					.append("'");
				break;
			case Constants.NODE_GASGN:
				_buffer.append(" id='")
					.append(iVisited.getEntry().getId())
					.append("'");
				break;
			
			case Constants.NODE_LASGN:
				_buffer.append(" count='")
					.append(iVisited.getCount())
					.append("'");
				break;
			

			case Constants.NODE_LIT:
				_buffer.append(" value='")
					.append(iVisited.getLiteral().toString())
					.append("'");
				break;
			case Constants.NODE_DEFN:
				_buffer.append(" mId='")
					.append(iVisited.getMId())
					.append("' noex='")
					.append(iVisited.getNoex())
					.append("'");
				break;
			case Constants.NODE_ARGS:
				_buffer.append(" rest='")
					.append(iVisited.getRest())
					.append("' count='")
					.append(iVisited.getCount())
					.append("'");
			default:
				break;
		}
		if (mayLeave)
		{
			_buffer.append(">\n");
			indent();
		}
		else
			_buffer.append("/>\n");
	}
	protected void leave(Node iVisited)
	{
		undent();
		_buffer.append(_Indent.toString()).append("</")
			.append( Constants.NODE_TRANSLATOR[iVisited.getType()])
			.append(">\n");
	}
}
