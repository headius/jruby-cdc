/*
 * RubyArgsFile.java - No description
 * Created on 13.01.2002, 17:08:47
 * 
 * Copyright (C) 2001, 2002 Jan Arne Petersen, Alan Moore, Benoit Cerrina, Chad Fowler
 * Copyright (C) 2004 Thomas E Enebo
 * Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Alan Moore <alan_moore@gmx.net>
 * Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Chad Fowler <chadfowler@yahoo.com>
 * Thomas E Enebo <enebo@acm.org>
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
package org.jruby;

import org.jruby.runtime.builtin.IRubyObject;

public class RubyArgsFile extends RubyObject {

    public RubyArgsFile(Ruby runtime) {
        super(runtime, runtime.getClasses().getObjectClass());
    }

    private RubyIO currentFile = null;
    private int currentLineNumber;
    
    public void setCurrentLineNumber(int newLineNumber) {
        this.currentLineNumber = newLineNumber;
    }
    
    public void initArgsFile() {
        extendObject(runtime.getModule("Enumerable"));
        
        runtime.defineReadonlyVariable("$<", this);
        runtime.defineGlobalConstant("ARGF", this);
        
        defineSingletonMethod("each", callbackFactory().getOptMethod(RubyArgsFile.class, "each_line"));
        defineSingletonMethod("each_line", callbackFactory().getOptMethod(RubyArgsFile.class, "each_line"));

		defineSingletonMethod("filename", callbackFactory().getMethod(RubyArgsFile.class, "filename"));
//		defineSingletonMethod("gets", callbackFactory().getOptSingletonMethod(RubyGlobal.class, "gets"));
//		defineSingletonMethod("readline", callbackFactory().getOptSingletonMethod(RubyGlobal.class, "readline"));
		//defineSingletonMethod("readlines", callbackFactory().getOptSingletonMethod(RubyGlobal.class, "readlines"));
		
//		defineSingletonMethod("to_a", callbackFactory().getOptSingletonMethod(RubyGlobal.class, "readlines"));
		defineSingletonMethod("to_s", callbackFactory().getMethod(RubyArgsFile.class, "filename"));

        runtime.defineReadonlyVariable("$FILENAME", RubyString.newString(runtime, "-"));

        // This is ugly.  nextArgsFile both checks existence of another
        // file and the setup of any files.  On top of that it handles
        // the logic for figuring out stdin versus a list of files.
        // I hacked this to make a null currentFile indicate that things
        // have not been set up yet.  This seems fragile, but it at least
        // it passes tests now.
        //currentFile = (RubyIO) runtime.getGlobalVariables().get("$stdin");
    }

    protected boolean nextArgsFile() {
        RubyArray args = (RubyArray)runtime.getGlobalVariables().get("$*");

        if (args.getLength() == 0) {
            if (currentFile == null) { 
                currentFile = (RubyIO) runtime.getGlobalVariables().get("$stdin");
                ((RubyString) runtime.getGlobalVariables().get("$FILENAME")).setValue("-");
                currentLineNumber = 0;
                return true;
            }

            return false;
        }

        String filename = ((RubyString) args.shift()).getValue();
        ((RubyString) runtime.getGlobalVariables().get("$FILENAME")).setValue(filename);

        if (filename.equals("-")) {
            currentFile = (RubyIO) runtime.getGlobalVariables().get("$stdin");
        } else {
            currentFile = new RubyFile(runtime, filename); 
        }

        return true;
    }
    
    public RubyString internalGets(IRubyObject[] args) {
        if (currentFile == null && !nextArgsFile()) {
            return RubyString.nilString(runtime);
        }
        
        RubyString line = (RubyString)currentFile.callMethod("gets", args);
        
        while (line.isNil()) {
            currentFile.callMethod("close");
            if (! nextArgsFile()) {
                currentFile = null;
                return line;
        	}
            line = (RubyString) currentFile.callMethod("gets", args);
        }
        
        currentLineNumber++;
        runtime.getGlobalVariables().set("$.", RubyFixnum.newFixnum(runtime, currentLineNumber));
        
        return line;
    }
    
    // ARGF methods
    
    /** Invoke a block for each line.
     * 
     */
    public IRubyObject each_line(IRubyObject[] args) {
        RubyString nextLine = internalGets(args);
        
        while (!nextLine.isNil()) {
        	getRuntime().yield(nextLine);
        	nextLine = internalGets(args);
        }
        
        return this;
    }
    
	public RubyString filename() {
        return (RubyString)runtime.getGlobalVariables().get("$FILENAME");
    }
}
