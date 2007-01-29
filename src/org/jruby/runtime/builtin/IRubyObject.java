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
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2002-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2006 Ola Bini <ola.bini@ki.se>
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
package org.jruby.runtime.builtin;

import java.util.Iterator;
import java.util.Map;

import org.jruby.MetaClass;
import org.jruby.IRuby;
import org.jruby.RubyArray;
import org.jruby.RubyClass;
import org.jruby.RubyFloat;
import org.jruby.RubyInteger;
import org.jruby.RubyModule;
import org.jruby.RubyFixnum;
import org.jruby.RubyString;
import org.jruby.ast.Node;
import org.jruby.runtime.Block;
import org.jruby.runtime.CallType;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.callback.Callback;

/** Object is the parent class of all classes in Ruby. Its methods are
 * therefore available to all objects unless explicitly overridden.
 *
 * @author  jpetersen
 */
public interface IRubyObject {
    public static final IRubyObject[] NULL_ARRAY = new IRubyObject[0];
    
    /**
     * Return the ClassIndex value for the native type this object was
     * constructed from. Particularly useful for determining marshalling
     * format. All instances of Hash instances of subclasses of Hash, for example
     * are of Java type RubyHash, and so should utilize RubyHash marshalling
     * logic in addition to user-defined class marshalling logic.
     * 
     * @return the ClassIndex of the native type this object was constructed from
     */
    int getNativeTypeIndex();
    
    /**
     * Gets a copy of the instance variables for this object, if any exist.
     * Returns null if this object has no instance variables.
     * "safe" in that it doesn't cause the instance var map to be created.
     * 
     * @return A snapshot of the instance vars, or null if none.
     */
    Map safeGetInstanceVariables();
    
    /**
     * Returns true if the object has any instance variables, false otherwise.
     * "safe" in that it doesn't cause the instance var map to be created.
     * 
     * @return true if the object has instance variables, false otherwise.
     */
    boolean safeHasInstanceVariables();
    
    /**
     * RubyMethod getInstanceVar.
     * @param string
     * @return RubyObject
     */
    IRubyObject getInstanceVariable(String string);

    /**
     * RubyMethod setInstanceVar.
     * @param string
     * @param rubyObject
     * @return RubyObject
     */
    IRubyObject setInstanceVariable(String string, IRubyObject rubyObject);
    
    Map getInstanceVariables();
    Map getInstanceVariablesSnapshot();

    IRubyObject callMethod(ThreadContext context, RubyModule rubyclass, String name, IRubyObject[] args, CallType callType, Block block);
    IRubyObject callMethod(ThreadContext context, RubyModule rubyclass, byte switchvalue, String name, IRubyObject[] args, CallType callType);
    
    IRubyObject callMethod(ThreadContext context, byte switchValue, String name, IRubyObject[] args, CallType callType, Block block);
    
    IRubyObject callMethod(ThreadContext context, String name, IRubyObject[] args, CallType callType);
    IRubyObject callMethod(ThreadContext context, String name, IRubyObject[] args, CallType callType, Block block);
    
    /**
     * RubyMethod funcall.
     * @param context TODO
     * @param string
     * @return RubyObject
     */
    IRubyObject callMethod(ThreadContext context, String string);
    IRubyObject callMethod(ThreadContext context, String string, Block aBlock);

    /**
     * RubyMethod funcall.
     * @param context TODO
     * @param string
     * @param arg
     * @return RubyObject
     */
    IRubyObject callMethod(ThreadContext context, String string, IRubyObject arg);

    /**
     * RubyMethod callMethod.
     * @param context TODO
     * @param method
     * @param rubyArgs
     * @return IRubyObject
     */
    IRubyObject callMethod(ThreadContext context, String method, IRubyObject[] rubyArgs);
    IRubyObject callMethod(ThreadContext context, String method, IRubyObject[] rubyArgs, Block block);

    /**
     * RubyMethod isNil.
     * @return boolean
     */
    boolean isNil();

    boolean isTrue();

    /**
     * RubyMethod isTaint.
     * @return boolean
     */
    boolean isTaint();

    /**
     * RubyMethod isFrozen.
     * @return boolean
     */
    boolean isFrozen();

    boolean isImmediate();

    /**
     * RubyMethod getRubyClass.
     */
    RubyClass getMetaClass();

    void setMetaClass(RubyClass metaClass);

    /**
     * RubyMethod getSingletonClass.
     * @return RubyClass
     */
    MetaClass getSingletonClass();

    /**
     * RubyMethod getType.
     * @return RubyClass
     */
    RubyClass getType();

    /**
     * RubyMethod isKindOf.
     * @param rubyClass
     * @return boolean
     */
    boolean isKindOf(RubyModule rubyClass);

    /**
     * RubyMethod respondsTo.
     * @param string
     * @return boolean
     */
    boolean respondsTo(String string);

    /**
     * RubyMethod getRuntime.
     */
    IRuby getRuntime();

