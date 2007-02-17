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
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004-2005 Charles O Nutter <headius@headius.com>
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
package org.jruby.test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.jruby.*;
import org.jruby.RubySymbol.SymbolTable;
import org.jruby.ast.Node;
import org.jruby.common.RubyWarnings;
import org.jruby.exceptions.RaiseException;
import org.jruby.internal.runtime.GlobalVariables;
import org.jruby.internal.runtime.ThreadService;
import org.jruby.javasupport.JavaSupport;
import org.jruby.lexer.yacc.ISourcePosition;
import org.jruby.parser.Parser;
import org.jruby.runtime.Block;
import org.jruby.runtime.CacheMap;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.GlobalVariable;
import org.jruby.runtime.MethodSelectorTable;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ObjectSpace;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.load.LoadService;
import org.jruby.util.collections.SinglyLinkedList;
import org.jruby.util.JRubyClassLoader;
import org.jruby.util.KCode;

public class BaseMockRuby implements IRuby {

    public CacheMap getCacheMap() {
        throw new MockException();
    }

    public IRubyObject evalScript(String script) {
        throw new MockException();
    }

    public IRubyObject eval(Node node) {
        throw new MockException();
    }

    public RubyClass getObject() {
        throw new MockException();
    }

    public RubyModule getKernel() {
        throw new MockException();
    }

    public RubyClass getString() {
        throw new MockException();
    }

    public RubyClass getFixnum() {
        throw new MockException();
    }

    public RubyClass getArray() {
        throw new MockException();
    }    

    public RubyBoolean getTrue() {
        throw new MockException();
    }

    public RubyBoolean getFalse() {
        throw new MockException();
    }

    public IRubyObject getNil() {
        throw new MockException();
    }

    public RubyClass getNilClass() {
        throw new MockException();
    }

    public RubyModule getModule(String name) {
        throw new MockException();
    }

    public RubyClass getClass(String name) {
        throw new MockException();
    }

    public RubyClass defineClass(String name, RubyClass superClass, ObjectAllocator allocator) {
        throw new MockException();
    }

    public RubyClass defineClassUnder(String name, RubyClass superClass, ObjectAllocator allocator,
            SinglyLinkedList parentCRef) {
        throw new MockException();
    }

    public RubyModule defineModule(String name) {
        throw new MockException();
    }

    public RubyModule defineModuleUnder(String name, SinglyLinkedList parentCRef) {
        throw new MockException();
    }

    public RubyModule getOrCreateModule(String name) {
        throw new MockException();
    }

    public int getSafeLevel() {
        throw new MockException();
    }

    public void setSafeLevel(int safeLevel) {
        throw new MockException();
    }

    public void secure(int level) {
        throw new MockException();
    }

    public void defineGlobalConstant(String name, IRubyObject value) {
        throw new MockException();
    }

    public IRubyObject getTopConstant(String name) {
        throw new MockException();
    }

    public boolean isClassDefined(String name) {
        throw new MockException();
    }

    public IRubyObject yield(IRubyObject value) {
        throw new MockException();
    }

    public IRubyObject yield(IRubyObject value, IRubyObject self,
            RubyModule klass, boolean checkArguments) {
        throw new MockException();
    }

    public IRubyObject getTopSelf() {
        throw new MockException();
    }

    public String getSourceFile() {
        throw new MockException();
    }

    public int getSourceLine() {
        throw new MockException();
    }

    public IRubyObject getVerbose() {
        throw new MockException();
    }

    public IRubyObject getDebug() {
        throw new MockException();
    }

    public boolean isBlockGiven() {
        throw new MockException();
    }

    public boolean isFBlockGiven() {
        throw new MockException();

    }

    public void setVerbose(IRubyObject verbose) {
        throw new MockException();

    }

    public void setDebug(IRubyObject debug) {
        throw new MockException();
    }

    public Visibility getCurrentVisibility() {
        throw new MockException();

    }

    public void setCurrentVisibility(Visibility visibility) {
        throw new MockException();

    }

    public void defineVariable(GlobalVariable variable) {
        throw new MockException();

    }

    public void defineReadonlyVariable(String name, IRubyObject value) {
        throw new MockException();

    }

    public Node parse(Reader content, String file) {
        throw new MockException();

    }

    public Node parse(String content, String file) {
        throw new MockException();

    }

    public Parser getParser() {
        throw new MockException();

    }

    public ThreadService getThreadService() {
        throw new MockException();

    }

    public ThreadContext getCurrentContext() {
        throw new MockException();

    }

