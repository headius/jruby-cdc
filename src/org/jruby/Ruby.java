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
 * Copyright (C) 2001 Chad Fowler <chadfowler@chadfowler.com>
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;

import org.jruby.ast.Node;
import org.jruby.common.RubyWarnings;
import org.jruby.exceptions.ArgumentError;
import org.jruby.exceptions.BreakJump;
import org.jruby.exceptions.IOError;
import org.jruby.exceptions.IndexError;
import org.jruby.exceptions.RetryJump;
import org.jruby.exceptions.ReturnJump;
import org.jruby.exceptions.SecurityError;
import org.jruby.exceptions.TypeError;
import org.jruby.internal.runtime.GlobalVariables;
import org.jruby.internal.runtime.ThreadService;
import org.jruby.internal.runtime.ValueAccessor;
import org.jruby.internal.runtime.methods.IterateMethod;
import org.jruby.javasupport.JavaSupport;
import org.jruby.lexer.yacc.SourcePosition;
import org.jruby.parser.Parser;
import org.jruby.runtime.BlockStack;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.DynamicVariableSet;
import org.jruby.runtime.Frame;
import org.jruby.runtime.FrameStack;
import org.jruby.runtime.GlobalVariable;
import org.jruby.runtime.IAccessor;
import org.jruby.runtime.Iter;
import org.jruby.runtime.LastCallStatus;
import org.jruby.runtime.ObjectSpace;
import org.jruby.runtime.RubyExceptions;
import org.jruby.runtime.Scope;
import org.jruby.runtime.ScopeStack;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.callback.Callback;
import org.jruby.runtime.load.ILoadService;
import org.jruby.runtime.load.LoadServiceFactory;
import org.jruby.util.collections.ArrayStack;

/**
 * The jruby runtime.
 *
 * @author  jpetersen
 * @version $Revision$
 * @since   0.1
 */
public final class Ruby {
    private ThreadService threadService = new ThreadService(this);

    public int stackTraces = 0;

    public ObjectSpace objectSpace = new ObjectSpace();

    public final RubyFixnum[] fixnumCache = new RubyFixnum[256];
    public final RubySymbol.SymbolTable symbolTable = new RubySymbol.SymbolTable();
    public Hashtable ioHandlers = new Hashtable();
    public long randomSeed = 0;
    public long randomSeedSequence = 0;
    public Random random = new Random();

    private RubyProc traceFunction;
    private boolean isWithinTrace = false;

    /** safe-level:
    		0 - strings from streams/environment/ARGV are tainted (default)
    		1 - no dangerous operation by tainted value
    		2 - process/file operations prohibited
    		3 - all genetated objects are tainted
    		4 - no global (non-tainted) variable modification/no direct output
    */
    private int safeLevel = 0;

    // Default objects
    private IRubyObject nilObject;
    private RubyBoolean trueObject;
    private RubyBoolean falseObject;

    // Default classes
    private RubyClasses classes;
    private RubyExceptions exceptions;

    private IRubyObject topSelf;

    private IRubyObject verbose;

    // Java support
    private JavaSupport javaSupport;

    private Parser parser = new Parser(this);

    private LastCallStatus lastCallStatus = new LastCallStatus(this);

    private ILoadService loadService = LoadServiceFactory.createLoadService(this);
    private GlobalVariables globalVariables = new GlobalVariables(this);
    private RubyWarnings warnings = new RubyWarnings(this);

    // Contains a list of all blocks (as Procs) that should be called when
    // the runtime environment exits.
    private ArrayStack atExitBlocks = new ArrayStack();

    /**
     * Create and initialize a new jruby Runtime.
     */
    private Ruby() {
        nilObject = new RubyNil(this);
        trueObject = new RubyBoolean(this, true);
        falseObject = new RubyBoolean(this, false);

        verbose = falseObject;
        
        javaSupport = new JavaSupport(this);
    }