    /**
     * RubyMethod getJavaClass.
     * @return Class
     */
    Class getJavaClass();

    /**
     * RubyMethod eval.
     * @param iNode
     * @return IRubyObject
     */
    IRubyObject eval(Node iNode);

    /**
     * Evaluate the given string under the specified binding object. If the binding is not a Proc or Binding object
     * (RubyProc or RubyBinding) throw an appropriate type error.
     * @param context TODO
     * @param evalString The string containing the text to be evaluated
     * @param binding The binding object under which to perform the evaluation
     * @param file The filename to use when reporting errors during the evaluation
     * @return An IRubyObject result from the evaluation
     */
    IRubyObject evalWithBinding(ThreadContext context, IRubyObject evalString, IRubyObject binding, String file);

    /**
     * Evaluate the given string.
     * @param context TODO
     * @param evalString The string containing the text to be evaluated
     * @param file The filename to use when reporting errors during the evaluation
     * @param binding The binding object under which to perform the evaluation
     * @return An IRubyObject result from the evaluation
     */
    IRubyObject evalSimple(ThreadContext context, IRubyObject evalString, String file);

    /**
     * RubyMethod extendObject.
     * @param rubyModule
     */
    void extendObject(RubyModule rubyModule);

    /**
     * Convert the object into a symbol name if possible.
     * 
     * @return String the symbol name
     */
    String asSymbol();

    /**
     * Methods which perform to_xxx if the object has such a method
     */
    RubyArray convertToArray();
    RubyFloat convertToFloat();
    RubyInteger convertToInteger();
    RubyString convertToString();

    /**
     * Converts this object to type 'targetType' using 'convertMethod' method (MRI: convert_type).
     * 
     * @param targetType is the type we are trying to convert to
     * @param convertMethod is the method to be called to try and convert to targeType
     * @param raiseOnError will throw an Error if conversion does not work
     * @return the converted value
     */
    IRubyObject convertToType(String targetType, String convertMethod, boolean raiseOnError);

    /**
     * Higher level conversion utility similiar to convertToType but it can throw an
     * additional TypeError during conversion (MRI: rb_check_convert_type).
     * 
     * @param targetType is the type we are trying to convert to
     * @param convertMethod is the method to be called to try and convert to targeType
     * @return the converted value
     */
    IRubyObject convertToTypeWithCheck(String targetType, String convertMethod);


    /**
     * RubyMethod setTaint.
     * @param b
     */
    void setTaint(boolean b);

    /**
     * RubyMethod checkSafeString.
     */
    void checkSafeString();

    /**
     * RubyMethod convertType.
     * @param type
     * @param string
     * @param string1
     */
    IRubyObject convertType(Class type, String string, String string1);

    /**
     * RubyMethod dup.
     */
    IRubyObject dup();

    /**
     * RubyMethod setupClone.
     * @param original
     */
    void initCopy(IRubyObject original);

    /**
     * RubyMethod setFrozen.
     * @param b
     */
    void setFrozen(boolean b);

    /**
     * RubyMethod inspect.
     * @return String
     */
    IRubyObject inspect();

    /**
     * Make sure the arguments fit the range specified by minimum and maximum.  On
     * a failure, The Ruby runtime will generate an ArgumentError.
     * 
     * @param arguments to check
     * @param minimum number of args
     * @param maximum number of args (-1 for any number of args)
     * @return the number of arguments in args
     */
    int checkArgumentCount(IRubyObject[] arguments, int minimum, int maximum);

    /**
     * RubyMethod rbClone.
     * @return IRubyObject
     */
    IRubyObject rbClone();


    public void callInit(IRubyObject[] args, Block block);

    /**
     * RubyMethod defineSingletonMethod.
     * @param name
     * @param callback
     */
    void defineSingletonMethod(String name, Callback callback);

    boolean singletonMethodsAllowed();

	Iterator instanceVariableNames();

    /**
     * rb_scan_args
     *
     * This method will take the arguments specified, fill in an array and return it filled
     * with nils for every argument not provided. It's guaranteed to always return a new array.
     * 
     * @param args the arguments to check
     * @param required the amount of required arguments
     * @param optional the amount of optional arguments
     * @return a new array containing all arguments provided, and nils in those spots not provided.
     * 
     */
    IRubyObject[] scanArgs(IRubyObject[] args, int required, int optional);

    /**
     * Our version of Data_Wrap_Struct.
     *
     * This method will just set a private pointer to the object provided. This pointer is transient
     * and will not be accessible from Ruby.
     *
     * @param obj the object to wrap
     */
    void dataWrapStruct(Object obj);

    /**
     * Our version of Data_Get_Struct.
     *
     * Returns a wrapped data value if there is one, otherwise returns null.
     *
     * @return the object wrapped.
     */
    Object dataGetStruct();

    RubyFixnum id();

    IRubyObject anyToString();

    IRubyObject checkStringType();
}
