/*
 * RubyKernel.java
 * Created on May 2, 2002
 *
 * Copyright (C) 2001-2002 Jan Arne Petersen, Alan Moore, Benoit Cerrina,
 * Chad Fowler, Anders Bengtsson
 * Copyright (C) 2002-2004 Thomas E Enebo
 * Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Alan Moore <alan_moore@gmx.net>
 * Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Chad Fowler <chadfowler@chadfowler.com>
 * Anders Bengtsson <ndrsbngtssn@yahoo.se>
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

import org.jruby.exceptions.ArgumentError;
import org.jruby.exceptions.EOFError;
import org.jruby.exceptions.IOError;
import org.jruby.exceptions.NotImplementedError;
import org.jruby.exceptions.RaiseException;
import org.jruby.exceptions.SystemExit;
import org.jruby.exceptions.ThreadError;
import org.jruby.exceptions.ThrowJump;
import org.jruby.exceptions.TypeError;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.load.IAutoloadMethod;
import org.jruby.runtime.load.ILoadService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;

/**
 *
 * @author jpetersen
 * @version $Revision$
 */
public class KernelModule {
    public static RubyModule createKernelModule(Ruby ruby) {
        RubyModule kernelModule = ruby.defineModule("Kernel");
        CallbackFactory callbackFactory = ruby.callbackFactory();

        kernelModule.defineMethod("Array", callbackFactory.getSingletonMethod(KernelModule.class, "new_array", IRubyObject.class));
        kernelModule.defineMethod("autoload", callbackFactory.getSingletonMethod(KernelModule.class, "autoload", IRubyObject.class, IRubyObject.class));
        kernelModule.defineMethod("`", callbackFactory.getSingletonMethod(KernelModule.class, "backquote", IRubyObject.class));
        kernelModule.defineMethod("block_given?", callbackFactory.getSingletonMethod(KernelModule.class, "block_given"));
        kernelModule.defineMethod("iterator?", callbackFactory.getSingletonMethod(KernelModule.class, "block_given"));
        kernelModule.defineMethod("caller", callbackFactory.getOptSingletonMethod(KernelModule.class, "caller"));
        kernelModule.defineMethod("catch", callbackFactory.getSingletonMethod(KernelModule.class, "rbCatch", IRubyObject.class));
        kernelModule.defineMethod("chomp", callbackFactory.getOptSingletonMethod(KernelModule.class, "chomp"));
        kernelModule.defineMethod("chomp!", callbackFactory.getOptSingletonMethod(KernelModule.class, "chomp_bang"));
        kernelModule.defineMethod("chop", callbackFactory.getSingletonMethod(KernelModule.class, "chop"));
        kernelModule.defineMethod("chop!", callbackFactory.getSingletonMethod(KernelModule.class, "chop_bang"));
        kernelModule.defineMethod("eval", callbackFactory.getOptSingletonMethod(KernelModule.class, "eval"));
        kernelModule.defineMethod("exit", callbackFactory.getOptSingletonMethod(KernelModule.class, "exit"));
        kernelModule.defineMethod("Float", callbackFactory.getSingletonMethod(KernelModule.class, "new_float", IRubyObject.class));
        kernelModule.defineMethod("format", callbackFactory.getOptSingletonMethod(KernelModule.class, "sprintf"));
        kernelModule.defineMethod("gets", callbackFactory.getOptSingletonMethod(KernelModule.class, "gets"));
        kernelModule.defineMethod("global_variables", callbackFactory.getSingletonMethod(KernelModule.class, "global_variables"));
        kernelModule.defineMethod("gsub", callbackFactory.getOptSingletonMethod(KernelModule.class, "gsub"));
        kernelModule.defineMethod("gsub!", callbackFactory.getOptSingletonMethod(KernelModule.class, "gsub_bang"));
        kernelModule.defineMethod("Integer", callbackFactory.getSingletonMethod(KernelModule.class, "new_integer", IRubyObject.class));
        kernelModule.defineMethod("lambda", callbackFactory.getSingletonMethod(KernelModule.class, "proc"));
        kernelModule.defineMethod("load", callbackFactory.getOptSingletonMethod(KernelModule.class, "load"));
        kernelModule.defineMethod("local_variables", callbackFactory.getSingletonMethod(KernelModule.class, "local_variables"));
        kernelModule.defineMethod("loop", callbackFactory.getSingletonMethod(KernelModule.class, "loop"));
        kernelModule.defineMethod("open", callbackFactory.getOptSingletonMethod(KernelModule.class, "open"));
        kernelModule.defineMethod("p", callbackFactory.getOptSingletonMethod(KernelModule.class, "p"));
        kernelModule.defineMethod("puts", callbackFactory.getOptSingletonMethod(KernelModule.class, "puts"));
        kernelModule.defineMethod("print", callbackFactory.getOptSingletonMethod(KernelModule.class, "print"));
        kernelModule.defineMethod("printf", callbackFactory.getOptSingletonMethod(KernelModule.class, "printf"));
        kernelModule.defineMethod("proc", callbackFactory.getSingletonMethod(KernelModule.class, "proc"));
        kernelModule.defineMethod("raise", callbackFactory.getOptSingletonMethod(KernelModule.class, "raise"));
        kernelModule.defineMethod("rand", callbackFactory.getOptSingletonMethod(KernelModule.class, "rand"));
        kernelModule.defineMethod("readline", callbackFactory.getOptSingletonMethod(KernelModule.class, "readline"));
        kernelModule.defineMethod("readlines", callbackFactory.getOptSingletonMethod(KernelModule.class, "readlines"));
        kernelModule.defineMethod("require", callbackFactory.getSingletonMethod(KernelModule.class, "require", IRubyObject.class));
        kernelModule.defineMethod("scan", callbackFactory.getSingletonMethod(KernelModule.class, "scan", IRubyObject.class));
        kernelModule.defineMethod("set_trace_func", callbackFactory.getSingletonMethod(KernelModule.class, "set_trace_func", IRubyObject.class));
        kernelModule.defineMethod("singleton_method_added", callbackFactory.getSingletonMethod(KernelModule.class, "singleton_method_added", IRubyObject.class));
        kernelModule.defineMethod("sleep", callbackFactory.getSingletonMethod(KernelModule.class, "sleep", IRubyObject.class));
        kernelModule.defineMethod("split", callbackFactory.getOptSingletonMethod(KernelModule.class, "split"));
        kernelModule.defineMethod("sprintf", callbackFactory.getOptSingletonMethod(KernelModule.class, "sprintf"));
        kernelModule.defineMethod("srand", callbackFactory.getOptSingletonMethod(KernelModule.class, "srand"));
        kernelModule.defineMethod("String", callbackFactory.getSingletonMethod(KernelModule.class, "new_string", IRubyObject.class));
        kernelModule.defineMethod("sub", callbackFactory.getOptSingletonMethod(KernelModule.class, "sub"));
        kernelModule.defineMethod("sub!", callbackFactory.getOptSingletonMethod(KernelModule.class, "sub_bang"));
        kernelModule.defineMethod("system", callbackFactory.getOptSingletonMethod(KernelModule.class, "system"));
        kernelModule.defineMethod("throw", callbackFactory.getOptSingletonMethod(KernelModule.class, "rbThrow"));

        return kernelModule;
    }

