/*
 * RubyRuntime.java - No description
 * Created on 09. November 2001, 15:47
 * 
 * Copyright (C) 2001, 2002 Jan Arne Petersen, Alan Moore, Benoit Cerrina
 * Jan Arne Petersen <jpetersen@uni-bonn.de>
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

import java.io.*;

import org.ablaf.ast.*;
import org.jruby.*;
import org.jruby.exceptions.*;
import org.jruby.ast.*;
import org.jruby.util.*;

/**
 * @version $Revision$
 * @author  jpetersen
 */
public class RubyRuntime {
	private static final int TRACE_HEAD = 8;
	private static final int TRACE_TAIL = 5;
	private static final int TRACE_MAX = TRACE_HEAD + TRACE_TAIL + 5;

	private boolean printBugs = false;

	private Ruby ruby;

	private RubyProc traceFunction;
	private boolean tracing = false;

	public RubyRuntime(Ruby ruby) {
		this.ruby = ruby;
	}

	/** Print a bug report to the Error stream if bug 
	 * reporting is enabled
	 * 
	 */
	public void printBug(String description) {
		if (printBugs) {
			getErrorStream().println("[BUG] " + description);
		}
	}

	/** Call the current method in the superclass of the current object
	 * 
	 * @matz rb_call_super
	 */
	public RubyObject callSuper(RubyObject[] args) {
		if (ruby.getActFrame().getLastClass() == null) {
			throw new NameError(ruby, "superclass method '" + ruby.getActFrame().getLastFunc() + "' must be enabled by enableSuper().");
		}

		ruby.getIterStack().push(ruby.getActIter().isNot() ? Iter.ITER_NOT : Iter.ITER_PRE);

		RubyObject result = ruby.getNil();

		try {
			result =
				ruby.getActFrame().getLastClass().getSuperClass().call(
						ruby.getActFrame().getSelf(),
						ruby.getActFrame().getLastFunc(),
						new RubyPointer(args),
						3);
		} finally {
			ruby.getIterStack().pop();
		}

		return result;
	}

	/** This method compiles and interprets a Ruby script.
	 *
	 *  It can be used if you want to use JRuby as a Macro language.
	 *
	 */
	public void loadScript(RubyString scriptName, RubyString source, boolean wrap) {
		RubyObject self = ruby.getRubyTopSelf();
		Namespace savedNamespace = ruby.getNamespace();

		// TMP_PROTECT;
		if (wrap && ruby.getSafeLevel() >= 4) {
			// Check_Type(fname, T_STRING);
		} else {
			// Check_SafeStr(fname);
		}

		// volatile ID last_func;
		// ruby_errinfo = Qnil; /* ensure */
		RubyVarmap.push(ruby);

		RubyModule wrapper = ruby.getWrapper();
		ruby.setNamespace(ruby.getTopNamespace());

		if (!wrap) {
			ruby.secure(4); /* should alter global state */
			ruby.pushClass(ruby.getClasses().getObjectClass());
			ruby.setWrapper(null);
		} else {
			/* load in anonymous module as toplevel */
			ruby.setWrapper(RubyModule.newModule(ruby));
			ruby.pushClass(ruby.getWrapper());
			self = ruby.getRubyTopSelf().rbClone();
			self.extendObject(ruby.getRubyClass());
			ruby.setNamespace(new Namespace(ruby.getWrapper(), ruby.getNamespace()));
		}

		String last_func = ruby.getActFrame().getLastFunc();

		ruby.getFrameStack().push();
		ruby.getActFrame().setLastFunc(null);
		ruby.getActFrame().setLastClass(null);
		ruby.getActFrame().setSelf(self);
		ruby.getActFrame().setNamespace(new Namespace(ruby.getRubyClass(), null));
		ruby.getScope().push();

		/* default visibility is private at loading toplevel */
		ruby.setActMethodScope(Constants.SCOPE_PRIVATE);

		try {
			// RubyId last_func = ruby.getRubyFrame().getLastFunc();
			// DEFER_INTS;

			// FIXME
			ruby.setInEval(ruby.getInEval() + 1);

			INode node = ruby.compile(source.toString(), scriptName.getValue(), 0);

			// ---
			ruby.setInEval(ruby.getInEval() - 1);

			self.evalNode(node);
		} finally {
			ruby.getActFrame().setLastFunc(last_func);

			/*if (ruby.getRubyScope().getFlags() == SCOPE_ALLOCA && ruby.getRubyClass() == ruby.getClasses().getObjectClass()) {
			  if (ruby_scope->local_tbl)
			  free(ruby_scope->local_tbl);
			  }*/
			ruby.setNamespace(savedNamespace);
			ruby.getScope().pop();
			ruby.getFrameStack().pop();
			ruby.popClass();
			RubyVarmap.pop(ruby);
			ruby.setWrapper(wrapper);
		}

		/*if (ruby_nerrs > 0) {
		  ruby_nerrs = 0;
		  rb_exc_raise(ruby_errinfo);
		  }*/
	}

