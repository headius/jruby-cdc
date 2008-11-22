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
 * Copyright (C) 2001 Alan Moore <alan_moore@gmx.net>
 * Copyright (C) 2001 Chad Fowler <chadfowler@chadfowler.com>
 * Copyright (C) 2001-2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2001-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2002-2005 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004-2005 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2006 Ola Bini <Ola.Bini@ki.se>
 * Copyright (C) 2006 Daniel Steer <damian.steer@hp.com>
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

import static org.jruby.RubyEnumerator.enumeratorize;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.Stack;

import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.common.IRubyWarnings.ID;
import org.jruby.javasupport.JavaUtil;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.BlockBody;
import org.jruby.runtime.ClassIndex;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.marshal.MarshalStream;
import org.jruby.runtime.marshal.UnmarshalStream;
import org.jruby.util.ByteList;
import org.jruby.util.Pack;

/**
 * The implementation of the built-in class Array in Ruby.
 *
 * Concurrency: no synchronization is required among readers, but
 * all users must synchronize externally with writers.
 *
 */
@JRubyClass(name="Array")
public class RubyArray extends RubyObject implements List {

    public static RubyClass createArrayClass(Ruby runtime) {
        RubyClass arrayc = runtime.defineClass("Array", runtime.getObject(), ARRAY_ALLOCATOR);
        runtime.setArray(arrayc);
        arrayc.index = ClassIndex.ARRAY;
        arrayc.kindOf = new RubyModule.KindOf() {
            @Override
            public boolean isKindOf(IRubyObject obj, RubyModule type) {
                return obj instanceof RubyArray;
            }
        };

        arrayc.includeModule(runtime.getEnumerable());
        arrayc.defineAnnotatedMethods(RubyArray.class);

        return arrayc;
    }

    private static ObjectAllocator ARRAY_ALLOCATOR = new ObjectAllocator() {
        public IRubyObject allocate(Ruby runtime, RubyClass klass) {
            return new RubyArray(runtime, klass, IRubyObject.NULL_ARRAY);
        }
    };

    @Override
    public int getNativeTypeIndex() {
        return ClassIndex.ARRAY;
    }

    private final void concurrentModification() {
        throw getRuntime().newConcurrencyError("Detected invalid array contents due to unsynchronized modifications with concurrent users");
    }

    /** rb_ary_s_create
     * 
     */
    @JRubyMethod(name = "[]", rest = true, frame = true, meta = true)
    public static IRubyObject create(IRubyObject klass, IRubyObject[] args, Block block) {
        RubyArray arr = (RubyArray) ((RubyClass) klass).allocate();

        if (args.length > 0) {
            arr.values = new IRubyObject[args.length];
            System.arraycopy(args, 0, arr.values, 0, args.length);
            arr.realLength = args.length;
        }
        return arr;
    }

    /** rb_ary_new2
     *
     */
    public static final RubyArray newArray(final Ruby runtime, final long len) {
        RubyArray array = new RubyArray(runtime, len);
        fillNil(array.values, 0, array.values.length, runtime);
        return array;
    }
    public static final RubyArray newArrayLight(final Ruby runtime, final long len) {
        RubyArray array = new RubyArray(runtime, len, false);
        fillNil(array.values, 0, array.values.length, runtime);
        return array;
    }

    /** rb_ary_new
     *
     */
    public static final RubyArray newArray(final Ruby runtime) {
        return newArray(runtime, ARRAY_DEFAULT_SIZE);
    }

    /** rb_ary_new
     *
     */
    public static final RubyArray newArrayLight(final Ruby runtime) {
        /* Ruby arrays default to holding 16 elements, so we create an
         * ArrayList of the same size if we're not told otherwise
         */
        return newArrayLight(runtime, ARRAY_DEFAULT_SIZE);
    }

    public static RubyArray newArray(Ruby runtime, IRubyObject obj) {
        return new RubyArray(runtime, new IRubyObject[] { obj });
    }

    public static RubyArray newArrayLight(Ruby runtime, IRubyObject obj) {
        return new RubyArray(runtime, new IRubyObject[] { obj }, false);
    }

    public static RubyArray newArrayLight(Ruby runtime, IRubyObject... objs) {
        return new RubyArray(runtime, objs, false);
    }

    /** rb_assoc_new
     *
     */
    public static RubyArray newArray(Ruby runtime, IRubyObject car, IRubyObject cdr) {
        return new RubyArray(runtime, new IRubyObject[] { car, cdr });
    }
    
    public static RubyArray newEmptyArray(Ruby runtime) {
        return new RubyArray(runtime, NULL_ARRAY);
    }

    /** rb_ary_new4, rb_ary_new3
     *   
     */
    public static RubyArray newArray(Ruby runtime, IRubyObject[] args) {
        RubyArray arr = new RubyArray(runtime, new IRubyObject[args.length]);
        System.arraycopy(args, 0, arr.values, 0, args.length);
        arr.realLength = args.length;
        return arr;
    }
    
    public static RubyArray newArrayNoCopy(Ruby runtime, IRubyObject[] args) {
        return new RubyArray(runtime, args);
    }
    
    public static RubyArray newArrayNoCopy(Ruby runtime, IRubyObject[] args, int begin) {
        return new RubyArray(runtime, args, begin);
    }
    
    public static RubyArray newArrayNoCopyLight(Ruby runtime, IRubyObject[] args) {
        RubyArray arr = new RubyArray(runtime, false);
        arr.values = args;
        arr.realLength = args.length;
        return arr;
    }

    public static RubyArray newArray(Ruby runtime, Collection<IRubyObject> collection) {
        RubyArray arr = new RubyArray(runtime, collection.size());
        collection.toArray(arr.values);
        arr.realLength = arr.values.length;
        return arr;
    }

    public static final int ARRAY_DEFAULT_SIZE = 16;    

    // volatile to ensure that initial nil-fill is visible to other threads
    private volatile IRubyObject[] values;

    private static final int TMPLOCK_ARR_F = 1 << 9;
    private static final int TMPLOCK_OR_FROZEN_ARR_F = TMPLOCK_ARR_F | FROZEN_F;

    private volatile boolean isShared = false;
    private int begin = 0;
    private int realLength = 0;

    /* 
     * plain internal array assignment
     */
    private RubyArray(Ruby runtime, IRubyObject[] vals) {
        super(runtime, runtime.getArray());
        values = vals;
        realLength = vals.length;
    }

    /* 
     * plain internal array assignment
     */
    private RubyArray(Ruby runtime, IRubyObject[] vals, boolean objectSpace) {
        super(runtime, runtime.getArray(), objectSpace);
        values = vals;
        realLength = vals.length;
    }

    /* 
     * plain internal array assignment
     */
    private RubyArray(Ruby runtime, IRubyObject[] vals, int begin) {
        super(runtime, runtime.getArray());
        this.values = vals;
        this.begin = begin;
        this.realLength = vals.length - begin;
        this.isShared = true;
    }
    
    /* rb_ary_new2
     * just allocates the internal array
     */
    private RubyArray(Ruby runtime, long length) {
        super(runtime, runtime.getArray());
        checkLength(length);
        values = new IRubyObject[(int)length];
    }
    
    private RubyArray(Ruby runtime, int length) {
        super(runtime, runtime.getArray());
        values = new IRubyObject[length];
    }

    private RubyArray(Ruby runtime, long length, boolean objectspace) {
        super(runtime, runtime.getArray(), objectspace);
        checkLength(length);
        values = new IRubyObject[(int)length];
    }

    private RubyArray(Ruby runtime, int length, boolean objectspace) {
        super(runtime, runtime.getArray(), objectspace);
        values = new IRubyObject[length];
    }

    /* NEWOBJ and OBJSETUP equivalent
     * fastest one, for shared arrays, optional objectspace
     */
    private RubyArray(Ruby runtime, boolean objectSpace) {
        super(runtime, runtime.getArray(), objectSpace);
    }

    public RubyArray(Ruby runtime, RubyClass klass) {
        super(runtime, klass);
        alloc(ARRAY_DEFAULT_SIZE);
    }
    
    /* Array constructors taking the MetaClass to fulfil MRI Array subclass behaviour
     * 
     */
    private RubyArray(Ruby runtime, RubyClass klass, int length) {
        super(runtime, klass);
        values = new IRubyObject[length];
    }
    
    private RubyArray(Ruby runtime, RubyClass klass, long length) {
        super(runtime, klass);
        checkLength(length);
        values = new IRubyObject[(int)length];
    }

    private RubyArray(Ruby runtime, RubyClass klass, IRubyObject[]vals, boolean objectspace) {
        super(runtime, klass, objectspace);
        values = vals;
    }    

    private RubyArray(Ruby runtime, RubyClass klass, boolean objectSpace) {
        super(runtime, klass, objectSpace);
    }
    
    private RubyArray(Ruby runtime, RubyClass klass, RubyArray original) {
        super(runtime, klass);
        realLength = original.realLength;
        values = new IRubyObject[realLength];
        try {
            System.arraycopy(original.values, original.begin, values, 0, realLength);
        } catch (ArrayIndexOutOfBoundsException e) {
            concurrentModification();
        }
    }
    
    private RubyArray(Ruby runtime, RubyClass klass, IRubyObject[] vals) {
        super(runtime, klass);
        values = vals;
        realLength = vals.length;
    }

    private final void alloc(int length) {
        final IRubyObject[] newValues = new IRubyObject[length];
        fillNil(newValues, getRuntime());
        values = newValues;
    }

