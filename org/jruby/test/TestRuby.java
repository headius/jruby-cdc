/*
 * TestRuby.java - TestClass for the Ruby class
 * Created on 28. Nov 2001, 15:18
 * 
 * Copyright (C) 2001, 2002 Jan Arne Petersen, Alan Moore, Benoit Cerrina, Chad Fowler
 * Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Alan Moore <alan_moore@gmx.net>
 * Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Chad Fowler <chadfowler@chadfowler.com>
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
package org.jruby.test;

import java.io.File;
import java.util.ArrayList;

import org.jruby.*;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * Unit test for the ruby class.
 * 
 * @author Benoit
 * @version $Revision$
 */
public class TestRuby extends TestRubyBase {

    public TestRuby(String name) {
        super(name);
    }

    public void setUp() {
        ruby = Ruby.getDefaultInstance(null);
        oldHomeProperty = System.getProperty("jruby.home");
        oldLibProperty = System.getProperty("jruby.lib");
    }
    
    public void tearDown() {
        super.tearDown();
		if (oldHomeProperty != null) {
	        System.setProperty("jruby.home", oldHomeProperty);
		}
		if (oldLibProperty != null) {
	        System.setProperty("jruby.lib", oldLibProperty);
		}
    }

    private String oldHomeProperty;
    private String oldLibProperty;

    public void testInitLoad() throws Exception {
        ArrayList list = new ArrayList();
        //check without a RubyHome and with one parameter
        System.setProperty("jruby.home", "");
        System.setProperty("jruby.lib", "");
        list.add("toto");
        ruby.initLoad(list);
        //check that the global vars are correctly valuated
        IRubyObject lCol = ruby.getGlobalVar("$:");
        IRubyObject lI = ruby.getGlobalVar("$-I");
        IRubyObject lLoad = ruby.getGlobalVar("$LOAD_PATH");
        assertTrue(lCol == lI && lI == lLoad && lLoad != null);
        RubyArray lLoadA = (RubyArray) lLoad;
        //check that we have 2 non null element
        assertEquals(2, RubyNumeric.num2long(lLoadA.nitems()));
        //check that it is what we expect, a RubyString of the correct type
        assertTrue(new RubyString(ruby, "toto").equal(lLoadA.shift()));
        assertTrue(new RubyString(ruby, ".").equal(lLoadA.shift()));
        //check the case when RubyHome is valuated
        System.setProperty("jruby.home", "RubyHome");
        //MRI result
        /*
        C:\dev\jruby>ruby -e "puts $:"
        /cygdrive/d/ruby/lib/ruby/site_ruby/1.6
        /cygdrive/d/ruby/lib/ruby/site_ruby/1.6/i386-cygwin
        /cygdrive/d/ruby/lib/ruby/site_ruby
        /cygdrive/d/ruby/lib/ruby/1.6
        /cygdrive/d/ruby/lib/ruby/1.6/i386-cygwin
        .
        
         */
        ruby.initLoad(new ArrayList());
        String wanted;
        if (File.separatorChar == '/') {
            wanted =
                "RubyHome/lib/ruby/site_ruby/1.6"
                    + "RubyHome/lib/ruby/site_ruby/1.6/java"
                    + "RubyHome/lib/ruby/site_ruby"
                    + "RubyHome/lib/ruby/1.6"
                    + "RubyHome/lib/ruby/1.6/java"
                    + ".";
        } else {
            wanted =
                "RubyHome\\lib\\ruby\\site_ruby\\1.6"
                    + "RubyHome\\lib\\ruby\\site_ruby\\1.6\\java"
                    + "RubyHome\\lib\\ruby\\site_ruby"
                    + "RubyHome\\lib\\ruby\\1.6"
                    + "RubyHome\\lib\\ruby\\1.6\\java"
                    + ".";
        }
        assertEquals(wanted, eval("puts $:"));
    }

    public void testFindFile() {
        ArrayList list = new ArrayList();
        ruby.initLoad(list);
        list.clear();
        File testFile = new File("fib.rb");
        try {
            ruby.findFile(ruby, testFile);
            fail("should have thrown an exception, the file fib.rb is not 					in the search path");
        } catch (Exception e) {
        }
        list.add("./samples");
        //now we valuate the path 
        ruby.initLoad(list);
        assertEquals(new File("./samples/fib.rb"), ruby.findFile(ruby, testFile));
    }

    public void testVarAndMet() throws Exception {
        ArrayList list = new ArrayList();
        ruby.initLoad(list);
        eval("load './test/testVariableAndMethod.rb'");
        assertEquals("Hello World", eval("puts($a)"));
        assertEquals("dlroW olleH", eval("puts $b"));
        assertEquals("Hello World", eval("puts $d.reverse, $c, $e.reverse"));
        assertEquals("135 20 3", eval("puts $f, \" \", $g, \" \",  $h"));
    }

    private void assertTrue(IRubyObject iObj) {
        assertTrue(iObj.isTrue());
    }
}