    /**
     * Returns a default instance of the JRuby runtime.
     *
     * @return the JRuby runtime
     */
    public static Ruby getDefaultInstance() {
        Ruby runtime = new Ruby();
        runtime.init();
        return runtime;
    }

    /**
     * Evaluates a script and returns a RubyObject.
     */
    public IRubyObject evalScript(String script) {
        return eval(parse(script, "<script>"));
    }

    public IRubyObject eval(Node node) {
        return getCurrentContext().eval(node);
    }

    public RubyClasses getClasses() {
        return classes;
    }

    /** Returns the "true" instance from the instance pool.
     * @return The "true" instance.
     */
    public RubyBoolean getTrue() {
        return trueObject;
    }

    /** Returns the "false" instance from the instance pool.
     * @return The "false" instance.
     */
    public RubyBoolean getFalse() {
        return falseObject;
    }

    /** Returns the "nil" singleton instance.
     * @return "nil"
     */
    public IRubyObject getNil() {
        return nilObject;
    }

    public RubyModule getModule(String name) {
        return classes.getClass(name);
    }

    /** Returns a class from the instance pool.
     *
     * @param name The name of the class.
     * @return The class.
     */
    public RubyClass getClass(String name) {
        try {
            return (RubyClass) getModule(name); 
        } catch (ClassCastException e) {
            throw newTypeError(name + " is not a Class");
        }
    }

    /** Define a new class with name 'name' and super class 'superClass'.
     *
     * MRI: rb_define_class / rb_define_class_id
     *
     */
    public RubyClass defineClass(String name, RubyClass superClass) {
        return defineClassUnder(name, superClass, getClasses().getObjectClass());
    }
    
    public RubyClass defineClassUnder(String name, RubyClass superClass, RubyModule parentModule) {
        if (superClass == null) {
            superClass = getClasses().getObjectClass();
        }

        return superClass.newSubClass(name, parentModule);
    }
    
    /** rb_define_module / rb_define_module_id
     *
     */
    public RubyModule defineModule(String name) {
        return defineModuleUnder(name, getClasses().getObjectClass());
    }
    
    public RubyModule defineModuleUnder(String name, RubyModule parentModule) {
        RubyModule newModule = RubyModule.newModule(this, name, parentModule);

        getClasses().putClass(name, newModule);
        
        return newModule;
    }
    
    /**
     * In the current context, get the named module. If it doesn't exist a
     * new module is created.
     */
    public RubyModule getOrCreateModule(String name) {
        RubyModule module = (RubyModule) getRubyClass().getConstant(name, false);
        
        if (module == null) {
            module = (RubyModule) getRubyClass().setConstant(name, 
            		defineModule(name)); 
        } else if (getSafeLevel() >= 4) {
        	throw new SecurityError(this, "Extending module prohibited.");
        }

        if (getWrapper() != null) {
            module.getSingletonClass().includeModule(getWrapper());
            module.includeModule(getWrapper());
        }
        return module;
    }
    

    /** Getter for property securityLevel.
     * @return Value of property securityLevel.
     */
    public int getSafeLevel() {
        return this.safeLevel;
    }

    /** Setter for property securityLevel.
     * @param safeLevel New value of property securityLevel.
     */
    public void setSafeLevel(int safeLevel) {
        this.safeLevel = safeLevel;
    }

    public void secure(int level) {
        if (level <= safeLevel) {
            throw new SecurityError(this, "Insecure operation '" + getCurrentFrame().getLastFunc() + "' at level " + safeLevel);
        }
    }

    /** rb_define_global_const
     *
     */
    public void defineGlobalConstant(String name, IRubyObject value) {
        getClasses().getObjectClass().defineConstant(name, value);
    }

    public IRubyObject getTopConstant(String name) {
        IRubyObject constant = getModule(name);
        if (constant == null) {
            constant = getLoadService().autoload(name);
        }
        return constant;
    }

    public boolean isClassDefined(String name) {
        return getModule(name) != null;
    }

    public IRubyObject yield(IRubyObject value) {
        return yield(value, null, null, false);
    }