    public static IRubyObject autoload(IRubyObject recv, IRubyObject symbol, final IRubyObject file) {
        final ILoadService loadService = recv.getRuntime().getLoadService();
        loadService.addAutoload(symbol.asSymbol(), new IAutoloadMethod() {
            /**
             * @see org.jruby.runtime.load.IAutoloadMethod#load(Ruby, String)
             */
            public IRubyObject load(Ruby runtime, String name) {
                loadService.require(file.toString());
                return runtime.getClasses().getObjectClass().getConstant(name);
            }
        });
        return recv;
    }

    public static IRubyObject open(IRubyObject recv, IRubyObject[] args) {
        if (args[0].toString().startsWith("|")) {
            // +++
            return recv.getRuntime().getNil();
            // ---
        }
        return RubyFile.open(recv.getRuntime().getClass("File"), args);
    }

    public static RubyString gets(IRubyObject recv, IRubyObject[] args) {
        RubyArgsFile argsFile = (RubyArgsFile) recv.getRuntime().getGlobalVariables().get("$<");

        RubyString line = argsFile.internalGets(args);

        recv.getRuntime().setLastline(line);

        return line;
    }

    public static IRubyObject new_array(IRubyObject recv, IRubyObject object) {
        return object.callMethod("to_a");
    }
    