    public LoadService getLoadService() {
        throw new MockException();

    }

    public RubyWarnings getWarnings() {
        throw new MockException();

    }

    public PrintStream getErrorStream() {
        throw new MockException();

    }

    public InputStream getInputStream() {
        throw new MockException();

    }

    public PrintStream getOutputStream() {
        throw new MockException();

    }

    public RubyModule getClassFromPath(String path) {
        throw new MockException();

    }

    public void printError(RubyException excp) {
        throw new MockException();

    }

    public void loadScript(RubyString scriptName, RubyString source,
            boolean wrap) {
        throw new MockException();

    }

    public void loadScript(String scriptName, Reader source, boolean wrap) {
        throw new MockException();

    }

    public void loadNode(String scriptName, Node node, boolean wrap) {
        throw new MockException();

    }

    public void loadFile(File file, boolean wrap) {
        throw new MockException();

    }

    public void callTraceFunction(ThreadContext context, String event, ISourcePosition position,
            IRubyObject self, String name, IRubyObject type) {
        throw new MockException();

    }

    public RubyProc getTraceFunction() {
        throw new MockException();

    }

    public void setTraceFunction(RubyProc traceFunction) {
        throw new MockException();

    }

    public GlobalVariables getGlobalVariables() {
        throw new MockException();

    }

    public void setGlobalVariables(GlobalVariables variables) {
        throw new MockException();

    }

    public CallbackFactory callbackFactory(Class type) {
        throw new MockException();

    }

    public IRubyObject pushExitBlock(RubyProc proc) {
        throw new MockException();

    }

    public void tearDown() {
        throw new MockException();

    }

    public RubyArray newArray() {
        throw new MockException();

    }

    public RubyArray newArray(IRubyObject object) {
        throw new MockException();

    }

    public RubyArray newArray(IRubyObject car, IRubyObject cdr) {
        throw new MockException();

    }

    public RubyArray newArray(IRubyObject[] objects) {
        throw new MockException();

    }

    public RubyArray newArrayNoCopy(IRubyObject[] objects) {
        throw new MockException();

    }

    public RubyArray newArray(List list) {
        throw new MockException();

    }

    public RubyArray newArray(int size) {
        throw new MockException();

    }

    public RubyBoolean newBoolean(boolean value) {
        throw new MockException();

    }

    public RubyFileStat newRubyFileStat(String file) {
        throw new MockException();

    }

    public RubyFixnum newFixnum(long value) {
        throw new MockException();

    }

    public RubyFloat newFloat(double value) {
        throw new MockException();

    }

    public RubyNumeric newNumeric() {
        throw new MockException();

    }

    public RubyProc newProc(boolean isLambda, Block block) {
        throw new MockException();

    }

    public RubyBinding newBinding() {
        throw new MockException();

    }

    public RubyBinding newBinding(Block block) {
        throw new MockException();

    }

    public RubyString newString(String string) {
        throw new MockException();

    }

    public RubySymbol newSymbol(String string) {
        throw new MockException();

    }

    public RubyTime newTime(long milliseconds) {
        throw new MockException();
    }

    public RaiseException newArgumentError(String message) {
        throw new MockException();

    }

    public RaiseException newArgumentError(int got, int expected) {
        throw new MockException();

    }

    public RaiseException newErrnoECONNREFUSEDError() {
        throw new MockException();

    }

    public RaiseException newErrnoEADDRINUSEError() {
        throw new MockException();

    }

    public RaiseException newErrnoEBADFError() {
        throw new MockException();

    }

    public RaiseException newErrnoEINVALError() {
        throw new MockException();

    }

    public RaiseException newErrnoENOENTError() {
        throw new MockException();

    }

    public RaiseException newErrnoESPIPEError() {
        throw new MockException();

    }

    public RaiseException newErrnoEBADFError(String message) {
        throw new MockException();

    }

    public RaiseException newErrnoEINVALError(String message) {
        throw new MockException();

    }

    public RaiseException newErrnoENOENTError(String message) {
        throw new MockException();

    }

    public RaiseException newErrnoESPIPEError(String message) {
        throw new MockException();

    }

    public RaiseException newErrnoEEXISTError(String message) {
        throw new MockException();

    }
    
    public RaiseException newErrnoEDOMError(String message) {
        throw new MockException();

    }
    

    public RaiseException newIndexError(String message) {
        throw new MockException();

    }

    public RaiseException newSecurityError(String message) {
        throw new MockException();

    }

