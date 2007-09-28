/*
 **** BEGIN LICENSE BLOCK *****
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
 * Copyright (C) 2004-2005 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2006 Miguel Covarrubias <mlcovarrubias@gmail.com>
 * Copyright (C) 2006 Michael Studman <codehaus@michaelstudman.com>
 * Copyright (C) 2006 Ola Bini <ola@ologix.com>
 * Copyright (C) 2007 Nick Sieger <nicksieger@gmail.com>
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

import com.sun.jna.Native;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Stack;
import java.util.WeakHashMap;
import org.jruby.ast.Node;
import org.jruby.ast.RootNode;
import org.jruby.ast.executable.Script;
import org.jruby.ast.executable.YARVCompiledRunner;
import org.jruby.common.RubyWarnings;
import org.jruby.compiler.ASTInspector;
import org.jruby.compiler.NodeCompilerFactory;
import org.jruby.compiler.NotCompilableException;
import org.jruby.compiler.impl.StandardASMCompiler;
import org.jruby.compiler.yarv.StandardYARVCompiler;
import org.jruby.evaluator.EvaluationState;
import org.jruby.exceptions.JumpException;
import org.jruby.exceptions.RaiseException;
import org.jruby.internal.runtime.GlobalVariables;
import org.jruby.internal.runtime.ThreadService;
import org.jruby.internal.runtime.ValueAccessor;
import org.jruby.javasupport.Java;
import org.jruby.javasupport.JavaSupport;
import org.jruby.libraries.IConvLibrary;
import org.jruby.libraries.JRubyLibrary;
import org.jruby.libraries.NKFLibrary;
import org.jruby.libraries.RbConfigLibrary;
import org.jruby.libraries.StringIOLibrary;
import org.jruby.libraries.StringScannerLibrary;
import org.jruby.libraries.ZlibLibrary;
import org.jruby.libraries.YamlLibrary;
import org.jruby.libraries.EnumeratorLibrary;
import org.jruby.libraries.BigDecimalLibrary;
import org.jruby.libraries.DigestLibrary;
import org.jruby.libraries.ThreadLibrary;
import org.jruby.libraries.IOWaitLibrary;
import org.jruby.ext.socket.RubySocket;
import org.jruby.ext.Generator;
import org.jruby.ext.JavaBasedPOSIX;
import org.jruby.ext.POSIX;
import org.jruby.ext.Readline;
import org.jruby.libraries.FiberLibrary;
import org.jruby.parser.Parser;
import org.jruby.parser.ParserConfiguration;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.*;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.load.Library;
import org.jruby.runtime.load.LoadService;
import org.jruby.util.BuiltinScript;
import org.jruby.util.ByteList;
import org.jruby.util.IOHandler;
import org.jruby.util.IOInputStream;
import org.jruby.util.IOOutputStream;
import org.jruby.util.JRubyClassLoader;
import org.jruby.util.KCode;
import org.jruby.util.MethodCache;
import org.jruby.util.NormalizedFile;
import org.jruby.regexp.RegexpFactory;

/**
 * The jruby runtime.
 */
public final class Ruby {
    private static String[] BUILTIN_LIBRARIES = {"fcntl", "yaml", "yaml/syck", "jsignal" };

    private CacheMap cacheMap = new CacheMap();
    private MethodCache methodCache = new MethodCache();
    private ThreadService threadService = new ThreadService(this);
    private Hashtable<Object, Object> runtimeInformation;
    
    private static POSIX posix = loadPosix();

    private int stackTraces = 0;

    private ObjectSpace objectSpace = new ObjectSpace();

    public final RubyFixnum[] fixnumCache = new RubyFixnum[256];
    private final RubySymbol.SymbolTable symbolTable = new RubySymbol.SymbolTable();
    private Hashtable<Integer, WeakReference<IOHandler>> ioHandlers = new Hashtable<Integer, WeakReference<IOHandler>>();
    private long randomSeed = 0;
    private long randomSeedSequence = 0;
    private Random random = new Random();

    private List<EventHook> eventHooks = new LinkedList<EventHook>();
    private boolean globalAbortOnExceptionEnabled = false;
    private boolean doNotReverseLookupEnabled = false;
    private final boolean objectSpaceEnabled;

    private long globalState = 1;

    /** safe-level:
            0 - strings from streams/environment/ARGV are tainted (default)
            1 - no dangerous operation by tainted value
            2 - process/file operations prohibited
            3 - all generated objects are tainted
            4 - no global (non-tainted) variable modification/no direct output
    */
    private int safeLevel = 0;

    // Default classes/objects
    private IRubyObject undef;

    private RubyClass objectClass;
    private RubyClass moduleClass;
    private RubyClass classClass;

    private RubyModule kernelModule;
    
    private RubyClass nilClass;
    private RubyClass trueClass;
    private RubyClass falseClass;
    
    private RubyModule comparableModule;
    
    private RubyClass numericClass;
    private RubyClass floatClass;
    private RubyClass integerClass;
    private RubyClass fixnumClass;

    private RubyModule enumerableModule;    
    private RubyClass arrayClass;
    private RubyClass hashClass;
    private RubyClass rangeClass;
    
    private RubyClass stringClass;
    private RubyClass symbolClass;
    
    private IRubyObject nilObject;
    private RubyBoolean trueObject;
    private RubyBoolean falseObject;
    
    private RubyClass procClass;
    private RubyClass bindingClass;
    private RubyClass methodClass;
    private RubyClass unboundMethodClass;

    private RubyClass matchDataClass;
    private RubyClass regexpClass;

    private RubyClass timeClass;

    private RubyModule mathModule;

    private RubyModule marshalModule;

    private RubyClass bignumClass;

    private RubyClass dirClass;

    private RubyClass fileClass;
    private RubyClass fileStatClass;
    private RubyModule fileTestModule;
    private RubyClass statClass;

    private RubyClass ioClass;

    private RubyClass threadClass;
    private RubyClass threadGroupClass;

    private RubyClass continuationClass;

    private RubyClass structClass;
    private IRubyObject tmsStruct;

    private RubyModule gcModule;
    private RubyModule objectSpaceModule;

    private RubyModule processModule;
    private RubyClass procStatusClass;

    private RubyModule precisionModule;

    private RubyClass exceptionClass;

    private RubyClass systemCallError = null;
    private RubyModule errnoModule = null;
    private IRubyObject topSelf;

    // former java.lang.System concepts now internalized for MVM
    private String currentDirectory;

    private long startTime = System.currentTimeMillis();

    private RubyInstanceConfig config;

    private InputStream in;
    private PrintStream out;
    private PrintStream err;

    private IRubyObject verbose;
    private IRubyObject debug;

    // Java support
    private JavaSupport javaSupport;
    private static JRubyClassLoader jrubyClassLoader;

    private static boolean securityRestricted = false;

    private Parser parser = new Parser(this);

    private LoadService loadService;
    private GlobalVariables globalVariables = new GlobalVariables(this);
    private RubyWarnings warnings = new RubyWarnings(this);

    // Contains a list of all blocks (as Procs) that should be called when
    // the runtime environment exits.
    private Stack<RubyProc> atExitBlocks = new Stack<RubyProc>();

    private Profile profile;

    private String jrubyHome;

    private KCode kcode = KCode.NONE;

    public int symbolLastId = 0;
    public int moduleLastId = 0;

    private Object respondToMethod;

    private RegexpFactory regexpFactory;
    
    /**
     * A list of finalizers, weakly referenced, to be executed on tearDown
     */
    private Map<Finalizable, Object> finalizers;

    /**
     * Create and initialize a new JRuby Runtime.
     */
    private Ruby(RubyInstanceConfig config) {
        this.config             = config;
        this.in                 = config.getInput();
        this.out                = config.getOutput();
        this.err                = config.getError();
        this.objectSpaceEnabled = config.isObjectSpaceEnabled();
        this.profile            = config.getProfile();
        this.currentDirectory   = config.getCurrentDirectory();;
    }

    /**
     * Returns a default instance of the JRuby runtime.
     *
     * @return the JRuby runtime
     */
    public static Ruby getDefaultInstance() {
        return newInstance(new RubyInstanceConfig());
    }

    /**
     * Returns a default instance of the JRuby runtime configured as provided.
     *
     * @param config the instance configuration
     * @return the JRuby runtime
     */
    public static Ruby newInstance(RubyInstanceConfig config) {
        Ruby ruby = new Ruby(config);
        ruby.init();
        return ruby;
    }