    public static IRubyObject new_float(IRubyObject recv, IRubyObject object) {
        return object.callMethod("to_f");
    }
    
    public static IRubyObject new_integer(IRubyObject recv, IRubyObject object) {
        return object.callMethod("to_i");
    }
    
    public static IRubyObject new_string(IRubyObject recv, IRubyObject object) {
        return object.callMethod("to_s");
    }
    
    
    public static IRubyObject p(IRubyObject recv, IRubyObject args[]) {
        IRubyObject defout = recv.getRuntime().getGlobalVariables().get("$>");

        for (int i = 0; i < args.length; i++) {
            if (args[i] != null) {
                defout.callMethod("write", args[i].callMethod("inspect"));
                defout.callMethod("write", RubyString.newString(recv.getRuntime(), "\n"));
            }
        }
        return recv.getRuntime().getNil();
    }

    public static IRubyObject puts(IRubyObject recv, IRubyObject args[]) {
        IRubyObject defout = recv.getRuntime().getGlobalVariables().get("$>");

        RubyIO.puts(defout, args);

        return recv.getRuntime().getNil();
    }

    public static IRubyObject print(IRubyObject recv, IRubyObject args[]) {
        IRubyObject defout = recv.getRuntime().getGlobalVariables().get("$>");

        RubyIO.print(defout, args);

        return recv.getRuntime().getNil();
    }

    public static IRubyObject printf(IRubyObject recv, IRubyObject args[]) {
        if (args.length != 0) {
            IRubyObject defout = recv.getRuntime().getGlobalVariables().get("$>");

            if (!(args[0] instanceof RubyString)) {
                defout = args[0];

                IRubyObject[] newArgs = new IRubyObject[args.length - 1];
                System.arraycopy(args, 1, newArgs, 0, args.length - 1);
                args = newArgs;
            }

            RubyIO.printf(defout, args);
        }

        return recv.getRuntime().getNil();
    }

    public static RubyString readline(IRubyObject recv, IRubyObject[] args) {
        RubyString line = gets(recv, args);

        if (line.isNil()) {
            throw new EOFError(recv.getRuntime());
        }

        return line;
    }

    public static RubyArray readlines(IRubyObject recv, IRubyObject[] args) {
        RubyArgsFile argsFile = (RubyArgsFile) recv.getRuntime().getGlobalVariables().get("$<");

        RubyArray lines = RubyArray.newArray(recv.getRuntime());

        RubyString line = argsFile.internalGets(args);
        while (!line.isNil()) {
            lines.append(line);

            line = argsFile.internalGets(args);
        }

        return lines;
    }

    /** Returns value of $_.
     *
     * @throws TypeError if $_ is not a String or nil.
     * @return value of $_ as String.
     */
    private static RubyString getLastlineString(Ruby ruby) {
        IRubyObject line = ruby.getLastline();

        if (line.isNil()) {
            throw new TypeError(ruby, "$_ value need to be String (nil given).");
        } else if (!(line instanceof RubyString)) {
            throw new TypeError(ruby, "$_ value need to be String (" + line.getMetaClass().toName() + " given).");
        } else {
            return (RubyString) line;
        }
    }

    public static IRubyObject sub_bang(IRubyObject recv, IRubyObject args[]) {
        return getLastlineString(recv.getRuntime()).sub_bang(args);
    }

