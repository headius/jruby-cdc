/***** BEGIN LICENSE BLOCK *****
 * Version: CPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Common Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/cpl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2001 Alan Moore <alan_moore@gmx.net>
 * Copyright (C) 2001-2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2001-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * 
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the CPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the CPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jruby;

import java.io.Reader;
import java.util.Iterator;
import java.util.Properties;

import org.jruby.ast.Node;
import org.jruby.exceptions.RaiseException;
import org.jruby.exceptions.ThrowJump;
import org.jruby.internal.runtime.ValueAccessor;
import org.jruby.javasupport.JavaUtil;
import org.jruby.parser.ParserSupport;
import org.jruby.runtime.Constants;
import org.jruby.runtime.IAccessor;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.CommandlineParser;

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

    public static void main(String[] args) {
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

        int status = runInterpreter(commandline.getScriptSource(), commandline.displayedFileName());

        if (commandline.isBenchmarking) {
            System.out.println("Runtime: " + (System.currentTimeMillis() - now) + " ms");
        }
        
        System.exit(status);
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
            hasPrintedUsage = true;
        }
    }

    private static int runInterpreter(Reader reader, String filename) {
        Ruby runtime = Ruby.getDefaultInstance();
        int status = 0;
        try {
            initializeRuntime(runtime, filename);
            Node parsedScript = getParsedScript(runtime, reader, filename);
            runtime.eval(parsedScript);

        } catch (RaiseException rExcptn) {
            runtime.printError(rExcptn.getException());
            status = 1;
        } catch (ThrowJump throwJump) {
            runtime.printError(throwJump.getNameError());
            status = 1;
        }
        runtime.tearDown();
        return status;
    }

    private static Node getParsedScript(Ruby runtime, Reader reader, String filename) {
        Node result = runtime.parse(reader, filename);
        if (commandline.assumePrinting) {
            result = new ParserSupport().appendPrintToBlock(result);
        }
        if (commandline.assumeLoop) {
            result = new ParserSupport().appendWhileLoopToBlock(result, commandline.processLineEnds, commandline.sDoSplit);
        }
        return result;
    }

    private static void initializeRuntime(final Ruby runtime, String filename) {
        IRubyObject argumentArray = runtime.newArray(JavaUtil.convertJavaArrayToRuby(runtime, commandline.scriptArguments));
        runtime.setVerbose(runtime.newBoolean(commandline.verbose));

        // $VERBOSE can be true, false, or nil.  Any non-false-nil value will get stored as true  
        runtime.getGlobalVariables().define("$VERBOSE", new IAccessor() {
            public IRubyObject getValue() {
                return runtime.getVerbose();
            }
            
            public IRubyObject setValue(IRubyObject newValue) {
                if (newValue.isNil()) {
                    runtime.setVerbose(newValue);
                } else {
                    runtime.setVerbose(runtime.newBoolean(newValue != runtime.getFalse()));
                }
            	
                return newValue;
            }
        });
        runtime.getClasses().getObjectClass().setConstant("$VERBOSE", 
        		commandline.verbose ? runtime.getTrue() : runtime.getNil());
        runtime.defineGlobalConstant("ARGV", argumentArray);

        // I guess ENV is not a hash, but should support a to_hash, though
        // it supposedly supports methods of a Hash?  Also, I think that
        // RubyGlobal may need to create an empty ENV var in the case that
        // the runtime is not initialized by Main.
        Properties envs = new Properties();
        runtime.defineGlobalConstant("ENV", RubyHash.newHash(runtime, envs, null));

        defineGlobal(runtime, "$-p", commandline.assumePrinting);
        defineGlobal(runtime, "$-n", commandline.assumeLoop);
        defineGlobal(runtime, "$-a", commandline.sDoSplit);
        defineGlobal(runtime, "$-l", commandline.processLineEnds);
        runtime.getGlobalVariables().defineReadonly("$*", new ValueAccessor(argumentArray));
        // TODO this is a fake cause we have no real process number in Java
        runtime.getGlobalVariables().defineReadonly("$$", new ValueAccessor(runtime.newFixnum(runtime.hashCode())));
        runtime.defineVariable(new RubyGlobal.StringGlobalVariable(runtime, "$0", runtime.newString(filename)));
        runtime.getLoadService().init(commandline.loadPaths());
        Iterator iter = commandline.requiredLibraries().iterator();
        while (iter.hasNext()) {
            String scriptName = (String) iter.next();
            RubyKernel.require(runtime.getTopSelf(), runtime.newString(scriptName));
        }
    }

    private static void defineGlobal(Ruby runtime, String name, boolean value) {
        runtime.getGlobalVariables().defineReadonly(name, new ValueAccessor(value ? runtime.getTrue() : runtime.getNil()));
    }

}