	/** This method loads, compiles and interprets a Ruby file.
	 *  It is used by Kernel#require.
	 *
	 *  (matz Ruby: rb_load)
	 */
	public void loadFile(File iFile, boolean wrap) {
		if (iFile == null) {
			throw new RuntimeException("No such file to load");
		}

		try {
			StringBuffer source = new StringBuffer((int) iFile.length());
			BufferedReader br = new BufferedReader(new FileReader(iFile));
			String line;
			while ((line = br.readLine()) != null) {
				source.append(line).append('\n');
			}
			br.close();

			loadScript(new RubyString(ruby, iFile.getPath()), new RubyString(ruby, source.toString()), wrap);

		} catch (IOException ioExcptn) {
			getErrorStream().println("Cannot read Rubyfile: \"" + iFile.getPath() + "\"");
			getErrorStream().println("IOEception: " + ioExcptn.getMessage());
		}
	}

	public void loadFile(RubyString fname, boolean wrap) {
		loadFile(new File(fname.getValue()), wrap);
	}

	/** Call the traceFunction
	 * 
	 * MRI: eval.c - call_trace_func
	 * 
	 */
	public synchronized void callTraceFunction(String event, String file, int line, RubyObject self, String name, RubyObject type) {
		if (!tracing && traceFunction != null) {
			tracing = true;

			// XXX

			if (file == null) {
				file = "(ruby)";
			}
			if (type == null)
				type = ruby.getFalse();

			
			ruby.getFrameStack().push();
			try {
				traceFunction.call(new RubyObject[] {
					RubyString.newString(ruby, event),
					RubyString.newString(ruby, file),
					RubyFixnum.newFixnum(ruby, line),
					RubySymbol.newSymbol(ruby, name),
					self, // XXX
					type});
			} finally {
				ruby.getFrameStack().pop();
				tracing = false;

				// XXX
			}
		}
	}

	/** Prints an error with backtrace to the error stream.
	 * 
	 * MRI: eval.c - error_print()
	 * 
	 */
	public void printError(RubyException excp) {
		if (excp == null || excp.isNil()) {
			return;
		}

		RubyArray backtrace = (RubyArray) excp.funcall("backtrace");

		if (backtrace.isNil()) {
			if (ruby.getSourceFile() != null) {
				getErrorStream().print(ruby.getSourceFile() + ':' + ruby.getSourceLine());
			} else {
				getErrorStream().print(ruby.getSourceLine());
			}
		} else if (backtrace.getLength() == 0) {
			printErrorPos();
		} else {
			RubyObject mesg = backtrace.entry(0);

			if (mesg.isNil()) {
				printErrorPos();
			} else {
				getErrorStream().print(mesg);
			}
		}

		RubyClass type = excp.getRubyClass();
		String info = excp.toString();

		if (type == ruby.getExceptions().getRuntimeError() && info.length() == 0) {
			getErrorStream().print(": unhandled exception\n");
		} else {
			String path = type.getClassPath().toString();

			if (info.length() == 0) {
				getErrorStream().print(": " + path + '\n');
			} else {
				if (path.startsWith("#")) {
					path = null;
				}

				String tail = null;
				if (info.indexOf("\n") != -1) {
					tail = info.substring(info.indexOf("\n") + 1);
					info = info.substring(0, info.indexOf("\n"));
				}

				getErrorStream().print(": " + info);

				if (path != null) {
					getErrorStream().print(" (" + path + ")\n");
				}

				if (tail != null) {
					getErrorStream().print(tail + '\n');
				}
			}
		}

		if (!backtrace.isNil()) {
			RubyObject[] elements = backtrace.toJavaArray();

			for (int i = 0; i < elements.length; i++) {
				if (elements[i] instanceof RubyString) {
					getErrorStream().print("\tfrom " + elements[i] + '\n');
				}

				if (i == TRACE_HEAD && elements.length > TRACE_MAX) {
					getErrorStream().print("\t ... " + (elements.length - TRACE_HEAD - TRACE_TAIL) + "levels...\n");
					i = elements.length - TRACE_TAIL;
				}
			}
		}
	}

