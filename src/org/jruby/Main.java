/*
 * Main.java - No description
 * Created on 18. September 2001, 21:48
 *
 * Copyright (C) 2001, 2002 Jan Arne Petersen, Alan Moore, Benoit Cerrina
 * Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Alan Moore <alan_moore@gmx.net>
 * Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Anders Bengtsson <ndrsbngtssn@yahoo.se>
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

import org.ablaf.ast.INode;
import org.jruby.exceptions.RaiseException;
import org.jruby.exceptions.ThrowJump;
import org.jruby.internal.runtime.ValueAccessor;
import org.jruby.javasupport.JavaUtil;
import org.jruby.parser.ParserSupport;
import org.jruby.runtime.Constants;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.CommandlineParser;

import java.io.Reader;
import java.util.Iterator;

/**
 * Class used to launch the interpreter.
 * This is the main class as defined in the jruby.mf manifest.
 * It is very basic and does not support yet the same array of switches
 * as the C interpreter.
 *       Usage: java -jar jruby.jar [switches] [rubyfile.rb] [arguments]
 *           -e 'command'    one line of script. Several -e's allowed. Omit [programfile]
 * @author  jpetersen
 * @version $Revision$
 */
public class Main {
    private static CommandlineParser commandline;
    private static boolean hasPrintedUsage = false;

    public static void main(String args[]) {
        commandline = new CommandlineParser(args);

        if (commandline.showVersion) {
            showVersion();
        }
        if (! commandline.shouldRunInterpreter()) {
            return;
        }

        long now = -1;
        if (commandline.isBenchmarking) {
            now = System.currentTimeMillis();
        }

        runInterpreter(commandline.getScriptSource(), commandline.displayedFileName());

        if (commandline.isBenchmarking) {
            System.out.println("Runtime: " + (System.currentTimeMillis() - now) + " ms");
        }
    }

    private static void showVersion() {
        System.out.print("ruby ");
        System.out.print(Constants.RUBY_VERSION);
        System.out.print(" (");
        System.out.print(Constants.COMPILE_DATE);
        System.out.print(") [");
        System.out.print("java");
        System.out.println("]");
    }

    public static void printUsage() {
        if (!hasPrintedUsage) {
            System.out.println("Usage: jruby [switches] [rubyfile.rb] [arguments]");
            System.out.println("    -e 'command'    one line of script. Several -e's allowed. Omit [programfile]");
            System.out.println("    -b              benchmark mode, times the script execution");
            System.out.println("    -Idirectory     specify $LOAD_PATH directory (may be used more than once)");
            System.out.println("    -R 'name'       The regexp engine to use, for now can be JDK, GNU or ORO");
            hasPrintedUsage = true;
        }
    }

    private static void runInterpreter(Reader reader, String filename) {
        Ruby runtime = Ruby.getDefaultInstance(commandline.sRegexpAdapter);
        try {
            initializeRuntime(runtime, filename);
            INode parsedScript = getParsedScript(runtime, reader, filename);
            runtime.eval(parsedScript);

        } catch (RaiseException rExcptn) {
            runtime.printError(rExcptn.getException());
        } catch (ThrowJump throwJump) {
            runtime.printError(throwJump.getNameError());
        }
        runtime.tearDown();
    }

    private static INode getParsedScript(Ruby runtime, Reader reader, String filename) {
        INode result = runtime.parse(reader, filename);
        if (commandline.assumePrinting) {
            result = new ParserSupport().appendPrintToBlock(result);
        }
        if (commandline.assumeLoop) {
            result = new ParserSupport().appendWhileLoopToBlock(result, commandline.processLineEnds, commandline.sDoSplit);
        }
        return result;
    }

    private static void initializeRuntime(Ruby runtime, String filename) {
        IRubyObject argumentArray = RubyArray.newArray(runtime, JavaUtil.convertJavaArrayToRuby(runtime, commandline.scriptArguments));
        runtime.setVerbose(commandline.verbose);
        defineGlobal(runtime, "$VERBOSE", commandline.verbose);
        runtime.defineGlobalConstant("ARGV", argumentArray);
        defineGlobal(runtime, "$-p", commandline.assumePrinting);
        defineGlobal(runtime, "$-n", commandline.assumeLoop);
        defineGlobal(runtime, "$-a", commandline.sDoSplit);
        defineGlobal(runtime, "$-l", commandline.processLineEnds);
        runtime.getGlobalVariables().defineReadonly("$*", new ValueAccessor(argumentArray));
        runtime.defineVariable(new RubyGlobal.StringGlobalVariable(runtime, "$0", RubyString.newString(runtime, filename)));
        runtime.getLoadService().init(runtime, commandline.loadPaths());
        Iterator iter = commandline.requiredLibraries().iterator();
        while (iter.hasNext()) {
            String scriptName = (String) iter.next();
            KernelModule.require(runtime.getTopSelf(), RubyString.newString(runtime, scriptName));
        }
    }

    private static void defineGlobal(Ruby runtime, String name, boolean value) {
        runtime.getGlobalVariables().defineReadonly(name, new ValueAccessor(value ? runtime.getTrue() : runtime.getNil()));
    }

}
