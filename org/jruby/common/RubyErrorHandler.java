/*
 * RubyErrorHandler.java - description
 * Created on 04.03.2002, 12:47:19
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
package org.jruby.common;

import org.ablaf.common.*;
import org.ablaf.common.ISourcePosition;

import org.jruby.*;
import org.jruby.parser.SyntaxErrorState;

/** 
 *
 * @author  jpetersen
 * @version $Revision$
 */
public class RubyErrorHandler implements IErrorHandler {
    private Ruby ruby;
    private boolean verbose;

    /**
     * Constructor for RubyErrorHandler.
     */
    public RubyErrorHandler(Ruby ruby, boolean verbose) {
        this.ruby = ruby;
        this.verbose = verbose;
    }

    /**
     * @see IErrorHandler#isHandled(int)
     */
    public boolean isHandled(int type) {
        if (type == IErrors.WARNING || type == IErrors.VERBOSE) {
            return verbose;
        }
        return true;
    }

    /**
     * @see IErrorHandler#handleError(int, ISourcePosition, String, Object)
     */
    public void handleError(int type, ISourcePosition position, String message, Object args) {
        if (isHandled(type)) {
            if (type == IErrors.WARN || type == IErrors.WARNING) {
                message = "warning: " + message;
            }

            if (position != null) {
                message = position.getFile() + ": [" + position.getLine() + ", " + position.getColumn() + "] " + message;
            }

            ruby.getGlobalVar("$stderr").callMethod("write", RubyString.newString(ruby, message + "\n"));

            if (type == IErrors.SYNTAX_ERROR) {
                ruby.getGlobalVar("$stderr").callMethod("write", RubyString.newString(ruby, "\tExpecting:"));
				String[] lExpected = {};
				String lFound = "";
                if (args instanceof String[]) {
					lExpected = (String[])args;
                }
				else if (args instanceof SyntaxErrorState)
					
				{
					lExpected = ((SyntaxErrorState)args).expected();
					lFound = ((SyntaxErrorState)args).found();
				}
				for (int i = 0; i < lExpected.length; i++) {
					String msg = lExpected[i];
					ruby.getGlobalVar("$stderr").callMethod("write", RubyString.newString(ruby, " " + msg));
				}
				ruby.getGlobalVar("$stderr").callMethod("write", RubyString.newString(ruby, " but found " + lFound + " instead\n"));
            }
        }
    }

    /**
     * @see IErrorHandler#handleError(int, ISourcePosition, String)
     */
    public void handleError(int type, ISourcePosition position, String message) {
        handleError(type, position, message, null);
    }

    /**
     * @see IErrorHandler#handleError(int, String)
     */
    public void handleError(int type, String message) {
        handleError(type, null, message, null);
    }

    /**
     * Gets the verbose.
     * @return Returns a boolean
     */
    public boolean getVerbose() {
        return verbose;
    }

    /**
     * Sets the verbose.
     * @param verbose The verbose to set
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}