    public IRubyObject yield(IRubyObject value, IRubyObject self, RubyModule klass, boolean checkArguments) {
        return getCurrentContext().yield(value, self, klass, false, checkArguments);
    }

    private Scope currentScope() {
        return getScope().current();
    }

    /** Getter for property rubyTopSelf.
     * @return Value of property rubyTopSelf.
     */
    public IRubyObject getTopSelf() {
        return topSelf;
    }

    /** rb_iterate
     *
     */
    public IRubyObject iterate(Callback iterateMethod, IRubyObject data1, Callback blockMethod, IRubyObject data2) {
        getIterStack().push(Iter.ITER_PRE);
        getBlockStack().push(null, new IterateMethod(blockMethod, data2), getTopSelf());

        try {
            while (true) {
                try {
                    return iterateMethod.execute(data1, null);
                } catch (BreakJump bExcptn) {
                    IRubyObject breakValue = bExcptn.getBreakValue();
                    
                    return breakValue == null ? getNil() : breakValue;
                } catch (ReturnJump rExcptn) {
                    return rExcptn.getReturnValue();
                } catch (RetryJump rExcptn) {
                    // Execute iterateMethod again.
                }
            }
        } finally {
            getIterStack().pop();
            getBlockStack().pop();
        }
    }

    /** ruby_init
     *
     */
    private void init() {
        getIterStack().push(Iter.ITER_NOT);
        getFrameStack().push();
        getScope().push();

        setCurrentVisibility(Visibility.PRIVATE);

        classes = new RubyClasses(this);
        classes.initCoreClasses();

        RubyGlobal.createGlobals(this);

        exceptions = new RubyExceptions(this);
        exceptions.initDefaultExceptionClasses();

        topSelf = TopSelfFactory.createTopSelf(this);

        getCurrentContext().pushClass(getClasses().getObjectClass());
        getCurrentFrame().setSelf(topSelf);

        classes.initBuiltinClasses();
    }

    /** Getter for property rubyScope.
     * @return Value of property rubyScope.
     */
    public ScopeStack getScope() {
        return getCurrentContext().getScopeStack();
    }

    /** Getter for property sourceFile.
     * @return Value of property sourceFile.
     */
    public String getSourceFile() {
        return getPosition().getFile();
    }

    /** Getter for property sourceLine.
     * @return Value of property sourceLine.
     */
    public int getSourceLine() {
        return getPosition().getLine();
    }

    /** Getter for property isVerbose.
     * @return Value of property isVerbose.
     */
    public IRubyObject getVerbose() {
        return verbose;
    }

    public boolean isBlockGiven() {
        return getCurrentFrame().isBlockGiven();
    }

    public boolean isFBlockGiven() {
        Frame previous = getFrameStack().getPrevious();
        if (previous == null) {
            return false;
        }
        return previous.isBlockGiven();
    }

    /** Setter for property isVerbose.
     * @param verbose New value of property isVerbose.
     */
    public void setVerbose(IRubyObject verbose) {
        this.verbose = verbose;
    }

    /** Getter for property dynamicVars.
     * @return Value of property dynamicVars.
     */
    public DynamicVariableSet getDynamicVars() {
        return getCurrentContext().getCurrentDynamicVars();
    }

    public RubyModule getRubyClass() {
        return getCurrentContext().getRubyClass();
    }

    public FrameStack getFrameStack() {
        return getCurrentContext().getFrameStack();
    }

    public Frame getCurrentFrame() {
        return getCurrentContext().getCurrentFrame();
    }

    public JavaSupport getJavaSupport() {
        return javaSupport;
    }

    public ArrayStack getIterStack() {
        return getCurrentContext().getIterStack();
    }

    public BlockStack getBlockStack() {
        return getCurrentContext().getBlockStack();
    }

    public Visibility getCurrentVisibility() {
        return currentScope().getVisibility();
    }

    public void setCurrentVisibility(Visibility visibility) {
        currentScope().setVisibility(visibility);
    }