    /**
     * Returns a default instance of the JRuby runtime configured with the given input, output and error streams.
     *
     * @param in the custom input stream
     * @param out the custom output stream
     * @param err the custom error stream
     * @return the JRuby runtime
     */
    public static Ruby newInstance(InputStream in, PrintStream out, PrintStream err) {
        RubyInstanceConfig config = new RubyInstanceConfig();
        config.setInput(in);
        config.setOutput(out);
        config.setError(err);
        return newInstance(config);
    }

    public IRubyObject evalFile(Reader reader, String name) {
        return eval(parseFile(reader, name, getCurrentContext().getCurrentScope()));
    }
    
    /**
     * Evaluates a script and returns a RubyObject.
     */
    public IRubyObject evalScriptlet(String script) {
        return eval(parseEval(script, "<script>", getCurrentContext().getCurrentScope(), 0));
    }

    public IRubyObject eval(Node node) {
        try {
            ThreadContext tc = getCurrentContext();

            return EvaluationState.eval(this, tc, node, tc.getFrameSelf(), Block.NULL_BLOCK);
        } catch (JumpException.ReturnJump rj) {
            throw newLocalJumpError("return", (IRubyObject)rj.getValue(), "unexpected return");
        } catch (JumpException.BreakJump bj) {
            throw newLocalJumpError("break", (IRubyObject)bj.getValue(), "unexpected break");
        } catch (JumpException.RedoJump rj) {
            throw newLocalJumpError("redo", (IRubyObject)rj.getValue(), "unexpected redo");
        }
    }
    
    /**
     * This differs from the other methods in that it accepts a string-based script and
     * parses and runs it as though it were loaded at a command-line. This is the preferred
     * way to start up a new script when calling directly into the Ruby object (which is
     * generally *dis*couraged.
     * 
     * @param script The contents of the script to run as a normal, root script
     * @return The last value of the script
     */
    public IRubyObject executeScript(String script, String filename) {
        Reader reader = new StringReader(script);
        Node node = parseInline(reader, filename, null);
        
        return compileOrFallbackAndRun(node);
    }
    
    public IRubyObject compileOrFallbackAndRun(Node node) {
        try {
            ThreadContext tc = getCurrentContext();
            
            // do the compile if JIT is enabled
            if (config.isJitEnabled() && !hasEventHooks()) {
                Script script = null;
                try {
                    String filename = node.getPosition().getFile();
                    String classname;
                    if (filename.equals("-e")) {
                        classname = "__dash_e__";
                    } else {
                        classname = filename.replace('\\', '/').replaceAll(".rb", "");
                    }
        
                    ASTInspector inspector = new ASTInspector();
                    inspector.inspect(node);

                    StandardASMCompiler compiler = new StandardASMCompiler(classname, filename);
                    NodeCompilerFactory.compileRoot(node, compiler, inspector);
                    script = (Script)compiler.loadClass(this.getJRubyClassLoader()).newInstance();
                    
                    if (config.isJitLogging()) {
                        System.err.println("compiled: " + node.getPosition().getFile());
                    }
                } catch (Throwable t) {
                    if (config.isJitLoggingVerbose()) {
                        System.err.println("coult not compile: " + node.getPosition().getFile() + " because of: \"" + t.getMessage() + "\"");
                    }
                    return eval(node);
                }
            
                // FIXME: Pass something better for args and block here?
                return script.load(getCurrentContext(), tc.getFrameSelf(), IRubyObject.NULL_ARRAY, Block.NULL_BLOCK);
            } else {
                return eval(node);
            }
        } catch (JumpException.ReturnJump rj) {
            return (IRubyObject) rj.getValue();
        }
        
    }

    public IRubyObject compileAndRun(Node node) {
        try {
            ThreadContext tc = getCurrentContext();
            
            // do the compile
            String filename = node.getPosition().getFile();
            String classname;
            if (filename.equals("-e")) {
                classname = "__dash_e__";
            } else {
                classname = filename.replace('\\', '/').replaceAll(".rb", "");
            }
        
            ASTInspector inspector = new ASTInspector();
            inspector.inspect(node);
            
            StandardASMCompiler compiler = new StandardASMCompiler(classname, filename);
            NodeCompilerFactory.compileRoot(node, compiler, inspector);
            Script script = (Script)compiler.loadClass(this.getJRubyClassLoader()).newInstance();
            
            return script.load(getCurrentContext(), tc.getFrameSelf(), IRubyObject.NULL_ARRAY, Block.NULL_BLOCK);
        } catch (NotCompilableException nce) {
            System.err.println("Error -- Not compileable: " + nce.getMessage());
            return null;
        } catch (JumpException.ReturnJump rj) {
            return (IRubyObject) rj.getValue();
        } catch (ClassNotFoundException e) {
            System.err.println("Error -- Not compileable: " + e.getMessage());
            return null;
        } catch (InstantiationException e) {
            System.err.println("Error -- Not compileable: " + e.getMessage());
            return null;
        } catch (IllegalAccessException e) {
            System.err.println("Error -- Not compileable: " + e.getMessage());
            return null;
        }
    }

    public IRubyObject ycompileAndRun(Node node) {
        try {
            StandardYARVCompiler compiler = new StandardYARVCompiler(this);
            NodeCompilerFactory.getYARVCompiler().compile(node, compiler);
            org.jruby.lexer.yacc.ISourcePosition p = node.getPosition();
            if(p == null && node instanceof org.jruby.ast.RootNode) {
                p = ((org.jruby.ast.RootNode)node).getBodyNode().getPosition();
            }
            return new YARVCompiledRunner(this,compiler.getInstructionSequence("<main>",p.getFile(),"toplevel")).run();
        } catch (NotCompilableException nce) {
            System.err.println("Error -- Not compileable: " + nce.getMessage());
            return null;
        } catch (JumpException.ReturnJump rj) {
            return (IRubyObject) rj.getValue();
        }
    }

    Object getRespondToMethod() {
        return respondToMethod;
    }

    void setRespondToMethod(Object rtm) {
        this.respondToMethod = rtm;
    }
    
    public IRubyObject getUndef() {
        return undef;
    }

    public RubyClass getObject() {
        return objectClass;
    }

    public RubyClass getModule() {
        return moduleClass;
    }

    public RubyClass getClassClass() {
        return classClass;
    }
    
    public RubyModule getKernel() {
        return kernelModule;
    }
    void setKernel(RubyModule kernelModule) {
        this.kernelModule = kernelModule;
    }    

    public RubyModule getComparable() {
        return comparableModule;
    }
    void setComparable(RubyModule comparableModule) {
        this.comparableModule = comparableModule;
    }    

    public RubyClass getNumeric() {
        return numericClass;
    }
    void setNumeric(RubyClass numericClass) {
        this.numericClass = numericClass;
    }    

    public RubyClass getFloat() {
        return floatClass;
    }
    void setFloat(RubyClass floatClass) {
        this.floatClass = floatClass;
    }
    
    public RubyClass getInteger() {
        return integerClass;
    }
    void setInteger(RubyClass integerClass) {
        this.integerClass = integerClass;
    }    
    
    public RubyClass getFixnum() {
        return fixnumClass;
    }
    void setFixnum(RubyClass fixnumClass) {
        this.fixnumClass = fixnumClass;
    }    

    public RubyModule getEnumerable() {
        return enumerableModule;
    }
    void setEnumerable(RubyModule enumerableModule) {
        this.enumerableModule = enumerableModule;
    }      

    public RubyClass getString() {
        return stringClass;
    }    
    void setString(RubyClass stringClass) {
        this.stringClass = stringClass;
    }    

    public RubyClass getSymbol() {
        return symbolClass;
    }
    void setSymbol(RubyClass symbolClass) {
        this.symbolClass = symbolClass;
    }   

    public RubyClass getArray() {
        return arrayClass;
    }    
    void setArray(RubyClass arrayClass) {
        this.arrayClass = arrayClass;
    }

    public RubyClass getHash() {
        return hashClass;
    }
    void setHash(RubyClass hashClass) {
        this.hashClass = hashClass;
    }

