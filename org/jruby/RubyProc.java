/*
 * RubyProc.java - No description
 * Created on 25. November 2001, 00:00
 * 
 * Copyright (C) 2001 Jan Arne Petersen, Stefan Matthias Aust, Alan Moore, Benoit Cerrina
 * Jan Arne Petersen <jpetersen@uni-bonn.de>
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
package org.jruby;

import org.jruby.exceptions.*;
import org.jruby.runtime.*;

public class RubyProc extends RubyObject {
    // private originalThread = null

    private RubyBlock block = null;
    private RubyModule wrapper = null;

    public RubyProc(Ruby ruby, RubyClass rubyClass) {
        super(ruby, rubyClass);
    }
    
    public static RubyClass createProcClass(Ruby ruby) {
    	RubyClass procClass = ruby.defineClass("Proc", ruby.getClasses().getObjectClass());
    	
    	Callback call = new ReflectionCallbackMethod(RubyProc.class, "call", true);
    	Callback s_new = new ReflectionCallbackMethod(RubyProc.class, "s_new", 
                                                                RubyObject[].class, true, true);
    	
    	procClass.defineMethod("call", call);
    	
    	procClass.defineSingletonMethod("new", s_new);
    	
    	return procClass;
    }

    public static RubyProc s_new(Ruby ruby, RubyObject rubyClass, RubyObject[] args) {
        RubyProc proc = newProc(ruby, ruby.getClasses().getProcClass());
        
        proc.callInit(args);
        
        return proc;
    }

    public static RubyProc newProc(Ruby ruby, RubyClass rubyClass) {
        if (!ruby.isBlockGiven() && !ruby.isFBlockGiven()) {
            throw new RubyArgumentException(ruby, "tried to create Proc object without a block");
        }

        RubyProc newProc = new RubyProc(ruby, rubyClass);

        newProc.block = ruby.getBlock().cloneBlock();

        newProc.wrapper = ruby.getWrapper();
        newProc.block.iter = newProc.block.prev != null ? 1 : 0;

        newProc.block.frame = ruby.getRubyFrame();
        newProc.block.scope = ruby.getRubyScope();
        // +++

        return newProc;
    }

    public RubyObject call(RubyObject[] args) {
        RubyModule oldWrapper = getRuby().getWrapper();
        RubyBlock oldBlock = getRuby().getBlock();

        getRuby().setWrapper(wrapper);
        getRuby().setBlock(block);

        getRuby().getIter().push(RubyIter.ITER_CUR);
        getRuby().getRubyFrame().setIter(RubyIter.ITER_CUR);

        RubyObject result = getRuby().getNil();

        try {
            result = getRuby().yield0(
                args != null ? RubyArray.create(getRuby(), null, args) : null,
                null, null, true);
        } finally {
            getRuby().getIter().pop();
            getRuby().setBlock(oldBlock);
            getRuby().setWrapper(oldWrapper);
        }

        return result;
    }
}