    public RaiseException newSystemCallError(String message) {
        throw new MockException();

    }

    public RaiseException newTypeError(String message) {
        throw new MockException();

    }

    public RaiseException newThreadError(String message) {
        throw new MockException();

    }

    public RaiseException newSyntaxError(String message) {
        throw new MockException();

    }

    public RaiseException newRangeError(String message) {
        throw new MockException();

    }

    public RaiseException newNotImplementedError(String message) {
        throw new MockException();

    }

    public RaiseException newNoMethodError(String message, String name) {
        throw new MockException();

    }

    public RaiseException newNameError(String message, String name) {
        throw new MockException();

    }

    public RaiseException newLocalJumpError(String message) {
        throw new MockException();

    }

    public RaiseException newLoadError(String message) {
        throw new MockException();

    }

    public RaiseException newFrozenError(String objectType) {
        throw new MockException();

    }

    public RaiseException newSystemStackError(String message) {
        throw new MockException();

    }

    public RaiseException newSystemExit(int status) {
        throw new MockException();

    }

    public RaiseException newIOError(String message) {
        throw new MockException();

    }

    public RaiseException newStandardError(String message) {
        throw new MockException();

    }

    public RaiseException newIOErrorFromException(IOException ioe) {
        throw new MockException();

    }

    public RaiseException newTypeError(IRubyObject receivedObject,
            RubyClass expectedType) {
        throw new MockException();

    }

    public RaiseException newEOFError() {
        throw new MockException();

    }

    public SymbolTable getSymbolTable() {
        throw new MockException();

    }

    public void setStackTraces(int stackTraces) {
        throw new MockException();

    }

    public int getStackTraces() {
        throw new MockException();

    }

    public void setRandomSeed(long randomSeed) {
        throw new MockException();

    }

    public long getRandomSeed() {
        throw new MockException();

    }

    public Random getRandom() {
        throw new MockException();

    }

    public ObjectSpace getObjectSpace() {
        throw new MockException();

    }

    public Hashtable getIoHandlers() {
        throw new MockException();

    }

    public RubyFixnum[] getFixnumCache() {
        throw new MockException();

    }

    public long incrementRandomSeedSequence() {
        throw new MockException();

    }

    public JavaSupport getJavaSupport() {
        throw new MockException();
    }

    public JRubyClassLoader getJRubyClassLoader() {
        throw new MockException();
    }

    public String getCurrentDirectory() {
        throw new MockException();
    }

    public void setCurrentDirectory(String dir) {
        throw new MockException();
    }

    public RaiseException newZeroDivisionError() {
        throw new MockException();
    }

    public RaiseException newFloatDomainError(String message) {
        throw new MockException();
    }

    public InputStream getIn() {
        throw new MockException();
    }

    public PrintStream getOut() {
        throw new MockException();
    }

    public PrintStream getErr() {
        throw new MockException();
    }

    public boolean isGlobalAbortOnExceptionEnabled() {
        throw new MockException();
    }

    public void setGlobalAbortOnExceptionEnabled(boolean b) {
        throw new MockException();
    }

    public boolean isDoNotReverseLookupEnabled() {
        throw new MockException();
    }

    public void setDoNotReverseLookupEnabled(boolean b) {
        throw new MockException();
    }

    public boolean registerInspecting(Object o) {
        throw new MockException();
    }
    public void unregisterInspecting(Object o) {
        throw new MockException();
    }

    public boolean isObjectSpaceEnabled() {
        return true;
    }

    public IRubyObject compileAndRun(Node node) {
        return null;
    }

    public IRubyObject ycompileAndRun(Node node) {
        return null;
    }

    public Node parse(Reader content, String file, DynamicScope scope) {
        return null;
    }

    public Node parse(String content, String file, DynamicScope scope) {
        return null;
    }

    public IRubyObject getTmsStruct() {
        return null;
    }

    public long getStartTime() {
        return 0;
    }

    public Profile getProfile() {
        return null;
    }

    public Map getRuntimeInformation() {
        return null;
    }

    public String getJRubyHome() {
        return null;
    }

    public RubyInstanceConfig getInstanceConfig() {
        return null;
    }

    public MethodSelectorTable getSelectorTable() {
        return null;
    }

    public KCode getKCode() {
        return null;
    }

    public void setKCode(KCode kcode) {
        return;
    }

    /** GET_VM_STATE_VERSION */
    public long getGlobalState() {
        return 0;
    }

    /** INC_VM_STATE_VERSION */
    public void incGlobalState() {
    }
}
