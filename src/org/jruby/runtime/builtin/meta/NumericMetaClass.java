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
 * Copyright (C) 2005 Thomas E Enebo <enebo@acm.org>
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
package org.jruby.runtime.builtin.meta;

import org.jruby.IRuby;
import org.jruby.RubyClass;
import org.jruby.RubyNumeric;
import org.jruby.runtime.Arity;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.collections.SinglyLinkedList;

public class NumericMetaClass extends ObjectMetaClass {
	public NumericMetaClass(IRuby runtime) {
        super("Numeric", RubyNumeric.class, runtime.getObject(), NUMERIC_ALLOCATOR);
    }
	    
	public NumericMetaClass(String name, RubyClass superClass, ObjectAllocator allocator, SinglyLinkedList parentCRef) {
        super(name, RubyNumeric.class, superClass, allocator, parentCRef);
    }

    public NumericMetaClass(String name, Class clazz, RubyClass superClass, ObjectAllocator allocator) {
    	super(name, clazz, superClass, allocator);
    }

    public NumericMetaClass(String name, Class clazz, RubyClass superClass, ObjectAllocator allocator, SinglyLinkedList parentCRef) {
    	super(name, clazz, superClass, allocator, parentCRef);
    }
    
    protected class NumericMeta extends Meta {
	    protected void initializeClass() {
	        includeModule(getRuntime().getModule("Comparable"));
	
	        defineFastMethod("+@", Arity.noArguments(), "op_uplus");
	        defineFastMethod("-@", Arity.noArguments(), "op_uminus");
	        defineFastMethod("<=>", Arity.singleArgument(), "cmp");
	        defineFastMethod("==", Arity.singleArgument(), "equal");
	        defineFastMethod("equal?", Arity.singleArgument(), "veryEqual");
	        defineFastMethod("===", Arity.singleArgument(), "equal");
	        defineFastMethod("abs", Arity.noArguments());
	        defineFastMethod("ceil", Arity.noArguments());
	        defineFastMethod("coerce", Arity.singleArgument());
	        defineFastMethod("clone", Arity.noArguments(), "rbClone");
	        defineFastMethod("divmod", Arity.singleArgument(), "divmod");
	        defineFastMethod("eql?", Arity.singleArgument(), "eql");
	        defineFastMethod("floor", Arity.noArguments());
	        defineFastMethod("integer?", Arity.noArguments(), "int_p");
	        defineFastMethod("modulo", Arity.singleArgument());
	        defineFastMethod("nonzero?", Arity.noArguments(), "nonzero_p");
	        defineFastMethod("remainder", Arity.singleArgument());
	        defineFastMethod("round", Arity.noArguments());
	        defineFastMethod("truncate", Arity.noArguments());
	        defineFastMethod("to_int", Arity.noArguments());
	        defineFastMethod("zero?", Arity.noArguments(), "zero_p");
            defineMethod("step", Arity.required(1), "step");
            
            // Add relational operators that are faster than comparable's implementations
            defineFastMethod(">=", Arity.singleArgument(), "op_ge");
            defineFastMethod(">", Arity.singleArgument(), "op_gt");
            defineFastMethod("<=", Arity.singleArgument(), "op_le");
            defineFastMethod("<", Arity.singleArgument(), "op_lt");
	    }
    };
    
    protected Meta getMeta() {
    	return new NumericMeta();
    }
		
    public RubyClass newSubClass(String name, SinglyLinkedList parentCRef) {
        // FIXME: this and the other newSubClass impls should be able to defer to the default impl
        return new NumericMetaClass(name, this, NUMERIC_ALLOCATOR, parentCRef);
    }

    private static ObjectAllocator NUMERIC_ALLOCATOR = new ObjectAllocator() {
        public IRubyObject allocate(IRuby runtime, RubyClass klass) {
            RubyNumeric instance = runtime.newNumeric();

            instance.setMetaClass(klass);

            return instance;
        }
    };
}