    /** Getter for property wrapper.
     * @return Value of property wrapper.
     */
    public RubyModule getWrapper() {
        return getCurrentContext().getWrapper();
    }

    /**
     * Gets the exceptions
     * @return Returns a RubyExceptions
     */
    public RubyExceptions getExceptions() {
        return exceptions;
    }

    /** Defines a global variable
     */
    public void defineVariable(final GlobalVariable variable) {
        globalVariables.define(variable.name(), new IAccessor() {
            public IRubyObject getValue() {
                return variable.get();
            }

            public IRubyObject setValue(IRubyObject newValue) {
                return variable.set(newValue);
            }
        });
    }

    /** defines a readonly global variable
     *
     */
    public void defineReadonlyVariable(String name, IRubyObject value) {
        globalVariables.defineReadonly(name, new ValueAccessor(value));
    }

    public Node parse(Reader content, String file) {
        return parser.parse(file, content);
    }

    public Node parse(String content, String file) {
        return parser.parse(file, content);
    }

    public IRubyObject getLastline() {
        return getScope().getLastLine();
    }

    public void setLastline(IRubyObject value) {
        getScope().setLastLine(value);
    }

    public IRubyObject getBackref() {
        return getScope().getBackref();
    }

    public Parser getParser() {
        return parser;
    }

    public ThreadService getThreadService() {
        return threadService;
    }

    public ThreadContext getCurrentContext() {
        return threadService.getCurrentContext();
    }

    public SourcePosition getPosition() {
        return getCurrentContext().getPosition();
    }

    public void setPosition(String file, int line) {
        getCurrentContext().setPosition(file, line);
    }

    public void setPosition(SourcePosition position) {
        getCurrentContext().setPosition(position);
    }

    public List getDynamicNames() {
        return getDynamicVars().names();
    }

    /**
     * Returns the lastCallStatus.
     * @return LastCallStatus
     */
    public LastCallStatus getLastCallStatus() {
        return lastCallStatus;
    }

    /**
     * Returns the loadService.
     * @return ILoadService
     */
    public ILoadService getLoadService() {
        return loadService;
    }

    public RubyWarnings getWarnings() {
        return warnings;
    }

    public PrintStream getErrorStream() {
        return new PrintStream(((RubyIO) getGlobalVariables().get("$stderr")).getOutStream());
    }

    public InputStream getInputStream() {
        return ((RubyIO) getGlobalVariables().get("$stdin")).getInStream();
    }

    public PrintStream getOutputStream() {
        return new PrintStream(((RubyIO) getGlobalVariables().get("$stdout")).getOutStream());
    }

