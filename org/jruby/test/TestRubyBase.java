/*
 * RubyHash.java - No description
 * Created on 28. Nov 2001, 15:18
 * 
 * Copyright (C) 2001 Jan Arne Petersen, Stefan Matthias Aust, Alan Moore, Benoit Cerrina, Chad Fowler
 * Jan Arne Petersen <japetersen@web.de>
 * Stefan Matthias Aust <sma@3plus4.de>
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
import junit.framework.TestCase;

import java.io.PipedOutputStream;
import java.io.PipedInputStream;
import java.io.BufferedReader;
import java.io.PrintStream;
import java.io.InputStreamReader;
import java.io.IOException;

import org.jruby.Ruby;
import org.jruby.RubyIO;
import org.jruby.RubyString;

/**
 * @author Benoit
 */
public class TestRubyBase extends TestCase {
	private PipedInputStream pipeIn;
	private PipedOutputStream pos;
	private BufferedReader in;
	protected Ruby ruby;
	private PrintStream out;

	public TestRubyBase(String name) {
		super(name);
	}

	/**
	 * evaluate a string and returns the standard output.
	 * @param script the String to eval as a String
	 * @return the value printed out on  stdout and stderr by 
	 **/
	protected String eval(String script) {
		pipeIn = new PipedInputStream();
		in = new BufferedReader(new InputStreamReader(pipeIn));

		String output = null;
		StringBuffer result = new StringBuffer();
		try {
			out = new PrintStream(new PipedOutputStream(pipeIn), true);
			//            ruby.getRuntime().setOutputStream(out);
			//            ruby.getRuntime().setErrorStream(out);
			RubyIO lStream = new RubyIO(ruby);
			lStream.initIO(null, out, null);
			ruby.setGlobalVar("$stdout", lStream);
			ruby.setGlobalVar("$>", lStream);
			lStream = (RubyIO) ruby.getGlobalVar("$stderr");
			lStream.initIO(null,out,null);
			ruby.setGlobalVar("$stderr", lStream);
			new EvalThread("test", script).start();
			while ((output = in.readLine()) != null) {
				result.append(output);
			}
		} catch (Exception ex) {
			throw new RuntimeException(ex.getMessage());
		}
		return result.toString();
	}

	class EvalThread extends Thread {
		private RubyString name;
		private RubyString script;

		EvalThread(String name, String script) {
			this.name = RubyString.newString(ruby, name);
			this.script = RubyString.newString(ruby, script);
		}

		public void run() {
			ruby.getRuntime().loadScript(name, script, false);
			out.close();
		}
	}

	public void tearDown() {
		try {
			if (in != null)
				in.close();
			if (out != null)
				out.close();
		} catch (IOException ex) {
		}
	}

}
