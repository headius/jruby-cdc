/*
 * Main.java - No description
 * Created on 18. September 2001, 21:48
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
package org.jruby;

import java.util.ArrayList;
import java.util.Iterator;
import java.io.StringReader;
import java.io.Reader;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.jruby.runtime.Constants;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.exceptions.RaiseException;
import org.jruby.exceptions.ThrowJump;
import org.jruby.javasupport.JavaUtil;
import org.jruby.parser.ParserSupport;
import org.ablaf.ast.INode;

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

    private static String sRegexpAdapter;

    private static ArrayList sLoadDirectories = new ArrayList();
    private static String sScript = null;
    private static String sFileName = null;
    //list of libraries to require first
    private static ArrayList sRequireFirst = new ArrayList();
    private static boolean sBenchmarkMode = false;
    private static boolean sDoLoop = false;
    private static boolean sDoPrint = false;
    private static boolean sDoLine = false;
    private static boolean sDoSplit = false;
    private static boolean verbose = false;
    private static boolean showVersion = false;

    private static int argumentIndex = 0;
    private static int characterIndex = 0;


    /*
     * helper function for args processing.
     */
    private static String grabValue(String args[], String errorMessage) {
        if (++characterIndex < args[argumentIndex].length()) {
            return args[argumentIndex].substring(characterIndex);
        } else if (++argumentIndex < args.length) {
            return args[argumentIndex];
        } else {
            System.err.println("invalid argument " + argumentIndex);
            System.err.println(errorMessage);
            printUsage();
            System.exit(1);
        }
        return null;
    }

    /**
     * process the command line arguments.
     * This method will consume the appropriate arguments and valuate
     * the static variables corresponding to the options.
     * @param args the command line arguments
     * @return the arguments left
     **/
    private static String[] processArgs(String args[]) {
        int argumentLength = args.length;
        StringBuffer lBuf = new StringBuffer();
        for (; argumentIndex < argumentLength; argumentIndex++) {
            if (args[argumentIndex].charAt(0) == '-') {
                FOR : for (characterIndex = 1; characterIndex < args[argumentIndex].length(); characterIndex++)
                    switch (args[argumentIndex].charAt(characterIndex)) {
                        case 'h' :
                            printUsage();
                            break;
                        case 'I' :
                            sLoadDirectories.add(grabValue(args, " -I must be followed by a directory name to add to lib path"));
                            break FOR;
                        case 'r' :
                            sRequireFirst.add(grabValue(args, "-r must be followed by a package to require"));
                            break FOR;
                        case 'e' :
                            lBuf.append(grabValue(args, " -e must be followed by an expression to evaluate")).append("\n");
                            break FOR;
                        case 'b' :
                            sBenchmarkMode = true;
                            break;
                        case 'R' :
                            sRegexpAdapter = grabValue(args, " -R must be followed by an expression to evaluate");
                            break FOR;
                        case 'p' :
                            sDoPrint = true;
                            sDoLoop = true;
                            break;
                        case 'n' :
                            sDoLoop = true;
                            break;
                        case 'a' :
                            sDoSplit = true;
                            break;
                        case 'l' :
                            sDoLine = true;
                            break;
                        case 'v' :
                            System.out.println("ruby " + Constants.RUBY_MAJOR_VERSION + " () [java]");
                            verbose = true;
                            break;
                        case 'w' :
                            verbose = true;
                            break;
                        case '-' :
                            if (args[argumentIndex].equals("--version")) {
                                showVersion = true;
                                break FOR;
                            }
                        default :
                            System.err.println("unknown option " + args[argumentIndex].charAt(characterIndex));
                            System.exit(1);
                    }
            } else {
                if (lBuf.length() == 0) //only get a filename if there were no -e
                    sFileName = args[argumentIndex++]; //consume the file name
                break; //the rests are args for the script
            }
        }
        sScript = lBuf.toString();
        String[] result = new String[argumentLength - argumentIndex];
        System.arraycopy(args, argumentIndex, result, 0, result.length);
        return result;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {

        // Benchmark
        long now = -1;
        String[] argv = processArgs(args);
        if (showVersion) {
            System.out.print("ruby ");
            System.out.print(Constants.RUBY_VERSION);
            System.out.print(" (");
            System.out.print(Constants.COMPILE_DATE);
            System.out.print(") [");
            System.out.print("java");
            System.out.println("]");
            return;
        }
        if (sBenchmarkMode)
            now = System.currentTimeMillis();
        if (sScript.length() > 0) {
            runInterpreter(new StringReader(sScript), "-e", argv);
        } else if (sFileName != null) {
            runInterpreterOnFile(sFileName, argv);
        } else {
            System.err.println("nothing to interpret");
            printUsage();
            return;
        }
        if (sBenchmarkMode) {
            System.out.println("Runtime: " + (System.currentTimeMillis() - now) + " ms");
        }
    }
    static boolean sPrintedUsage = false;
    /**
     * Prints the usage for the class.
     *       Usage: java -jar jruby.jar [switches] [rubyfile.rb] [arguments]
     *           -e 'command'   one line of script. Several -e's allowed. Omit [programfile]
     *           -b             benchmark mode
     *           -Idirectory    specify $LOAD_PATH directory (may be used more than once)
     *           -R 'adapter'  used to select a regexp engine
     */
    protected static void printUsage() {
        if (!sPrintedUsage) {
            System.out.println("Usage: jruby [switches] [rubyfile.rb] [arguments]");
            System.out.println("    -e 'command'    one line of script. Several -e's allowed. Omit [programfile]");
            System.out.println("    -b              benchmark mode, times the script execution");
            System.out.println("    -Idirectory     specify $LOAD_PATH directory (may be used more than once)");
            System.out.println("    -R 'name'       The regexp engine to use, for now can be JDK, GNU or ORO");
            sPrintedUsage = true;
        }
    }

    /**
     * Launch the interpreter on a specific String.
     *
     * @param reader the string to evaluate
     * @param filename the name of the File from which the string comes.
     * @fixme implement the -p and -n options
     */
    protected static void runInterpreter(Reader reader, String filename, String[] args) {
        Ruby runtime = Ruby.getDefaultInstance(sRegexpAdapter);

        IRubyObject argumentArray = JavaUtil.convertJavaToRuby(runtime, args);

        runtime.setVerbose(verbose);
        runtime.defineReadonlyVariable("$VERBOSE", verbose ? runtime.getTrue() : runtime.getNil());

        runtime.defineGlobalConstant("ARGV", argumentArray);
        runtime.defineReadonlyVariable("$-p", (sDoPrint ? runtime.getTrue() : runtime.getNil()));
        runtime.defineReadonlyVariable("$-n", (sDoLoop ? runtime.getTrue() : runtime.getNil()));
        runtime.defineReadonlyVariable("$-a", (sDoSplit ? runtime.getTrue() : runtime.getNil()));
        runtime.defineReadonlyVariable("$-l", (sDoLine ? runtime.getTrue() : runtime.getNil()));
        runtime.defineReadonlyVariable("$*", argumentArray);
        runtime.defineVariable(new RubyGlobal.StringGlobalVariable(runtime, "$0", RubyString.newString(runtime, filename)));
        runtime.getLoadService().init(runtime, sLoadDirectories);
        try {
            Iterator iter = sRequireFirst.iterator();
            while (iter.hasNext()) {
                String scriptName = (String) iter.next();
                KernelModule.require(runtime.getTopSelf(), RubyString.newString(runtime, scriptName));
            }

            INode parsedScript = runtime.parse(reader, filename);
            if (sDoPrint) {
                parsedScript = new ParserSupport().appendPrintToBlock(parsedScript);
            }
            if (sDoLoop) {
                parsedScript = new ParserSupport().appendWhileLoopToBlock(parsedScript, sDoLine, sDoSplit);
            }
            runtime.eval(parsedScript);

        } catch (RaiseException rExcptn) {
            runtime.getRuntime().printError(rExcptn.getException());
        } catch (ThrowJump throwJump) {
            runtime.getRuntime().printError(throwJump.getNameError());
        }
    }

    /**
     * Run the interpreter on a File.
     * open a file and feeds it to the interpreter.
     *
     * @param fileName the name of the file to interpret
     */
    protected static void runInterpreterOnFile(String fileName, String[] args) {
        File file = new File(fileName);
        if (!file.canRead()) {
            System.out.println("Cannot read source file: \"" + fileName + "\"");
        } else {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                runInterpreter(reader, fileName, args);
                reader.close();
            } catch (IOException ioExcptn) {
                System.out.println("Error reading source file: " + ioExcptn.getMessage());
            }
        }
    }
}