    public static IRubyObject sub(IRubyObject recv, IRubyObject args[]) {
        RubyString str = (RubyString) getLastlineString(recv.getRuntime()).dup();

        if (!str.sub_bang(args).isNil()) {
            recv.getRuntime().setLastline(str);
        }

        return str;
    }

    public static IRubyObject gsub_bang(IRubyObject recv, IRubyObject args[]) {
        return getLastlineString(recv.getRuntime()).gsub_bang(args);
    }

    public static IRubyObject gsub(IRubyObject recv, IRubyObject args[]) {
        RubyString str = (RubyString) getLastlineString(recv.getRuntime()).dup();

        if (!str.gsub_bang(args).isNil()) {
            recv.getRuntime().setLastline(str);
        }

        return str;
    }

    public static IRubyObject chop_bang(IRubyObject recv) {
        return getLastlineString(recv.getRuntime()).chop_bang();
    }

    public static IRubyObject chop(IRubyObject recv) {
        RubyString str = getLastlineString(recv.getRuntime());

        if (str.getValue().length() > 0) {
            str = (RubyString) str.dup();
            str.chop_bang();
            recv.getRuntime().setLastline(str);
        }

        return str;
    }

    public static IRubyObject chomp_bang(IRubyObject recv, IRubyObject[] args) {
        return getLastlineString(recv.getRuntime()).chomp_bang(args);
    }

    public static IRubyObject chomp(IRubyObject recv, IRubyObject[] args) {
        RubyString str = getLastlineString(recv.getRuntime());
        RubyString dup = (RubyString) str.dup();

        if (dup.chomp_bang(args).isNil()) {
            return str;
        } else {
            recv.getRuntime().setLastline(dup);
            return str;
        }
    }

    public static IRubyObject split(IRubyObject recv, IRubyObject[] args) {
        return getLastlineString(recv.getRuntime()).split(args);
    }

    public static IRubyObject scan(IRubyObject recv, IRubyObject pattern) {
        return getLastlineString(recv.getRuntime()).scan(pattern);
    }

    public static IRubyObject sleep(IRubyObject recv, IRubyObject seconds) {
        try {
            Thread.sleep((long) (RubyNumeric.numericValue(seconds).getDoubleValue() * 1000.0));
        } catch (InterruptedException iExcptn) {
        }

        return recv;
    }

    public static IRubyObject exit(IRubyObject recv, IRubyObject args[]) {
        recv.getRuntime().secure(4);

        int status = 0;
        if (args.length > 0) {
            status = RubyNumeric.fix2int(args[0]);
        }

        throw new SystemExit(recv.getRuntime(), status);
    }

    /** Returns an Array with the names of all global variables.
     *
     */
    public static RubyArray global_variables(IRubyObject recv) {
        RubyArray globalVariables = RubyArray.newArray(recv.getRuntime());

        Iterator iter = recv.getRuntime().getGlobalVariables().getNames();
        while (iter.hasNext()) {
            String globalVariableName = (String) iter.next();

            globalVariables.append(RubyString.newString(recv.getRuntime(), globalVariableName));
        }

        return globalVariables;
    }

    /** Returns an Array with the names of all local variables.
     *
     */
    public static RubyArray local_variables(IRubyObject recv) {
        final Ruby runtime = recv.getRuntime();
        RubyArray localVariables = RubyArray.newArray(runtime);

        if (runtime.getScope().getLocalNames() != null) {
            for (int i = 2; i < runtime.getScope().getLocalNames().size(); i++) {
                if (runtime.getScope().getLocalNames().get(i) != null) {
                    localVariables.append(RubyString.newString(runtime, (String) runtime.getScope().getLocalNames().get(i)));
                }
            }
        }

        Iterator dynamicNames = runtime.getDynamicNames().iterator();
        while (dynamicNames.hasNext()) {
            String name = (String) dynamicNames.next();
            localVariables.append(RubyString.newString(runtime, name));
        }

        return localVariables;
    }

    public static RubyBoolean block_given(IRubyObject recv) {
        return RubyBoolean.newBoolean(recv.getRuntime(), recv.getRuntime().isFBlockGiven());
    }