    public RubyClass getRange() {
        return rangeClass;
    }
    void setRange(RubyClass rangeClass) {
        this.rangeClass = rangeClass;
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

    public RubyClass getNilClass() {
        return nilClass;
    }
    void setNilClass(RubyClass nilClass) {
        this.nilClass = nilClass;
    }

    public RubyClass getTrueClass() {
        return trueClass;
    }
    void setTrueClass(RubyClass trueClass) {
        this.trueClass = trueClass;
    }

    public RubyClass getFalseClass() {
        return falseClass;
    }
    void setFalseClass(RubyClass falseClass) {
        this.falseClass = falseClass;
    }

    public RubyClass getProc() {
        return procClass;
    }
    void setProc(RubyClass procClass) {
        this.procClass = procClass;
    }

    public RubyClass getBinding() {
        return bindingClass;
    }
    void setBinding(RubyClass bindingClass) {
        this.bindingClass = bindingClass;
    }

    public RubyClass getMethod() {
        return methodClass;
    }
    void setMethod(RubyClass methodClass) {
        this.methodClass = methodClass;
    }    

    public RubyClass getUnboundMethod() {
        return unboundMethodClass;
    }
    void setUnboundMethod(RubyClass unboundMethodClass) {
        this.unboundMethodClass = unboundMethodClass;
    }    

    public RubyClass getMatchData() {
        return matchDataClass;
    }
    void setMatchData(RubyClass matchDataClass) {
        this.matchDataClass = matchDataClass;
    }    

    public RubyClass getRegexp() {
        return regexpClass;
    }
    void setRegexp(RubyClass regexpClass) {
        this.regexpClass = regexpClass;
    }    

    public RubyClass getTime() {
        return timeClass;
    }
    void setTime(RubyClass timeClass) {
        this.timeClass = timeClass;
    }    

    public RubyModule getMath() {
        return mathModule;
    }
    void setMath(RubyModule mathModule) {
        this.mathModule = mathModule;
    }    

    public RubyModule getMarshal() {;
        return marshalModule;
    }
    void setMarshal(RubyModule marshalModule) {
        this.marshalModule = marshalModule;
    }    

    public RubyClass getBignum() {
        return bignumClass;
    }
    void setBignum(RubyClass bignumClass) {
        this.bignumClass = bignumClass;
    }    

    public RubyClass getDir() {
        return dirClass;
    }
    void setDir(RubyClass dirClass) {
        this.dirClass = dirClass;
    }    

    public RubyClass getFile() {
        return fileClass;
    }
    void setFile(RubyClass fileClass) {
        this.fileClass = fileClass;
    }    

    public RubyClass getFileStat() {
        return fileStatClass;
    }
    void setFileStat(RubyClass fileStatClass) {
        this.fileStatClass = fileStatClass;
    }    

    public RubyModule getFileTest() {
        return fileTestModule;
    }
    void setFileTest(RubyModule fileTestModule) {
        this.fileTestModule = fileTestModule;
    }    

    public RubyClass getStat() {
        return statClass;
    }
    void setStat(RubyClass statClass) {
        this.statClass = statClass;
    }    

    public RubyClass getIO() {
        return ioClass;
    }
    void setIO(RubyClass ioClass) {
        this.ioClass = ioClass;
    }    

    public RubyClass getThread() {
        return threadClass;
    }
    void setThread(RubyClass threadClass) {
        this.threadClass = threadClass;
    }    

    public RubyClass getThreadGroup() {
        return threadGroupClass;
    }
    void setThreadGroup(RubyClass threadGroupClass) {
        this.threadGroupClass = threadGroupClass;
    }    

    public RubyClass getContinuation() {
        return continuationClass;
    }
    void setContinuation(RubyClass continuationClass) {
        this.continuationClass = continuationClass;
    }    

    public RubyClass getStructClass() {
        return structClass;
    }
    void setStructClass(RubyClass structClass) {
        this.structClass = structClass;
    }    

    public IRubyObject getTmsStruct() {
        return tmsStruct;
    }
    void setTmsStruct(RubyClass tmsStruct) {
        this.tmsStruct = tmsStruct;
    }    

    public RubyModule getGC() {
        return gcModule;
    }
    void setGC(RubyModule gcModule) {
        this.gcModule = gcModule;
    }    

    public RubyModule getObjectSpaceModule() {
        return objectSpaceModule;
    }
    void setObjectSpaceModule(RubyModule objectSpaceModule) {
        this.objectSpaceModule = objectSpaceModule;
    }    

    public RubyModule getProcess() {
        return processModule;
    }
    void setProcess(RubyModule processModule) {
        this.processModule = processModule;
    }    

    public RubyClass getProcStatus() {
        return procStatusClass; 
    }
    void setProcStatus(RubyClass procStatusClass) {
        this.procStatusClass = procStatusClass;
    }    

    public RubyModule getPrecision() {
        return precisionModule;
    }
    void setPrecision(RubyModule precisionModule) {
        this.precisionModule = precisionModule;
    }    

    public RubyClass getException() {
        return exceptionClass;
    }
    void setException(RubyClass exceptionClass) {
        this.exceptionClass = exceptionClass;
    }    

    public RubyModule getModule(String name) {
        return (RubyModule) objectClass.getConstantAt(name);
    }

    /** Returns a class from the instance pool.
     *
     * @param name The name of the class.
     * @return The class.
     */
    public RubyClass getClass(String name) {
        try {
            return objectClass.getClass(name);
        } catch (ClassCastException e) {
            throw newTypeError(name + " is not a Class");
        }
    }

    /** rb_define_class
     *
     */
    public RubyClass defineClass(String name, RubyClass superClass, ObjectAllocator allocator) {
        IRubyObject classObj = objectClass.getConstantAt(name);

        if (classObj != null) {
            if (!(classObj instanceof RubyClass)) throw newTypeError(name + " is not a class");
            RubyClass klazz = (RubyClass)classObj;
            if (klazz.getSuperClass().getRealClass() != superClass) throw newNameError(name + " is already defined", name);
            return klazz;
        }

        if (superClass == null) {
            warnings.warn("no super class for `" + name + "', Object assumed");
            superClass = objectClass;
        }

        return RubyClass.newClass(this, superClass, name, allocator, objectClass, false);
    }

    /** rb_define_class_under
    *
    */
    public RubyClass defineClassUnder(String name, RubyClass superClass, ObjectAllocator allocator, RubyModule parent) {
        IRubyObject classObj = parent.getConstantAt(name);

        if (classObj != null) {
            if (!(classObj instanceof RubyClass)) throw newTypeError(name + " is not a class");
            RubyClass klazz = (RubyClass)classObj;
            if (klazz.getSuperClass().getRealClass() != superClass) throw newNameError(name + " is already defined", name);
            return klazz;
        }

        if (superClass == null) {
            warnings.warn("no super class for `" + parent.getName() + "::" + name + "', Object assumed");
            superClass = objectClass;
        }

        return RubyClass.newClass(this, superClass, name, allocator, parent, true);
    }

    /** rb_define_module
     *
     */
    public RubyModule defineModule(String name) {
        IRubyObject moduleObj = objectClass.getConstantAt(name);

        if (moduleObj != null ) {
            if (moduleObj.isModule()) return (RubyModule)moduleObj;
            throw newTypeError(moduleObj.getMetaClass().getName() + " is not a module");
        }

        return RubyModule.newModule(this, name, objectClass, false);
    }

    /** rb_define_module_under
    *
    */
    public RubyModule defineModuleUnder(String name, RubyModule parent) {
        IRubyObject moduleObj = parent.getConstantAt(name);

        if (moduleObj != null ) {
            if (moduleObj.isModule()) return (RubyModule)moduleObj;
            throw newTypeError(parent.getName() + "::" + moduleObj.getMetaClass().getName() + " is not a module");
        }

        return RubyModule.newModule(this, name, parent, true);
    }

    /**
     * In the current context, get the named module. If it doesn't exist a
     * new module is created.
     */
    public RubyModule getOrCreateModule(String name) {
        IRubyObject module = objectClass.getConstantAt(name);
        if (module == null) {
            module = defineModule(name);
        } else if (getSafeLevel() >= 4) {
            throw newSecurityError("Extending module prohibited.");
        } else if (!module.isModule()) {
            throw newTypeError(name + " is not a Module");
        }

        return (RubyModule) module;
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

    public KCode getKCode() {
        return kcode;
    }

    public void setKCode(KCode kcode) {
        this.kcode = kcode;
    }

    public void secure(int level) {
        if (level <= safeLevel) {
            throw newSecurityError("Insecure operation '" + getCurrentContext().getFrameName() + "' at level " + safeLevel);
        }
    }

    // FIXME moved this hear to get what's obviously a utility method out of IRubyObject.
    // perhaps security methods should find their own centralized home at some point.
    public void checkSafeString(IRubyObject object) {
        if (getSafeLevel() > 0 && object.isTaint()) {
            ThreadContext tc = getCurrentContext();
            if (tc.getFrameName() != null) {
                throw newSecurityError("Insecure operation - " + tc.getFrameName());
            }
            throw newSecurityError("Insecure operation: -r");
        }
        secure(4);
        if (!(object instanceof RubyString)) {
            throw newTypeError(
                "wrong argument type " + object.getMetaClass().getName() + " (expected String)");
        }
    }

    /**
     * Retrieve mappings of cached methods to where they have been cached.  When a cached
     * method needs to be invalidated this map can be used to remove all places it has been
     * cached.
     *
     * @return the mappings of where cached methods have been stored
     */
    public CacheMap getCacheMap() {
        return cacheMap;
    }
    
    /**
     * Retrieve method cache.
     * 
     * @return method cache where cached methods have been stored
     */
    public MethodCache getMethodCache() {
        return methodCache;
    }

    /**
     * @see org.jruby.Ruby#getRuntimeInformation
     */
    public Map<Object, Object> getRuntimeInformation() {
        return runtimeInformation == null ? runtimeInformation = new Hashtable<Object, Object>() : runtimeInformation;
    }

    /** rb_define_global_const
     *
     */
    public void defineGlobalConstant(String name, IRubyObject value) {
        objectClass.defineConstant(name, value);
    }

    public boolean isClassDefined(String name) {
        return getModule(name) != null;
    }

    /** Getter for property rubyTopSelf.
     * @return Value of property rubyTopSelf.
     */
    public IRubyObject getTopSelf() {
        return topSelf;
    }

    public void setCurrentDirectory(String dir) {
        currentDirectory = dir;
    }

    public String getCurrentDirectory() {
        return currentDirectory;
    }

    /** ruby_init
     *
     */
    // TODO: Figure out real dependencies between vars and reorder/refactor into better methods
    private void init() {
        ThreadContext tc = getCurrentContext();

        this.regexpFactory = RegexpFactory.getFactory(this.config.getDefaultRegexpEngine());

        javaSupport = new JavaSupport(this);

        tc.preInitCoreClasses();

        initCoreClasses();
        
        // core classes are initialized, ensure top scope has Object as cref
        tc.getCurrentScope().getStaticScope().setModule(objectClass);

        verbose = falseObject;
        debug = falseObject;

        initLibraries();

        topSelf = TopSelfFactory.createTopSelf(this);

        tc.preInitBuiltinClasses(objectClass, topSelf);

        RubyGlobal.createGlobals(this);
        
        defineGlobalConstant("TRUE", trueObject);
        defineGlobalConstant("FALSE", falseObject);
        defineGlobalConstant("NIL", nilObject);

        getObject().defineConstant("TOPLEVEL_BINDING", newBinding());

        RubyKernel.autoload(topSelf, newSymbol("Java"), newString("java"));
        
        methodCache.initialized();
    }

    private void initLibraries() {
        loadService = config.createLoadService(this);
        registerBuiltin("java.rb", new Library() {
                public void load(Ruby runtime) throws IOException {
                    Java.createJavaModule(runtime);
                    new BuiltinScript("javasupport").load(runtime);
                    RubyClassPathVariable.createClassPathVariable(runtime);
                }
            });
        
        registerBuiltin("socket.rb", new RubySocket.Service());
        registerBuiltin("rbconfig.rb", new RbConfigLibrary());

        for (int i=0; i<BUILTIN_LIBRARIES.length; i++) {
            if(profile.allowBuiltin(BUILTIN_LIBRARIES[i])) {
                loadService.registerRubyBuiltin(BUILTIN_LIBRARIES[i]);
            }
        }

        final Library NO_OP_LIBRARY = new Library() {
                public void load(Ruby runtime) throws IOException {
                }
            };

        registerBuiltin("jruby.rb", new JRubyLibrary());
        registerBuiltin("jruby/ext.rb", new RubyJRuby.ExtLibrary());
        registerBuiltin("iconv.rb", new IConvLibrary());
        registerBuiltin("nkf.rb", new NKFLibrary());
        registerBuiltin("stringio.rb", new StringIOLibrary());
        registerBuiltin("strscan.rb", new StringScannerLibrary());
        registerBuiltin("zlib.rb", new ZlibLibrary());
        registerBuiltin("yaml_internal.rb", new YamlLibrary());
        registerBuiltin("enumerator.rb", new EnumeratorLibrary());
        registerBuiltin("generator_internal.rb", new Generator.Service());
        registerBuiltin("readline.rb", new Readline.Service());
        registerBuiltin("thread.so", new ThreadLibrary());
        registerBuiltin("openssl.so", new Library() {
                public void load(Ruby runtime) throws IOException {
                    runtime.getModule("Kernel").callMethod(runtime.getCurrentContext(),"require",runtime.newString("rubygems"));
                    runtime.getTopSelf().callMethod(runtime.getCurrentContext(),"gem",runtime.newString("jruby-openssl"));
                    runtime.getModule("Kernel").callMethod(runtime.getCurrentContext(),"require",runtime.newString("openssl.rb"));
                }
            });
        registerBuiltin("digest.so", new DigestLibrary());
        registerBuiltin("digest.rb", new DigestLibrary());
        registerBuiltin("digest/md5.rb", new DigestLibrary.MD5());
        registerBuiltin("digest/rmd160.rb", new DigestLibrary.RMD160());
        registerBuiltin("digest/sha1.rb", new DigestLibrary.SHA1());
        registerBuiltin("digest/sha2.rb", new DigestLibrary.SHA2());
        registerBuiltin("bigdecimal.rb", new BigDecimalLibrary());
        registerBuiltin("io/wait.so", new IOWaitLibrary());
        registerBuiltin("etc.so", NO_OP_LIBRARY);
        registerBuiltin("fiber.so", new FiberLibrary());
    }

    private void registerBuiltin(String nm, Library lib) {
        if(profile.allowBuiltin(nm)) {
            loadService.registerBuiltin(nm,lib);
        }
    }

    private void initCoreClasses() {
        undef = new RubyUndef();

        objectClass = RubyClass.createBootstrapClass(this, "Object", null, RubyObject.OBJECT_ALLOCATOR);
        moduleClass = RubyClass.createBootstrapClass(this, "Module", objectClass, RubyModule.MODULE_ALLOCATOR);
        classClass = RubyClass.createBootstrapClass(this, "Class", moduleClass, RubyClass.CLASS_ALLOCATOR);

        objectClass.setMetaClass(classClass);
        moduleClass.setMetaClass(classClass);
        classClass.setMetaClass(classClass);

        RubyClass metaClass; 
        metaClass = objectClass.makeMetaClass(classClass);
        metaClass = moduleClass.makeMetaClass(metaClass);
        metaClass = classClass.makeMetaClass(metaClass);
        
        RubyObject.createObjectClass(this, objectClass);
        RubyModule.createModuleClass(this, moduleClass);
        RubyClass.createClassClass(this, classClass);

        RubyKernel.createKernelModule(this);
        objectClass.includeModule(kernelModule);

        // Pre-create the core classes we know we will get referenced by starting up the runtime.
        RubyNil.createNilClass(this);
        RubyBoolean.createFalseClass(this);
        RubyBoolean.createTrueClass(this);
        
        nilObject = new RubyNil(this);
        falseObject = new RubyBoolean(this, false);
        trueObject = new RubyBoolean(this, true);

        RubyComparable.createComparable(this);
        RubyEnumerable.createEnumerableModule(this);
        RubyString.createStringClass(this);
        RubySymbol.createSymbolClass(this);
        
        if (profile.allowClass("ThreadGroup")) RubyThreadGroup.createThreadGroupClass(this);
        if (profile.allowClass("Thread")) RubyThread.createThreadClass(this);
        if (profile.allowClass("Exception")) RubyException.createExceptionClass(this);
        if (profile.allowModule("Precision")) RubyPrecision.createPrecisionModule(this);
        if (profile.allowClass("Numeric")) RubyNumeric.createNumericClass(this);
        if (profile.allowClass("Integer")) RubyInteger.createIntegerClass(this);
        if (profile.allowClass("Fixnum")) RubyFixnum.createFixnumClass(this);
        if (profile.allowClass("Hash")) RubyHash.createHashClass(this);
        if (profile.allowClass("Array")) RubyArray.createArrayClass(this);
        if (profile.allowClass("Float")) RubyFloat.createFloatClass(this);
        if (profile.allowClass("Bignum")) RubyBignum.createBignumClass(this);

        ioClass = RubyIO.createIOClass(this);        
        
        if (profile.allowClass("Struct")) RubyStruct.createStructClass(this);

        if (profile.allowClass("Tms")) {
            tmsStruct = RubyStruct.newInstance(structClass,
                    new IRubyObject[] { newString("Tms"), newSymbol("utime"), newSymbol("stime"),
                        newSymbol("cutime"), newSymbol("cstime")}, Block.NULL_BLOCK);
        }

        if (profile.allowClass("Binding")) RubyBinding.createBindingClass(this);
        // Math depends on all numeric types
        if (profile.allowModule("Math")) RubyMath.createMathModule(this); 
        if (profile.allowClass("Regexp")) RubyRegexp.createRegexpClass(this);
        if (profile.allowClass("Range")) RubyRange.createRangeClass(this);
        if (profile.allowModule("ObjectSpace")) RubyObjectSpace.createObjectSpaceModule(this);
        if (profile.allowModule("GC")) RubyGC.createGCModule(this);
        if (profile.allowClass("Proc")) RubyProc.createProcClass(this);
        if (profile.allowClass("Method")) RubyMethod.createMethodClass(this);
        if (profile.allowClass("MatchData")) RubyMatchData.createMatchDataClass(this);
        if (profile.allowModule("Marshal")) RubyMarshal.createMarshalModule(this);
        if (profile.allowClass("Dir")) RubyDir.createDirClass(this);
        if (profile.allowModule("FileTest")) RubyFileTest.createFileTestModule(this);
        // depends on IO, FileTest
        if (profile.allowClass("File")) RubyFile.createFileClass(this);
        if (profile.allowClass("File::Stat")) RubyFileStat.createFileStatClass(this);
        if (profile.allowModule("Process")) RubyProcess.createProcessModule(this);
        if (profile.allowClass("Time")) RubyTime.createTimeClass(this);
        if (profile.allowClass("UnboundMethod")) RubyUnboundMethod.defineUnboundMethodClass(this);

        RubyClass exceptionClass = getClass("Exception");
        RubyClass standardError = null;
        RubyClass runtimeError = null;
        RubyClass ioError = null;
        RubyClass scriptError = null;
        RubyClass nameError = null;
        RubyClass signalException = null;
        
        RubyClass rangeError = null;
        if (profile.allowClass("StandardError")) {
            standardError = defineClass("StandardError", exceptionClass, exceptionClass.getAllocator());
        }
        if (profile.allowClass("RuntimeError")) {
            runtimeError = defineClass("RuntimeError", standardError, standardError.getAllocator());
        }
        if (profile.allowClass("IOError")) {
            ioError = defineClass("IOError", standardError, standardError.getAllocator());
        }
        if (profile.allowClass("ScriptError")) {
            scriptError = defineClass("ScriptError", exceptionClass, exceptionClass.getAllocator());
        }
        if (profile.allowClass("NameError")) {
            nameError = RubyNameError.createNameErrorClass(this, standardError);
        }
        if (profile.allowClass("NoMethodError")) {
            RubyNoMethodError.createNoMethodErrorClass(this, nameError);
        }        
        if (profile.allowClass("RangeError")) {
            rangeError = defineClass("RangeError", standardError, standardError.getAllocator());
        }
        if (profile.allowClass("SystemExit")) {
            RubySystemExit.createSystemExitClass(this, exceptionClass);
        }
        if (profile.allowClass("Fatal")) {
            defineClass("Fatal", exceptionClass, exceptionClass.getAllocator());
        }
        if (profile.allowClass("SignalException")) {
            signalException = defineClass("SignalException", exceptionClass, exceptionClass.getAllocator());
        }
        if (profile.allowClass("Interrupt")) {
            defineClass("Interrupt", signalException, signalException.getAllocator());
        }
        if (profile.allowClass("TypeError")) {
            defineClass("TypeError", standardError, standardError.getAllocator());
        }
        if (profile.allowClass("ArgumentError")) {
            defineClass("ArgumentError", standardError, standardError.getAllocator());
        }
        if (profile.allowClass("IndexError")) {
            defineClass("IndexError", standardError, standardError.getAllocator());
        }
        if (profile.allowClass("SyntaxError")) {
            defineClass("SyntaxError", scriptError, scriptError.getAllocator());
        }
        if (profile.allowClass("LoadError")) {
            defineClass("LoadError", scriptError, scriptError.getAllocator());
        }
        if (profile.allowClass("NotImplementedError")) {
            defineClass("NotImplementedError", scriptError, scriptError.getAllocator());
        }
        if (profile.allowClass("SecurityError")) {
            defineClass("SecurityError", standardError, standardError.getAllocator());
        }
        if (profile.allowClass("NoMemoryError")) {
            defineClass("NoMemoryError", exceptionClass, exceptionClass.getAllocator());
        }
        if (profile.allowClass("RegexpError")) {
            defineClass("RegexpError", standardError, standardError.getAllocator());
        }
        if (profile.allowClass("EOFError")) {
            defineClass("EOFError", ioError, ioError.getAllocator());
        }
        if (profile.allowClass("LocalJumpError")) {
            RubyLocalJumpError.createLocalJumpErrorClass(this, standardError);
        }
        if (profile.allowClass("ThreadError")) {
            defineClass("ThreadError", standardError, standardError.getAllocator());
        }
        if (profile.allowClass("SystemStackError")) {
            defineClass("SystemStackError", exceptionClass, exceptionClass.getAllocator());
        }
        if (profile.allowClass("ZeroDivisionError")) {
            defineClass("ZeroDivisionError", standardError, standardError.getAllocator());
        }
        // FIXME: Actually this somewhere <- fixed
        if (profile.allowClass("FloatDomainError")) {
            defineClass("FloatDomainError", rangeError, rangeError.getAllocator());
        }
        if (profile.allowClass("NativeException")) NativeException.createClass(this, runtimeError);
        if (profile.allowClass("SystemCallError")) {
            systemCallError = defineClass("SystemCallError", standardError, standardError.getAllocator());
        }
        if (profile.allowModule("Errno")) errnoModule = defineModule("Errno");

        initErrnoErrors();

        if (profile.allowClass("Data")) defineClass("Data", objectClass, objectClass.getAllocator());
        if (profile.allowModule("Signal")) RubySignal.createSignal(this);
        if (profile.allowClass("Continuation")) RubyContinuation.createContinuation(this);
    }

    /**
     * Create module Errno's Variables.  We have this method since Errno does not have it's
     * own java class.
     */
    private void initErrnoErrors() {
        createSysErr(IErrno.ENOTEMPTY, "ENOTEMPTY");
        createSysErr(IErrno.ERANGE, "ERANGE");
        createSysErr(IErrno.ESPIPE, "ESPIPE");
        createSysErr(IErrno.ENFILE, "ENFILE");
        createSysErr(IErrno.EXDEV, "EXDEV");
        createSysErr(IErrno.ENOMEM, "ENOMEM");
        createSysErr(IErrno.E2BIG, "E2BIG");
        createSysErr(IErrno.ENOENT, "ENOENT");
        createSysErr(IErrno.ENOSYS, "ENOSYS");
        createSysErr(IErrno.EDOM, "EDOM");
        createSysErr(IErrno.ENOSPC, "ENOSPC");
        createSysErr(IErrno.EINVAL, "EINVAL");
        createSysErr(IErrno.EEXIST, "EEXIST");
        createSysErr(IErrno.EAGAIN, "EAGAIN");
        createSysErr(IErrno.ENXIO, "ENXIO");
        createSysErr(IErrno.EILSEQ, "EILSEQ");
        createSysErr(IErrno.ENOLCK, "ENOLCK");
        createSysErr(IErrno.EPIPE, "EPIPE");
        createSysErr(IErrno.EFBIG, "EFBIG");
        createSysErr(IErrno.EISDIR, "EISDIR");
        createSysErr(IErrno.EBUSY, "EBUSY");
        createSysErr(IErrno.ECHILD, "ECHILD");
        createSysErr(IErrno.EIO, "EIO");
        createSysErr(IErrno.EPERM, "EPERM");
        createSysErr(IErrno.EDEADLOCK, "EDEADLOCK");
        createSysErr(IErrno.ENAMETOOLONG, "ENAMETOOLONG");
        createSysErr(IErrno.EMLINK, "EMLINK");
        createSysErr(IErrno.ENOTTY, "ENOTTY");
        createSysErr(IErrno.ENOTDIR, "ENOTDIR");
        createSysErr(IErrno.EFAULT, "EFAULT");
        createSysErr(IErrno.EBADF, "EBADF");
        createSysErr(IErrno.EINTR, "EINTR");
        createSysErr(IErrno.EWOULDBLOCK, "EWOULDBLOCK");
        createSysErr(IErrno.EDEADLK, "EDEADLK");
        createSysErr(IErrno.EROFS, "EROFS");
        createSysErr(IErrno.EMFILE, "EMFILE");
        createSysErr(IErrno.ENODEV, "ENODEV");
        createSysErr(IErrno.EACCES, "EACCES");
        createSysErr(IErrno.ENOEXEC, "ENOEXEC");
        createSysErr(IErrno.ESRCH, "ESRCH");
        createSysErr(IErrno.ECONNREFUSED, "ECONNREFUSED");
        createSysErr(IErrno.ECONNRESET, "ECONNRESET");
        createSysErr(IErrno.EADDRINUSE, "EADDRINUSE");
    }

    /**
     * Creates a system error.
     * @param i the error code (will probably use a java exception instead)
     * @param name of the error to define.
     **/
    private void createSysErr(int i, String name) {
        if(profile.allowClass(name)) {
            errnoModule.defineClassUnder(name, systemCallError, systemCallError.getAllocator()).defineConstant("Errno", newFixnum(i));
        }
    }

    /** Getter for property isVerbose.
     * @return Value of property isVerbose.
     */
    public IRubyObject getVerbose() {
        return verbose;
    }

    /** Setter for property isVerbose.
     * @param verbose New value of property isVerbose.
     */
    public void setVerbose(IRubyObject verbose) {
        this.verbose = verbose;
    }

    /** Getter for property isDebug.
     * @return Value of property isDebug.
     */
    public IRubyObject getDebug() {
        return debug;
    }

    /** Setter for property isDebug.
     * @param debug New value of property isDebug.
     */
    public void setDebug(IRubyObject debug) {
        this.debug = debug;
    }

    public JavaSupport getJavaSupport() {
        return javaSupport;
    }

    public JRubyClassLoader getJRubyClassLoader() {
        if (!Ruby.isSecurityRestricted() && jrubyClassLoader == null)
            jrubyClassLoader = new JRubyClassLoader(Thread.currentThread().getContextClassLoader());
        return jrubyClassLoader;
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
    
    public Node parseFile(Reader content, String file, DynamicScope scope) {
        return parser.parse(file, content, scope, new ParserConfiguration(0, false, false, true));
    }

    public Node parseInline(Reader content, String file, DynamicScope scope) {
        return parser.parse(file, content, scope, new ParserConfiguration(0, false, true));
    }

    public Node parseEval(String content, String file, DynamicScope scope, int lineNumber) {
        return parser.parse(file, new StringReader(content), scope, new ParserConfiguration(lineNumber, false));
    }

    public Node parse(String content, String file, DynamicScope scope, int lineNumber, 
            boolean extraPositionInformation) {
        return parser.parse(file, new StringReader(content), scope, 
                new ParserConfiguration(lineNumber, extraPositionInformation, false));
    }

    public ThreadService getThreadService() {
        return threadService;
    }

    public ThreadContext getCurrentContext() {
        return threadService.getCurrentContext();
    }

    /**
     * Returns the loadService.
     * @return ILoadService
     */
    public LoadService getLoadService() {
        return loadService;
    }

    public RubyWarnings getWarnings() {
        return warnings;
    }

    public RegexpFactory getRegexpFactory() {
        return this.regexpFactory;
    }

    public PrintStream getErrorStream() {
        // FIXME: We can't guarantee this will always be a RubyIO...so the old code here is not safe
        /*java.io.OutputStream os = ((RubyIO) getGlobalVariables().get("$stderr")).getOutStream();
        if(null != os) {
            return new PrintStream(os);
        } else {
            return new PrintStream(new org.jruby.util.SwallowingOutputStream());
        }*/
        return new PrintStream(new IOOutputStream(getGlobalVariables().get("$stderr")));
    }

    public InputStream getInputStream() {
        return new IOInputStream(getGlobalVariables().get("$stdin"));
    }

    public PrintStream getOutputStream() {
        return new PrintStream(new IOOutputStream(getGlobalVariables().get("$stdout")));
    }

    public RubyModule getClassFromPath(String path) {
        RubyModule c = getObject();
        if (path.length() == 0 || path.charAt(0) == '#') {
            throw newTypeError("can't retrieve anonymous class " + path);
        }
        int pbeg = 0, p = 0;
        for(int l=path.length(); p<l; ) {
            while(p<l && path.charAt(p) != ':') {
                p++;
            }
            String str = path.substring(pbeg, p);

            if(p<l && path.charAt(p) == ':') {
                if(p+1 < l && path.charAt(p+1) != ':') {
                    throw newTypeError("undefined class/module " + path.substring(pbeg,p));
                }
                p += 2;
                pbeg = p;
            }

            IRubyObject cc = c.getConstant(str);
            if(!(cc instanceof RubyModule)) {
                throw newTypeError("" + path + " does not refer to class/module");
            }
            c = (RubyModule)cc;
        }
        return c;
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

        ThreadContext tc = getCurrentContext();
        IRubyObject backtrace = excp.callMethod(tc, "backtrace");

        PrintStream errorStream = getErrorStream();
        if (backtrace.isNil() || !(backtrace instanceof RubyArray)) {
            if (tc.getSourceFile() != null) {
                errorStream.print(tc.getPosition());
            } else {
                errorStream.print(tc.getSourceLine());
            }
        } else if (((RubyArray) backtrace).getLength() == 0) {
            printErrorPos(errorStream);
        } else {
            IRubyObject mesg = ((RubyArray) backtrace).first(IRubyObject.NULL_ARRAY);

            if (mesg.isNil()) {
                printErrorPos(errorStream);
            } else {
                errorStream.print(mesg);
            }
        }

        RubyClass type = excp.getMetaClass();
        String info = excp.toString();

        if (type == getClass("RuntimeError") && (info == null || info.length() == 0)) {
            errorStream.print(": unhandled exception\n");
        } else {
            String path = type.getName();

            if (info.length() == 0) {
                errorStream.print(": " + path + '\n');
            } else {
                if (path.startsWith("#")) {
                    path = null;
                }

                String tail = null;
                if (info.indexOf("\n") != -1) {
                    tail = info.substring(info.indexOf("\n") + 1);
                    info = info.substring(0, info.indexOf("\n"));
                }

                errorStream.print(": " + info);

                if (path != null) {
                    errorStream.print(" (" + path + ")\n");
                }

                if (tail != null) {
                    errorStream.print(tail + '\n');
                }
            }
        }

        excp.printBacktrace(errorStream);
    }

    private void printErrorPos(PrintStream errorStream) {
        ThreadContext tc = getCurrentContext();
        if (tc.getSourceFile() != null) {
            if (tc.getFrameName() != null) {
                errorStream.print(tc.getPosition());
                errorStream.print(":in '" + tc.getFrameName() + '\'');
            } else if (tc.getSourceLine() != 0) {
                errorStream.print(tc.getPosition());
            } else {
                errorStream.print(tc.getSourceFile());
            }
        }
    }

    /** This method compiles and interprets a Ruby script.
     *
     *  It can be used if you want to use JRuby as a Macro language.
     *
     */
    public void loadFile(String scriptName, Reader source) {
        if (!Ruby.isSecurityRestricted()) {
            File f = new File(scriptName);
            if(f.exists() && !f.isAbsolute() && !scriptName.startsWith("./")) {
                scriptName = "./" + scriptName;
            };
        }

        IRubyObject self = getTopSelf();
        ThreadContext context = getCurrentContext();

        try {
            secure(4); /* should alter global state */

            context.preNodeEval(objectClass, self);

            Node node = parseFile(source, scriptName, null);
            EvaluationState.eval(this, context, node, self, Block.NULL_BLOCK);
        } catch (JumpException.ReturnJump rj) {
            return;
        } finally {
            context.postNodeEval();
        }
    }

    public void loadScript(Script script) {
        IRubyObject self = getTopSelf();
        ThreadContext context = getCurrentContext();

        try {
            secure(4); /* should alter global state */

            context.preNodeEval(objectClass, self);
            
            script.load(context, self, IRubyObject.NULL_ARRAY, Block.NULL_BLOCK);
        } catch (JumpException.ReturnJump rj) {
            return;
        } finally {
            context.postNodeEval();
        }
    }

    public class CallTraceFuncHook implements EventHook {
        private RubyProc traceFunc;
        
        public void setTraceFunc(RubyProc traceFunc) {
            this.traceFunc = traceFunc;
        }
        
        public void event(ThreadContext context, int event, String file, int line, String name, IRubyObject type) {
            if (!context.isWithinTrace()) {
                if (file == null) file = "(ruby)";
                if (type == null) type = getFalse();
                
                RubyBinding binding = RubyBinding.newBinding(Ruby.this);

                context.preTrace();
                try {
                    traceFunc.call(new IRubyObject[] {
                        newString(EVENT_NAMES[event]), // event name
                        newString(file), // filename
                        newFixnum(line + 1), // line numbers should be 1-based
                        name != null ? RubySymbol.newSymbol(Ruby.this, name) : getNil(),
                        binding,
                        type
                    });
                } finally {
                    context.postTrace();
                }
            }
        }

        public boolean isInterestedInEvent(int event) {
            return true;
        }
    };
    
    private final CallTraceFuncHook callTraceFuncHook = new CallTraceFuncHook();
    
    public void addEventHook(EventHook hook) {
        eventHooks.add(hook);
    }
    
    public void removeEventHook(EventHook hook) {
        eventHooks.remove(hook);
    }

    public void setTraceFunction(RubyProc traceFunction) {
        removeEventHook(callTraceFuncHook);
        
        if (traceFunction == null) {
            return;
        }
        
        callTraceFuncHook.setTraceFunc(traceFunction);
        addEventHook(callTraceFuncHook);
    }
    
    public void callEventHooks(ThreadContext context, int event, String file, int line, String name, IRubyObject type) {
        for (EventHook eventHook : eventHooks) {
            if (eventHook.isInterestedInEvent(event)) {
                eventHook.event(context, event, file, line, name, type);
            }
        }
    }
    
    public boolean hasEventHooks() {
        return !eventHooks.isEmpty();
    }
    
    public GlobalVariables getGlobalVariables() {
        return globalVariables;
    }

    // For JSR 223 support: see http://scripting.java.net/
    public void setGlobalVariables(GlobalVariables globalVariables) {
        this.globalVariables = globalVariables;
    }

    public CallbackFactory callbackFactory(Class<?> type) {
        return CallbackFactory.createFactory(this, type);
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
    
    public void addFinalizer(Finalizable finalizer) {
        synchronized (this) {
            if (finalizers == null) {
                finalizers = new WeakHashMap<Finalizable, Object>();
            }
        }
        
        synchronized (finalizers) {
            finalizers.put(finalizer, null);
        }
    }
    
    public void removeFinalizer(Finalizable finalizer) {
        if (finalizers != null) {
            synchronized (finalizers) {
                finalizers.remove(finalizer);
            }
        }
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
            RubyProc proc = atExitBlocks.pop();

            proc.call(IRubyObject.NULL_ARRAY);
        }
        if (finalizers != null) {
            synchronized (finalizers) {
                for (Iterator<Finalizable> finalIter = new ArrayList<Finalizable>(finalizers.keySet()).iterator(); finalIter.hasNext();) {
                    finalIter.next().finalize();
                    finalIter.remove();
                }
            }
        }
    }

    // new factory methods ------------------------------------------------------------------------

    public RubyArray newArray() {
        return RubyArray.newArray(this);
    }

    public RubyArray newArrayLight() {
        return RubyArray.newArrayLight(this);
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
    
    public RubyArray newArrayNoCopy(IRubyObject[] objects) {
        return RubyArray.newArrayNoCopy(this, objects);
    }
    
    public RubyArray newArrayNoCopyLight(IRubyObject[] objects) {
        return RubyArray.newArrayNoCopyLight(this, objects);
    }
    
    public RubyArray newArray(List<IRubyObject> list) {
        return RubyArray.newArray(this, list);
    }

    public RubyArray newArray(int size) {
        return RubyArray.newArray(this, size);
    }

    public RubyBoolean newBoolean(boolean value) {
        return RubyBoolean.newBoolean(this, value);
    }

    public RubyFileStat newRubyFileStat(String file) {
        return (RubyFileStat)fileStatClass.callMethod(getCurrentContext(),"new",newString(file));
    }

    public RubyFixnum newFixnum(long value) {
        return RubyFixnum.newFixnum(this, value);
    }

    public RubyFloat newFloat(double value) {
        return RubyFloat.newFloat(this, value);
    }

    public RubyNumeric newNumeric() {
        return RubyNumeric.newNumeric(this);
    }

    public RubyProc newProc(boolean isLambda, Block block) {
        if (!isLambda && block.getProcObject() != null) return block.getProcObject();

        RubyProc proc =  RubyProc.newProc(this, isLambda);

        proc.callInit(IRubyObject.NULL_ARRAY, block);

        return proc;
    }

    public RubyBinding newBinding() {
        return RubyBinding.newBinding(this);
    }

    public RubyBinding newBinding(Block block) {
        return RubyBinding.newBinding(this, block);
    }

    public RubyString newString() {
        return RubyString.newString(this, "");
    }

    public RubyString newString(String string) {
        return RubyString.newString(this, string);
    }
    
    public RubyString newString(ByteList byteList) {
        return RubyString.newString(this, byteList);
    }
    
    public RubyString newStringShared(ByteList byteList) {
        return RubyString.newStringShared(this, byteList);
    }    

    public RubySymbol newSymbol(String string) {
        return RubySymbol.newSymbol(this, string);
    }

    public RubyTime newTime(long milliseconds) {
        return RubyTime.newTime(this, milliseconds);
    }

    public RaiseException newRuntimeError(String message) {
        return newRaiseException(getClass("RuntimeError"), message);
    }    
    
    public RaiseException newArgumentError(String message) {
        return newRaiseException(getClass("ArgumentError"), message);
    }

    public RaiseException newArgumentError(int got, int expected) {
        return newRaiseException(getClass("ArgumentError"), "wrong # of arguments(" + got + " for " + expected + ")");
    }

    public RaiseException newErrnoEBADFError() {
        return newRaiseException(getModule("Errno").getClass("EBADF"), "Bad file descriptor");
    }

    public RaiseException newErrnoECONNREFUSEDError() {
        return newRaiseException(getModule("Errno").getClass("ECONNREFUSED"), "Connection refused");
    }

    public RaiseException newErrnoEADDRINUSEError() {
        return newRaiseException(getModule("Errno").getClass("EADDRINUSE"), "Address in use");
    }

    public RaiseException newErrnoEINVALError() {
        return newRaiseException(getModule("Errno").getClass("EINVAL"), "Invalid file");
    }

    public RaiseException newErrnoENOENTError() {
        return newRaiseException(getModule("Errno").getClass("ENOENT"), "File not found");
    }

    public RaiseException newErrnoEISDirError() {
        return newRaiseException(getModule("Errno").getClass("EISDIR"), "Is a directory");
    }

    public RaiseException newErrnoESPIPEError() {
        return newRaiseException(getModule("Errno").getClass("ESPIPE"), "Illegal seek");
    }

    public RaiseException newErrnoEBADFError(String message) {
        return newRaiseException(getModule("Errno").getClass("EBADF"), message);
    }

    public RaiseException newErrnoEINVALError(String message) {
        return newRaiseException(getModule("Errno").getClass("EINVAL"), message);
    }

    public RaiseException newErrnoENOENTError(String message) {
        return newRaiseException(getModule("Errno").getClass("ENOENT"), message);
    }

    public RaiseException newErrnoESPIPEError(String message) {
        return newRaiseException(getModule("Errno").getClass("ESPIPE"), message);
    }

    public RaiseException newErrnoEEXISTError(String message) {
        return newRaiseException(getModule("Errno").getClass("EEXIST"), message);
    }
    
    public RaiseException newErrnoEDOMError(String message) {
        return newRaiseException(getModule("Errno").getClass("EDOM"), "Domain error - " + message);
    }    

    public RaiseException newIndexError(String message) {
        return newRaiseException(getClass("IndexError"), message);
    }

    public RaiseException newSecurityError(String message) {
        return newRaiseException(getClass("SecurityError"), message);
    }

    public RaiseException newSystemCallError(String message) {
        return newRaiseException(getClass("SystemCallError"), message);
    }

    public RaiseException newTypeError(String message) {
        return newRaiseException(getClass("TypeError"), message);
    }

    public RaiseException newThreadError(String message) {
        return newRaiseException(getClass("ThreadError"), message);
    }

    public RaiseException newSyntaxError(String message) {
        return newRaiseException(getClass("SyntaxError"), message);
    }

    public RaiseException newRegexpError(String message) {
        return newRaiseException(getClass("RegexpError"), message);
    }

    public RaiseException newRangeError(String message) {
        return newRaiseException(getClass("RangeError"), message);
    }

    public RaiseException newNotImplementedError(String message) {
        return newRaiseException(getClass("NotImplementedError"), message);
    }
    
    public RaiseException newInvalidEncoding(String message) {
        return newRaiseException(getClass("Iconv").getClass("InvalidEncoding"), message);
    }

    public RaiseException newNoMethodError(String message, String name, IRubyObject args) {
        return new RaiseException(new RubyNoMethodError(this, this.getClass("NoMethodError"), message, name, args), true);
    }

    public RaiseException newNameError(String message, String name) {
        return new RaiseException(new RubyNameError(this, this.getClass("NameError"), message, name), true);
    }

    public RaiseException newLocalJumpError(String reason, IRubyObject exitValue, String message) {
        return new RaiseException(new RubyLocalJumpError(this, getClass("LocalJumpError"), message, reason, exitValue), true);
    }

    public RaiseException newLoadError(String message) {
        return newRaiseException(getClass("LoadError"), message);
    }

    public RaiseException newFrozenError(String objectType) {
        // TODO: Should frozen error have its own distinct class?  If not should more share?
        return newRaiseException(getClass("TypeError"), "can't modify frozen " + objectType);
    }

    public RaiseException newSystemStackError(String message) {
        return newRaiseException(getClass("SystemStackError"), message);
    }

    public RaiseException newSystemExit(int status) {
        RubyClass exc = getClass("SystemExit");
        IRubyObject[]exArgs = new IRubyObject[]{newFixnum(status), newString("exit")};
        return new RaiseException((RubyException)exc.newInstance(exArgs, Block.NULL_BLOCK));
    }

    public RaiseException newIOError(String message) {
        return newRaiseException(getClass("IOError"), message);
    }

    public RaiseException newStandardError(String message) {
        return newRaiseException(getClass("StandardError"), message);
    }

    public RaiseException newIOErrorFromException(IOException ioe) {
        return newRaiseException(getClass("IOError"), ioe.getMessage());
    }

    public RaiseException newTypeError(IRubyObject receivedObject, RubyClass expectedType) {
        return newRaiseException(getClass("TypeError"), "wrong argument type " +
                receivedObject.getMetaClass().getRealClass() + " (expected " + expectedType + ")");
    }

    public RaiseException newEOFError() {
        return newRaiseException(getClass("EOFError"), "End of file reached");
    }

    public RaiseException newZeroDivisionError() {
        return newRaiseException(getClass("ZeroDivisionError"), "divided by 0");
    }

    public RaiseException newFloatDomainError(String message){
        return newRaiseException(getClass("FloatDomainError"), message);
    }

    /**
     * @param exceptionClass
     * @param message
     * @return
     */
    private RaiseException newRaiseException(RubyClass exceptionClass, String message) {
        RaiseException re = new RaiseException(this, exceptionClass, message, true);
        return re;
    }


    public RubySymbol.SymbolTable getSymbolTable() {
        return symbolTable;
    }

    public void setStackTraces(int stackTraces) {
        this.stackTraces = stackTraces;
    }

    public int getStackTraces() {
        return stackTraces;
    }

    public void setRandomSeed(long randomSeed) {
        this.randomSeed = randomSeed;
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    public Random getRandom() {
        return random;
    }

    public ObjectSpace getObjectSpace() {
        return objectSpace;
    }

    public Hashtable<Integer, WeakReference<IOHandler>> getIoHandlers() {
        return ioHandlers;
    }

    public long incrementRandomSeedSequence() {
        return randomSeedSequence++;
    }

    public InputStream getIn() {
        return in;
    }

    public PrintStream getOut() {
        return out;
    }

    public PrintStream getErr() {
        return err;
    }

    public boolean isGlobalAbortOnExceptionEnabled() {
        return globalAbortOnExceptionEnabled;
    }

    public void setGlobalAbortOnExceptionEnabled(boolean enable) {
        globalAbortOnExceptionEnabled = enable;
    }

    public boolean isDoNotReverseLookupEnabled() {
        return doNotReverseLookupEnabled;
    }

    public void setDoNotReverseLookupEnabled(boolean b) {
        doNotReverseLookupEnabled = b;
    }

    private ThreadLocal<Map<Object, Object>> inspect = new ThreadLocal<Map<Object, Object>>();
    public void registerInspecting(Object obj) {
        Map<Object, Object> val = inspect.get();
        if (val == null) inspect.set(val = new IdentityHashMap<Object, Object>());
        val.put(obj, null);
    }

    public boolean isInspecting(Object obj) {
        Map<Object, Object> val = inspect.get();
        return val == null ? false : val.containsKey(obj);
    }

    public void unregisterInspecting(Object obj) {
        Map<Object, Object> val = inspect.get();
        if (val != null ) val.remove(obj);
    }

    public boolean isObjectSpaceEnabled() {
        return objectSpaceEnabled;
    }

    public long getStartTime() {
        return startTime;
    }

    public Profile getProfile() {
        return profile;
    }

    public String getJRubyHome() {
        if (jrubyHome == null) {
            jrubyHome = verifyHome(System.getProperty("jruby.home", System.getProperty("user.home") + "/.jruby"));
        }
        
        try {
            // This comment also in rbConfigLibrary
            // Our shell scripts pass in non-canonicalized paths, but even if we didn't
            // anyone who did would become unhappy because Ruby apps expect no relative
            // operators in the pathname (rubygems, for example).
            return new NormalizedFile(jrubyHome).getCanonicalPath();
        } catch (IOException e) {}
        
        return new NormalizedFile(jrubyHome).getAbsolutePath();
    }
    
    public void setJRubyHome(String home) {
        jrubyHome = verifyHome(home);
    }

    // We require the home directory to be absolute
    private String verifyHome(String home) {
        if (home.equals(".")) {
            home = System.getProperty("user.dir");
        }
        NormalizedFile f = new NormalizedFile(home);
        if (!f.isAbsolute()) {
            home = f.getAbsolutePath();
        }
        f.mkdirs();
        return home;
    }

    public RubyInstanceConfig getInstanceConfig() {
        return config;
    }

    /** GET_VM_STATE_VERSION */
    public long getGlobalState() {
        synchronized(this) {
            return globalState;
        }
    }

    /** INC_VM_STATE_VERSION */
    public void incGlobalState() {
        synchronized(this) {
            globalState = (globalState+1) & 0x8fffffff;
        }
    }

    public static boolean isSecurityRestricted() {
        return securityRestricted;
    }
    
    public static void setSecurityRestricted(boolean restricted) {
        securityRestricted = restricted;
    }

    private static POSIX loadPosix() {
        try {
            POSIX posix = (POSIX)Native.loadLibrary("c", POSIX.class);
            if (posix != null) {
                return posix;
            }
        } catch (Throwable t) {
        }

        // on any error, fall back on our own stupid POSIX impl
        return new JavaBasedPOSIX();
    }
    
    public static POSIX getPosix() {
        return posix;
    }
}