    private final void realloc(int newLength) {
        IRubyObject[] reallocated = new IRubyObject[newLength];
        try {
            if (newLength > values.length) {
                fillNil(reallocated, values.length, newLength, getRuntime());
                System.arraycopy(values, 0, reallocated, 0, values.length); // elements and trailing nils
            } else {
                System.arraycopy(values, 0, reallocated, 0, newLength);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            concurrentModification();
        }
        values = reallocated;
    }

    private static void fill(IRubyObject[]arr, int from, int to, IRubyObject with) {
        for (int i=from; i<to; i++) {
            arr[i] = with;
        }
    }

    private static void fillNil(IRubyObject[]arr, int from, int to, Ruby runtime) {
        IRubyObject nils[] = runtime.getNilPrefilledArray();
        int i;
        
        for (i = from; i + Ruby.NIL_PREFILLED_ARRAY_SIZE < to; i += Ruby.NIL_PREFILLED_ARRAY_SIZE) {
            System.arraycopy(nils, 0, arr, i, Ruby.NIL_PREFILLED_ARRAY_SIZE);
        }
        System.arraycopy(nils, 0, arr, from, to - i);
    }

    private static void fillNil(IRubyObject[]arr, Ruby runtime) {
        fillNil(arr, 0, arr.length, runtime);
    }

    private final void checkLength(long length) {
        if (length < 0) {
            throw getRuntime().newArgumentError("negative array size (or size too big)");
        }

        if (length >= Integer.MAX_VALUE) {
            throw getRuntime().newArgumentError("array size too big");
        }
    }

    /** Getter for property list.
     * @return Value of property list.
     */
    public List getList() {
        return Arrays.asList(toJavaArray()); 
    }

    public int getLength() {
        return realLength;
    }

    public IRubyObject[] toJavaArray() {
        IRubyObject[] copy = new IRubyObject[realLength];
        try {
            System.arraycopy(values, begin, copy, 0, realLength);
        } catch (ArrayIndexOutOfBoundsException e) {
            concurrentModification();
        }
        return copy;
    }
    
    public IRubyObject[] toJavaArrayUnsafe() {
        return !isShared ? values : toJavaArray();
    }    

    public IRubyObject[] toJavaArrayMaybeUnsafe() {
        return (!isShared && begin == 0 && values.length == realLength) ? values : toJavaArray();
    }    

    /** rb_ary_make_shared
    *
    */
    private RubyArray makeShared(int beg, int len, RubyClass klass) {
        return makeShared(beg, len, klass, klass.getRuntime().isObjectSpaceEnabled());
    }
    
    /** ary_shared_first
     * 
     */
    private RubyArray makeSharedFirst(ThreadContext context, IRubyObject num, boolean last) {
        int n = RubyNumeric.num2int(num);
        
        if (n > realLength) {
            n = realLength;
        } else if (n < 0) {
            throw context.getRuntime().newArgumentError("negative array size");
        }
        
        return makeShared(last ? realLength - n : 0, n, getMetaClass());
    }
    
    
    /** rb_ary_make_shared
     *
     */
    private RubyArray makeShared(int beg, int len, RubyClass klass, boolean objectSpace) {
        RubyArray sharedArray = new RubyArray(getRuntime(), klass, objectSpace);
        isShared = true;
        sharedArray.values = values;
        sharedArray.isShared = true;
        sharedArray.begin = beg;
        sharedArray.realLength = len;
        return sharedArray;
    }

    /** rb_ary_modify_check
     *
     */
    private final void modifyCheck() {
        if ((flags & TMPLOCK_OR_FROZEN_ARR_F) != 0) {
            if ((flags & FROZEN_F) != 0) throw getRuntime().newFrozenError("array");           
            if ((flags & TMPLOCK_ARR_F) != 0) throw getRuntime().newTypeError("can't modify array during iteration");
        }
        if (!isTaint() && getRuntime().getSafeLevel() >= 4) {
            throw getRuntime().newSecurityError("Insecure: can't modify array");
        }
    }

    /** rb_ary_modify
     *
     */
    private final void modify() {
        modifyCheck();
        if (isShared) {
            IRubyObject[] vals = new IRubyObject[realLength];
            isShared = false;
            try {
                System.arraycopy(values, begin, vals, 0, realLength);
            } catch (ArrayIndexOutOfBoundsException e) {
                concurrentModification();
            }
            begin = 0;            
            values = vals;
        }
    }

    /*  ================
     *  Instance Methods
     *  ================ 
     */

    /**
     * Variable arity version for compatibility. Not bound to a Ruby method.
     * @deprecated Use the versions with zero, one, or two args.
     */
    public IRubyObject initialize(ThreadContext context, IRubyObject[] args, Block block) {
        switch (args.length) {
        case 0:
            return initialize(context, block);
        case 1:
            return initializeCommon(context, args[0], null, block);
        case 2:
            return initializeCommon(context, args[0], args[1], block);
        default:
            Arity.raiseArgumentError(getRuntime(), args.length, 0, 2);
            return null; // not reached
        }
    }    
    
    /** rb_ary_initialize
     * 
     */
    @JRubyMethod(name = "initialize", frame = true, visibility = Visibility.PRIVATE)
    public IRubyObject initialize(ThreadContext context, Block block) {
        modifyCheck();
        realLength = 0;
        if (block.isGiven()) context.getRuntime().getWarnings().warn(ID.BLOCK_UNUSED, "given block not used");
        return this;
    }

    /** rb_ary_initialize
     * 
     */
    @JRubyMethod(name = "initialize", frame = true, visibility = Visibility.PRIVATE)
    public IRubyObject initialize(ThreadContext context, IRubyObject arg0, Block block) {
        return initializeCommon(context, arg0, null, block);
    }

    /** rb_ary_initialize
     * 
     */
    @JRubyMethod(name = "initialize", frame = true, visibility = Visibility.PRIVATE)
    public IRubyObject initialize(ThreadContext context, IRubyObject arg0, IRubyObject arg1, Block block) {
        return initializeCommon(context, arg0, arg1, block);
    }

    private IRubyObject initializeCommon(ThreadContext context, IRubyObject arg0, IRubyObject arg1, Block block) {
        Ruby runtime = context.getRuntime();

        if (arg1 == null && !(arg0 instanceof RubyFixnum)) {
            IRubyObject val = arg0.checkArrayType();
            if (!val.isNil()) {
                replace(val);
                return this;
            }
        }

        long len = RubyNumeric.num2long(arg0);
        if (len < 0) throw runtime.newArgumentError("negative array size");
        if (len >= Integer.MAX_VALUE) throw runtime.newArgumentError("array size too big");
        int ilen = (int) len;

        modify();

        if (ilen > values.length) alloc(ilen);

        if (block.isGiven()) {
            if (arg1 != null) {
                runtime.getWarnings().warn(ID.BLOCK_BEATS_DEFAULT_VALUE, "block supersedes default value argument");
            }

            if (block.getBody().getArgumentType() == BlockBody.ZERO_ARGS) {
                IRubyObject nil = runtime.getNil();
                for (int i = 0; i < ilen; i++) {
                    store(i, block.yield(context, nil));
                    realLength = i + 1;
                }
            } else {
                for (int i = 0; i < ilen; i++) {
                    store(i, block.yield(context, RubyFixnum.newFixnum(runtime, i)));
                    realLength = i + 1;
                }
            }
            
        } else {
            try {
                if (arg1 == null) {
                    fillNil(values, 0, ilen, runtime);
                } else {
                    fill(values, 0, ilen, arg1);
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                concurrentModification();
            }
            realLength = ilen;
        }
        return this;
    }

    /** rb_ary_initialize_copy
     * 
     */
    @JRubyMethod(name = {"initialize_copy"}, required = 1, visibility=Visibility.PRIVATE)
    @Override
    public IRubyObject initialize_copy(IRubyObject orig) {
        return this.replace(orig);
    }
    
    /** rb_ary_replace
     *
     */
    @JRubyMethod(name = {"replace"}, required = 1)
    public IRubyObject replace(IRubyObject orig) {
        modifyCheck();

        RubyArray origArr = orig.convertToArray();

        if (this == orig) return this;

        origArr.isShared = true;
        isShared = true;
        values = origArr.values;
        realLength = origArr.realLength;
        begin = origArr.begin;


        return this;
    }

    /** rb_ary_to_s
     *
     */
    @JRubyMethod(name = "to_s")
    @Override
    public IRubyObject to_s() {
        if (realLength == 0) return RubyString.newEmptyString(getRuntime());

        return join(getRuntime().getCurrentContext(), getRuntime().getGlobalVariables().get("$,"));
    }

    
    public boolean includes(ThreadContext context, IRubyObject item) {
        int begin = this.begin;
        
        for (int i = begin; i < begin + realLength; i++) {
            final IRubyObject value;
            try {
                value = values[i];
            } catch (ArrayIndexOutOfBoundsException e) {
                concurrentModification();
                continue;
            }
            if (equalInternal(context, value, item)) return true;
    	}
        
        return false;
    }

    /** rb_ary_hash
     * 
     */
    @JRubyMethod(name = "hash")
    public RubyFixnum hash(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        if (runtime.isInspecting(this)) return  RubyFixnum.zero(runtime);

        try {
            runtime.registerInspecting(this);
            int begin = this.begin;
            int h = realLength;
            for (int i = begin; i < begin + realLength; i++) {
                h = (h << 1) | (h < 0 ? 1 : 0);
                final IRubyObject value;
                try {
                    value = values[i];
                } catch (ArrayIndexOutOfBoundsException e) {
                    concurrentModification();
                    continue;
                }
                h ^= RubyNumeric.num2long(value.callMethod(context, "hash"));
            }
            return runtime.newFixnum(h);
        } finally {
            runtime.unregisterInspecting(this);
        }
    }

    /** rb_ary_store
     *
     */
    public final IRubyObject store(long index, IRubyObject value) {
        if (index < 0) {
            index += realLength;
            if (index < 0) {
                throw getRuntime().newIndexError("index " + (index - realLength) + " out of array");
            }
        }

        modify();

        if (index >= realLength) {
            if (index >= values.length) {
                long newLength = values.length >> 1;

                if (newLength < ARRAY_DEFAULT_SIZE) newLength = ARRAY_DEFAULT_SIZE;

                newLength += index;
                if (index >= Integer.MAX_VALUE || newLength >= Integer.MAX_VALUE) {
                    throw getRuntime().newArgumentError("index too big");
                }
                realloc((int) newLength);
            }
            
            realLength = (int) index + 1;
        }

        try {
            values[(int) index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            concurrentModification();
        }
        return value;
    }

    /** rb_ary_elt
     *
     */
    private final IRubyObject elt(long offset) {
        if (offset < 0 || offset >= realLength) {
            return getRuntime().getNil();
        }
        return eltOk(offset);
    }
    private final IRubyObject eltOk(long offset) {
        try {
            return values[begin + (int)offset];
        } catch (ArrayIndexOutOfBoundsException e) {
            concurrentModification();
            return getRuntime().getNil();
        }
    }

    /** rb_ary_entry
     *
     */
    public final IRubyObject entry(long offset) {
        return (offset < 0 ) ? elt(offset + realLength) : elt(offset);
    }


    /** rb_ary_entry
     *
     */
    public final IRubyObject entry(int offset) {
        return (offset < 0 ) ? elt(offset + realLength) : elt(offset);
    }

    public final IRubyObject eltInternal(int offset) {
        return values[begin + offset];
    }
    
    public final IRubyObject eltInternalSet(int offset, IRubyObject item) {
        return values[begin + offset] = item;
    }

    /**
     * Variable arity version for compatibility. Not bound to a Ruby method.
     * @deprecated Use the versions with zero, one, or two args.
     */
    public IRubyObject fetch(ThreadContext context, IRubyObject[] args, Block block) {
        switch (args.length) {
        case 1:
            return fetch(context, args[0], block);
        case 2:
            return fetch(context, args[0], args[1], block);
        default:
            Arity.raiseArgumentError(getRuntime(), args.length, 1, 2);
            return null; // not reached
        }
    }    

    /** rb_ary_fetch
     *
     */
    @JRubyMethod(name = "fetch", frame = true)
    public IRubyObject fetch(ThreadContext context, IRubyObject arg0, Block block) {
        long index = RubyNumeric.num2long(arg0);

        if (index < 0) index += realLength;
        if (index < 0 || index >= realLength) {
            if (block.isGiven()) return block.yield(context, arg0);
            throw getRuntime().newIndexError("index " + index + " out of array");
        }
        
        try {
            return values[begin + (int) index];
        } catch (ArrayIndexOutOfBoundsException e) {
            concurrentModification();
            return getRuntime().getNil();
        }
    }

    /** rb_ary_fetch
    *
    */
   @JRubyMethod(name = "fetch", frame = true)
   public IRubyObject fetch(ThreadContext context, IRubyObject arg0, IRubyObject arg1, Block block) {
       if (block.isGiven()) getRuntime().getWarnings().warn(ID.BLOCK_BEATS_DEFAULT_VALUE, "block supersedes default value argument");

       long index = RubyNumeric.num2long(arg0);

       if (index < 0) index += realLength;
       if (index < 0 || index >= realLength) {
           if (block.isGiven()) return block.yield(context, arg0);
           return arg1;
       }
       
       try {
           return values[begin + (int) index];
       } catch (ArrayIndexOutOfBoundsException e) {
           concurrentModification();
           return getRuntime().getNil();
       }
   }    

    /** rb_ary_to_ary
     * 
     */
    private static RubyArray aryToAry(IRubyObject obj) {
        if (obj instanceof RubyArray) return (RubyArray) obj;

        if (obj.respondsTo("to_ary")) return obj.convertToArray();

        RubyArray arr = new RubyArray(obj.getRuntime(), false); // possibly should not in object space
        arr.values = new IRubyObject[]{obj};
        arr.realLength = 1;
        return arr;
    }

    /** rb_ary_splice
     * 
     */
    private final void splice(long beg, long len, IRubyObject rpl) {
        if (len < 0) throw getRuntime().newIndexError("negative length (" + len + ")");

        if (beg < 0) {
            beg += realLength;
            if (beg < 0) {
                beg -= realLength;
                throw getRuntime().newIndexError("index " + beg + " out of array");
            }
        }

        final RubyArray rplArr;
        final int rlen;

        if (rpl == null || rpl.isNil()) {
            rplArr = null;
            rlen = 0;
        } else {
            rplArr = aryToAry(rpl);
            rlen = rplArr.realLength;
        }

        modify();

        if (beg >= realLength) {
            len = beg + rlen;

            if (len >= values.length) spliceRealloc((int)len);

            fillNil(values, begin + realLength, begin + ((int)beg), getRuntime());
            realLength = (int) len;
        } else {
            if (beg + len > realLength) len = realLength - beg;
            int alen = realLength + rlen - (int)len;

            if (alen >= values.length) spliceRealloc(alen);

            if (len != rlen) {
                try {
                    System.arraycopy(values, (int) (beg + len), values, (int) beg + rlen, realLength - (int) (beg + len));
                } catch (ArrayIndexOutOfBoundsException e) {
                    concurrentModification();
                }
                realLength = alen;
            }
        }

        if (rlen > 0) {
            try {
                System.arraycopy(rplArr.values, rplArr.begin, values, (int) beg, rlen);
            } catch (ArrayIndexOutOfBoundsException e) {
                concurrentModification();
            }
        }
    }

    /** rb_ary_splice
     * 
     */
    private final void spliceOne(long beg, long len, IRubyObject rpl) {
        if (len < 0) throw getRuntime().newIndexError("negative length (" + len + ")");

        if (beg < 0) {
            beg += realLength;
            if (beg < 0) {
                beg -= realLength;
                throw getRuntime().newIndexError("index " + beg + " out of array");
            }
        }

        modify();

        if (beg >= realLength) {
            len = beg + 1;

            if (len >= values.length) spliceRealloc((int)len);

            fillNil(values, begin + realLength, begin + ((int)beg), getRuntime());
            realLength = (int) len;
        } else {
            if (beg + len > realLength) len = realLength - beg;
            int alen = realLength + 1 - (int)len;

            if (alen >= values.length) spliceRealloc((int)alen);

            if (len != 1) {
                try {
                    System.arraycopy(values, (int) (beg + len), values, (int) beg + 1, realLength - (int) (beg + len));
                } catch (ArrayIndexOutOfBoundsException e) {
                    concurrentModification();
                }
                realLength = alen;
            }
        }

        try {
            values[(int)beg] = rpl;
        } catch (ArrayIndexOutOfBoundsException e) {
            concurrentModification();
        }
    }

    private void spliceRealloc(int length) {
        int tryLength = values.length + (values.length >> 1);
        int len = length > tryLength ? length : tryLength;
        IRubyObject[] vals = new IRubyObject[len];
        System.arraycopy(values, begin, vals, 0, realLength);

        // only fill if there actually will remain trailing storage
        if (tryLength > length) fillNil(vals, length, tryLength, getRuntime());

        values = vals;
    }

    @JRubyMethod
    public IRubyObject insert() {
        throw getRuntime().newArgumentError(0, 1);
    }

    /** rb_ary_insert
     * 
     */
    @JRubyMethod
    public IRubyObject insert(IRubyObject arg) {
        return this;
    }

    /** rb_ary_insert
     * 
     */
    @JRubyMethod
    public IRubyObject insert(IRubyObject arg1, IRubyObject arg2) {
        long pos = RubyNumeric.num2long(arg1);

        if (pos == -1) pos = realLength;
        if (pos < 0) pos++;
        
        spliceOne(pos, 0, arg2); // rb_ary_new4
        
        return this;
    }

    /** rb_ary_insert
     * 
     */
    @JRubyMethod(name = "insert", required = 1, rest = true)
    public IRubyObject insert(IRubyObject[] args) {
        if (args.length == 1) return this;

        long pos = RubyNumeric.num2long(args[0]);

        if (pos == -1) pos = realLength;
        if (pos < 0) pos++;

        RubyArray inserted = new RubyArray(getRuntime(), false);
        inserted.values = args;
        inserted.begin = 1;
        inserted.realLength = args.length - 1;
        
        splice(pos, 0, inserted); // rb_ary_new4
        
        return this;
    }

    /** rb_ary_dup
     * 
     */
    public final RubyArray aryDup() {
        RubyArray dup = new RubyArray(getRuntime(), getMetaClass(), this);
        dup.flags |= flags & TAINTED_F; // from DUP_SETUP
        // rb_copy_generic_ivar from DUP_SETUP here ...unlikely..
        return dup;
    }

    /** rb_ary_transpose
     * 
     */
    @JRubyMethod(name = "transpose")
    public RubyArray transpose() {
        RubyArray tmp, result = null;

        int alen = realLength;
        if (alen == 0) return aryDup();
    
        Ruby runtime = getRuntime();
        int elen = -1;
        int end = begin + alen;
        for (int i = begin; i < end; i++) {
            tmp = elt(i).convertToArray();
            if (elen < 0) {
                elen = tmp.realLength;
                result = new RubyArray(runtime, elen);
                for (int j = 0; j < elen; j++) {
                    result.store(j, new RubyArray(runtime, alen));
                }
            } else if (elen != tmp.realLength) {
                throw runtime.newIndexError("element size differs (" + tmp.realLength
                        + " should be " + elen + ")");
            }
            for (int j = 0; j < elen; j++) {
                ((RubyArray) result.elt(j)).store(i - begin, tmp.elt(j));
            }
        }
        return result;
    }

    /** rb_values_at (internal)
     * 
     */
    private final IRubyObject values_at(long olen, IRubyObject[] args) {
        RubyArray result = new RubyArray(getRuntime(), args.length);

        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof RubyFixnum) {
                result.append(entry(((RubyFixnum)args[i]).getLongValue()));
                continue;
            }

            long beglen[];
            if (!(args[i] instanceof RubyRange)) {
            } else if ((beglen = ((RubyRange) args[i]).begLen(olen, 0)) == null) {
                continue;
            } else {
                int beg = (int) beglen[0];
                int len = (int) beglen[1];
                int end = begin + len;
                for (int j = begin; j < end; j++) {
                    result.append(entry(j + beg));
                }
                continue;
            }
            result.append(entry(RubyNumeric.num2long(args[i])));
        }

        fillNil(result.values, result.realLength, result.values.length, getRuntime());
        return result;
    }

    /** rb_values_at
     * 
     */
    @JRubyMethod(name = "values_at", rest = true)
    public IRubyObject values_at(IRubyObject[] args) {
        return values_at(realLength, args);
    }

    /** rb_ary_subseq
     *
     */
    public IRubyObject subseq(long beg, long len) {
        if (beg > realLength || beg < 0 || len < 0) return getRuntime().getNil();

        if (beg + len > realLength) {
            len = realLength - beg;
            
            if (len < 0) len = 0;
        }
        
        if (len == 0) return new RubyArray(getRuntime(), getMetaClass(), IRubyObject.NULL_ARRAY);

        return makeShared(begin + (int) beg, (int) len, getMetaClass());
    }

    /** rb_ary_subseq
     *
     */
    public IRubyObject subseqLight(long beg, long len) {
        if (beg > realLength || beg < 0 || len < 0) return getRuntime().getNil();

        if (beg + len > realLength) {
            len = realLength - beg;
            
            if (len < 0) len = 0;
        }
        
        if (len == 0) return new RubyArray(getRuntime(), getMetaClass(), IRubyObject.NULL_ARRAY, false);

        return makeShared(begin + (int) beg, (int) len, getMetaClass(), false);
    }

    /** rb_ary_length
     *
     */
    @JRubyMethod(name = "length", alias = "size")
    public RubyFixnum length() {
        return getRuntime().newFixnum(realLength);
    }

    /** rb_ary_push - specialized rb_ary_store 
     *
     */
    @JRubyMethod(name = "<<", required = 1)
    public RubyArray append(IRubyObject item) {
        modify();
        
        if (realLength == values.length) {
            if (realLength == Integer.MAX_VALUE) throw getRuntime().newArgumentError("index too big");

            long newLength = values.length + (values.length >> 1);
            if (newLength > Integer.MAX_VALUE) {
                newLength = Integer.MAX_VALUE;
            } else if (newLength < ARRAY_DEFAULT_SIZE) {
                newLength = ARRAY_DEFAULT_SIZE;
            }

            realloc((int) newLength);
        }
        
        try {
            values[realLength++] = item;
        } catch (ArrayIndexOutOfBoundsException e) {
            concurrentModification();
        }
        return this;
    }

    /** rb_ary_push_m
     * FIXME: Whis is this named "push_m"?
     */
    @JRubyMethod(name = "push", rest = true)
    public RubyArray push_m(IRubyObject[] items) {
        for (int i = 0; i < items.length; i++) {
            append(items[i]);
        }
        
        return this;
    }

    /** rb_ary_pop
     *
     */
    @JRubyMethod(name = "pop", compat = CompatVersion.RUBY1_8)
    public IRubyObject pop(ThreadContext context) {
        modifyCheck();

        if (realLength == 0) return context.getRuntime().getNil();

        try {
            if (isShared) {
                return values[begin + --realLength];
            } else {
                int index = begin + --realLength;
                final IRubyObject obj = values[index];
                values[index] = context.getRuntime().getNil();
                return obj;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            concurrentModification();
            return context.getRuntime().getNil();
        }
    }

    @JRubyMethod(name = "pop", compat = CompatVersion.RUBY1_9)
    public IRubyObject pop19(ThreadContext context) {
        return pop(context);
    }

    @JRubyMethod(name = "pop", compat = CompatVersion.RUBY1_9)
    public IRubyObject pop19(ThreadContext context, IRubyObject num) {
        modifyCheck();
        RubyArray result = makeSharedFirst(context, num, true);
        realLength -= result.realLength;
        return result;
    }
    
    /** rb_ary_shift
     *
     */
    @JRubyMethod(name = "shift", compat = CompatVersion.RUBY1_8)
    public IRubyObject shift(ThreadContext context) {
        modify();
        Ruby runtime = context.getRuntime();

        if (realLength == 0) return runtime.getNil();

        final IRubyObject obj;
        try {
            realLength--;
            obj = values[0];
            if (realLength <= ARRAY_DEFAULT_SIZE && values.length > ARRAY_DEFAULT_SIZE) {
                IRubyObject[]tmp = new IRubyObject[ARRAY_DEFAULT_SIZE];
                System.arraycopy(values, 1, tmp, 0, realLength);
                fillNil(tmp, realLength, ARRAY_DEFAULT_SIZE, runtime);
                values = tmp;
            } else {
                System.arraycopy(values, 1, values, 0, realLength);
                values[realLength] = runtime.getNil();
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            concurrentModification();
            return runtime.getNil();
        }
        return obj;
        
    }    

    @JRubyMethod(name = "shift", compat = CompatVersion.RUBY1_9)
    public IRubyObject shift19(ThreadContext context) {
        return shift(context);
    }

    @JRubyMethod(name = "shift", compat = CompatVersion.RUBY1_9)
    public IRubyObject shift19(ThreadContext context, IRubyObject num) {
        modify();

        RubyArray result = makeSharedFirst(context, num, false);

        int n = result.realLength;
        begin += n;
        realLength -= n;
        return result;
    }

    /** rb_ary_unshift
     *
     */
    public RubyArray unshift(IRubyObject item) {
        modify();

        if (realLength == values.length) {
            int newLength = values.length >> 1;
            if (newLength < ARRAY_DEFAULT_SIZE) newLength = ARRAY_DEFAULT_SIZE;

            newLength += values.length;
            realloc(newLength);
        }
        try {
            System.arraycopy(values, 0, values, 1, realLength);
        } catch (ArrayIndexOutOfBoundsException e) {
            concurrentModification();
        }

        realLength++;
        values[0] = item;

        return this;
    }

    /** rb_ary_unshift_m
     *
     */
    @JRubyMethod(name = "unshift", rest = true)
    public RubyArray unshift_m(IRubyObject[] items) {
        long len = realLength;

        if (items.length == 0) return this;

        store(len + items.length - 1, getRuntime().getNil());

        try {
            // it's safe to use zeroes here since modified by store()
            System.arraycopy(values, 0, values, items.length, (int) len);
            System.arraycopy(items, 0, values, 0, items.length);
        } catch (ArrayIndexOutOfBoundsException e) {
            concurrentModification();
        }
        
        return this;
    }

    /** rb_ary_includes
     * 
     */
    @JRubyMethod(name = "include?", required = 1)
    public RubyBoolean include_p(ThreadContext context, IRubyObject item) {
        return context.getRuntime().newBoolean(includes(context, item));
    }

    /** rb_ary_frozen_p
     *
     */
    @JRubyMethod(name = "frozen?")
    @Override
    public RubyBoolean frozen_p(ThreadContext context) {
        return context.getRuntime().newBoolean(isFrozen() || (flags & TMPLOCK_ARR_F) != 0);
    }

    /**
     * Variable arity version for compatibility. Not bound to a Ruby method.
     * @deprecated Use the versions with zero, one, or two args.
     */
    public IRubyObject aref(IRubyObject[] args) {
        switch (args.length) {
        case 1:
            return aref(args[0]);
        case 2:
            return aref(args[0], args[1]);
        default:
            Arity.raiseArgumentError(getRuntime(), args.length, 1, 2);
            return null; // not reached
        }
    }

    /** rb_ary_aref
     */
    @JRubyMethod(name = {"[]", "slice"})
    public IRubyObject aref(IRubyObject arg0) {
        if (arg0 instanceof RubyFixnum) return entry(((RubyFixnum)arg0).getLongValue());
        if (arg0 instanceof RubySymbol) throw getRuntime().newTypeError("Symbol as array index");
            
        long[] beglen;
        if (!(arg0 instanceof RubyRange)) {
        } else if ((beglen = ((RubyRange) arg0).begLen(realLength, 0)) == null) {
            return getRuntime().getNil();
        } else {
            return subseq(beglen[0], beglen[1]);
        }
        return entry(RubyNumeric.num2long(arg0));            
    }        

    /** rb_ary_aref
     */
    @JRubyMethod(name = {"[]", "slice"})
    public IRubyObject aref(IRubyObject arg0, IRubyObject arg1) {
        if (arg0 instanceof RubySymbol) throw getRuntime().newTypeError("Symbol as array index");

        long beg = RubyNumeric.num2long(arg0);
        if (beg < 0) beg += realLength;

        return subseq(beg, RubyNumeric.num2long(arg1));
    }

    /**
     * Variable arity version for compatibility. Not bound to a Ruby method.
     * @deprecated Use the versions with zero, one, or two args.
     */
    public IRubyObject aset(IRubyObject[] args) {
        switch (args.length) {
        case 2:
            return aset(args[0], args[1]);
        case 3:
            return aset(args[0], args[1], args[2]);
        default:
            throw getRuntime().newArgumentError("wrong number of arguments (" + args.length + " for 2)");
        }
    }

    /** rb_ary_aset
     *
     */
    @JRubyMethod(name = "[]=")
    public IRubyObject aset(IRubyObject arg0, IRubyObject arg1) {
        if (arg0 instanceof RubyFixnum) {
            store(((RubyFixnum)arg0).getLongValue(), arg1);
            return arg1;
        }
        if (arg0 instanceof RubyRange) {
            long[] beglen = ((RubyRange) arg0).begLen(realLength, 1);
            splice(beglen[0], beglen[1], arg1);
            return arg1;
        }
        if (arg0 instanceof RubySymbol) throw getRuntime().newTypeError("Symbol as array index");

        store(RubyNumeric.num2long(arg0), arg1);
        return arg1;
    }

    /** rb_ary_aset
    *
    */
    @JRubyMethod(name = "[]=")
    public IRubyObject aset(IRubyObject arg0, IRubyObject arg1, IRubyObject arg2) {
        if (arg0 instanceof RubySymbol) throw getRuntime().newTypeError("Symbol as array index");
        if (arg1 instanceof RubySymbol) throw getRuntime().newTypeError("Symbol as subarray length");
        splice(RubyNumeric.num2long(arg0), RubyNumeric.num2long(arg1), arg2);
        return arg2;
    }

    /** rb_ary_at
     *
     */
    @JRubyMethod(name = "at", required = 1)
    public IRubyObject at(IRubyObject pos) {
        return entry(RubyNumeric.num2long(pos));
    }

	/** rb_ary_concat
     *
     */
    @JRubyMethod(name = "concat", required = 1)
    public RubyArray concat(IRubyObject obj) {
        RubyArray ary = obj.convertToArray();
        
        if (ary.realLength > 0) splice(realLength, 0, ary);

        return this;
    }

    /** inspect_ary
     * 
     */
    private IRubyObject inspectAry(ThreadContext context) {
        ByteList buffer = new ByteList();
        buffer.append('[');
        boolean tainted = isTaint();

        for (int i = 0; i < realLength; i++) {
            if (i > 0) buffer.append(',').append(' ');

            RubyString str = inspect(context, values[begin + i]);
            if (str.isTaint()) tainted = true;
            buffer.append(str.getByteList());
        }
        buffer.append(']');

        RubyString str = getRuntime().newString(buffer);
        if (tainted) str.setTaint(true);

        return str;
    }

    /** rb_ary_inspect
    *
    */
    @JRubyMethod(name = "inspect")
    @Override
    public IRubyObject inspect() {
        if (realLength == 0) return getRuntime().newString("[]");
        if (getRuntime().isInspecting(this)) return  getRuntime().newString("[...]");

        try {
            getRuntime().registerInspecting(this);
            return inspectAry(getRuntime().getCurrentContext());
        } finally {
            getRuntime().unregisterInspecting(this);
        }
    }

    /**
     * Variable arity version for compatibility. Not bound to a Ruby method.
     * @deprecated Use the versions with zero, one, or two args.
     */
    public IRubyObject first(IRubyObject[] args) {
        switch (args.length) {
        case 0:
            return first();
        case 1:
            return first(args[0]);
        default:
            Arity.raiseArgumentError(getRuntime(), args.length, 0, 1);
            return null; // not reached
        }
    }

    /** rb_ary_first
     *
     */
    @JRubyMethod(name = "first")
    public IRubyObject first() {
        if (realLength == 0) return getRuntime().getNil();
        return values[begin];
    }

    /** rb_ary_first
    *
    */
    @JRubyMethod(name = "first")
    public IRubyObject first(IRubyObject arg0) {
        long n = RubyNumeric.num2long(arg0);
        if (n > realLength) {
            n = realLength;
        } else if (n < 0) {
            throw getRuntime().newArgumentError("negative array size (or size too big)");
        }

        return makeShared(begin, (int) n, getRuntime().getArray());
    }

    /**
     * Variable arity version for compatibility. Not bound to a Ruby method.
     * @deprecated Use the versions with zero, one, or two args.
     */
    public IRubyObject last(IRubyObject[] args) {
        switch (args.length) {
        case 0:
            return last();
        case 1:
            return last(args[0]);
        default:
            Arity.raiseArgumentError(getRuntime(), args.length, 0, 1);
            return null; // not reached
        }
    }

    /** rb_ary_last
     *
     */
    @JRubyMethod(name = "last")
    public IRubyObject last() {
        if (realLength == 0) return getRuntime().getNil();
        return values[begin + realLength - 1];
    }

    /** rb_ary_last
    *
    */
    @JRubyMethod(name = "last")
    public IRubyObject last(IRubyObject arg0) {
        long n = RubyNumeric.num2long(arg0);
        if (n > realLength) {
            n = realLength;
        } else if (n < 0) {
            throw getRuntime().newArgumentError("negative array size (or size too big)");
        }

        return makeShared(begin + realLength - (int) n, (int) n, getRuntime().getArray());
    }

    /** rb_ary_each
     *
     */
    @JRubyMethod(name = "each", frame = true, compat = CompatVersion.RUBY1_8)
    public IRubyObject each(ThreadContext context, Block block) {
        if (!block.isGiven()) {
            throw context.getRuntime().newLocalJumpErrorNoBlock();
        }
        for (int i = 0; i < realLength; i++) {
            block.yield(context, values[begin + i]);
        }
        return this;
    }

    @JRubyMethod(name = "each", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject each19(ThreadContext context, Block block) {
        return block.isGiven() ? each(context, block) : enumeratorize(context.getRuntime(), this, "each");
    }

    /** rb_ary_each_index
     *
     */
    @JRubyMethod(name = "each_index", frame = true)
    public IRubyObject each_index(ThreadContext context, Block block) {
        Ruby runtime = getRuntime();
        if (!block.isGiven()) {
            throw runtime.newLocalJumpErrorNoBlock();
        }
        for (int i = 0; i < realLength; i++) {
            block.yield(context, runtime.newFixnum(i));
        }
        return this;
    }
    
    @JRubyMethod(name = "each_index", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject each_index19(ThreadContext context, Block block) {
        return block.isGiven() ? each_index(context, block) : enumeratorize(context.getRuntime(), this, "each_index");
    }

    /** rb_ary_reverse_each
     *
     */
    @JRubyMethod(name = "reverse_each", frame = true)
    public IRubyObject reverse_each(ThreadContext context, Block block) {
        int len = realLength;
        
        while(len-- > 0) {
            block.yield(context, values[begin + len]);
            
            if (realLength < len) len = realLength;
        }
        
        return this;
    }

    private IRubyObject inspectJoin(ThreadContext context, RubyArray tmp, IRubyObject sep) {
        Ruby runtime = getRuntime();

        // If already inspecting, there is no need to register/unregister again.
        if (runtime.isInspecting(this)) {
            return tmp.join(context, sep);
        }

        try {
            runtime.registerInspecting(this);
            return tmp.join(context, sep);
        } finally {
            runtime.unregisterInspecting(this);
        }
    }

    /** rb_ary_join
     *
     */
    public RubyString join(ThreadContext context, IRubyObject sep) {
        final Ruby runtime = getRuntime();

        if (realLength == 0) return RubyString.newEmptyString(getRuntime());

        boolean taint = isTaint() || sep.isTaint();

        long len = 1;
        for (int i = begin; i < begin + realLength; i++) {            
            IRubyObject value;
            try {
                value = values[i];
            } catch (ArrayIndexOutOfBoundsException e) {
                concurrentModification();
                return runtime.newString("");
            }
            IRubyObject tmp = value.checkStringType();
            len += tmp.isNil() ? 10 : ((RubyString) tmp).getByteList().length();
        }

        RubyString strSep = null;
        if (!sep.isNil()) {
            sep = strSep = sep.convertToString();
            len += strSep.getByteList().length() * (realLength - 1);
        }

        ByteList buf = new ByteList((int)len);
        for (int i = begin; i < begin + realLength; i++) {
            IRubyObject tmp;
            try {
                tmp = values[i];
            } catch (ArrayIndexOutOfBoundsException e) {
                concurrentModification();
                return runtime.newString("");
            }
            if (tmp instanceof RubyString) {
                // do nothing
            } else if (tmp instanceof RubyArray) {
                if (runtime.isInspecting(tmp)) {
                    tmp = runtime.newString("[...]");
                } else {
                    tmp = inspectJoin(context, (RubyArray)tmp, sep);
                }
            } else {
                tmp = RubyString.objAsString(context, tmp);
            }

            if (i > begin && !sep.isNil()) buf.append(strSep.getByteList());

            buf.append(tmp.asString().getByteList());
            if (tmp.isTaint()) taint = true;
        }

        RubyString result = runtime.newString(buf); 

        if (taint) result.setTaint(true);

        return result;
    }

    /** rb_ary_join_m
     *
     */
    @JRubyMethod(name = "join", optional = 1)
    public RubyString join_m(ThreadContext context, IRubyObject[] args) {
        int argc = args.length;
        IRubyObject sep = (argc == 1) ? args[0] : getRuntime().getGlobalVariables().get("$,");
        
        return join(context, sep);
    }

    /** rb_ary_to_a
     *
     */
    @JRubyMethod(name = "to_a")
    @Override
    public RubyArray to_a() {
        if(getMetaClass() != getRuntime().getArray()) {
            RubyArray dup = new RubyArray(getRuntime(), getRuntime().isObjectSpaceEnabled());

            isShared = true;
            dup.isShared = true;
            dup.values = values;
            dup.realLength = realLength; 
            dup.begin = begin;
            
            return dup;
        }        
        return this;
    }

    @JRubyMethod(name = "to_ary")
    public IRubyObject to_ary() {
    	return this;
    }

    @Override
    public RubyArray convertToArray() {
        return this;
    }
    
    @Override
    public IRubyObject checkArrayType(){
        return this;
    }

    /** rb_ary_equal
     *
     */
    @JRubyMethod(name = "==", required = 1)
    @Override
    public IRubyObject op_equal(ThreadContext context, IRubyObject obj) {
        Ruby runtime = context.getRuntime();
        if (this == obj) return runtime.getTrue();

        if (!(obj instanceof RubyArray)) {
            if (!obj.respondsTo("to_ary")) {
                return runtime.getFalse();
            } else {
                if (equalInternal(context, obj.callMethod(context, "to_ary"), this)) return runtime.getTrue();
                return runtime.getFalse();                
            }
        }

        RubyArray ary = (RubyArray) obj;
        if (realLength != ary.realLength || runtime.isInspecting(this)) return getRuntime().getFalse();

        try {
            runtime.registerInspecting(this);
            for (long i = 0; i < realLength; i++) {
                if (!equalInternal(context, elt(i), ary.elt(i))) return runtime.getFalse();            
            }
        } finally {
            runtime.unregisterInspecting(this);
        }
        return runtime.getTrue();
    }

    /** rb_ary_eql
     *
     */
    @JRubyMethod(name = "eql?", required = 1)
    public RubyBoolean eql_p(ThreadContext context, IRubyObject obj) {
        Ruby runtime = context.getRuntime();
        if (this == obj) return runtime.getTrue();
        if (!(obj instanceof RubyArray)) return runtime.getFalse();

        RubyArray ary = (RubyArray) obj;

        if (realLength != ary.realLength || runtime.isInspecting(this)) return runtime.getFalse();

        try {
            runtime.registerInspecting(this);
            for (int i = 0; i < realLength; i++) {
                if (!eqlInternal(context, elt(i), ary.elt(i))) return runtime.getFalse();
            }
        } finally {
            runtime.unregisterInspecting(this);
        }

        return runtime.getTrue();
    }

    /** rb_ary_compact_bang
     *
     */
    @JRubyMethod(name = "compact!")
    public IRubyObject compact_bang() {
        modify();

        int p = 0;
        int t = 0;
        int end = p + realLength;

        while (t < end) {
            if (values[t].isNil()) {
                t++;
            } else {
                values[p++] = values[t++];
            }
        }

        if (realLength == p) return getRuntime().getNil();

        realloc(p);
        realLength = p;
        return this;
    }

    /** rb_ary_compact
     *
     */
    @JRubyMethod(name = "compact")
    public IRubyObject compact() {
        RubyArray ary = aryDup();
        ary.compact_bang();
        return ary;
    }

    /** rb_ary_empty_p
     *
     */
    @JRubyMethod(name = "empty?")
    public IRubyObject empty_p() {
        return realLength == 0 ? getRuntime().getTrue() : getRuntime().getFalse();
    }

    /** rb_ary_clear
     *
     */
    @JRubyMethod(name = "clear")
    public IRubyObject rb_clear() {
        modifyCheck();

        if (isShared) {
            alloc(ARRAY_DEFAULT_SIZE);
            isShared = false;
        } else if (values.length > ARRAY_DEFAULT_SIZE << 1) {
            alloc(ARRAY_DEFAULT_SIZE << 1);
        } else {
            final int begin = this.begin;
            try {
                fillNil(values, begin, begin + realLength, getRuntime());
            } catch (ArrayIndexOutOfBoundsException e) {
                concurrentModification();
            }
        }

        begin = 0;
        realLength = 0;
        return this;
    }

    /** rb_ary_fill
     *
     */
    @JRubyMethod(name = "fill", optional = 3, frame = true)
    public IRubyObject fill(ThreadContext context, IRubyObject[] args, Block block) {
        IRubyObject item = null;
        IRubyObject begObj = null;
        IRubyObject lenObj = null;
        int argc = args.length;

        if (block.isGiven()) {
            Arity.checkArgumentCount(getRuntime(), args, 0, 2);
            item = null;
        	begObj = argc > 0 ? args[0] : null;
        	lenObj = argc > 1 ? args[1] : null;
        	argc++;
        } else {
            Arity.checkArgumentCount(getRuntime(), args, 1, 3);
            item = args[0];
        	begObj = argc > 1 ? args[1] : null;
        	lenObj = argc > 2 ? args[2] : null;
        }

        int beg = 0, end = 0, len = 0;
        switch (argc) {
        case 1:
            beg = 0;
            len = realLength;
            break;
        case 2:
            if (begObj instanceof RubyRange) {
                long[] beglen = ((RubyRange) begObj).begLen(realLength, 1);
                beg = (int) beglen[0];
                len = (int) beglen[1];
                break;
            }
            /* fall through */
        case 3:
            beg = begObj.isNil() ? 0 : RubyNumeric.num2int(begObj);
            if (beg < 0) {
                beg = realLength + beg;
                if (beg < 0) beg = 0;
            }
            len = (lenObj == null || lenObj.isNil()) ? realLength - beg : RubyNumeric.num2int(lenObj);
            // TODO: In MRI 1.9, an explicit check for negative length is
            // added here. IndexError is raised when length is negative.
            // See [ruby-core:12953] for more details.
            //
            // New note: This is actually under re-evaluation,
            // see [ruby-core:17483].
            break;
        }

        modify();

        // See [ruby-core:17483]
        if (len < 0) {
            return this;
        }

        if (len > Integer.MAX_VALUE - beg) {
            throw getRuntime().newArgumentError("argument too big");
        }

        end = beg + len;
        if (end > realLength) {
            if (end >= values.length) realloc(end);

            realLength = end;
        }

        if (block.isGiven()) {
            Ruby runtime = getRuntime();
            for (int i = beg; i < end; i++) {
                IRubyObject v = block.yield(context, runtime.newFixnum(i));
                if (i >= realLength) break;
                try {
                    values[i] = v;
                } catch (ArrayIndexOutOfBoundsException e) {
                    concurrentModification();
                }
            }
        } else {
            if (len > 0) {
                try {
                    fill(values, beg, beg + len, item);
                } catch (ArrayIndexOutOfBoundsException e) {
                    concurrentModification();
                }
            }
        }

        return this;
    }

    /** rb_ary_index
     *
     */
    @JRubyMethod(name = "index", compat = CompatVersion.RUBY1_8)
    public IRubyObject index(ThreadContext context, IRubyObject obj) {
        Ruby runtime = getRuntime();
        for (int i = begin; i < begin + realLength; i++) {
            if (equalInternal(context, values[i], obj)) return runtime.newFixnum(i - begin);            
        }

        return runtime.getNil();
    }

    @JRubyMethod(name = "index", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject index19(ThreadContext context, IRubyObject obj, Block unused) {
        return index(context, obj); 
    }

    @JRubyMethod(name = "index", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject index19(ThreadContext context, Block block) {
        Ruby runtime = getRuntime();
        if (!block.isGiven()) return enumeratorize(runtime, this, "index");
        
        for (int i = begin; i < begin + realLength; i++) {
            if (block.yield(context, values[i]).isTrue()) return runtime.newFixnum(i - begin);
        }

        return runtime.getNil();
    }

    /** rb_ary_rindex
     *
     */
    @JRubyMethod(name = "rindex", required = 1, compat = CompatVersion.RUBY1_8)
    public IRubyObject rindex(ThreadContext context, IRubyObject obj) {
        Ruby runtime = getRuntime();
        int i = realLength;

        while (i-- > 0) {
            if (i > realLength) {
                i = realLength;
                continue;
            }
            if (equalInternal(context, values[begin + i], obj)) return runtime.newFixnum(i);
        }

        return runtime.getNil();
    }

    @JRubyMethod(name = "rindex", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject rindex19(ThreadContext context, IRubyObject obj, Block unused) {
        return rindex(context, obj); 
    }

    @JRubyMethod(name = "rindex", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject rindex19(ThreadContext context, Block block) {
        Ruby runtime = getRuntime();
        if (!block.isGiven()) return enumeratorize(runtime, this, "rindex");

        int i = realLength;

        while (i-- > 0) {
            if (i > realLength) {
                i = realLength;
                continue;
            }
            if (block.yield(context, values[begin + i]).isTrue()) return runtime.newFixnum(i);
        }

        return runtime.getNil();
    }

    /** rb_ary_indexes
     * 
     */
    @JRubyMethod(name = {"indexes", "indices"}, required = 1, rest = true)
    public IRubyObject indexes(IRubyObject[] args) {
        getRuntime().getWarnings().warn(ID.DEPRECATED_METHOD, "Array#indexes is deprecated; use Array#values_at", "Array#indexes", "Array#values_at");

        RubyArray ary = new RubyArray(getRuntime(), args.length);

        for (int i = 0; i < args.length; i++) {
            ary.append(aref(args[i]));
        }

        return ary;
    }

    /** rb_ary_reverse_bang
     *
     */
    @JRubyMethod(name = "reverse!")
    public IRubyObject reverse_bang() {
        modify();

        final int realLength = this.realLength;
        final IRubyObject[] values = this.values;
        try {
            if (realLength > 1) {
                int p1 = 0;
                int p2 = p1 + realLength - 1;

                while (p1 < p2) {
                    final IRubyObject tmp = values[p1];
                    values[p1++] = values[p2];
                    values[p2--] = tmp;
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            concurrentModification();
        }
        return this;
    }

    /** rb_ary_reverse_m
     *
     */
    @JRubyMethod(name = "reverse")
    public IRubyObject reverse() {
        modify();
        RubyArray dup = new RubyArray(getRuntime(), getMetaClass(), safeReverse(values, realLength));
        dup.flags |= flags & TAINTED_F; // from DUP_SETUP
        // rb_copy_generic_ivar from DUP_SETUP here ...unlikely..
        return dup;
    }

    private IRubyObject[] safeReverse(final IRubyObject[] values, final int length) {
        final IRubyObject[] newValues = new IRubyObject[length];
        try {
            int p1 = 0;
            int p2 = length - 1;    

            while (p1 <= p2) {
                newValues[p1] = values[p2];
                newValues[p2--] = values[p1++];
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            concurrentModification();
        }
        return newValues;
    }

    /** rb_ary_collect
     *
     */
    @JRubyMethod(name = {"collect", "map"}, frame = true)
    public RubyArray collect(ThreadContext context, Block block) {
        Ruby runtime = getRuntime();

        if (!block.isGiven()) return new RubyArray(getRuntime(), runtime.getArray(), this);

        RubyArray collect = new RubyArray(runtime, realLength);

        for (int i = begin; i < begin + realLength; i++) {
            collect.append(block.yield(context, values[i]));
        }
        
        return collect;
    }

    /** rb_ary_collect_bang
     *
     */
    @JRubyMethod(name = {"collect!", "map!"}, frame = true)
    public RubyArray collect_bang(ThreadContext context, Block block) {
        if (!block.isGiven()) {
            throw context.getRuntime().newLocalJumpErrorNoBlock();
        }
        modify();
        for (int i = 0, len = realLength; i < len; i++) {
            store(i, block.yield(context, values[begin + i]));
        }
        return this;
    }

    /** rb_ary_collect_bang
    *
    */
    @JRubyMethod(name = "collect!", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject collect_bang19(ThreadContext context, Block block) {
        return block.isGiven() ? collect_bang(context, block) : enumeratorize(context.getRuntime(), this, "collect!");
    }

    /** rb_ary_collect_bang
    *
    */
    @JRubyMethod(name = "map!", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject map_bang19(ThreadContext context, Block block) {
        return block.isGiven() ? collect_bang(context, block) : enumeratorize(context.getRuntime(), this, "map!");
    }

    /** rb_ary_select
     *
     */
    @JRubyMethod(name = "select", frame = true, compat = CompatVersion.RUBY1_8)
    public RubyArray select(ThreadContext context, Block block) {
        Ruby runtime = getRuntime();
        RubyArray result = new RubyArray(runtime, realLength);

        if (isShared) {
            for (int i = begin; i < begin + realLength; i++) {
                if (block.yield(context, values[i]).isTrue()) result.append(elt(i - begin));
            }
        } else {
            for (int i = 0; i < realLength; i++) {
                if (block.yield(context, values[i]).isTrue()) result.append(elt(i));
            }
        }
        fillNil(result.values, result.realLength, result.values.length, context.getRuntime());
        return result;
    }

    @JRubyMethod(name = "select", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject select19(ThreadContext context, Block block) {
        return block.isGiven() ? select(context, block) : enumeratorize(context.getRuntime(), this, "select");
    }

    /** rb_ary_delete
     *
     */
    @JRubyMethod(name = "delete", required = 1, frame = true)
    public IRubyObject delete(ThreadContext context, IRubyObject item, Block block) {
        int i2 = 0;

        Ruby runtime = getRuntime();
        for (int i1 = 0; i1 < realLength; i1++) {
            IRubyObject e = values[begin + i1];
            if (equalInternal(context, e, item)) continue;
            if (i1 != i2) store(i2, e);
            i2++;
        }

        if (realLength == i2) {
            if (block.isGiven()) return block.yield(context, item);

            return runtime.getNil();
        }

        modify();

        final int realLength = this.realLength;
        final int begin = this.begin;
        final IRubyObject[] values = this.values;
        if (realLength > i2) {
            try {
                fillNil(values, begin + i2, begin + realLength, context.getRuntime());
            } catch (ArrayIndexOutOfBoundsException e) {
                concurrentModification();
            }
            this.realLength = i2;
            if (i2 << 1 < values.length && values.length > ARRAY_DEFAULT_SIZE) realloc(i2 << 1);
        }

        return item;
    }

    /** rb_ary_delete_at
     *
     */
    private final IRubyObject delete_at(int pos) {
        int len = realLength;

        if (pos >= len) return getRuntime().getNil();

        if (pos < 0) pos += len;

        if (pos < 0) return getRuntime().getNil();

        modify();

        IRubyObject obj = values[pos];
        try {
            System.arraycopy(values, pos + 1, values, pos, len - (pos + 1));
            values[realLength-1] = getRuntime().getNil();
        } catch (ArrayIndexOutOfBoundsException e) {
            concurrentModification();
        }
        realLength--;

        return obj;
    }

    /** rb_ary_delete_at_m
     * 
     */
    @JRubyMethod(name = "delete_at", required = 1)
    public IRubyObject delete_at(IRubyObject obj) {
        return delete_at((int) RubyNumeric.num2long(obj));
    }

    /** rb_ary_reject_bang
     * 
     */
    @JRubyMethod(name = "reject", frame = true)
    public IRubyObject reject(ThreadContext context, Block block) {
        RubyArray ary = aryDup();
        ary.reject_bang(context, block);
        return ary;
    }

    @JRubyMethod(name = "reject", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject reject19(ThreadContext context, Block block) {
        return block.isGiven() ? reject(context, block) : enumeratorize(context.getRuntime(), this, "reject");
    }

    /** rb_ary_reject_bang
     *
     */
    @JRubyMethod(name = "reject!", frame = true)
    public IRubyObject reject_bang(ThreadContext context, Block block) {
        if (!block.isGiven()) {
            throw context.getRuntime().newLocalJumpErrorNoBlock();
        }
        int i2 = 0;
        modify();

        for (int i1 = 0; i1 < realLength; i1++) {
            IRubyObject v = values[i1];
            if (block.yield(context, v).isTrue()) continue;

            if (i1 != i2) store(i2, v);
            i2++;
        }
        if (realLength == i2) return context.getRuntime().getNil();

        if (i2 < realLength) {
            try {
                fillNil(values, i2, realLength, context.getRuntime());
            } catch (ArrayIndexOutOfBoundsException e) {
                concurrentModification();
            }
            realLength = i2;
        }

        return this;
    }

    @JRubyMethod(name = "reject!", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject reject_bang19(ThreadContext context, Block block) {
        return block.isGiven() ? reject_bang(context, block) : enumeratorize(context.getRuntime(), this, "reject!");
    }

    /** rb_ary_delete_if
     *
     */
    @JRubyMethod(name = "delete_if", frame = true)
    public IRubyObject delete_if(ThreadContext context, Block block) {
        reject_bang(context, block);
        return this;
    }

    @JRubyMethod(name = "delete_if", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject delete_if19(ThreadContext context, Block block) {
        return block.isGiven() ? delete_if(context, block) : enumeratorize(context.getRuntime(), this, "delete_if");
    }

    /** rb_ary_zip
     * 
     */
    @JRubyMethod(name = "zip", optional = 1, rest = true, frame = true)
    public IRubyObject zip(ThreadContext context, IRubyObject[] args, Block block) {
        for (int i = 0; i < args.length; i++) {
            args[i] = args[i].convertToArray();
        }

        Ruby runtime = getRuntime();
        if (block.isGiven()) {
            for (int i = 0; i < realLength; i++) {
                RubyArray tmp = new RubyArray(runtime, args.length + 1);
                tmp.append(elt(i));
                for (int j = 0; j < args.length; j++) {
                    tmp.append(((RubyArray) args[j]).elt(i));
                }
                block.yield(context, tmp);
            }
            return runtime.getNil();
        }
        
        int len = realLength;
        RubyArray result = new RubyArray(runtime, len);
        for (int i = 0; i < len; i++) {
            RubyArray tmp = new RubyArray(runtime, args.length + 1);
            tmp.append(elt(i));
            for (int j = 0; j < args.length; j++) {
                tmp.append(((RubyArray) args[j]).elt(i));
            }
            result.append(tmp);
        }
        return result;
    }

    /** rb_ary_cmp
     *
     */
    @JRubyMethod(name = "<=>", required = 1)
    public IRubyObject op_cmp(ThreadContext context, IRubyObject obj) {
        Ruby runtime = context.getRuntime(); 
        RubyArray ary2 = obj.convertToArray();

        if (this == ary2 || runtime.isInspecting(this)) return RubyFixnum.zero(runtime);

        try {
            runtime.registerInspecting(this);

            int len = realLength;
            if (len > ary2.realLength) len = ary2.realLength;

            for (int i = 0; i < len; i++) {
                IRubyObject v = elt(i).callMethod(context, "<=>", ary2.elt(i));
                if (!(v instanceof RubyFixnum) || ((RubyFixnum) v).getLongValue() != 0) return v;
            }
        } finally {
            runtime.unregisterInspecting(this);
        }

        int len = realLength - ary2.realLength;

        if (len == 0) return RubyFixnum.zero(runtime);
        if (len > 0) return RubyFixnum.one(runtime);

        return RubyFixnum.minus_one(runtime);
    }

    /**
     * Variable arity version for compatibility. Not bound to a Ruby method.
     * @deprecated Use the versions with zero, one, or two args.
     */
    public IRubyObject slice_bang(IRubyObject[] args) {
        switch (args.length) {
        case 1:
            return slice_bang(args[0]);
        case 2:
            return slice_bang(args[0], args[1]);
        default:
            Arity.raiseArgumentError(getRuntime(), args.length, 1, 2);
            return null; // not reached
        }
    }

    /** rb_ary_slice_bang
     *
     */
    @JRubyMethod(name = "slice!")
    public IRubyObject slice_bang(IRubyObject arg0) {
        if (arg0 instanceof RubyRange) {
            long[] beglen = ((RubyRange) arg0).begLen(realLength, 1);
            long pos = beglen[0];
            long len = beglen[1];

            if (pos < 0) pos = realLength + pos;

            arg0 = subseq(pos, len);
            splice(pos, len, null);
            return arg0;
        }
        return delete_at((int) RubyNumeric.num2long(arg0));
    }

    /** rb_ary_slice_bang
    *
    */
    @JRubyMethod(name = "slice!")
    public IRubyObject slice_bang(IRubyObject arg0, IRubyObject arg1) {
        long pos = RubyNumeric.num2long(arg0);
        long len = RubyNumeric.num2long(arg1);

        if (pos < 0) pos = realLength + pos;

        arg1 = subseq(pos, len);
        splice(pos, len, null);

        return arg1;
    }    

    /** rb_ary_assoc
     *
     */
    @JRubyMethod(name = "assoc", required = 1)
    public IRubyObject assoc(ThreadContext context, IRubyObject key) {
        Ruby runtime = getRuntime();

        for (int i = begin; i < begin + realLength; i++) {
            IRubyObject v = values[i];
            if (v instanceof RubyArray) {
                RubyArray arr = (RubyArray)v;
                if (arr.realLength > 0 && equalInternal(context, arr.values[arr.begin], key)) return arr;
            }
        }

        return runtime.getNil();
    }

    /** rb_ary_rassoc
     *
     */
    @JRubyMethod(name = "rassoc", required = 1)
    public IRubyObject rassoc(ThreadContext context, IRubyObject value) {
        Ruby runtime = getRuntime();

        for (int i = begin; i < begin + realLength; i++) {
            IRubyObject v = values[i];
            if (v instanceof RubyArray) {
                RubyArray arr = (RubyArray)v;
                if (arr.realLength > 1 && equalInternal(context, arr.values[arr.begin + 1], value)) return arr;
            }
        }

        return runtime.getNil();
    }

    /** flatten
     * 
     */
    private final int flatten(ThreadContext context, int index, RubyArray ary2, RubyArray memo) {
        int i = index;
        int n;
        int lim = index + ary2.realLength;

        IRubyObject id = ary2.id();

        if (memo.includes(context, id)) throw getRuntime().newArgumentError("tried to flatten recursive array");

        memo.append(id);
        splice(index, 1, ary2);
        while (i < lim) {
            IRubyObject tmp = elt(i).checkArrayType();
            if (!tmp.isNil()) {
                n = flatten(context, i, (RubyArray) tmp, memo);
                i += n;
                lim += n;
            }
            i++;
        }
        memo.pop(context);
        return lim - index - 1; /* returns number of increased items */
    }

    /** rb_ary_flatten_bang
     *
     */
    @JRubyMethod(name = "flatten!")
    public IRubyObject flatten_bang(ThreadContext context) {
        int i = 0;
        RubyArray memo = null;

        while (i < realLength) {
            IRubyObject ary2 = values[begin + i];
            IRubyObject tmp = ary2.checkArrayType();
            if (!tmp.isNil()) {
                if (memo == null) {
                    memo = new RubyArray(getRuntime(), ARRAY_DEFAULT_SIZE, false); // doesn't escape, don't fill with nils
                }

                i += flatten(context, i, (RubyArray) tmp, memo);
            }
            i++;
        }
        if (memo == null) return getRuntime().getNil();

        return this;
    }

    /** rb_ary_flatten
    *
    */
    @JRubyMethod(name = "flatten")
    public IRubyObject flatten(ThreadContext context) {
        RubyArray ary = aryDup();
        ary.flatten_bang(context);
        return ary;
    }

    private boolean flatten19(ThreadContext context, int level, RubyArray result) {
        Ruby runtime = context.getRuntime();
        RubyArray stack = new RubyArray(runtime, ARRAY_DEFAULT_SIZE, false);
        IdentityHashMap<Object, Object> memo = new IdentityHashMap<Object, Object>();
        RubyArray ary = this;
        memo.put(ary, NEVER);
        boolean modified = false;

        int i = 0;

        while (true) {
            IRubyObject tmp;
            while (i < ary.realLength) {
                IRubyObject elt = ary.values[ary.begin + i++];
                tmp = elt.checkArrayType();
                if (tmp.isNil() || (level >= 0 && stack.realLength / 2 >= level)) {
                    result.append(elt);
                } else {
                    modified = true;
                    if (memo.get(tmp) != null) throw runtime.newArgumentError("tried to flatten recursive array");
                    memo.put(tmp, NEVER);
                    stack.append(ary);
                    stack.append(RubyFixnum.newFixnum(runtime, i));
                    ary = (RubyArray)tmp;
                    i = 0;
                }
            }
            if (stack.realLength == 0) break;
            memo.remove(ary);
            tmp = stack.pop19(context);
            i = (int)((RubyFixnum)tmp).getLongValue();
            ary = (RubyArray)stack.pop19(context);
        }
        return modified;
    }

    @JRubyMethod(name = "flatten!", compat = CompatVersion.RUBY1_9)
    public IRubyObject flatten_bang19(ThreadContext context) {
        Ruby runtime = context.getRuntime();

        RubyArray result = new RubyArray(runtime, realLength);
        if (flatten19(context, -1, result)) {
            begin = 0;
            realLength = result.realLength;
            values = result.values;
            return this;
        }
        return runtime.getNil();
    }

    @JRubyMethod(name = "flatten!", compat = CompatVersion.RUBY1_9)
    public IRubyObject flatten_bang19(ThreadContext context, IRubyObject arg) {
        Ruby runtime = context.getRuntime();
        int level = RubyNumeric.num2int(arg);
        if (level == 0) return this;

        RubyArray result = new RubyArray(runtime, realLength);
        if (flatten19(context, level, result)) {
            begin = 0;
            realLength = result.realLength;
            values = result.values;
            return this;
        }
        return runtime.getNil();
    }

    @JRubyMethod(name = "flatten", compat = CompatVersion.RUBY1_9)
    public IRubyObject flatten19(ThreadContext context) {
        Ruby runtime = context.getRuntime();

        RubyArray result = new RubyArray(runtime, realLength);
        flatten19(context, -1, result);
        result.infectBy(this);
        return result;
    }

    @JRubyMethod(name = "flatten", compat = CompatVersion.RUBY1_9)
    public IRubyObject flatten19(ThreadContext context, IRubyObject arg) {
        Ruby runtime = context.getRuntime();
        int level = RubyNumeric.num2int(arg);
        if (level == 0) return this;

        RubyArray result = new RubyArray(runtime, realLength);
        flatten19(context, level, result);
        result.infectBy(this);
        return result;
    }

    /** rb_ary_nitems
     *
     */
    @JRubyMethod(name = "nitems")
    public IRubyObject nitems() {
        int n = 0;

        for (int i = begin; i < begin + realLength; i++) {
            if (!values[i].isNil()) n++;
        }
        
        return getRuntime().newFixnum(n);
    }

    /** rb_ary_plus
     *
     */
    @JRubyMethod(name = "+", required = 1)
    public IRubyObject op_plus(IRubyObject obj) {
        RubyArray y = obj.convertToArray();
        int len = realLength + y.realLength;
        RubyArray z = new RubyArray(getRuntime(), len);
        try {
            System.arraycopy(values, begin, z.values, 0, realLength);
            System.arraycopy(y.values, y.begin, z.values, realLength, y.realLength);
        } catch (ArrayIndexOutOfBoundsException e) {
            concurrentModification();
        }
        z.realLength = len;
        return z;
    }

    /** rb_ary_times
     *
     */
    @JRubyMethod(name = "*", required = 1)
    public IRubyObject op_times(ThreadContext context, IRubyObject times) {
        IRubyObject tmp = times.checkStringType();

        if (!tmp.isNil()) return join(context, tmp);

        long len = RubyNumeric.num2long(times);
        if (len == 0) return new RubyArray(getRuntime(), getMetaClass(), IRubyObject.NULL_ARRAY);
        if (len < 0) throw getRuntime().newArgumentError("negative argument");

        if (Long.MAX_VALUE / len < realLength) {
            throw getRuntime().newArgumentError("argument too big");
        }

        len *= realLength;

        RubyArray ary2 = new RubyArray(getRuntime(), getMetaClass(), len);
        ary2.realLength = ary2.values.length;

        try {
            for (int i = 0; i < len; i += realLength) {
                System.arraycopy(values, begin, ary2.values, i, realLength);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            concurrentModification();
        }

        ary2.infectBy(this);

        return ary2;
    }

    /** ary_make_hash
     * 
     */
    private final RubyHash makeHash(RubyArray ary2) {
        RubyHash hash = new RubyHash(getRuntime(), false);
        int begin = this.begin;
        for (int i = begin; i < begin + realLength; i++) {
            hash.fastASet(values[i], NEVER);
        }

        if (ary2 != null) {
            begin = ary2.begin;            
            for (int i = begin; i < begin + ary2.realLength; i++) {
                hash.fastASet(ary2.values[i], NEVER);
            }
        }
        return hash;
    }

    /** rb_ary_uniq_bang
     *
     */
    @JRubyMethod(name = "uniq!")
    public IRubyObject uniq_bang() {
        RubyHash hash = makeHash(null);

        if (realLength == hash.size()) return getRuntime().getNil();

        int j = 0;
        for (int i = 0; i < realLength; i++) {
            IRubyObject v = elt(i);
            if (hash.fastDelete(v)) store(j++, v);
        }
        realLength = j;
        return this;
    }

    /** rb_ary_uniq
     *
     */
    @JRubyMethod(name = "uniq")
    public IRubyObject uniq() {
        RubyArray ary = aryDup();
        ary.uniq_bang();
        return ary;
    }

    /** rb_ary_diff
     *
     */
    @JRubyMethod(name = "-", required = 1)
    public IRubyObject op_diff(IRubyObject other) {
        RubyHash hash = other.convertToArray().makeHash(null);
        RubyArray ary3 = new RubyArray(getRuntime(), ARRAY_DEFAULT_SIZE);

        int begin = this.begin;
        for (int i = begin; i < begin + realLength; i++) {
            if (hash.fastARef(values[i]) != null) continue;
            ary3.append(elt(i - begin));
        }
        fillNil(ary3.values, ary3.realLength, ary3.values.length, getRuntime());

        return ary3;
    }

    /** rb_ary_and
     *
     */
    @JRubyMethod(name = "&", required = 1)
    public IRubyObject op_and(IRubyObject other) {
        RubyArray ary2 = other.convertToArray();
        RubyHash hash = ary2.makeHash(null);
        RubyArray ary3 = new RubyArray(getRuntime(), realLength < ary2.realLength ? realLength : ary2.realLength);

        for (int i = 0; i < realLength; i++) {
            IRubyObject v = elt(i);
            if (hash.fastDelete(v)) ary3.append(v);
        }
        fillNil(ary3.values, ary3.realLength, ary3.values.length, getRuntime());

        return ary3;
    }

    /** rb_ary_or
     *
     */
    @JRubyMethod(name = "|", required = 1)
    public IRubyObject op_or(IRubyObject other) {
        RubyArray ary2 = other.convertToArray();
        RubyHash set = makeHash(ary2);

        RubyArray ary3 = new RubyArray(getRuntime(), realLength + ary2.realLength);

        for (int i = 0; i < realLength; i++) {
            IRubyObject v = elt(i);
            if (set.fastDelete(v)) ary3.append(v);
        }
        for (int i = 0; i < ary2.realLength; i++) {
            IRubyObject v = ary2.elt(i);
            if (set.fastDelete(v)) ary3.append(v);
        }
        fillNil(ary3.values, ary3.realLength, ary3.values.length, getRuntime());

        return ary3;
    }

    /** rb_ary_sort
     *
     */
    @JRubyMethod(name = "sort", frame = true)
    public RubyArray sort(Block block) {
        RubyArray ary = aryDup();
        ary.sort_bang(block);
        return ary;
    }

    /** rb_ary_sort_bang
     *
     */
    @JRubyMethod(name = "sort!", frame = true)
    public RubyArray sort_bang(Block block) {
        modify();
        if (realLength > 1) {
            flags |= TMPLOCK_ARR_F;
            try {
                if (block.isGiven()) {
                    Arrays.sort(values, 0, realLength, new BlockComparator(block));
                } else {
                    Arrays.sort(values, 0, realLength, new DefaultComparator());
                }
            } finally {
                flags &= ~TMPLOCK_ARR_F;
            }
        }
        return this;
    }
    
    /** rb_ary_take
     * 
     */
    @JRubyMethod(name = "take", compat = CompatVersion.RUBY1_9)
    public IRubyObject take(ThreadContext context, IRubyObject n) {
        Ruby runtime = context.getRuntime();
        long len = RubyNumeric.num2long(n);
        if (len < 0) throw runtime.newArgumentError("attempt to take negative size");

        return subseq(0, len);
    }

    /** rb_ary_take_while
     * 
     */
    @JRubyMethod(name = "take_while", compat = CompatVersion.RUBY1_9)
    public IRubyObject take_while(ThreadContext context, Block block) {
        Ruby runtime = context.getRuntime();
        if (!block.isGiven()) return enumeratorize(runtime, this, "take_while");

        int i = begin;
        for (; i < begin + realLength; i++) {
            if (!block.yield(context, values[i]).isTrue()) break; 
        }
        return subseq(0, i - begin);
    }

    /** rb_ary_take
     * 
     */
    @JRubyMethod(name = "drop", compat = CompatVersion.RUBY1_9)
    public IRubyObject drop(ThreadContext context, IRubyObject n) {
        Ruby runtime = context.getRuntime();
        long pos = RubyNumeric.num2long(n);
        if (pos < 0) throw runtime.newArgumentError("attempt to drop negative size");

        IRubyObject result = subseq(pos, realLength);
        return result.isNil() ? runtime.newEmptyArray() : result;
    }

    /** rb_ary_take_while
     * 
     */
    @JRubyMethod(name = "drop_while", compat = CompatVersion.RUBY1_9)
    public IRubyObject drop_while(ThreadContext context, Block block) {
        Ruby runtime = context.getRuntime();
        if (!block.isGiven()) return enumeratorize(runtime, this, "drop_while");

        int i= begin;
        for (; i < begin + realLength; i++) {
            if (!block.yield(context, values[i]).isTrue()) break;
        }
        IRubyObject result = subseq(i - begin, realLength);
        return result.isNil() ? runtime.newEmptyArray() : result;
    }

    /** rb_ary_cycle
     * 
     */
    @JRubyMethod(name = "cycle", compat = CompatVersion.RUBY1_9)
    public IRubyObject cycle(ThreadContext context, Block block) {
        if (!block.isGiven()) return enumeratorize(context.getRuntime(), this, "cycle");
        return cycleCommon(context, -1, block);
    }

    /** rb_ary_cycle
     * 
     */
    @JRubyMethod(name = "cycle", compat = CompatVersion.RUBY1_9)
    public IRubyObject cycle(ThreadContext context, IRubyObject arg, Block block) {
        if (!block.isGiven()) return enumeratorize(context.getRuntime(), this, "cycle", arg);
        long n = RubyNumeric.num2long(arg);
        return n <= 0 ? context.getRuntime().getNil() : cycleCommon(context, n, block);
    }

    private IRubyObject cycleCommon(ThreadContext context, long n, Block block) {
        while (realLength > 0 && (n < 0 || 0 < n--)) {
            for (int i=begin; i < begin + realLength; i++) {
                block.yield(context, values[i]);
            }
        }
        return context.getRuntime().getNil();
    }

    /** rb_ary_product
     * 
     */
    @JRubyMethod(name = "product", rest = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject product(ThreadContext context, IRubyObject[] args) {
        Ruby runtime = context.getRuntime();

        int n = args.length + 1;
        RubyArray arrays[] = new RubyArray[n];
        int counters[] = new int[n];

        arrays[0] = this;
        for (int i = 1; i < n; i++) arrays[i] = args[i - 1].convertToArray();

        int resultLen = 1;
        for (int i = 0; i < n; i++) {
            int k = arrays[i].realLength;
            int l = resultLen;
            if (k == 0) return newEmptyArray(runtime);
            resultLen *= k;
            if (resultLen < k || resultLen < l || resultLen / k != l) {
                throw runtime.newRangeError("too big to product");
            }
        }

        RubyArray result = newArray(runtime, resultLen);

        for (int i = 0; i < resultLen; i++) {
            RubyArray sub = newArray(runtime, n);
            for (int j = 0; j < n; j++) sub.append(arrays[j].entry(counters[j]));

            result.append(sub);
            int m = n - 1;
            counters[m]++;

            while (m > 0 && counters[m] == arrays[m].realLength) {
                counters[m] = 0;
                m--;
                counters[m]++;
            }
        }
        return result;
    }

    private int combinationLength(ThreadContext context, int n, int k) {
        if (k * 2 > n) k = n - k;
        if (k == 0) return 1;
        if (k < 0) return 0;
        int val = 1;
        for (int i = 1; i <= k; i++, n--) {
            long m = val;
            val *= n;
            if (val < m) throw context.getRuntime().newRangeError("too big for combination");
            val /= i;
        }
        return val;
    }

    /** rb_ary_combination
     * 
     */
    @JRubyMethod(name = "combination", compat = CompatVersion.RUBY1_9)
    public IRubyObject combination(ThreadContext context, IRubyObject num, Block block) {
        Ruby runtime = context.getRuntime();
        if (!block.isGiven()) return enumeratorize(runtime, this, "combination", num);

        int n = RubyNumeric.num2int(num);

        if (n == 0) {
            block.yield(context, newEmptyArray(runtime));
        } else if (n == 1) {
            for (int i = 0; i < realLength; i++) {
                block.yield(context, values[begin + i]);
            }
        } else if (n >= 0 && realLength >= n) {
            int stack[] = new int[n + 1];
            int nlen = combinationLength(context, (int)realLength, n);
            IRubyObject chosen[] = new IRubyObject[n];
            int lev = 0;

            stack[0] = -1;
            for (int i = 0; i < nlen; i++) {
                chosen[lev] = values[begin + stack[lev + 1]];
                for (lev++; lev < n; lev++) {
                    chosen[lev] = values[begin + (stack[lev + 1] = stack[lev] + 1)];
                }
                block.yield(context, newArray(runtime, chosen));
                do {
                    stack[lev--]++;
                } while (lev != 0 && stack[lev + 1] + n == realLength + lev + 1);
            }
        }
        return this;
    }
    
    private void permute(ThreadContext context, int n, int r, int[]p, int index, boolean[]used, RubyArray values, Block block) {
        for (int i = 0; i < n; i++) {
            if (!used[i]) {
                p[index] = i;
                if (index < r - 1) {
                    used[i] = true;
                    permute(context, n, r, p, index + 1, used, values, block);
                    used[i] = false;
                } else {
                    RubyArray result = newArray(context.getRuntime(), r);

                    for (int j = 0; j < r; j++) {
                        result.values[result.begin + j] = values.values[values.begin + p[j]];
                    }

                    result.realLength = r;
                    block.yield(context, result);
                }
            }
        }
    }

    /** rb_ary_permutation
     * 
     */
    @JRubyMethod(name = "permutation", compat = CompatVersion.RUBY1_9)
    public IRubyObject permutation(ThreadContext context, IRubyObject num, Block block) {
        return block.isGiven() ? permutationCommon(context, RubyNumeric.num2int(num), block) : enumeratorize(context.getRuntime(), this, "permutation", num);
    }

    @JRubyMethod(name = "permutation", compat = CompatVersion.RUBY1_9)
    public IRubyObject permutation(ThreadContext context, Block block) {
        return block.isGiven() ? permutationCommon(context, realLength, block) : enumeratorize(context.getRuntime(), this, "permutation");
    }

    private IRubyObject permutationCommon(ThreadContext context, int r, Block block) {
        if (r == 0) {
            return newEmptyArray(context.getRuntime());
        } else if (r == 1) {
            for (int i = 0; i < realLength; i++) {
                block.yield(context, newArray(context.getRuntime(), values[begin + i]));
            }
        } else if (r >= 0 && realLength >= r) {
            int n = realLength;
            permute(context, n, r,
                    new int[n], 0,
                    new boolean[n],
                    makeShared(0, realLength, getMetaClass()), block);
        }
        return this;
    }

    @JRubyMethod(name = "shuffle!", compat = CompatVersion.RUBY1_9)
    public IRubyObject shuffle_bang(ThreadContext context) {
        modify();
        Random random = context.getRuntime().getRandom();
        
        int i = realLength;
        
        try {
            while (i > 0) {
                int r = random.nextInt(i);
                IRubyObject tmp = values[begin + --i];
                values[begin + i] = values[begin + r];
                values[begin + r] = tmp;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            concurrentModification();
        }

        return this;
    }

    @JRubyMethod(name = "shuffle", compat = CompatVersion.RUBY1_9)
    public IRubyObject shuffle(ThreadContext context) {
        RubyArray ary = aryDup();
        ary.shuffle_bang(context);
        return ary;
    }

    @JRubyMethod(name = "sample", compat = CompatVersion.RUBY1_9)
    public IRubyObject sample(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        if (realLength == 0) return runtime.getNil();
        int i = realLength == 1 ? 0 : runtime.getRandom().nextInt(realLength);
        try {
            return values[begin + i];
        } catch (ArrayIndexOutOfBoundsException e) {
            concurrentModification();
            return runtime.getNil();
        }
    }

    private static int SORTED_THRESHOLD = 10; 
    @JRubyMethod(name = "sample", compat = CompatVersion.RUBY1_9)
    public IRubyObject sample(ThreadContext context, IRubyObject nv) {
        Ruby runtime = context.getRuntime();
        Random random = runtime.getRandom();
        int n = RubyNumeric.num2int(nv);

        int i, j, k;
        switch (n) {
        case 0: 
            return newEmptyArray(runtime);
        case 1:
            return newArray(runtime, values[random.nextInt(realLength)]);
        case 2:
            i = random.nextInt(realLength);
            j = random.nextInt(realLength - 1);
            if (j >= i) j++;
            return newArray(runtime, values[begin + i], values[begin + j]);
        case 3:
            i = random.nextInt(realLength);
            j = random.nextInt(realLength - 1);
            k = random.nextInt(realLength - 2);
            int l = j, g = i;
            if (j >= i) {
                l = i;
                g = ++j;
            }
            if (k >= l && (++k >= g)) ++k;
            return new RubyArray(runtime, new IRubyObject[] {values[begin + i], values[begin + j], values[begin + k]});
        }
        
        int len = realLength;
        if (n > len) n = len;
        if (n < SORTED_THRESHOLD) {
            int idx[] = new int[SORTED_THRESHOLD];
            int sorted[] = new int[SORTED_THRESHOLD];
            sorted[0] = idx[0] = random.nextInt(len);
            for (i = 1; i < n; i++) {
                k = random.nextInt(--len);
                for (j = 0; j < i; j++) {
                    if (k < sorted[j]) break;
                    k++;
                }
                System.arraycopy(sorted, j, sorted, j + 1, i - j);
                sorted[j] = idx[i] = k;
            }
            IRubyObject[]result = new IRubyObject[n];
            for (i = 0; i < n; i++) result[i] = values[begin + idx[i]];
            return new RubyArray(runtime, result);
        } else {
            IRubyObject[]result = new IRubyObject[len];
            System.arraycopy(values, begin, result, 0, len);
            for (i = 0; i < n; i++) {
                j = random.nextInt(len - i) + i;
                IRubyObject tmp = result[j];
                result[j] = result[i];
                result[i] = tmp;
            }
            RubyArray ary = new RubyArray(runtime, result);
            ary.realLength = n;
            return ary;
        }
    }

    final class BlockComparator implements Comparator {
        private Block block;

        public BlockComparator(Block block) {
            this.block = block;
        }

        public int compare(Object o1, Object o2) {
            ThreadContext context = getRuntime().getCurrentContext();
            IRubyObject obj1 = (IRubyObject) o1;
            IRubyObject obj2 = (IRubyObject) o2;
            IRubyObject ret = block.yield(context, getRuntime().newArray(obj1, obj2), null, null, true);
            int n = RubyComparable.cmpint(context, ret, obj1, obj2);
            //TODO: ary_sort_check should be done here
            return n;
        }
    }

    static final class DefaultComparator implements Comparator {
        public int compare(Object o1, Object o2) {
            if (o1 instanceof RubyFixnum && o2 instanceof RubyFixnum) {
                return compareFixnums((RubyFixnum)o1, (RubyFixnum)o2);
            }
            if (o1 instanceof RubyString && o2 instanceof RubyString) {
                return ((RubyString) o1).op_cmp((RubyString) o2);
            }
            //TODO: ary_sort_check should be done here
            return compareOthers((IRubyObject)o1, (IRubyObject)o2);
        }

        private int compareFixnums(RubyFixnum o1, RubyFixnum o2) {
            long a = o1.getLongValue();
            long b = o2.getLongValue();
            if (a > b) {
                return 1;
            }
            if (a < b) {
                return -1;
            }
            return 0;
        }

        private int compareOthers(IRubyObject o1, IRubyObject o2) {
            ThreadContext context = o1.getRuntime().getCurrentContext();
            IRubyObject ret = o1.callMethod(context, "<=>", o2);
            int n = RubyComparable.cmpint(context, ret, o1, o2);
            //TODO: ary_sort_check should be done here
            return n;
        }
    }

    public static void marshalTo(RubyArray array, MarshalStream output) throws IOException {
        output.registerLinkTarget(array);
        output.writeInt(array.getList().size());
        for (Iterator iter = array.getList().iterator(); iter.hasNext();) {
            output.dumpObject((IRubyObject) iter.next());
        }
    }

    public static RubyArray unmarshalFrom(UnmarshalStream input) throws IOException {
        RubyArray result = input.getRuntime().newArray();
        input.registerLinkTarget(result);
        int size = input.unmarshalInt();
        for (int i = 0; i < size; i++) {
            result.append(input.unmarshalObject());
        }
        return result;
    }

    /**
     * @see org.jruby.util.Pack#pack
     */
    @JRubyMethod(name = "pack", required = 1)
    public RubyString pack(ThreadContext context, IRubyObject obj) {
        RubyString iFmt = RubyString.objAsString(context, obj);
        return Pack.pack(getRuntime(), this, iFmt.getByteList());
    }

    @Override
    public Class getJavaClass() {
        return List.class;
    }

    // Satisfy java.util.List interface (for Java integration)
    public int size() {
        return realLength;
    }

    public boolean isEmpty() {
        return realLength == 0;
    }

    public boolean contains(Object element) {
        return indexOf(element) != -1;
    }

    public Object[] toArray() {
        Object[] array = new Object[realLength];
        for (int i = begin; i < realLength; i++) {
            array[i - begin] = JavaUtil.convertRubyToJava(values[i]);
        }
        return array;
    }

    public Object[] toArray(final Object[] arg) {
        Object[] array = arg;
        if (array.length < realLength) {
            Class type = array.getClass().getComponentType();
            array = (Object[]) Array.newInstance(type, realLength);
        }
        int length = realLength - begin;

        for (int i = 0; i < length; i++) {
            array[i] = JavaUtil.convertRubyToJava(values[i + begin]);
        }
        return array;
    }

    public boolean add(Object element) {
        append(JavaUtil.convertJavaToRuby(getRuntime(), element));
        return true;
    }

    public boolean remove(Object element) {
        IRubyObject deleted = delete(getRuntime().getCurrentContext(), JavaUtil.convertJavaToRuby(getRuntime(), element), Block.NULL_BLOCK);
        return deleted.isNil() ? false : true; // TODO: is this correct ?
    }

    public boolean containsAll(Collection c) {
        for (Iterator iter = c.iterator(); iter.hasNext();) {
            if (indexOf(iter.next()) == -1) {
                return false;
            }
        }

        return true;
    }

    public boolean addAll(Collection c) {
        for (Iterator iter = c.iterator(); iter.hasNext();) {
            add(iter.next());
        }
        return !c.isEmpty();
    }

    public boolean addAll(int index, Collection c) {
        Iterator iter = c.iterator();
        for (int i = index; iter.hasNext(); i++) {
            add(i, iter.next());
        }
        return !c.isEmpty();
    }

    public boolean removeAll(Collection c) {
        boolean listChanged = false;
        for (Iterator iter = c.iterator(); iter.hasNext();) {
            if (remove(iter.next())) {
                listChanged = true;
            }
        }
        return listChanged;
    }

    public boolean retainAll(Collection c) {
        boolean listChanged = false;

        for (Iterator iter = iterator(); iter.hasNext();) {
            Object element = iter.next();
            if (!c.contains(element)) {
                remove(element);
                listChanged = true;
            }
        }
        return listChanged;
    }

    public Object get(int index) {
        return JavaUtil.convertRubyToJava((IRubyObject) elt(index), Object.class);
    }

    public Object set(int index, Object element) {
        return store(index, JavaUtil.convertJavaToRuby(getRuntime(), element));
    }

    // TODO: make more efficient by not creating IRubyArray[]
    public void add(int index, Object element) {
        insert(new IRubyObject[]{RubyFixnum.newFixnum(getRuntime(), index), JavaUtil.convertJavaToRuby(getRuntime(), element)});
    }

    public Object remove(int index) {
        return JavaUtil.convertRubyToJava(delete_at(index), Object.class);
    }

    public int indexOf(Object element) {
        int begin = this.begin;

        if (element != null) {
            IRubyObject convertedElement = JavaUtil.convertJavaToRuby(getRuntime(), element);

            for (int i = begin; i < begin + realLength; i++) {
                if (convertedElement.equals(values[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    public int lastIndexOf(Object element) {
        int begin = this.begin;

        if (element != null) {
            IRubyObject convertedElement = JavaUtil.convertJavaToRuby(getRuntime(), element);

            for (int i = begin + realLength - 1; i >= begin; i--) {
                if (convertedElement.equals(values[i])) {
                    return i;
                }
            }
        }

        return -1;
    }

    public class RubyArrayConversionIterator implements Iterator {
        protected int index = 0;
        protected int last = -1;

        public boolean hasNext() {
            return index < realLength;
        }

        public Object next() {
            IRubyObject element = elt(index);
            last = index++;
            return JavaUtil.convertRubyToJava(element, Object.class);
        }

        public void remove() {
            if (last == -1) throw new IllegalStateException();

            delete_at(last);
            if (last < index) index--;

            last = -1;
	
        }
    }

    public Iterator iterator() {
        return new RubyArrayConversionIterator();
    }

    final class RubyArrayConversionListIterator extends RubyArrayConversionIterator implements ListIterator {
        public RubyArrayConversionListIterator() {
        }

        public RubyArrayConversionListIterator(int index) {
            this.index = index;
		}

		public boolean hasPrevious() {
            return index >= 0;
		}

		public Object previous() {
            return JavaUtil.convertRubyToJava((IRubyObject) elt(last = --index), Object.class);
		}

		public int nextIndex() {
            return index;
		}

		public int previousIndex() {
            return index - 1;
		}

        public void set(Object obj) {
            if (last == -1) throw new IllegalStateException();

            store(last, JavaUtil.convertJavaToRuby(getRuntime(), obj));
        }

        public void add(Object obj) {
            insert(new IRubyObject[] { RubyFixnum.newFixnum(getRuntime(), index++), JavaUtil.convertJavaToRuby(getRuntime(), obj) });
            last = -1;
        }
    }

    public ListIterator listIterator() {
        return new RubyArrayConversionListIterator();
    }

    public ListIterator listIterator(int index) {
        return new RubyArrayConversionListIterator(index);
	}

    // TODO: list.subList(from, to).clear() is supposed to clear the sublist from the list.
    // How can we support this operation?
    public List subList(int fromIndex, int toIndex) {
        if (fromIndex < 0 || toIndex > size() || fromIndex > toIndex) {
            throw new IndexOutOfBoundsException();
        }
        
        IRubyObject subList = subseq(fromIndex, toIndex - fromIndex + 1);

        return subList.isNil() ? null : (List) subList;
    }

    public void clear() {
        rb_clear();
    }
}