    public static IRubyObject sprintf(IRubyObject recv, IRubyObject args[]) {
        if (args.length == 0) {
            throw new ArgumentError(recv.getRuntime(), "sprintf must have at least one argument");
        }

        RubyString str = RubyString.stringValue(args[0]);

        RubyArray newArgs = RubyArray.newArray(recv.getRuntime(), args);
        newArgs.shift();

        return str.format(newArgs);
    }

    public static IRubyObject raise(IRubyObject recv, IRubyObject args[]) {
        Ruby runtime = recv.getRuntime();
        switch (args.length) {
        case 0 :
            IRubyObject defaultException = runtime.getGlobalVariables().get("$!");
            if (defaultException.isNil()) {
                throw new RaiseException(runtime, runtime.getExceptions().getRuntimeError(), "");
            }
            throw new RaiseException((RubyException) defaultException);
        case 1 :
            if (args[0] instanceof RubyException) {
                throw new RaiseException((RubyException) args[0]);
            } else {
                throw new RaiseException(RubyException.newInstance(runtime.getExceptions().getRuntimeError(), args));
            }
        case 2 :
            RubyException excptn = (RubyException) args[0].callMethod("exception", args[1]);
            throw new RaiseException(excptn);
        default :
            throw new ArgumentError(runtime, "wrong # of arguments");
        }
    }

    /**
     * Require.
     * MRI allows to require ever .rb files or ruby extension dll (.so or .dll depending on system).
     * we allow requiring either .rb files or jars.
     * @param recv ruby object used to call require (any object will do and it won't be used anyway).
     * @param name the name of the file to require
     **/
    public static IRubyObject require(IRubyObject recv, IRubyObject name) {
        if (recv.getRuntime().getLoadService().require(name.toString())) {
            return recv.getRuntime().getTrue();
        }
        return recv.getRuntime().getFalse();
    }

    public static IRubyObject load(IRubyObject recv, IRubyObject[] args) {
        RubyString file = (RubyString)args[0];
        if (recv.getRuntime().getLoadService().load(file.toString())) {
            return recv.getRuntime().getTrue();
        }
        return recv.getRuntime().getFalse();
    }

    public static IRubyObject eval(IRubyObject recv, IRubyObject[] args) {
        Ruby runtime = recv.getRuntime();
        RubyString src = (RubyString) args[0];
        IRubyObject scope = args.length > 1 ? args[1] : runtime.getNil();

        String file = "(eval)";
        if (args.length > 2) {
            file = args[2].toString();
        }
        int line = 1;
        if (args.length > 3) {
            line = RubyFixnum.fix2int(args[3]);
        }
        src.checkSafeString();

        if (scope.isNil() && runtime.getFrameStack().getPrevious() != null) {
            try {
                runtime.getFrameStack().push(runtime.getFrameStack().getPrevious());
                return recv.eval(src, scope, file, line);
            } finally {
                runtime.getFrameStack().pop();
            }
        }
        return recv.eval(src, scope, file, line);
    }

    public static IRubyObject caller(IRubyObject recv, IRubyObject[] args) {
        int level = args.length > 0 ? RubyFixnum.fix2int(args[0]) : 1;

        if (level < 0) {
            throw new ArgumentError(recv.getRuntime(), "negative level(" + level + ')');
        }

        return RaiseException.createBacktrace(recv.getRuntime(), level);
    }

    public static IRubyObject rbCatch(IRubyObject recv, IRubyObject tag) {
        try {
            return recv.getRuntime().yield(tag);
        } catch (ThrowJump throwJump) {
            if (throwJump.getTag().equals(tag.asSymbol())) {
                return throwJump.getValue();
            } else {
                throw throwJump;
            }
        }
    }

    public static IRubyObject rbThrow(IRubyObject recv, IRubyObject[] args) {
        throw new ThrowJump(args[0].asSymbol(), args.length > 1 ? args[1] : recv.getRuntime().getNil());
    }