	private void printErrorPos() {
		if (ruby.getSourceFile() != null) {
			if (ruby.getActFrame().getLastFunc() != null) {
				getErrorStream().print(ruby.getSourceFile() + ':' + ruby.getSourceLine());
				getErrorStream().print(":in '" + ruby.getActFrame().getLastFunc() + '\'');
			} else if (ruby.getSourceLine() != 0) {
				getErrorStream().print(ruby.getSourceFile() + ':' + ruby.getSourceLine());
			} else {
				getErrorStream().print(ruby.getSourceFile());
			}
		}
	}

	/**
	 * Gets the errorStream
	 * @return Returns a PrintStream
	 */
	public PrintStream getErrorStream() {
		return new PrintStream(((RubyIO) ruby.getGlobalVar("$stderr")).getOutStream());
	}

	/**
	 * Sets the errorStream
	 * @param errorStream The errorStream to set
	 */
	public void setErrorStream(PrintStream errStream) {
		ruby.setGlobalVar("$stderr", RubyIO.stderr(ruby, ruby.getClasses().getIoClass(), errStream));
	}

	/**
	 * Gets the inputStream
	 * @return Returns a InputStream
	 */
	public InputStream getInputStream() {
		return ((RubyIO) ruby.getGlobalVar("$stdin")).getInStream();
	}

	/**
	 * Sets the inputStream
	 * @param inputStream The inputStream to set
	 */
	public void setInputStream(InputStream inStream) {
		ruby.setGlobalVar("$stdin", RubyIO.stdin(ruby, ruby.getClasses().getIoClass(), inStream));
	}

	/**
	 * Gets the outputStream
	 * @return Returns a PrintStream
	 */
	public PrintStream getOutputStream() {
		return new PrintStream(((RubyIO) ruby.getGlobalVar("$stdout")).getOutStream());
	}

	/**
	 * Sets the outputStream
	 * @param outputStream The outputStream to set
	 */
	public void setOutputStream(PrintStream outStream) {
		RubyObject stdout = RubyIO.stdout(ruby, ruby.getClasses().getIoClass(), outStream);
		if (ruby.getGlobalVar("$stdout") == ruby.getGlobalVar("$>")) {
			ruby.setGlobalVar("$>", stdout);
		}
		ruby.setGlobalVar("$stdout", stdout);
	}

	/**
	 * Gets the printBugs
	 * @return Returns a boolean
	 */
	public boolean getPrintBugs() {
		return printBugs;
	}
	/**
	 * Sets the printBugs
	 * @param printBugs The printBugs to set
	 */
	public void setPrintBugs(boolean printBugs) {
		this.printBugs = printBugs;
	}

	/**
	 * Gets the traceFunction.
	 * @return Returns a RubyProc
	 */
	public RubyProc getTraceFunction() {
		return traceFunction;
	}

	/**
	 * Sets the traceFunction.
	 * @param traceFunction The traceFunction to set
	 */
	public void setTraceFunction(RubyProc traceFunction) {
		this.traceFunction = traceFunction;
	}
}