    private static final int TRACE_HEAD = 8;
    private static final int TRACE_TAIL = 5;
    private static final int TRACE_MAX = TRACE_HEAD + TRACE_TAIL + 5;
    /** Prints an error with backtrace to the error stream.
     *
     * MRI: eval.c - error_print()
     *
     */
    public void printError(RubyException excp) {
        if (excp == null || excp.isNil()) {
            return;
        }

        RubyArray backtrace = (RubyArray) excp.callMethod("backtrace");

        if (backtrace.isNil()) {
            if (getSourceFile() != null) {
                getErrorStream().print(getPosition());
            } else {
                getErrorStream().print(getSourceLine());
            }
        } else if (backtrace.getLength() == 0) {
            printErrorPos();
        } else {
            IRubyObject mesg = backtrace.first(null);

            if (mesg.isNil()) {
                printErrorPos();
            } else {
                getErrorStream().print(mesg);
            }
        }

        RubyClass type = excp.getMetaClass();
        String info = excp.toString();

        if (type == getExceptions().getRuntimeError() && (info == null || info.length() == 0)) {
            getErrorStream().print(": unhandled exception\n");
        } else {
            String path = type.getName();

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
            IRubyObject[] elements = backtrace.toJavaArray();

            for (int i = 1; i < elements.length; i++) {
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
        if (getSourceFile() != null) {
            if (getCurrentFrame().getLastFunc() != null) {
                getErrorStream().print(getPosition());
                getErrorStream().print(":in '" + getCurrentFrame().getLastFunc() + '\'');
            } else if (getSourceLine() != 0) {
                getErrorStream().print(getPosition());
            } else {
                getErrorStream().print(getSourceFile());
            }
        }
    }

    /** This method compiles and interprets a Ruby script.
     *
     *  It can be used if you want to use JRuby as a Macro language.
     *
     */
    public void loadScript(RubyString scriptName, RubyString source, boolean wrap) {
        loadScript(scriptName.getValue(), new StringReader(source.getValue()), wrap);
    }

    public void loadScript(String scriptName, Reader source, boolean wrap) {
        IRubyObject self = getTopSelf();

        ThreadContext context = getCurrentContext();

        context.pushDynamicVars();

        RubyModule wrapper = context.getWrapper();

        if (!wrap) {
            secure(4); /* should alter global state */
            context.pushClass(getClasses().getObjectClass());
            context.setWrapper(null);
        } else {
            /* load in anonymous module as toplevel */
            context.setWrapper(RubyModule.newModule(this, null));
            context.pushClass(context.getWrapper());
            self = getTopSelf().rbClone();
            self.extendObject(context.getRubyClass());
        }

        String last_func = context.getCurrentFrame().getLastFunc();

        context.getFrameStack().push();
        context.getCurrentFrame().setLastFunc(null);
        context.getCurrentFrame().setLastClass(null);
        context.getCurrentFrame().setSelf(self);
        context.getScopeStack().push();

        /* default visibility is private at loading toplevel */
        setCurrentVisibility(Visibility.PRIVATE);

        try {
        	Node node = parse(source, scriptName);
            self.eval(node);
        } finally {
            context.getCurrentFrame().setLastFunc(last_func);
            context.getScopeStack().pop();
            context.getFrameStack().pop();
            context.popClass();
            context.popDynamicVars();
            context.setWrapper(wrapper);
        }
    }

    public void loadNode(String scriptName, Node node, boolean wrap) {
        IRubyObject self = getTopSelf();

        ThreadContext context = getCurrentContext();

        context.pushDynamicVars();

        RubyModule wrapper = context.getWrapper();

        if (!wrap) {
            secure(4); /* should alter global state */
            context.pushClass(getClasses().getObjectClass());
            context.setWrapper(null);
        } else {
            /* load in anonymous module as toplevel */
            context.setWrapper(RubyModule.newModule(this, null));
            context.pushClass(context.getWrapper());
            self = getTopSelf().rbClone();
            self.extendObject(context.getRubyClass());
        }

        String last_func = getCurrentFrame().getLastFunc();

        context.getFrameStack().push();
        context.getCurrentFrame().setLastFunc(null);
        context.getCurrentFrame().setLastClass(null);
        context.getCurrentFrame().setSelf(self);
        context.getScopeStack().push();

        /* default visibility is private at loading toplevel */
        setCurrentVisibility(Visibility.PRIVATE);

        try {
            self.eval(node);

        } finally {
            context.getCurrentFrame().setLastFunc(last_func);
            context.getScopeStack().pop();
            context.getFrameStack().pop();
            context.popClass();
            context.popDynamicVars();
            context.setWrapper(wrapper);
        }
    }


    /** Loads, compiles and interprets a Ruby file.
     *  Used by Kernel#require.
     *
     *  @mri rb_load
     */
    public void loadFile(File file, boolean wrap) {
        assert file != null : "No such file to load";
        try {
            BufferedReader source = new BufferedReader(new FileReader(file));
            loadScript(file.getPath(), source, wrap);
            source.close();
        } catch (IOException ioExcptn) {
            throw IOError.fromException(this, ioExcptn);
        }
    }

    /** Call the trace function
     *
     * MRI: eval.c - call_trace_func
     *
     */
    public synchronized void callTraceFunction(
        String event,
        SourcePosition position,
        IRubyObject self,
        String name,
        IRubyObject type) {
        if (!isWithinTrace && traceFunction != null) {
            isWithinTrace = true;

            SourcePosition savePosition = getPosition();
            String file = position.getFile();

            if (file == null) {
                file = "(ruby)";
            }
            if (type == null) {
				type = getFalse();
			}
            getFrameStack().push();
            getCurrentFrame().setIter(Iter.ITER_NOT);

            try {
                traceFunction
                    .call(new IRubyObject[] {
                        newString(event),
                        newString(file),
                        newFixnum(position.getLine()),
                        name != null ? RubySymbol.newSymbol(this, name) : getNil(),
                        self != null ? self: getNil(),
                        type });
            } finally {
                getFrameStack().pop();
                setPosition(savePosition);
                isWithinTrace = false;
            }
        }
    }

    public RubyProc getTraceFunction() {
        return traceFunction;
    }

    public void setTraceFunction(RubyProc traceFunction) {
        this.traceFunction = traceFunction;
    }
    public GlobalVariables getGlobalVariables() {
        return globalVariables;
    }

    public CallbackFactory callbackFactory(Class type) {
        return CallbackFactory.createFactory(type);
    }

    /**
     * Push block onto exit stack.  When runtime environment exits
     * these blocks will be evaluated.
     * 
     * @return the element that was pushed onto stack
     */
    public IRubyObject pushExitBlock(RubyProc proc) {
    	atExitBlocks.push(proc);
        return proc;
    }
    
    /**
     * Make sure Kernel#at_exit procs get invoked on runtime shutdown.
     * This method needs to be explicitly called to work properly.
     * I thought about using finalize(), but that did not work and I
     * am not sure the runtime will be at a state to run procs by the
     * time Ruby is going away.  This method can contain any other
     * things that need to be cleaned up at shutdown.  
     */
    public void tearDown() {
        while (!atExitBlocks.empty()) {
            RubyProc proc = (RubyProc) atExitBlocks.pop();
            
            proc.call(null);
        }
    }
    
    // new factory methods ------------------------------------------------------------------------
    
    public RubyArray newArray() {
    	return RubyArray.newArray(this);
    }
    
    public RubyArray newArray(IRubyObject object) {
    	return RubyArray.newArray(this, object);
    }

    public RubyArray newArray(IRubyObject car, IRubyObject cdr) {
    	return RubyArray.newArray(this, car, cdr);
    }
    
    public RubyArray newArray(IRubyObject[] objects) {
    	return RubyArray.newArray(this, objects);
    }
    
    public RubyArray newArray(List list) {
    	return RubyArray.newArray(this, list);
    }
    
    public RubyArray newArray(int size) {
    	return RubyArray.newArray(this, size);
    }
    
    public RubyBoolean newBoolean(boolean value) {
    	return RubyBoolean.newBoolean(this, value);
    }
    
    public RubyFileStat newRubyFileStat(File file) {
    	return new RubyFileStat(this, file);
    }
    
    public RubyFixnum newFixnum(long value) {
    	return RubyFixnum.newFixnum(this, value);
    }
    
    public RubyFloat newFloat(double value) {
    	return RubyFloat.newFloat(this, value);
    }
    
    public RubyProc newProc() {
    	return RubyProc.newProc(this, false);
    }

    public RubyString newString(String string) {
    	return RubyString.newString(this, string);
    }
    
    public RubySymbol newSymbol(String string) {
    	return RubySymbol.newSymbol(this, string);
    }
    
    public ArgumentError newArgumentError(String message) {
    	return new ArgumentError(this, message);
    }
    
    public ArgumentError newArgumentError(int got, int expected) {
    	return new ArgumentError(this, got, expected);
    }
    
    public IndexError newIndexError(String message) {
    	return new IndexError(this, message);
    }

    public TypeError newTypeError(String message) {
    	return new TypeError(this, message);
    }
    
    public TypeError newTypeError(IRubyObject receivedObject, RubyClass expectedClass) {
    	return new TypeError(this, receivedObject, expectedClass);
    }
}