    public static IRubyObject set_trace_func(IRubyObject recv, IRubyObject trace_func) {
        if (trace_func.isNil()) {
            recv.getRuntime().setTraceFunction(null);
        } else if (!(trace_func instanceof RubyProc)) {
            throw new TypeError(recv.getRuntime(), "trace_func needs to be Proc.");
        } else {
            recv.getRuntime().setTraceFunction((RubyProc) trace_func);
        }
        return trace_func;
    }

    public static IRubyObject singleton_method_added(IRubyObject recv, IRubyObject symbolId) {
        return recv.getRuntime().getNil();
    }

    public static RubyProc proc(IRubyObject recv) {
        return RubyProc.newProc(recv.getRuntime());
    }

    public static IRubyObject loop(IRubyObject recv) {
        while (true) {
            recv.getRuntime().yield(recv.getRuntime().getNil());

            Thread.yield();
        }
    }

    public static IRubyObject backquote(IRubyObject recv, IRubyObject aString) {
        StringBuffer output = new StringBuffer();
        runInShell(recv.getRuntime(), aString.toString(), output);
        return RubyString.newString(recv.getRuntime(), output.toString());
    }

    private static int runInShell(Ruby runtime, String command, StringBuffer output) {
        try {
            String shell = System.getProperty("jruby.shell");
            Process aProcess;
            String shellSwitch = "-c";
            if (shell != null) {
                if (!shell.endsWith("sh")) {
                    shellSwitch = "/c";
                }
                aProcess = Runtime.getRuntime().exec(new String[] { shell, shellSwitch, command });
            } else {
                aProcess = Runtime.getRuntime().exec(command);
            }

            final BufferedReader reader = new BufferedReader(new InputStreamReader(aProcess.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
            return aProcess.waitFor();

        } catch (IOException e) {
            throw IOError.fromException(runtime, e);
        } catch (InterruptedException e) {
            throw new ThreadError(runtime, "unexpected interrupt");
        }
    }

    public static RubyInteger srand(IRubyObject recv, IRubyObject[] args) {

        long oldRandomSeed = recv.getRuntime().randomSeed;
        if (args.length > 0) {
            RubyInteger integerSeed = (RubyInteger) args[0].convertToType("Integer", "to_i", true);
            recv.getRuntime().randomSeed = integerSeed.getLongValue();
        } else {
            recv.getRuntime().randomSeed = System.currentTimeMillis(); // FIXME
        }
        recv.getRuntime().random.setSeed(recv.getRuntime().randomSeed);
        return RubyFixnum.newFixnum(recv.getRuntime(), oldRandomSeed);
    }

    public static RubyNumeric rand(IRubyObject recv, IRubyObject[] args) {
        long ceil;
        if (args.length == 0) {
            ceil = 0;
        } else if (args.length == 1) {
            RubyInteger integerCeil = (RubyInteger) args[0].convertToType("Integer", "to_i", true);
            ceil = integerCeil.getLongValue();
            ceil = Math.abs(ceil);
            if (ceil > Integer.MAX_VALUE) {
                throw new NotImplementedError(recv.getRuntime(), "Random values larger than Integer.MAX_VALUE not supported");
            }
        } else {
            throw new ArgumentError(recv.getRuntime(), "wrong # of arguments(" + args.length + " for 1)");
        }

        if (ceil == 0) {
            double result = recv.getRuntime().random.nextDouble();
            return RubyFloat.newFloat(recv.getRuntime(), result);
        } else {
            return RubyFixnum.newFixnum(recv.getRuntime(), recv.getRuntime().random.nextInt((int) ceil));
        }
    }

    public static RubyBoolean system(IRubyObject recv, IRubyObject[] args) {
        Ruby runtime = recv.getRuntime();
        if (args.length > 1) {
            throw new ArgumentError(runtime, "more arguments not yet supported");
        }
        StringBuffer output = new StringBuffer();
        int resultCode = runInShell(runtime, args[0].toString(), output);
        recv.getRuntime().getGlobalVariables().set("$?", RubyFixnum.newFixnum(runtime, resultCode));
        return RubyBoolean.newBoolean(runtime, resultCode == 0);
    }
}
