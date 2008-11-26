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
 * Copyright (C) 2001-2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2001-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2002-2006 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2004 David Corbin <dcorbin@users.sourceforge.net>
 * Copyright (C) 2005 Tim Azzopardi <tim@tigerfive.com>
 * Copyright (C) 2006 Miguel Covarrubias <mlcovarrubias@gmail.com>
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

import static org.jruby.RubyEnumerator.enumeratorize;
import static org.jruby.anno.FrameField.BACKREF;
import static org.jruby.anno.FrameField.LASTLINE;
import static org.jruby.util.StringSupport.CODERANGE_7BIT;
import static org.jruby.util.StringSupport.CODERANGE_BROKEN;
import static org.jruby.util.StringSupport.CODERANGE_MASK;
import static org.jruby.util.StringSupport.CODERANGE_VALID;
import static org.jruby.util.StringSupport.codeLength;
import static org.jruby.util.StringSupport.codePoint;
import static org.jruby.util.StringSupport.strLengthWithCodeRange;
import static org.jruby.util.StringSupport.unpackCodeRange;
import static org.jruby.util.StringSupport.unpackLength;

import java.io.UnsupportedEncodingException;
import java.util.Locale;

import org.joni.Matcher;
import org.joni.Option;
import org.joni.Regex;
import org.joni.Region;
import org.jcodings.Encoding;
import org.jcodings.EncodingDB.Entry;
import org.jcodings.specific.ASCIIEncoding;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.exceptions.RaiseException;
import org.jruby.java.MiniJava;
import org.jruby.javasupport.util.RuntimeHelpers;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.ClassIndex;
import org.jruby.runtime.Frame;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.encoding.EncodingCapable;
import org.jruby.runtime.marshal.UnmarshalStream;
import org.jruby.util.ByteList;
import org.jruby.util.Numeric;
import org.jruby.util.Pack;
import org.jruby.util.Sprintf;
import org.jruby.util.StringSupport;
import org.jruby.util.string.JavaCrypt;

/**
 * Implementation of Ruby String class
 * 
 * Concurrency: no synchronization is required among readers, but
 * all users must synchronize externally with writers.
 *
 */
@JRubyClass(name="String", include={"Enumerable", "Comparable"})
public class RubyString extends RubyObject implements EncodingCapable {
    private static final ASCIIEncoding ASCII = ASCIIEncoding.INSTANCE;

    // string doesn't share any resources
    private static final int SHARE_LEVEL_NONE = 0;
    // string has it's own ByteList, but it's pointing to a shared buffer (byte[])
    private static final int SHARE_LEVEL_BUFFER = 1;
    // string doesn't have it's own ByteList (values)
    private static final int SHARE_LEVEL_BYTELIST = 2;

    private volatile int shareLevel = SHARE_LEVEL_NONE;

    private ByteList value;

    private static ObjectAllocator STRING_ALLOCATOR = new ObjectAllocator() {
        public IRubyObject allocate(Ruby runtime, RubyClass klass) {
            return RubyString.newEmptyString(runtime, klass);
        }
    };

    public static RubyClass createStringClass(Ruby runtime) {
        RubyClass stringClass = runtime.defineClass("String", runtime.getObject(), STRING_ALLOCATOR);
        runtime.setString(stringClass);
        stringClass.index = ClassIndex.STRING;
        stringClass.kindOf = new RubyModule.KindOf() {
            @Override
                public boolean isKindOf(IRubyObject obj, RubyModule type) {
                    return obj instanceof RubyString;
                }
            };
            
        stringClass.includeModule(runtime.getComparable());
        stringClass.includeModule(runtime.getEnumerable());
        stringClass.defineAnnotatedMethods(RubyString.class);
        
        return stringClass;
    }

    public Encoding getEncoding() {
        return value.encoding;
    }

    public void associateEncoding(Encoding enc) {
        if (value.encoding != enc) {
            if (!isCodeRangeAsciiOnly() || !enc.isAsciiCompatible()) clearCodeRange();
            value.encoding = enc;
        }
    }

    public final Encoding toEncoding(Ruby runtime) {
        if (!value.encoding.isAsciiCompatible()) {
            throw runtime.newArgumentError("invalid name encoding (non ASCII)");
        }
        Entry entry = runtime.getEncodingService().findEncodingOrAliasEntry(value);
        if (entry == null) {
            throw runtime.newArgumentError("unknown encoding name - " + value);
        }
        return entry.getEncoding();
    }

    public final int getCodeRange() {
        return flags & CODERANGE_MASK;
    }

    public final void setCodeRange(int codeRange) {
        flags |= codeRange & CODERANGE_MASK;
    }

    public final void clearCodeRange() {
        flags &= ~CODERANGE_MASK;
    }
    
    public final boolean isCodeRangeAsciiOnly() {
        return getCodeRange() == CODERANGE_7BIT;
    }

    public final boolean isCodeRangeValid() {
        return (flags & CODERANGE_VALID) != 0;
    }

    public final boolean isCodeRangeBroken() {
        return (flags & CODERANGE_BROKEN) != 0;
    }

    private final boolean singleByteOptimizable() {
        return getCodeRange() == CODERANGE_7BIT || value.encoding.isSingleByte();
    }

    final Encoding checkEncoding(RubyString other) {
        Encoding enc = RubyEncoding.areCompatible(this, other);
        if (enc == null) throw getRuntime().newArgumentError("incompatible character encodings: " + 
                                value.encoding + " and " + other.value.encoding);
        return enc;
    }

    private int strLength(Encoding enc) {
        if (singleByteOptimizable()) return value.realSize;
        value.encoding = enc;
        return strLength(value);
    }

    private int strLength() {
        if (singleByteOptimizable()) return value.realSize;
        return strLength(value);
    }

    private int strLength(ByteList bytes) {
        long lencr = strLengthWithCodeRange(value);
        int cr = unpackCodeRange(lencr);
        if (cr != 0) setCodeRange(cr);
        return unpackLength(lencr);
    }

    /** short circuit for String key comparison
     * 
     */
    @Override
    public final boolean eql(IRubyObject other) {
        if (otherIsString(other)) return eqlString(other);
        return super.eql(other);
    }
    private final boolean otherIsString(IRubyObject other) {
        return other.getMetaClass() == getRuntime().getString();
    }
    private final boolean eqlString(IRubyObject other) {
        return value.equal(((RubyString)other).value);
    }

    public RubyString(Ruby runtime, RubyClass rubyClass, CharSequence value) {
        super(runtime, rubyClass);
        assert value != null;
        this.value = new ByteList(ByteList.plain(value), false);
    }

    public RubyString(Ruby runtime, RubyClass rubyClass, byte[] value) {
        super(runtime, rubyClass);
        assert value != null;
        this.value = new ByteList(value);
    }

    public RubyString(Ruby runtime, RubyClass rubyClass, ByteList value) {
        super(runtime, rubyClass);
        assert value != null;
        this.value = value;
    }
    
    public RubyString(Ruby runtime, RubyClass rubyClass, ByteList value, boolean objectSpace) {
        super(runtime, rubyClass, objectSpace);
        assert value != null;
        this.value = value;
    }

    // Deprecated String construction routines
    /** Create a new String which uses the same Ruby runtime and the same
     *  class like this String.
     *
     *  This method should be used to satisfy RCR #38.
     *  @deprecated  
     */
    @Deprecated
    public RubyString newString(CharSequence s) {
        return new RubyString(getRuntime(), getType(), s);
    }

    /** Create a new String which uses the same Ruby runtime and the same
     *  class like this String.
     *
     *  This method should be used to satisfy RCR #38.
     *  @deprecated
     */
    @Deprecated
    public RubyString newString(ByteList s) {
        return new RubyString(getRuntime(), getMetaClass(), s);
    }

    @Deprecated
    public static RubyString newString(Ruby runtime, RubyClass clazz, CharSequence str) {
        return new RubyString(runtime, clazz, str);
    }

    public static RubyString newStringLight(Ruby runtime, ByteList bytes) {
        return new RubyString(runtime, runtime.getString(), bytes, false);
    }

    // String construction routines by copying byte[] buffer   
    public static RubyString newString(Ruby runtime, CharSequence str) {
        return new RubyString(runtime, runtime.getString(), str);
    }
    
    public static RubyString newString(Ruby runtime, byte[] bytes) {
        return new RubyString(runtime, runtime.getString(), bytes);
    }

    public static RubyString newString(Ruby runtime, byte[] bytes, int start, int length) {
        byte[] copy = new byte[length];
        System.arraycopy(bytes, start, copy, 0, length);
        return new RubyString(runtime, runtime.getString(), new ByteList(copy, false));
    }

    public static RubyString newString(Ruby runtime, ByteList bytes) {
        return new RubyString(runtime, runtime.getString(), bytes);
    }
    
    public static RubyString newUnicodeString(Ruby runtime, String str) {
        try {
            return new RubyString(runtime, runtime.getString(), new ByteList(str.getBytes("UTF8"), false));
        } catch (UnsupportedEncodingException uee) {
            return new RubyString(runtime, runtime.getString(), str);
        }
    }

    // String construction routines by NOT byte[] buffer and making the target String shared 
    public static RubyString newStringShared(Ruby runtime, RubyString orig) {
        orig.shareLevel = SHARE_LEVEL_BYTELIST;
        RubyString str = new RubyString(runtime, runtime.getString(), orig.value);
        str.shareLevel = SHARE_LEVEL_BYTELIST;
        return str;
    }       

    public static RubyString newStringShared(Ruby runtime, ByteList bytes) {
        return newStringShared(runtime, runtime.getString(), bytes);
    }    

    public static RubyString newStringShared(Ruby runtime, RubyClass clazz, ByteList bytes) {
        RubyString str = new RubyString(runtime, clazz, bytes);
        str.shareLevel = SHARE_LEVEL_BYTELIST;
        return str;
    }

    public static RubyString newStringShared(Ruby runtime, byte[] bytes) {
        return newStringShared(runtime, new ByteList(bytes, false));
    }

    public static RubyString newStringShared(Ruby runtime, byte[] bytes, int start, int length) {
        return newStringShared(runtime, new ByteList(bytes, start, length, false));
    }

    public static RubyString newEmptyString(Ruby runtime) {
        return newEmptyString(runtime, runtime.getString());
    }

    public static RubyString newEmptyString(Ruby runtime, RubyClass metaClass) {
        RubyString empty = new RubyString(runtime, metaClass, ByteList.EMPTY_BYTELIST);
        empty.shareLevel = SHARE_LEVEL_BYTELIST;
        return empty;
    }

    // String construction routines by NOT byte[] buffer and NOT making the target String shared 
    public static RubyString newStringNoCopy(Ruby runtime, ByteList bytes) {
        return newStringNoCopy(runtime, runtime.getString(), bytes);
    }    

    public static RubyString newStringNoCopy(Ruby runtime, RubyClass clazz, ByteList bytes) {
        return new RubyString(runtime, clazz, bytes);
    }    

    public static RubyString newStringNoCopy(Ruby runtime, byte[] bytes, int start, int length) {
        return newStringNoCopy(runtime, new ByteList(bytes, start, length, false));
    }

    public static RubyString newStringNoCopy(Ruby runtime, byte[] bytes) {
        return newStringNoCopy(runtime, new ByteList(bytes, false));
    }

    /** Encoding aware String construction routines for 1.9
     * 
     */
    private static final ByteList EMPTY_BYTELISTS[] = new ByteList[4];
    static ByteList getEmptyByteList(Encoding enc) {
        int index = enc.getIndex();
        ByteList bytes;
        if (index < EMPTY_BYTELISTS.length && (bytes = EMPTY_BYTELISTS[index]) != null) {
            return bytes;
        }
        return prepareEmptyByteList(enc);
    }

    private static ByteList prepareEmptyByteList(Encoding enc) {
        int index = enc.getIndex();
        if (index >= EMPTY_BYTELISTS.length) {
            ByteList tmp[] = new ByteList[index + 4];
            System.arraycopy(EMPTY_BYTELISTS,0, tmp, 0, EMPTY_BYTELISTS.length);
        }
        return EMPTY_BYTELISTS[index] = new ByteList(ByteList.NULL_ARRAY, enc);
    }

    public static RubyString newEmptyString(Ruby runtime, RubyClass metaClass, Encoding enc) {
        RubyString empty = new RubyString(runtime, metaClass, getEmptyByteList(enc));
        empty.shareLevel = SHARE_LEVEL_BYTELIST;
        return empty;
    }

    public static RubyString newEmptyString(Ruby runtime, Encoding enc) {
        return newEmptyString(runtime, runtime.getString(), enc);
    }

    @Override
    public int getNativeTypeIndex() {
        return ClassIndex.STRING;
    }

    @Override
    public Class getJavaClass() {
        return String.class;
    }

    @Override
    public RubyString convertToString() {
        return this;
    }

    @Override
    public String toString() {
        return value.toString();
    }

    /** rb_str_dup
     * 
     */
    @Deprecated
    public final RubyString strDup() {
        return strDup(getRuntime(), getMetaClass());
    }
    
    public final RubyString strDup(Ruby runtime) {
        return strDup(runtime, getMetaClass());
    }
    
    @Deprecated
    final RubyString strDup(RubyClass clazz) {
        return strDup(getRuntime(), getMetaClass());
    }

    final RubyString strDup(Ruby runtime, RubyClass clazz) {
        shareLevel = SHARE_LEVEL_BYTELIST;
        RubyString dup = new RubyString(runtime, clazz, value);
        dup.shareLevel = SHARE_LEVEL_BYTELIST;

        dup.infectBy(this);
        return dup;
    }

    public final RubyString makeShared(Ruby runtime, int index, int len) {
        if (len == 0) {
            RubyString s = newEmptyString(runtime, getMetaClass());
            s.infectBy(this);
            return s;
        }

        if (shareLevel == SHARE_LEVEL_NONE) shareLevel = SHARE_LEVEL_BUFFER;
        RubyString shared = new RubyString(runtime, getMetaClass(), value.makeShared(index, len));
        shared.shareLevel = SHARE_LEVEL_BUFFER;

        shared.infectBy(this);
        return shared;
    }

    final void modifyCheck() {
        if ((flags & FROZEN_F) != 0) throw getRuntime().newFrozenError("string");

        if (!isTaint() && getRuntime().getSafeLevel() >= 4) {
            throw getRuntime().newSecurityError("Insecure: can't modify string");
        }
    }
    
    private final void modifyCheck(byte[] b, int len) {
        if (value.bytes != b || value.realSize != len) throw getRuntime().newRuntimeError("string modified");
    }
    
    private final void frozenCheck() {
        if (isFrozen()) throw getRuntime().newRuntimeError("string frozen");
    }

    /** rb_str_modify
     * 
     */
    public final void modify() {
        modifyCheck();

        if (shareLevel != SHARE_LEVEL_NONE) {
            if (shareLevel == SHARE_LEVEL_BYTELIST) {
                value = value.dup();
            } else {
                value.unshare();
            }
            shareLevel = SHARE_LEVEL_NONE;
        }

        value.invalidate();
    }

    /** rb_str_modify (with length bytes ensured)
     * 
     */    
    public final void modify(int length) {
        modifyCheck();

        if (shareLevel != SHARE_LEVEL_NONE) {
            if (shareLevel == SHARE_LEVEL_BYTELIST) {
                value = value.dup(length);
            } else {
                value.unshare(length);
            }
            shareLevel = SHARE_LEVEL_NONE;
        } else {
            value.ensure(length);
        }

        value.invalidate();
    }
    
    private final void view(ByteList bytes) {
        modifyCheck();

        value = bytes;
        shareLevel = SHARE_LEVEL_NONE;
    }

    private final void view(byte[]bytes) {
        modifyCheck();        

        value.replace(bytes);
        shareLevel = SHARE_LEVEL_NONE;

        value.invalidate();        
    }

    private final void view(int index, int len) {
        modifyCheck();

        if (shareLevel != SHARE_LEVEL_NONE) {
            if (shareLevel == SHARE_LEVEL_BYTELIST) {
                // if len == 0 then shared empty
                value = value.makeShared(index, len);
                shareLevel = SHARE_LEVEL_BUFFER;
            } else {
                value.view(index, len);
            }
        } else {        
            value.view(index, len);
            // FIXME this below is temporary, but its much safer for COW (it prevents not shared Strings with begin != 0)
            // this allows now e.g.: ByteList#set not to be begin aware
            shareLevel = SHARE_LEVEL_BUFFER;
        }

        value.invalidate();
    }

    public static String bytesToString(byte[] bytes, int beg, int len) {
        return new String(ByteList.plain(bytes, beg, len));
    }

    public static String byteListToString(ByteList bytes) {
        return bytesToString(bytes.unsafeBytes(), bytes.begin(), bytes.length());
    }

    public static String bytesToString(byte[] bytes) {
        return bytesToString(bytes, 0, bytes.length);
    }

    public static byte[] stringToBytes(String string) {
        return ByteList.plain(string);
    }

    public static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    public static boolean isUpper(int c) {
        return c >= 'A' && c <= 'Z';
    }

    public static boolean isLower(int c) {
        return c >= 'a' && c <= 'z';
    }

    public static boolean isLetter(int c) {
        return isUpper(c) || isLower(c);
    }

    public static boolean isAlnum(int c) {
        return isUpper(c) || isLower(c) || isDigit(c);
    }

    public static boolean isPrint(int c) {
        return c >= 0x20 && c <= 0x7E;
    }

    @Override
    public RubyString asString() {
        return this;
    }

    @Override
    public IRubyObject checkStringType() {
        return this;
    }

    @JRubyMethod(name = {"to_s", "to_str"})
    @Override
    public IRubyObject to_s() {
        Ruby runtime = getRuntime();
        if (getMetaClass().getRealClass() != runtime.getString()) {
            return strDup(runtime, runtime.getString());
        }
        return this;
    }

    /* rb_str_cmp_m */
    @JRubyMethod(name = "<=>", required = 1)
    public IRubyObject op_cmp(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyString) {
            return context.getRuntime().newFixnum(op_cmp((RubyString)other));
        }

        // deal with case when "other" is not a string
        if (other.respondsTo("to_str") && other.respondsTo("<=>")) {
            IRubyObject result = other.callMethod(context, "<=>", this);

            if (result instanceof RubyNumeric) {
                return ((RubyNumeric) result).op_uminus(context);
            }
        }

        return context.getRuntime().getNil();
    }
        
    /**
     * 
     */
    @JRubyMethod(name = "==", required = 1)
    @Override
    public IRubyObject op_equal(ThreadContext context, IRubyObject other) {
        Ruby runtime = context.getRuntime();
        
        if (this == other) return runtime.getTrue();
        
        if (!(other instanceof RubyString)) {
            if (!other.respondsTo("to_str")) return runtime.getFalse();

            return other.callMethod(context, "==", this).isTrue() ? runtime.getTrue() : runtime.getFalse();
        }
        return value.equal(((RubyString)other).value) ? runtime.getTrue() : runtime.getFalse();
    }

    @JRubyMethod(name = "+", required = 1)
    public IRubyObject op_plus(ThreadContext context, IRubyObject other) {
        RubyString str = other.convertToString();
        
        ByteList result = new ByteList(value.realSize + str.value.realSize);
        result.realSize = value.realSize + str.value.realSize;
        System.arraycopy(value.bytes, value.begin, result.bytes, 0, value.realSize);
        System.arraycopy(str.value.bytes, str.value.begin, result.bytes, value.realSize, str.value.realSize);
      
        RubyString resultStr = newString(context.getRuntime(), result);
        if (isTaint() || str.isTaint()) resultStr.setTaint(true);
        return resultStr;
    }

    @JRubyMethod(name = "*", required = 1)
    public IRubyObject op_mul(ThreadContext context, IRubyObject other) {
        RubyInteger otherInteger = (RubyInteger) other.convertToInteger();
        long len = otherInteger.getLongValue();

        if (len < 0) throw context.getRuntime().newArgumentError("negative argument");

        // we limit to int because ByteBuffer can only allocate int sizes
        if (len > 0 && Integer.MAX_VALUE / len < value.length()) {
            throw context.getRuntime().newArgumentError("argument too big");
        }
        ByteList newBytes = new ByteList(value.length() * (int)len);

        for (int i = 0; i < len; i++) {
            newBytes.append(value);
        }

        RubyString newString = new RubyString(context.getRuntime(), getMetaClass(), newBytes);
        newString.setTaint(isTaint());
        return newString;
    }

    @JRubyMethod(name = "%", required = 1)
    public IRubyObject op_format(ThreadContext context, IRubyObject arg) {
        final RubyString s;

        IRubyObject tmp = arg.checkArrayType();
        if (tmp.isNil()) {
            tmp = arg;
        }

        // FIXME: Should we make this work with platform's locale,
        // or continue hardcoding US?
        s = Sprintf.sprintf(context.getRuntime(), Locale.US, value, tmp);

        s.infectBy(this);
        return s;
    }

    @JRubyMethod(name = "hash")
    @Override
    public RubyFixnum hash() {
        return getRuntime().newFixnum(value.hashCode());
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;

        if (other instanceof RubyString) {
            RubyString string = (RubyString) other;

            if (string.value.equal(value)) return true;
        }

        return false;
    }

    /** rb_obj_as_string
     *
     */
    public static RubyString objAsString(ThreadContext context, IRubyObject obj) {
        if (obj instanceof RubyString) return (RubyString) obj;

        IRubyObject str = obj.callMethod(context, "to_s");

        if (!(str instanceof RubyString)) return (RubyString) obj.anyToString();

        if (obj.isTaint()) str.setTaint(true);

        return (RubyString) str;
    }

    /** rb_str_cmp
     *
     */
    public int op_cmp(RubyString other) {
        return value.cmp(other.value);
    }

    /** rb_to_id
     *
     */
    @Override
    public String asJavaString() {
        // TODO: This used to intern; but it didn't appear to change anything
        // turning that off, and it's unclear if it was needed. Plus, we intern
        // 
        return toString();
    }

    public IRubyObject doClone(){
        return newString(getRuntime(), value.dup());
    }

    public RubyString cat(byte[] str) {
        modify(value.realSize + str.length);
        System.arraycopy(str, 0, value.bytes, value.begin + value.realSize, str.length);
        value.realSize += str.length;
        return this;
    }

    public RubyString cat(byte[] str, int beg, int len) {
        modify(value.realSize + len);        
        System.arraycopy(str, beg, value.bytes, value.begin + value.realSize, len);
        value.realSize += len;
        return this;
    }

    public RubyString cat(ByteList str) {
        modify(value.realSize + str.realSize);
        System.arraycopy(str.bytes, str.begin, value.bytes, value.begin + value.realSize, str.realSize);
        value.realSize += str.realSize;
        return this;
    }

    public RubyString cat(byte ch) {
        modify(value.realSize + 1);        
        value.bytes[value.begin + value.realSize] = ch;
        value.realSize++;
        return this;
    }
    
    /** rb_str_replace_m
     *
     */
    @JRubyMethod(name = {"replace", "initialize_copy"}, required = 1)
    public RubyString replace(IRubyObject other) {
        if (this == other) return this;

        modifyCheck();

        RubyString otherStr =  stringValue(other);

        otherStr.shareLevel = shareLevel = SHARE_LEVEL_BYTELIST;

        value = otherStr.value;

        infectBy(other);
        return this;
    }

    @JRubyMethod(name = "reverse")
    public RubyString reverse(ThreadContext context) {
        if (value.length() <= 1) return strDup(context.getRuntime());

        ByteList buf = new ByteList(value.length()+2);
        buf.realSize = value.length();
        int src = value.length() - 1;
        int dst = 0;
        
        while (src >= 0) buf.set(dst++, value.get(src--));

        RubyString rev = new RubyString(context.getRuntime(), getMetaClass(), buf);
        rev.infectBy(this);
        return rev;
    }

    @JRubyMethod(name = "reverse!")
    public RubyString reverse_bang() {
        if (value.length() > 1) {
            modify();
            for (int i = 0; i < (value.length() / 2); i++) {
                byte b = (byte) value.get(i);
                
                value.set(i, value.get(value.length() - i - 1));
                value.set(value.length() - i - 1, b);
            }
        }
        
        return this;
    }

    /** rb_str_s_new
     *
     */
    public static RubyString newInstance(IRubyObject recv, IRubyObject[] args, Block block) {
        RubyString newString = newStringShared(recv.getRuntime(), ByteList.EMPTY_BYTELIST);
        newString.setMetaClass((RubyClass) recv);
        newString.callInit(args, block);
        return newString;
    }
    
    /**
     * Variable-arity version for compatibility. Not bound to Ruby.
     * @deprecated Use the versions with zero or one arguments
     */
    public IRubyObject initialize(IRubyObject[] args, Block unusedBlock) {
        switch (args.length) {
        case 0:
            return this;
        case 1:
            return initialize(args[0]);
        default:
            Arity.raiseArgumentError(getRuntime(), args.length, 0, 1);
            return null; // not reached
        }
    }

    @JRubyMethod(frame = true, visibility = Visibility.PRIVATE)
    @Override
    public IRubyObject initialize() {
        return this;
    }

    @JRubyMethod(frame = true, visibility = Visibility.PRIVATE)
    public IRubyObject initialize(IRubyObject arg0) {
        replace(arg0);

        return this;
    }

    @JRubyMethod
    public IRubyObject casecmp(IRubyObject other) {
        int compare = value.caseInsensitiveCmp(stringValue(other).value);
        return RubyFixnum.newFixnum(getRuntime(), compare);
    }

    /** rb_str_match
     *
     */
    @JRubyMethod(name = "=~")
    @Override
    public IRubyObject op_match(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyRegexp) return ((RubyRegexp) other).op_match(context, this);
        if (other instanceof RubyString) {
            throw context.getRuntime().newTypeError("type mismatch: String given");
        }
        return other.callMethod(context, "=~", this);
    }

    /**
     * String#match(pattern)
     *
     * rb_str_match_m
     *
     * @param pattern Regexp or String
     */
    @JRubyMethod
    public IRubyObject match(ThreadContext context, IRubyObject pattern) {
        return getPattern(pattern, false).callMethod(context, "match", this);
    }

    /** rb_str_capitalize
     *
     */
    @JRubyMethod
    public IRubyObject capitalize(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.capitalize_bang(context);
        return str;
    }

    /** rb_str_capitalize_bang
     *
     */
    @JRubyMethod(name = "capitalize!")
    public IRubyObject capitalize_bang(ThreadContext context) {        
        if (value.realSize == 0) {
            modifyCheck();
            return context.getRuntime().getNil();
        }
        
        modify();
        
        int s = value.begin;
        int send = s + value.realSize;
        byte[]buf = value.bytes;
        
        
        
        boolean modify = false;
        
        int c = buf[s] & 0xff;
        if (ASCII.isLower(c)) {
            buf[s] = (byte)ASCIIEncoding.asciiToUpper(c);
            modify = true;
        }
        
        while (++s < send) {
            c = (char)(buf[s] & 0xff);
            if (ASCII.isUpper(c)) {
                buf[s] = (byte)ASCIIEncoding.asciiToLower(c);
                modify = true;
            }
        }
        
        if (modify) return this;
        return context.getRuntime().getNil();
    }

    @JRubyMethod(name = ">=")
    public IRubyObject op_ge(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyString) {
            return context.getRuntime().newBoolean(op_cmp((RubyString) other) >= 0);
        }

        return RubyComparable.op_ge(context, this, other);
    }

    @JRubyMethod(name = ">")
    public IRubyObject op_gt(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyString) {
            return context.getRuntime().newBoolean(op_cmp((RubyString) other) > 0);
        }

        return RubyComparable.op_gt(context, this, other);
    }

    @JRubyMethod(name = "<=")
    public IRubyObject op_le(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyString) {
            return context.getRuntime().newBoolean(op_cmp((RubyString) other) <= 0);
        }

        return RubyComparable.op_le(context, this, other);
    }

    @JRubyMethod(name = "<")
    public IRubyObject op_lt(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyString) {
            return context.getRuntime().newBoolean(op_cmp((RubyString) other) < 0);
        }

        return RubyComparable.op_lt(context, this, other);
    }

    @JRubyMethod(name = "eql?")
    public IRubyObject str_eql_p(ThreadContext context, IRubyObject other) {
        if (!(other instanceof RubyString)) return context.getRuntime().getFalse();
        RubyString otherString = (RubyString)other;
        return value.equal(otherString.value) ? context.getRuntime().getTrue() : context.getRuntime().getFalse();
    }

    /** rb_str_upcase
     *
     */
    @JRubyMethod
    public RubyString upcase(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.upcase_bang(context);
        return str;
    }

    /** rb_str_upcase_bang
     *
     */
    @JRubyMethod(name = "upcase!")
    public IRubyObject upcase_bang(ThreadContext context) {
        if (value.realSize == 0) {
            modifyCheck();
            return context.getRuntime().getNil();
        }

        modify();

        int s = value.begin;
        int send = s + value.realSize;
        byte []buf = value.bytes;

        boolean modify = false;
        while (s < send) {
            int c = buf[s] & 0xff;
            if (ASCII.isLower(c)) {
                buf[s] = (byte)ASCIIEncoding.asciiToUpper(c);
                modify = true;
            }
            s++;
        }

        if (modify) return this;
        return context.getRuntime().getNil();        
    }

    /** rb_str_downcase
     *
     */
    @JRubyMethod
    public RubyString downcase(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.downcase_bang(context);
        return str;
    }

    /** rb_str_downcase_bang
     *
     */
    @JRubyMethod(name = "downcase!")
    public IRubyObject downcase_bang(ThreadContext context) {
        if (value.realSize == 0) {
            modifyCheck();
            return context.getRuntime().getNil();
        }
        
        modify();
        
        int s = value.begin;
        int send = s + value.realSize;
        byte []buf = value.bytes;
        
        boolean modify = false;
        while (s < send) {
            int c = buf[s] & 0xff;
            if (ASCII.isUpper(c)) {
                buf[s] = (byte)ASCIIEncoding.asciiToLower(c);
                modify = true;
            }
            s++;
        }
        
        if (modify) return this;
        return context.getRuntime().getNil();
    }

    /** rb_str_swapcase
     *
     */
    @JRubyMethod
    public RubyString swapcase(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.swapcase_bang(context);
        return str;
    }

    /** rb_str_swapcase_bang
     *
     */
    @JRubyMethod(name = "swapcase!")
    public IRubyObject swapcase_bang(ThreadContext context) {
        if (value.realSize == 0) {
            modifyCheck();
            return context.getRuntime().getNil();        
        }
        
        modify();
        
        int s = value.begin;
        int send = s + value.realSize;
        byte[]buf = value.bytes;
        
        boolean modify = false;
        while (s < send) {
            int c = buf[s] & 0xff;
            if (ASCII.isUpper(c)) {
                buf[s] = (byte)ASCIIEncoding.asciiToLower(c);
                modify = true;
            } else if (ASCII.isLower(c)) {
                buf[s] = (byte)ASCIIEncoding.asciiToUpper(c);
                modify = true;
            }
            s++;
        }

        if (modify) return this;
        return context.getRuntime().getNil();
    }

    /** rb_str_dump
     *
     */
    @JRubyMethod
    public IRubyObject dump() {
        RubyString s = new RubyString(getRuntime(), getMetaClass(), inspectIntoByteList(true));
        s.infectBy(this);
        return s;
    }

    @JRubyMethod
    public IRubyObject insert(ThreadContext context, IRubyObject indexArg, IRubyObject stringArg) {
        // MRI behavior: first check for ability to convert to String...
        RubyString s = (RubyString)stringArg.convertToString();
        ByteList insert = s.value;

        // ... and then the index
        int index = (int) indexArg.convertToInteger().getLongValue();
        if (index < 0) index += value.length() + 1;

        if (index < 0 || index > value.length()) {
            throw context.getRuntime().newIndexError("index " + index + " out of range");
        }

        modify();

        value.unsafeReplace(index, 0, insert);
        this.infectBy(s);
        return this;
    }

    /** rb_str_inspect
     *
     */
    @JRubyMethod
    @Override
    public IRubyObject inspect() {
        RubyString s = getRuntime().newString(inspectIntoByteList(false));
        s.infectBy(this);
        return s;
    }
    
    private ByteList inspectIntoByteList(boolean ignoreKCode) {
        Ruby runtime = getRuntime();
        Encoding enc = runtime.getKCode().getEncoding();
        final int length = value.length();
        ByteList sb = new ByteList(length + 2 + length / 100);

        sb.append('\"');

        for (int i = 0; i < length; i++) {
            int c = value.get(i) & 0xFF;
            
            if (!ignoreKCode) {
                int seqLength = enc.length((byte)c);
                
                if (seqLength > 1 && (i + seqLength -1 < length)) {
                    // don't escape multi-byte characters, leave them as bytes
                    sb.append(value, i, seqLength);
                    i += seqLength - 1;
                    continue;
                }
            } 
            
            if (isAlnum(c)) {
                sb.append((char)c);
            } else if (c == '\"' || c == '\\') {
                sb.append('\\').append((char)c);
            } else if (c == '#' && isEVStr(i, length)) {
                sb.append('\\').append((char)c);
            } else if (isPrint(c)) {
                sb.append((char)c);
            } else if (c == '\n') {
                sb.append('\\').append('n');
            } else if (c == '\r') {
                sb.append('\\').append('r');
            } else if (c == '\t') {
                sb.append('\\').append('t');
            } else if (c == '\f') {
                sb.append('\\').append('f');
            } else if (c == '\u000B') {
                sb.append('\\').append('v');
            } else if (c == '\u0007') {
                sb.append('\\').append('a');
            } else if (c == '\u0008') {
                sb.append('\\').append('b');
            } else if (c == '\u001B') {
                sb.append('\\').append('e');
            } else {
                sb.append(ByteList.plain(Sprintf.sprintf(runtime,"\\%03o",c)));
            }
        }

        sb.append('\"');
        return sb;
    }
    
    private boolean isEVStr(int i, int length) {
        if (i+1 >= length) return false;
        int c = value.get(i+1) & 0xFF;
        
        return c == '$' || c == '@' || c == '{';
    }

    /** rb_str_length
     *
     */
    @JRubyMethod(name = {"length", "size"}, compat = CompatVersion.RUBY1_8)
    public RubyFixnum length() {
        return getRuntime().newFixnum(value.realSize);
    }

    @JRubyMethod(name = {"length", "size"}, compat = CompatVersion.RUBY1_9)
    public RubyFixnum length19() {
        return getRuntime().newFixnum(strLength());
    }

    @JRubyMethod(name = "bytesize", compat = CompatVersion.RUBY1_9)
    public RubyFixnum bytesize() {
        return length(); // use 1.8 impl
    }
    /** rb_str_empty
     *
     */
    @JRubyMethod(name = "empty?")
    public RubyBoolean empty_p(ThreadContext context) {
        return isEmpty() ? context.getRuntime().getTrue() : context.getRuntime().getFalse();
    }

    public boolean isEmpty() {
        return value.length() == 0;
    }

    /** rb_str_append
     *
     */
    public RubyString append(IRubyObject other) {
        infectBy(other);
        return cat(stringValue(other).value);
    }

    /** rb_str_concat
     *
     */
    @JRubyMethod(name = {"concat", "<<"})
    public RubyString concat(IRubyObject other) {
        if (other instanceof RubyFixnum) {
            long value = ((RubyFixnum) other).getLongValue();
            if (value >= 0 && value < 256) return cat((byte) value);
        }
        return append(other);
    }

    /** rb_str_crypt
     *
     */
    @JRubyMethod(name = "crypt")
    public RubyString crypt(ThreadContext context, IRubyObject other) {
        ByteList salt = stringValue(other).getByteList();
        if (salt.realSize < 2) {
            throw context.getRuntime().newArgumentError("salt too short(need >=2 bytes)");
        }

        salt = salt.makeShared(0, 2);
        RubyString s = RubyString.newStringShared(context.getRuntime(), JavaCrypt.crypt(salt, this.getByteList()));
        s.infectBy(this);
        s.infectBy(other);
        return s;
    }

    /* RubyString aka rb_string_value */
    public static RubyString stringValue(IRubyObject object) {
        return (RubyString) (object instanceof RubyString ? object :
            object.convertToString());
    }

    /**
     * Variable-arity version for compatibility. Not bound to Ruby.
     * @deprecated Use the versions with one or two args.
     */
    public IRubyObject sub(ThreadContext context, IRubyObject[] args, Block block) {
        RubyString str = strDup(context.getRuntime());
        str.sub_bang(context, args, block);
        return str;
    }

    /** rb_str_sub
     *
     */
    @JRubyMethod(name = "sub", frame = true)
    public IRubyObject sub(ThreadContext context, IRubyObject arg0, Block block) {
        RubyString str = strDup(context.getRuntime());
        str.sub_bang(context, arg0, block);
        return str;
    }

    /** rb_str_sub
     *
     */
    @JRubyMethod(name = "sub", frame = true)
    public IRubyObject sub(ThreadContext context, IRubyObject arg0, IRubyObject arg1, Block block) {
        RubyString str = strDup(context.getRuntime());
        str.sub_bang(context, arg0, arg1, block);
        return str;
    }

    /**
     * Variable-arity version for compatibility. Not bound to Ruby.
     * @deprecated Use the versions with one or two arguments.
     */
    public IRubyObject sub_bang(ThreadContext context, IRubyObject[] args, Block block) {
        switch (args.length) {
        case 1:
            return sub_bang(context, args[0], block);
        case 2:
            return sub_bang(context, args[0], args[1], block);
        default:
            Arity.raiseArgumentError(context.getRuntime(), args.length, 1, 2);
            return null; // not reached
        }
    }

    /** rb_str_sub_bang
     *
     */
    @JRubyMethod(name = "sub!", frame = true, reads = BACKREF, writes = BACKREF)
    public IRubyObject sub_bang(ThreadContext context, IRubyObject arg0, Block block) {
        if (block.isGiven()) {
            return subBangIter(context, getPattern(arg0, true), block);
        } else {
            throw context.getRuntime().newArgumentError("wrong number of arguments (1 for 2)");
        }
    }

    /** rb_str_sub_bang
     *
     */
    @JRubyMethod(name = "sub!", frame = true, reads = BACKREF, writes = BACKREF)
    public IRubyObject sub_bang(ThreadContext context, IRubyObject arg0, IRubyObject arg1, Block block) {
        return subBangNoIter(context, getPattern(arg0, true), arg1.convertToString());
    }

    private IRubyObject subBangIter(ThreadContext context, RubyRegexp rubyRegex, Block block) {
        Regex regex = rubyRegex.getPattern();
        int range = value.begin + value.realSize;
        Matcher matcher = regex.matcher(value.bytes, value.begin, range);

        Frame frame = context.getPreviousFrame();
        if (matcher.search(value.begin, range, Option.NONE) >= 0) {
            byte[] bytes = value.bytes;
            int size = value.realSize;
            RubyMatchData match = rubyRegex.updateBackRef(context, this, frame, matcher);
            match.use();
            final RubyString repl;
            if (regex.numberOfCaptures() == 0) {
                repl = objAsString(context, block.yield(context, substr(matcher.getBegin(), matcher.getEnd() - matcher.getBegin())));
            } else {
                Region region = matcher.getRegion();
                repl = objAsString(context, block.yield(context, substr(region.beg[0], region.end[0] - region.beg[0])));
            }
            modifyCheck(bytes, size);
            frozenCheck();
            frame.setBackRef(match);            
            return subBangCommon(context, regex, matcher, repl, false);
        } else {
            return frame.setBackRef(context.getRuntime().getNil());
        }
    }

    private IRubyObject subBangNoIter(ThreadContext context, RubyRegexp rubyRegex, RubyString repl) {
        boolean tained = repl.isTaint();
        Regex regex = rubyRegex.getPattern();
        int range = value.begin + value.realSize;
        Matcher matcher = regex.matcher(value.bytes, value.begin, range);

        Frame frame = context.getPreviousFrame();
        if (matcher.search(value.begin, range, Option.NONE) >= 0) {
            repl = rubyRegex.regsub(repl, this, matcher);
            rubyRegex.updateBackRef(context, this, frame, matcher);
            return subBangCommon(context, regex, matcher, repl, tained);
        } else {
            return frame.setBackRef(context.getRuntime().getNil());
        }
    }

    private IRubyObject subBangCommon(ThreadContext context, Regex regex, Matcher matcher, RubyString repl, boolean tainted) {
        final int beg;
        final int plen;
        if (regex.numberOfCaptures() == 0) {
            beg = matcher.getBegin();
            plen = matcher.getEnd() - beg;
        } else {
            Region region = matcher.getRegion();
            beg = region.beg[0];
            plen = region.end[0] - beg;
        }

        ByteList replValue = repl.value;
        if (replValue.realSize > plen) {
            modify(value.realSize + replValue.realSize - plen);
        } else {
            modify();
        }
        if (repl.isTaint()) tainted = true;

        if (replValue.realSize != plen) {
            int src = value.begin + beg + plen;
            int dst = value.begin + beg + replValue.realSize;
            int length = value.realSize - beg - plen;
            System.arraycopy(value.bytes, src, value.bytes, dst, length);
        }
        System.arraycopy(replValue.bytes, replValue.begin, value.bytes, value.begin + beg, replValue.realSize);
        value.realSize += replValue.realSize - plen;
        if (tainted) setTaint(true);
        return this;
    }

    /**
     * Variable-arity version for compatibility. Not bound to Ruby.
     * @deprecated Use the versions with one or two arguments.
     */
    public IRubyObject gsub(ThreadContext context, IRubyObject[] args, Block block) {
        switch (args.length) {
        case 1:
            return gsub(context, args[0], block);
        case 2:
            return gsub(context, args[0], args[1], block);
        default:
            Arity.raiseArgumentError(context.getRuntime(), args.length, 1, 2);
            return null; // not reached
        }
    }
    
    /** rb_str_gsub
     *
     */
    @JRubyMethod(name = "gsub", frame = true, reads = BACKREF, writes = BACKREF)
    public IRubyObject gsub(ThreadContext context, IRubyObject arg0, Block block) {
        return gsub(context, arg0, block, false);
    }
    
    /** rb_str_gsub
     *
     */
    @JRubyMethod(name = "gsub", frame = true, reads = BACKREF, writes = BACKREF)
    public IRubyObject gsub(ThreadContext context, IRubyObject arg0, IRubyObject arg1, Block block) {
        return gsub(context, arg0, arg1, block, false);
    }

    /**
     * Variable-arity version for compatibility. Not bound to Ruby.
     * @deprecated Use the versions with one or two arguments.
     */
    public IRubyObject gsub_bang(ThreadContext context, IRubyObject[] args, Block block) {
        switch (args.length) {
        case 1:
            return gsub_bang(context, args[0], block);
        case 2:
            return gsub_bang(context, args[0], args[1], block);
        default:
            Arity.raiseArgumentError(context.getRuntime(), args.length, 1, 2);
            return null; // not reached
        }
    }

    /** rb_str_gsub_bang
     *
     */
    @JRubyMethod(name = "gsub!", frame = true, reads = BACKREF, writes = BACKREF)
    public IRubyObject gsub_bang(ThreadContext context, IRubyObject arg0, Block block) {
        return gsub(context, arg0, block, true);
    }

    /** rb_str_gsub_bang
     *
     */
    @JRubyMethod(name = "gsub!", frame = true, reads = BACKREF, writes = BACKREF)
    public IRubyObject gsub_bang(ThreadContext context, IRubyObject arg0, IRubyObject arg1, Block block) {
        return gsub(context, arg0, arg1, block, true);
    }

    private final IRubyObject gsub(ThreadContext context, IRubyObject arg0, Block block, final boolean bang) {
        if (block.isGiven()) {
            return gsubCommon(context, bang, getPattern(arg0, true), block, null, false);
        } else {
            throw context.getRuntime().newArgumentError("wrong number of arguments (1 for 2)");
        }
    }

    private final IRubyObject gsub(ThreadContext context, IRubyObject arg0, IRubyObject arg1, Block block, final boolean bang) {
        RubyString repl = arg1.convertToString();
        return gsubCommon(context, bang, getPattern(arg0, true), block, repl, repl.isTaint());
    }

    private IRubyObject gsubCommon(ThreadContext context, final boolean bang, RubyRegexp rubyRegex, Block block, RubyString repl, boolean tainted) {
        Ruby runtime = context.getRuntime();
        Regex regex = rubyRegex.getPattern();
        Frame frame = context.getPreviousFrame();

        int begin = value.begin;
        int range = begin + value.realSize;
        Matcher matcher = regex.matcher(value.bytes, begin, range);

        int beg = matcher.search(begin, range, Option.NONE);
        if (beg < 0) {
            frame.setBackRef(runtime.getNil());
            return bang ? runtime.getNil() : strDup(runtime); /* bang: true, no match, no substitution */
        }

        int blen = value.realSize + 30; /* len + margin */
        ByteList dest = new ByteList(blen);
        dest.realSize = blen;
        int offset = 0, buf = 0, bp = 0, cp = begin;

        RubyMatchData match = null;
        while (beg >= 0) {
            final RubyString val;
            final int begz;
            final int endz;
            if (regex.numberOfCaptures() == 0) {
                begz = matcher.getBegin();
                endz = matcher.getEnd();
            } else {
                Region region = matcher.getRegion();
                begz = region.beg[0];
                endz = region.end[0];
            }

            if (repl == null) { // block given
                byte[] bytes = value.bytes;
                int size = value.realSize;
                match = rubyRegex.updateBackRef(context, this, frame, matcher);
                match.use();
                val = objAsString(context, block.yield(context, substr(runtime, begz, endz - begz)));
                modifyCheck(bytes, size);
                if (bang) frozenCheck();
            } else {
                val = rubyRegex.regsub(repl, this, matcher);
            }

            if (val.isTaint()) tainted = true;

            ByteList vbuf = val.value;
            int len = (bp - buf) + (beg - offset) + vbuf.realSize + 3;
            if (blen < len) {
                while (blen < len) {
                    blen <<= 1;
                }
                len = bp - buf;
                dest.realloc(blen);
                dest.realSize = blen;
                bp = buf + len;
            }
            len = beg - offset; /* copy pre-match substr */
            System.arraycopy(value.bytes, cp, dest.bytes, bp, len);
            bp += len;
            System.arraycopy(vbuf.bytes, vbuf.begin, dest.bytes, bp, vbuf.realSize);
            bp += vbuf.realSize;
            offset = endz;

            if (begz == endz) {
                if (value.realSize <= endz) {
                    break;
                }
                len = regex.getEncoding().length(value.bytes, begin + endz, range);
                System.arraycopy(value.bytes, begin + endz, dest.bytes, bp, len);
                bp += len;
                offset = endz + len;
            }
            cp = begin + offset;
            if (offset > value.realSize) {
                break;
            }
            beg = matcher.search(cp, range, Option.NONE);
        }

        if (value.realSize > offset) {
            int len = bp - buf;
            if (blen - len < value.realSize - offset) {
                blen = len + value.realSize - offset;
                dest.realloc(blen);
                bp = buf + len;
            }
            System.arraycopy(value.bytes, cp, dest.bytes, bp, value.realSize - offset);
            bp += value.realSize - offset;
        }

        if (match != null) {
            frame.setBackRef(match);
        } else {
            rubyRegex.updateBackRef(context, this, frame, matcher);
        }

        dest.realSize = bp - buf;
        if (bang) {
            view(dest);
            if (tainted) setTaint(true);
            return this;
        } else {
            RubyString destStr = new RubyString(runtime, getMetaClass(), dest);
            destStr.infectBy(this);
            if (tainted) destStr.setTaint(true);
            return destStr;
        }
    }

    /**
     * Variable-arity version for compatibility. Not bound to Ruby.
     * @deprecated Use the versions with one or two args.
     */
    public IRubyObject index(ThreadContext context, IRubyObject[] args) {
        switch (args.length) {
        case 1:
            return index(context, args[0]);
        case 2:
            return index(context, args[0], args[1]);
        default:
            Arity.raiseArgumentError(context.getRuntime(), args.length, 1, 2);
            return null; // not reached
        }
    }

    /** rb_str_index_m
     *
     */
    @JRubyMethod(reads = BACKREF, writes = BACKREF)
    public IRubyObject index(ThreadContext context, IRubyObject arg0) {
        return indexCommon(0, arg0, context);
    }

    /** rb_str_index_m
     *
     */
    @JRubyMethod(reads = BACKREF, writes = BACKREF)
    public IRubyObject index(ThreadContext context, IRubyObject arg0, IRubyObject arg1) {
        int pos = RubyNumeric.num2int(arg1);

        if (pos < 0) {
            pos += value.realSize;
            if (pos < 0) {
                if (arg0 instanceof RubyRegexp) {
                    context.getPreviousFrame().setBackRef(context.getRuntime().getNil());
                }
                return context.getRuntime().getNil();
            }
        }

        return indexCommon(pos, arg0, context);
    }

    private IRubyObject indexCommon(int pos, IRubyObject sub, ThreadContext context) throws RaiseException {
        if (sub instanceof RubyRegexp) {
            RubyRegexp regSub = (RubyRegexp) sub;

            pos = regSub.adjustStartPos(this, pos, false);
            pos = regSub.search(context, this, pos, false);
        } else if (sub instanceof RubyFixnum) {
            int c_int = RubyNumeric.fix2int(sub);
            if (c_int < 0x00 || c_int > 0xFF) {
                // out of byte range
                // there will be no match for sure
                return context.getRuntime().getNil();
            }
            byte c = (byte) c_int;
            byte[] bytes = value.bytes;
            int end = value.begin + value.realSize;

            pos += value.begin;
            for (; pos < end; pos++) {
                if (bytes[pos] == c) {
                    return RubyFixnum.newFixnum(context.getRuntime(), pos - value.begin);
                }
            }
            return context.getRuntime().getNil();
        } else if (sub instanceof RubyString) {
            pos = strIndex((RubyString) sub, pos);
        } else {
            IRubyObject tmp = sub.checkStringType();

            if (tmp.isNil()) {
                throw context.getRuntime().newTypeError("type mismatch: " + sub.getMetaClass().getName() + " given");
            }
            pos = strIndex((RubyString) tmp, pos);
        }

        return pos == -1 ? context.getRuntime().getNil() : RubyFixnum.newFixnum(context.getRuntime(), pos);
    }
    
    private int strIndex(RubyString sub, int offset) {
        ByteList byteList = value;
        if (offset < 0) {
            offset += byteList.realSize;
            if (offset < 0) return -1;
        }
        
        ByteList other = sub.value;
        if (sizeIsSmaller(byteList, offset, other)) return -1;
        if (other.realSize == 0) return offset;
        return byteList.indexOf(other, offset);
    }
    private static boolean sizeIsSmaller(ByteList byteList, int offset, ByteList other) {
        return byteList.realSize - offset < other.realSize;
    }

    /**
     * Variable-arity version for compatibility. Not bound to Ruby.
     * @deprecated Use the versions with one or two arguments.
     */
    public IRubyObject rindex(ThreadContext context, IRubyObject[] args) {
        switch (args.length) {
        case 1:
            return rindex(context, args[0]);
        case 2:
            return rindex(context, args[0], args[1]);
        default:
            Arity.raiseArgumentError(context.getRuntime(), args.length, 1, 2);
            return null; // not reached
        }
    }

    /** rb_str_rindex_m
     *
     */
    @JRubyMethod(reads = BACKREF, writes = BACKREF)
    public IRubyObject rindex(ThreadContext context, IRubyObject arg0) {
        return rindexCommon(arg0, value.realSize, context);
    }

    /** rb_str_rindex_m
     *
     */
    @JRubyMethod(reads = BACKREF, writes = BACKREF)
    public IRubyObject rindex(ThreadContext context, IRubyObject arg0, IRubyObject arg1) {
        int pos = RubyNumeric.num2int(arg1);

        if (pos < 0) {
            pos += value.realSize;
            if (pos < 0) {
                if (arg0 instanceof RubyRegexp) { 
                    context.getPreviousFrame().setBackRef(context.getRuntime().getNil());
                }
                return context.getRuntime().getNil();
            }
        }            
        if (pos > value.realSize) pos = value.realSize;

        return rindexCommon(arg0, pos, context);
    }

    private IRubyObject rindexCommon(final IRubyObject sub, int pos, ThreadContext context) throws RaiseException {

        if (sub instanceof RubyRegexp) {
            RubyRegexp regSub = (RubyRegexp) sub;
            if (regSub.length() > 0) {
                pos = regSub.adjustStartPos(this, pos, true);
                pos = regSub.search(context, this, pos, true);
            }
            if (pos >= 0) {
                return RubyFixnum.newFixnum(context.getRuntime(), pos);
            }
        } else if (sub instanceof RubyString) {
            pos = strRindex((RubyString) sub, pos);
            if (pos >= 0) return RubyFixnum.newFixnum(context.getRuntime(), pos);
        } else if (sub instanceof RubyFixnum) {
            int c_int = RubyNumeric.fix2int(sub);
            if (c_int < 0x00 || c_int > 0xFF) {
                // out of byte range
                // there will be no match for sure
                return context.getRuntime().getNil();
            }
            byte c = (byte) c_int;

            byte[] bytes = value.bytes;
            int pbeg = value.begin;
            int p = pbeg + pos;

            if (pos == value.realSize) {
                if (pos == 0) {
                    return context.getRuntime().getNil();
                }
                --p;
            }
            while (pbeg <= p) {
                if (bytes[p] == c) {
                    return RubyFixnum.newFixnum(context.getRuntime(), p - value.begin);
                }
                p--;
            }
            return context.getRuntime().getNil();
        } else {
            IRubyObject tmp = sub.checkStringType();
            if (tmp.isNil()) throw context.getRuntime().newTypeError("type mismatch: " + sub.getMetaClass().getName() + " given");
            pos = strRindex((RubyString) tmp, pos);
            if (pos >= 0) return RubyFixnum.newFixnum(context.getRuntime(), pos);
        }

        return context.getRuntime().getNil();
    }

    private int strRindex(RubyString sub, int pos) {
        int subLength = sub.value.realSize;
        
        /* substring longer than string */
        if (value.realSize < subLength) return -1;
        if (value.realSize - pos < subLength) pos = value.realSize - subLength;

        return value.lastIndexOf(sub.value, pos);
    }
    
    /* rb_str_substr */
    public IRubyObject substr(int beg, int len) {
        return substr(getRuntime(), beg, len);
    }
    
    public IRubyObject substr(Ruby runtime, int beg, int len) {    
        int length = value.length();
        if (len < 0 || beg > length) return getRuntime().getNil();

        if (beg < 0) {
            beg += length;
            if (beg < 0) return getRuntime().getNil();
        }
        
        int end = Math.min(length, beg + len);
        return makeShared(getRuntime(), beg, end - beg);
    }
    
    

    /* rb_str_replace */
    public IRubyObject replace(int beg, int len, RubyString replaceWith) {
        if (beg + len >= value.length()) len = value.length() - beg;

        modify();
        value.unsafeReplace(beg,len,replaceWith.value);

        return infectBy(replaceWith);
    }

    /**
     * Variable-arity version for compatibility. Not bound to Ruby.
     * @deprecated Use the versions with one or two args
     */
    public IRubyObject op_aref(ThreadContext context, IRubyObject[] args) {
        switch (args.length) {
        case 1:
            return op_aref(context, args[0]);
        case 2:
            return op_aref(context, args[0], args[1]);
        default:
            Arity.raiseArgumentError(context.getRuntime(), args.length, 1, 2);
            return null; // not reached
        }
    }

    /** rb_str_aref, rb_str_aref_m
     *
     */
    @JRubyMethod(name = {"[]", "slice"}, reads = BACKREF, writes = BACKREF)
    public IRubyObject op_aref(ThreadContext context, IRubyObject arg1, IRubyObject arg2) {
        if (arg1 instanceof RubyRegexp) {
            if(((RubyRegexp)arg1).search(context, this, 0, false) >= 0) {
                return RubyRegexp.nth_match(RubyNumeric.fix2int(arg2), context.getCurrentFrame().getBackRef());
            }
            return context.getRuntime().getNil();
        }
        return substr(context.getRuntime(), RubyNumeric.fix2int(arg1), RubyNumeric.fix2int(arg2));
    }

    /** rb_str_aref, rb_str_aref_m
     *
     */
    @JRubyMethod(name = {"[]", "slice"}, reads = BACKREF, writes = BACKREF)
    public IRubyObject op_aref(ThreadContext context, IRubyObject arg) {
        if (arg instanceof RubyRegexp) {
            if(((RubyRegexp)arg).search(context, this, 0, false) >= 0) {
                return RubyRegexp.nth_match(0, context.getCurrentFrame().getBackRef());
            }
            return context.getRuntime().getNil();
        } else if (arg instanceof RubyString) {
            return value.indexOf(stringValue(arg).value) != -1 ?
                arg : context.getRuntime().getNil();
        } else if (arg instanceof RubyRange) {
            int[] begLen = ((RubyRange) arg).begLenInt(value.length(), 0);
            return begLen == null ? context.getRuntime().getNil() :
                substr(context.getRuntime(), begLen[0], begLen[1]);
        }
        int idx = (int) arg.convertToInteger().getLongValue();
        
        if (idx < 0) idx += value.length();
        if (idx < 0 || idx >= value.length()) return context.getRuntime().getNil();

        return context.getRuntime().newFixnum(value.get(idx) & 0xFF);
    }

    /**
     * rb_str_subpat_set
     *
     */
    private void subpatSet(ThreadContext context, RubyRegexp regexp, int nth, IRubyObject repl) {
        RubyMatchData match;
        int start, end, len;        
        if (regexp.search(context, this, 0, false) < 0) throw context.getRuntime().newIndexError("regexp not matched");

        match = (RubyMatchData)context.getCurrentFrame().getBackRef();

        if (match.regs == null) {
            if (nth >= 1) throw context.getRuntime().newIndexError("index " + nth + " out of regexp");
            if (nth < 0) {
                if(-nth >= 1) throw context.getRuntime().newIndexError("index " + nth + " out of regexp");
                nth += 1;
            }
            start = match.begin;
            if(start == -1) throw context.getRuntime().newIndexError("regexp group " + nth + " not matched");
            end = match.end;
        } else {
            if(nth >= match.regs.numRegs) throw context.getRuntime().newIndexError("index " + nth + " out of regexp");
            if(nth < 0) {
                if(-nth >= match.regs.numRegs) throw context.getRuntime().newIndexError("index " + nth + " out of regexp");
                nth += match.regs.numRegs;
            }
            start = match.regs.beg[nth];
            if(start == -1) throw context.getRuntime().newIndexError("regexp group " + nth + " not matched");
            end = match.regs.end[nth];
        }
        
        len = end - start;
        replace(start, len, stringValue(repl));
    }

    /**
     * Variable arity version for compatibility. Not bound to a Ruby method.
     * @deprecated Use the versions with two or three args.
     */
    public IRubyObject op_aset(ThreadContext context, IRubyObject[] args) {
        switch (args.length) {
        case 2:
            return op_aset(context, args[0], args[1]);
        case 3:
            return op_aset(context, args[0], args[1], args[2]);
        default:
            Arity.raiseArgumentError(context.getRuntime(), args.length, 2, 3);
            return null; // not reached
        }
    }

    /** rb_str_aset, rb_str_aset_m
     *
     */
    @JRubyMethod(name = "[]=", reads = BACKREF)
    public IRubyObject op_aset(ThreadContext context, IRubyObject arg0, IRubyObject arg1) {
        if (arg0 instanceof RubyFixnum || arg0.respondsTo("to_int")) { // FIXME: RubyNumeric or RubyInteger instead?
            int idx = RubyNumeric.fix2int(arg0);
            
            if (idx < 0) idx += value.length();

            if (idx < 0 || idx >= value.length()) {
                throw context.getRuntime().newIndexError("string index out of bounds");
            }
            if (arg1 instanceof RubyFixnum) {
                modify();
                value.set(idx, (byte) RubyNumeric.fix2int(arg1));
            } else {
                replace(idx, 1, stringValue(arg1));
            }
            return arg1;
        }
        if (arg0 instanceof RubyRegexp) {
            RubyString repl = stringValue(arg1);
            subpatSet(context, (RubyRegexp) arg0, 0, repl);
            return repl;
        }
        if (arg0 instanceof RubyString) {
            RubyString orig = (RubyString)arg0;
            int beg = value.indexOf(orig.value);
            if (beg < 0) throw context.getRuntime().newIndexError("string not matched");
            replace(beg, orig.value.length(), stringValue(arg1));
            return arg1;
        }
        if (arg0 instanceof RubyRange) {
            int[] begLen = ((RubyRange) arg0).begLenInt(value.realSize, 2);
            replace(begLen[0], begLen[1], stringValue(arg1));
            return arg1;
        }
        throw context.getRuntime().newTypeError("wrong argument type");
    }

    /** rb_str_aset, rb_str_aset_m
     *
     */
    @JRubyMethod(name = "[]=", reads = BACKREF)
    public IRubyObject op_aset(ThreadContext context, IRubyObject arg0, IRubyObject arg1, IRubyObject arg2) {
        if (arg0 instanceof RubyRegexp) {
            RubyString repl = stringValue(arg2);
            int nth = RubyNumeric.fix2int(arg1);
            subpatSet(context, (RubyRegexp) arg0, nth, repl);
            return repl;
        }
        RubyString repl = stringValue(arg2);
        int beg = RubyNumeric.fix2int(arg0);
        int len = RubyNumeric.fix2int(arg1);
        if (len < 0) throw context.getRuntime().newIndexError("negative length");
        int strLen = value.length();
        if (beg < 0) beg += strLen;

        if (beg < 0 || (beg > 0 && beg > strLen)) {
            throw context.getRuntime().newIndexError("string index out of bounds");
        }
        if (beg + len > strLen) len = strLen - beg;

        replace(beg, len, repl);
        return repl;
    }

    /**
     * Variable arity version for compatibility. Not bound as a Ruby method.
     * @deprecated Use the versions with one or two args.
     */
    public IRubyObject slice_bang(ThreadContext context, IRubyObject[] args) {
        switch (args.length) {
        case 1:
            return slice_bang(context, args[0]);
        case 2:
            return slice_bang(context, args[0], args[1]);
        default:
            Arity.raiseArgumentError(context.getRuntime(), args.length, 1, 2);
            return null; // not reached
        }
    }

    /** rb_str_slice_bang
     *
     */
    @JRubyMethod(name = "slice!", reads = BACKREF, writes = BACKREF)
    public IRubyObject slice_bang(ThreadContext context, IRubyObject arg0) {
        IRubyObject result = op_aref(context, arg0);
        if (result.isNil()) return result;

        op_aset(context, arg0, RubyString.newEmptyString(context.getRuntime()));
        return result;
    }

    /** rb_str_slice_bang
     *
     */
    @JRubyMethod(name = "slice!", reads = BACKREF, writes = BACKREF)
    public IRubyObject slice_bang(ThreadContext context, IRubyObject arg0, IRubyObject arg1) {
        IRubyObject result = op_aref(context, arg0, arg1);
        if (result.isNil()) return result;

        op_aset(context, arg0, arg1, RubyString.newEmptyString(context.getRuntime()));
        return result;
    }

    @JRubyMethod(name = {"succ", "next"})
    public IRubyObject succ(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.succ_bang();
        return str;
    }

    @JRubyMethod(name = {"succ!", "next!"})
    public IRubyObject succ_bang() {
        if (value.length() == 0) {
            modifyCheck();
            return this;
        }

        modify();
        
        boolean alnumSeen = false;
        int pos = -1;
        int c = 0;
        int n = 0;
        for (int i = value.length() - 1; i >= 0; i--) {
            c = value.get(i) & 0xFF;
            if (isAlnum(c)) {
                alnumSeen = true;
                if ((isDigit(c) && c < '9') || (isLower(c) && c < 'z') || (isUpper(c) && c < 'Z')) {
                    value.set(i, (byte)(c + 1));
                    pos = -1;
                    break;
                }
                pos = i;
                n = isDigit(c) ? '1' : (isLower(c) ? 'a' : 'A');
                value.set(i, (byte)(isDigit(c) ? '0' : (isLower(c) ? 'a' : 'A')));
            }
        }
        if (!alnumSeen) {
            for (int i = value.length() - 1; i >= 0; i--) {
                c = value.get(i) & 0xFF;
                if (c < 0xff) {
                    value.set(i, (byte)(c + 1));
                    pos = -1;
                    break;
                }
                pos = i;
                n = '\u0001';
                value.set(i, 0);
            }
        }
        if (pos > -1) {
            // This represents left most digit in a set of incremented
            // values?  Therefore leftmost numeric must be '1' and not '0'
            // 999 -> 1000, not 999 -> 0000.  whereas chars should be
            // zzz -> aaaa and non-alnum byte values should be "\377" -> "\001\000"
            value.insert(pos, (byte) n);
        }
        return this;
    }

    /** rb_str_upto_m
     *
     */
    @JRubyMethod(name = "upto", required = 1, frame = true)
    public IRubyObject upto(ThreadContext context, IRubyObject str, Block block) {
        return upto(context, str, false, block);
    }

    /* rb_str_upto */
    public IRubyObject upto(ThreadContext context, IRubyObject str, boolean excl, Block block) {
        RubyString end = str.convertToString();

        int n = value.cmp(end.value);
        if (n > 0 || (excl && n == 0)) return this;

        IRubyObject afterEnd = end.callMethod(context, "succ");
        RubyString current = this;

        while (!current.op_equal(context, afterEnd).isTrue()) {
            block.yield(context, current);            
            if (!excl && current.op_equal(context, end).isTrue()) break;
            current = current.callMethod(context, "succ").convertToString();
            if (excl && current.op_equal(context, end).isTrue()) break;
            if (current.value.realSize > end.value.realSize || current.value.realSize == 0) break;
        }

        return this;
    }

    /** rb_str_include
     *
     */
    @JRubyMethod(name = "include?", required = 1)
    public RubyBoolean include_p(ThreadContext context, IRubyObject obj) {
        if (obj instanceof RubyFixnum) {
            int c = RubyNumeric.fix2int(obj);
            for (int i = 0; i < value.length(); i++) {
                if (value.get(i) == (byte)c) {
                    return context.getRuntime().getTrue();
                }
            }
            return context.getRuntime().getFalse();
        }
        ByteList str = stringValue(obj).value;
        return context.getRuntime().newBoolean(value.indexOf(str) != -1);
    }

    /**
     * Variable-arity version for compatibility. Not bound as a Ruby method.
     * @deprecated Use the versions with zero or one args.
     */
    public IRubyObject to_i(IRubyObject[] args) {
        switch (args.length) {
        case 0:
            return to_i();
        case 1:
            return to_i(args[0]);
        default:
            Arity.raiseArgumentError(getRuntime(), args.length, 0, 1);
            return null; // not reached
        }
    }

    /** rb_str_to_i
     *
     */
    @JRubyMethod(name = "to_i")
    public IRubyObject to_i() {
        return RubyNumeric.str2inum(getRuntime(), this, 10);
    }

    /** rb_str_to_i
     *
     */
    @JRubyMethod(name = "to_i")
    public IRubyObject to_i(IRubyObject arg0) {
        long base = arg0.convertToInteger().getLongValue();
        return RubyNumeric.str2inum(getRuntime(), this, (int) base);
    }

    /** rb_str_oct
     *
     */
    @JRubyMethod(name = "oct")
    public IRubyObject oct(ThreadContext context) {
        if (isEmpty()) return context.getRuntime().newFixnum(0);

        int base = 8;

        int ix = value.begin;

        while(ix < value.begin+value.realSize && ASCII.isSpace(value.bytes[ix] & 0xff)) {
            ix++;
        }

        int pos = (value.bytes[ix] == '-' || value.bytes[ix] == '+') ? ix+1 : ix;
        if((pos+1) < value.begin+value.realSize && value.bytes[pos] == '0') {
            if(value.bytes[pos+1] == 'x' || value.bytes[pos+1] == 'X') {
                base = 16;
            } else if(value.bytes[pos+1] == 'b' || value.bytes[pos+1] == 'B') {
                base = 2;
            } else if(value.bytes[pos+1] == 'd' || value.bytes[pos+1] == 'D') {
                base = 10;
            }
        }
        return RubyNumeric.str2inum(context.getRuntime(), this, base);
    }

    /** rb_str_hex
     *
     */
    @JRubyMethod(name = "hex")
    public IRubyObject hex(ThreadContext context) {
        return RubyNumeric.str2inum(context.getRuntime(), this, 16);
    }

    /** rb_str_to_f
     *
     */
    @JRubyMethod(name = "to_f")
    public IRubyObject to_f() {
        return RubyNumeric.str2fnum(getRuntime(), this);
    }

    /**
     * Variable arity version for compatibility. Not bound to a Ruby method.
     * @deprecated Use the versions with zero, one, or two args.
     */
    public RubyArray split(ThreadContext context, IRubyObject[] args) {
        switch (args.length) {
        case 0:
            return split(context);
        case 1:
            return split(context, args[0]);
        case 2:
            return split(context, args[0], args[1]);
        default:
            Arity.raiseArgumentError(context.getRuntime(), args.length, 0, 2);
            return null; // not reached
        }
    }

    /** rb_str_split_m
     *
     */
    @JRubyMethod(writes = BACKREF)
    public RubyArray split(ThreadContext context) {
        return split(context, context.getRuntime().getNil());
    }

    /** rb_str_split_m
     *
     */
    @JRubyMethod(writes = BACKREF)
    public RubyArray split(ThreadContext context, IRubyObject arg0) {
        return splitCommon(arg0, false, 0, 0, context);
    }

    /** rb_str_split_m
     *
     */
    @JRubyMethod(writes = BACKREF)
    public RubyArray split(ThreadContext context, IRubyObject arg0, IRubyObject arg1) {
        final int lim = RubyNumeric.fix2int(arg1);
        if (lim <= 0) {
            return splitCommon(arg0, false, lim, 1, context);
        } else {
            if (lim == 1) return value.realSize == 0 ? context.getRuntime().newArray() : context.getRuntime().newArray(this);
            return splitCommon(arg0, true, lim, 1, context);
        }
    }

    private RubyArray splitCommon(IRubyObject spat, final boolean limit, final int lim, final int i, ThreadContext context) {
        final RubyArray result;
        if (spat.isNil() && (spat = context.getRuntime().getGlobalVariables().get("$;")).isNil()) {
            result = awkSplit(limit, lim, i);
        } else {
            if (spat instanceof RubyString && ((RubyString) spat).value.realSize == 1) {
                RubyString strSpat = (RubyString) spat;
                if (strSpat.value.bytes[strSpat.value.begin] == (byte) ' ') {
                    result = awkSplit(limit, lim, i);
                } else {
                    result = regexSplit(context, spat, limit, lim, i);
                }
            } else {
                result = regexSplit(context, spat, limit, lim, i);
            }
        }

        if (!limit && lim == 0) {
            while (result.size() > 0 && ((RubyString) result.eltInternal(result.size() - 1)).value.realSize == 0) {
                result.pop(context);
            }
        }

        return result;
    }

    private RubyArray regexSplit(ThreadContext context, IRubyObject pat, boolean limit, int lim, int i) {
        Ruby runtime = context.getRuntime();

        final Regex regex = getPattern(pat, true).getPattern();

        int begin = value.begin;
        final Matcher matcher = regex.matcher(value.bytes, begin, begin + value.realSize);

        RubyArray result = runtime.newArray();
        final Encoding enc = regex.getEncoding();

        final int beg;
        if (regex.numberOfCaptures() == 0) { // shorter path, no captures defined, no region will be returned
            beg = regexSplitNG(result, matcher, enc, limit, lim, i, runtime);
        } else {
            beg = regexSplit(result, matcher, enc, limit, lim, i, runtime);
        }

        // only this case affects backrefs 
        context.getCurrentFrame().setBackRef(runtime.getNil());

        if (value.realSize > 0 && (limit || value.realSize > beg || lim < 0)) {
            if (value.realSize == beg) {
                result.append(newEmptyString(runtime, getMetaClass()));
            } else {
                result.append(substr(beg, value.realSize - beg));
            }
        }

        return result;
    }

    private int regexSplit(RubyArray result, Matcher matcher, Encoding enc, boolean limit, int lim, int i, Ruby runtime) {
        byte[]bytes = value.bytes;
        int begin = value.begin;
        int start = begin;
        int range = begin + value.realSize;
        int end, beg = 0;
        boolean lastNull = false;

        while ((end = matcher.search(start, range, Option.NONE)) >= 0) {
            final Region region = matcher.getRegion();
            if (start == end + begin && region.beg[0] == region.end[0]) {
                if (value.realSize == 0) {                        
                    result.append(newEmptyString(runtime, getMetaClass()));
                    break;
                } else if (lastNull) {
                    result.append(substr(beg, enc.length(bytes, begin + beg, range)));
                    beg = start - begin;
                } else {
                    if (start == range) {
                        start++;
                    } else {
                        start += enc.length(bytes, start, range);
                    }
                    lastNull = true;
                    continue;
                }                    
            } else {
                result.append(substr(beg, end - beg));
                beg = start = region.end[0];
                start += begin;
            }
            lastNull = false;
            
            for (int idx=1; idx<region.numRegs; idx++) {
                if (region.beg[idx] == -1) continue;
                if (region.beg[idx] == region.end[idx]) {
                    result.append(newEmptyString(runtime, getMetaClass()));
                } else {
                    result.append(substr(region.beg[idx], region.end[idx] - region.beg[idx]));
                }
            }
            if (limit && lim <= ++i) break;
        }
        return beg;
    }

    private int regexSplitNG(RubyArray result, Matcher matcher, Encoding enc, boolean limit, int lim, int i, Ruby runtime) {
        byte[]bytes = value.bytes;
        int begin = value.begin;
        int start = begin;
        int range = begin + value.realSize;
        int end, beg = 0;
        boolean lastNull = false;

        while ((end = matcher.search(start, range, Option.NONE)) >= 0) {
            if (start == end + begin && matcher.getBegin() == matcher.getEnd()) {
                if (value.realSize == 0) {
                    result.append(newEmptyString(runtime, getMetaClass()));
                    break;
                } else if (lastNull) {
                    result.append(substr(runtime, beg, enc.length(bytes, begin + beg, range)));
                    beg = start - begin;
                } else {
                    if (start == range) {
                        start++;
                    } else {
                        start += enc.length(bytes, start, range);
                    }
                    lastNull = true;
                    continue;
                }
            } else {
                result.append(substr(beg, end - beg));
                beg = matcher.getEnd();
                start = begin + matcher.getEnd();
            }
            lastNull = false;
            if (limit && lim <= ++i) break;
        }
        return beg;
    }

    private RubyArray awkSplit(boolean limit, int lim, int i) {
        Ruby runtime = getRuntime();
        RubyArray result = runtime.newArray();

        byte[]bytes = value.bytes;
        int p = value.begin; 
        int endp = p + value.realSize;

        boolean skip = true;

        int end, beg = 0;        
        for (end = beg = 0; p < endp; p++) {
            if (skip) {
                if (ASCII.isSpace(bytes[p] & 0xff)) {
                    beg++;
                } else {
                    end = beg + 1;
                    skip = false;
                    if (limit && lim <= i) break;
                }
            } else {
                if (ASCII.isSpace(bytes[p] & 0xff)) {
                    result.append(makeShared(runtime, beg, end - beg));
                    skip = true;
                    beg = end + 1;
                    if (limit) i++;
                } else {
                    end++;
                }
            }
        }

        if (value.realSize > 0 && (limit || value.realSize > beg || lim < 0)) {
            if (value.realSize == beg) {
                result.append(newEmptyString(runtime, getMetaClass()));
            } else {
                result.append(makeShared(runtime, beg, value.realSize - beg));
            }
        }
        return result;
    }

    /** get_pat
     * 
     */
    private final RubyRegexp getPattern(IRubyObject obj, boolean quote) {
        if (obj instanceof RubyRegexp) {
            return (RubyRegexp)obj;
        } else if (!(obj instanceof RubyString)) {
            IRubyObject val = obj.checkStringType();
            if (val.isNil()) throw getRuntime().newTypeError("wrong argument type " + obj.getMetaClass() + " (expected Regexp)");
            obj = val; 
        }

        return RubyRegexp.newRegexp(getRuntime(), ((RubyString)obj).value, 0, quote);
    }

    /** rb_str_scan
     *
     */
    @JRubyMethod(name = "scan", required = 1, frame = true, reads = BACKREF, writes = BACKREF)
    public IRubyObject scan(ThreadContext context, IRubyObject arg, Block block) {
        final RubyRegexp rubyRegex = getPattern(arg, true);
        final Regex regex = rubyRegex.getPattern();

        int range = value.begin + value.realSize;
        final Matcher matcher = regex.matcher(value.bytes, value.begin, range);
        matcher.value = 0; // implicit start argument to scanOnce(NG)

        if (block.isGiven()) {
            return scanIter(context, rubyRegex, matcher, block, range);
        } else {
            return scanNoIter(context, rubyRegex, matcher, range);
        }
    }

    private IRubyObject scanIter(ThreadContext context, RubyRegexp rubyRegex, Matcher matcher, Block block, int range) {
        Regex regex = rubyRegex.getPattern();
        byte[]bytes = value.bytes;
        int size = value.realSize;
        RubyMatchData match = null;
        Frame frame = context.getPreviousFrame();

        IRubyObject result;
        if (regex.numberOfCaptures() == 0) {
            while ((result = scanOnceNG(rubyRegex, matcher, range)) != null) {
                match = rubyRegex.updateBackRef(context, this, frame, matcher);
                match.use();
                block.yield(context, result);
                modifyCheck(bytes, size);
            }
        } else {
            while ((result = scanOnce(rubyRegex, matcher, range)) != null) {
                match = rubyRegex.updateBackRef(context, this, frame, matcher);
                match.use();
                block.yield(context, result);
                modifyCheck(bytes, size);
            }
        }
        frame.setBackRef(match == null ? context.getRuntime().getNil() : match);
        return this;
    }

    private IRubyObject scanNoIter(ThreadContext context, RubyRegexp rubyRegex, Matcher matcher, int range) {
        Regex regex = rubyRegex.getPattern();
        Ruby runtime = context.getRuntime();
        RubyArray ary = runtime.newArray();
        Frame frame = context.getPreviousFrame();

        IRubyObject result;
        if (regex.numberOfCaptures() == 0) {
            while ((result = scanOnceNG(rubyRegex, matcher, range)) != null) ary.append(result);
        } else {
            while ((result = scanOnce(rubyRegex, matcher, range)) != null) ary.append(result);
        }

        if (ary.size() > 0) {
            rubyRegex.updateBackRef(context, this, frame, matcher);
        } else {
            frame.setBackRef(runtime.getNil());
        }
        return ary;
    }

    // no group version
    private IRubyObject scanOnceNG(RubyRegexp regex, Matcher matcher, int range) {    
        if (matcher.search(matcher.value + value.begin, range, Option.NONE) >= 0) {
            int end = matcher.getEnd();
            if (matcher.getBegin() == end) {
                if (value.realSize > end) {
                    matcher.value = end + regex.getPattern().getEncoding().length(value.bytes, value.begin + end, range);
                } else {
                    matcher.value = end + 1;
                }
            } else {
                matcher.value = end;
            }
            return substr(matcher.getBegin(), end - matcher.getBegin()).infectBy(regex);
        }
        return null;
    }

    // group version
    private IRubyObject scanOnce(RubyRegexp regex, Matcher matcher, int range) {    
        if (matcher.search(matcher.value + value.begin, range, Option.NONE) >= 0) {
            Region region = matcher.getRegion();
            int end = region.end[0];
            if (region.beg[0] == end) {
                if (value.realSize > end) {
                    matcher.value = end + regex.getPattern().getEncoding().length(value.bytes, value.begin + end, range);
                } else {
                    matcher.value = end + 1;
                }
            } else {
                matcher.value = end;
            }
            
            RubyArray result = getRuntime().newArray(region.numRegs);
            for (int i=1; i<region.numRegs; i++) {
                int beg = region.beg[i]; 
                if (beg == -1) {
                    result.append(getRuntime().getNil());
                } else {
                    result.append(substr(beg, region.end[i] - beg).infectBy(regex));
                }
            }
            return result;
        }
        return null;
    }    

    private static final ByteList SPACE_BYTELIST = new ByteList(ByteList.plain(" "));
    
    private final IRubyObject justify(IRubyObject arg0, char jflag) {
        Ruby runtime = getRuntime();
        
        int width = RubyFixnum.num2int(arg0);
        
        int f, flen = 0;
        byte[]fbuf;
        
        IRubyObject pad;

        f = SPACE_BYTELIST.begin;
        flen = SPACE_BYTELIST.realSize;
        fbuf = SPACE_BYTELIST.bytes;
        pad = runtime.getNil();
        
        return justifyCommon(width, jflag, flen, fbuf, f, runtime, pad);
    }
    
    private final IRubyObject justify(IRubyObject arg0, IRubyObject arg1, char jflag) {
        Ruby runtime = getRuntime();
        
        int width = RubyFixnum.num2int(arg0);
        
        int f, flen = 0;
        byte[]fbuf;
        
        IRubyObject pad;

        pad = arg1.convertToString();
        ByteList fList = ((RubyString)pad).value;
        f = fList.begin;
        flen = fList.realSize;

        if (flen == 0) throw runtime.newArgumentError("zero width padding");

        fbuf = fList.bytes;
        
        return justifyCommon(width, jflag, flen, fbuf, f, runtime, pad);
    }

    private IRubyObject justifyCommon(int width, char jflag, int flen, byte[] fbuf, int f, Ruby runtime, IRubyObject pad) {
        if (width < 0 || value.realSize >= width) return strDup(runtime);

        ByteList res = new ByteList(width);
        res.realSize = width;

        int p = res.begin;
        int pend;
        byte[] pbuf = res.bytes;

        if (jflag != 'l') {
            int n = width - value.realSize;
            pend = p + ((jflag == 'r') ? n : n / 2);
            if (flen <= 1) {
                while (p < pend) {
                    pbuf[p++] = fbuf[f];
                }
            } else {
                int q = f;
                while (p + flen <= pend) {
                    System.arraycopy(fbuf, f, pbuf, p, flen);
                    p += flen;
                }
                while (p < pend) {
                    pbuf[p++] = fbuf[q++];
                }
            }
        }

        System.arraycopy(value.bytes, value.begin, pbuf, p, value.realSize);

        if (jflag != 'r') {
            p += value.realSize;
            pend = res.begin + width;
            if (flen <= 1) {
                while (p < pend) {
                    pbuf[p++] = fbuf[f];
                }
            } else {
                while (p + flen <= pend) {
                    System.arraycopy(fbuf, f, pbuf, p, flen);
                    p += flen;
                }
                while (p < pend) {
                    pbuf[p++] = fbuf[f++];
                }
            }
        }

        RubyString resStr = new RubyString(runtime, getMetaClass(), res);
        resStr.infectBy(this);
        if (flen > 0) {
            resStr.infectBy(pad);
        }
        return resStr;
    }

    /**
     * Variable-arity version for compatibility. Not bound to Ruby.
     * @deprecated use the one or two argument versions.
     */
    public IRubyObject ljust(IRubyObject [] args) {
        switch (args.length) {
        case 1:
            return ljust(args[0]);
        case 2:
            return ljust(args[0], args[1]);
        default:
            Arity.raiseArgumentError(getRuntime(), args.length, 1, 2);
            return null; // not reached
        }
    }

    /** rb_str_ljust
     *
     */
    @JRubyMethod
    public IRubyObject ljust(IRubyObject arg0) {
        return justify(arg0, 'l');
    }

    /** rb_str_ljust
     *
     */
    @JRubyMethod
    public IRubyObject ljust(IRubyObject arg0, IRubyObject arg1) {
        return justify(arg0, arg1, 'l');
    }

    /**
     * Variable-arity version for compatibility. Not bound to Ruby.
     * @deprecated use the one or two argument versions.
     */
    public IRubyObject rjust(IRubyObject [] args) {
        switch (args.length) {
        case 1:
            return rjust(args[0]);
        case 2:
            return rjust(args[0], args[1]);
        default:
            Arity.raiseArgumentError(getRuntime(), args.length, 1, 2);
            return null; // not reached
        }
    }

    /** rb_str_rjust
     *
     */
    @JRubyMethod
    public IRubyObject rjust(IRubyObject arg0) {
        return justify(arg0, 'r');
    }

    /** rb_str_rjust
     *
     */
    @JRubyMethod
    public IRubyObject rjust(IRubyObject arg0, IRubyObject arg1) {
        return justify(arg0, arg1, 'r');
    }

    /**
     * Variable-arity version for compatibility. Not bound to Ruby.
     * @deprecated use the one or two argument versions.
     */
    public IRubyObject center(IRubyObject [] args) {
        switch (args.length) {
        case 1:
            return center(args[0]);
        case 2:
            return center(args[0], args[1]);
        default:
            Arity.raiseArgumentError(getRuntime(), args.length, 1, 2);
            return null; // not reached
        }
    }

    /** rb_str_center
     *
     */
    @JRubyMethod
    public IRubyObject center(IRubyObject arg0) {
        return justify(arg0, 'c');
    }

    /** rb_str_center
     *
     */
    @JRubyMethod
    public IRubyObject center(IRubyObject arg0, IRubyObject arg1) {
        return justify(arg0, arg1, 'c');
    }

    @JRubyMethod(name = "chop")
    public IRubyObject chop(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.chop_bang();
        return str;
    }

    /** rb_str_chop_bang
     * 
     */
    @JRubyMethod(name = "chop!")
    public IRubyObject chop_bang() {
        int end = value.realSize - 1;
        if (end < 0) return getRuntime().getNil(); 

        if ((value.bytes[value.begin + end]) == '\n') {
            if (end > 0 && (value.bytes[value.begin + end - 1]) == '\r') end--;
        }

        view(0, end);
        return this;
    }

    /**
     * Variable-arity version for compatibility. Not bound to Ruby
     * 
     * @param args
     * @return
     * @deprecated Use the zero or one argument versions.
     */
    public RubyString chomp(IRubyObject[] args) {
        switch (args.length) {
        case 0:
            return chomp();
        case 1:
            return chomp(args[0]);
        default:
            Arity.raiseArgumentError(getRuntime(), args.length, 0, 1);
            return null; // not reached
        }
    }

    /** rb_str_chop
     * 
     */
    @JRubyMethod
    public RubyString chomp() {
        RubyString str = strDup(getRuntime());
        str.chomp_bang();
        return str;
    }

    /** rb_str_chop
     * 
     */
    @JRubyMethod
    public RubyString chomp(IRubyObject arg0) {
        RubyString str = strDup(getRuntime());
        str.chomp_bang(arg0);
        return str;
    }

    /**
     * Variable-arity version for compatibility. Not bound to Ruby.
     * @deprecated Use the zero or one argument versions.
     */
    public IRubyObject chomp_bang(IRubyObject[] args) {
        switch (args.length) {
        case 0:
            return chomp_bang();
        case 1:
            return chomp_bang(args[0]);
        default:
            Arity.raiseArgumentError(getRuntime(), args.length, 0, 1);
            return null; // not reached
        }
    }

    /**
     * rb_str_chomp_bang
     *
     * In the common case, removes CR and LF characters in various ways depending on the value of
     *   the optional args[0].
     * If args.length==0 removes one instance of CR, CRLF or LF from the end of the string.
     * If args.length>0 and args[0] is "\n" then same behaviour as args.length==0 .
     * If args.length>0 and args[0] is "" then removes trailing multiple LF or CRLF (but no CRs at
     *   all(!)).
     * @param args See method description.
     */
    @JRubyMethod(name = "chomp!")
    public IRubyObject chomp_bang() {
        IRubyObject rsObj;

        int len = value.length();
        if (len == 0) return getRuntime().getNil();
        byte[]buff = value.bytes;

        rsObj = getRuntime().getGlobalVariables().get("$/");

        if (rsObj == getRuntime().getGlobalVariables().getDefaultSeparator()) {
            int realSize = value.realSize;
            int begin = value.begin;
            if (buff[begin + len - 1] == (byte)'\n') {
                realSize--;
                if (realSize > 0 && buff[begin + realSize - 1] == (byte)'\r') realSize--;
                view(0, realSize);
            } else if (buff[begin + len - 1] == (byte)'\r') {
                realSize--;
                view(0, realSize);
            } else {
                modifyCheck();
                return getRuntime().getNil();
            }
            return this;                
        }
        
        return chompBangCommon(rsObj);
    }

    /**
     * rb_str_chomp_bang
     *
     * In the common case, removes CR and LF characters in various ways depending on the value of
     *   the optional args[0].
     * If args.length==0 removes one instance of CR, CRLF or LF from the end of the string.
     * If args.length>0 and args[0] is "\n" then same behaviour as args.length==0 .
     * If args.length>0 and args[0] is "" then removes trailing multiple LF or CRLF (but no CRs at
     *   all(!)).
     * @param args See method description.
     */
    @JRubyMethod(name = "chomp!")
    public IRubyObject chomp_bang(IRubyObject arg0) {
        return chompBangCommon(arg0);
    }

    private IRubyObject chompBangCommon(IRubyObject rsObj) {

        if (rsObj.isNil()) {
            return getRuntime().getNil();
        }
        RubyString rs = rsObj.convertToString();
        int len = value.realSize;
        int begin = value.begin;
        if (len == 0) {
            return getRuntime().getNil();
        }
        byte[] buff = value.bytes;
        int rslen = rs.value.realSize;

        if (rslen == 0) {
            while (len > 0 && buff[begin + len - 1] == (byte) '\n') {
                len--;
                if (len > 0 && buff[begin + len - 1] == (byte) '\r') {
                    len--;
                }
            }
            if (len < value.realSize) {
                view(0, len);
                return this;
            }
            return getRuntime().getNil();
        }

        if (rslen > len) {
            return getRuntime().getNil();
        }
        byte newline = rs.value.bytes[rslen - 1];

        if (rslen == 1 && newline == (byte) '\n') {
            buff = value.bytes;
            int realSize = value.realSize;
            if (buff[begin + len - 1] == (byte) '\n') {
                realSize--;
                if (realSize > 0 && buff[begin + realSize - 1] == (byte) '\r') {
                    realSize--;
                }
                view(0, realSize);
            } else if (buff[begin + len - 1] == (byte) '\r') {
                realSize--;
                view(0, realSize);
            } else {
                modifyCheck();
                return getRuntime().getNil();
            }
            return this;
        }

        if (buff[begin + len - 1] == newline && rslen <= 1 || value.endsWith(rs.value)) {
            view(0, value.realSize - rslen);
            return this;
        }

        return getRuntime().getNil();
    }

    /** rb_str_lstrip
     * 
     */
    @JRubyMethod
    public IRubyObject lstrip(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.lstrip_bang();
        return str;
    }

    /** rb_str_lstrip_bang
     */
    @JRubyMethod(name = "lstrip!")
    public IRubyObject lstrip_bang() {
        if (value.realSize == 0) return getRuntime().getNil();
        
        int i=0;
        while (i < value.realSize && ASCII.isSpace(value.bytes[value.begin + i] & 0xff)) i++;
        
        if (i > 0) {
            view(i, value.realSize - i);
            return this;
        }
        
        return getRuntime().getNil();
    }

    /** rb_str_rstrip
     *  
     */
    @JRubyMethod
    public IRubyObject rstrip(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.rstrip_bang();
        return str;
    }

    /** rb_str_rstrip_bang
     */ 
    @JRubyMethod(name = "rstrip!")
    public IRubyObject rstrip_bang() {
        if (value.realSize == 0) return getRuntime().getNil();
        int i=value.realSize - 1;

        while (i >= 0 && value.bytes[value.begin+i] == 0) i--;
        while (i >= 0 && ASCII.isSpace(value.bytes[value.begin + i] & 0xff)) i--;

        if (i < value.realSize - 1) {
            view(0, i + 1);
            return this;
        }

        return getRuntime().getNil();
    }

    /** rb_str_strip
     *
     */
    @JRubyMethod
    public IRubyObject strip(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.strip_bang();
        return str;
    }

    /** rb_str_strip_bang
     */
    @JRubyMethod(name = "strip!")
    public IRubyObject strip_bang() {
        IRubyObject l = lstrip_bang();
        IRubyObject r = rstrip_bang();

        if(l.isNil() && r.isNil()) {
            return l;
        }
        return this;
    }

    /** rb_str_count
     *
     */
    @JRubyMethod(name = "count", required = 1, rest = true)
    public IRubyObject count(IRubyObject[] args) {
        if (args.length < 1) throw getRuntime().newArgumentError("wrong number of arguments");
        if (value.realSize == 0) return getRuntime().newFixnum(0);

        boolean[]table = new boolean[TRANS_SIZE];
        boolean init = true;
        for (int i=0; i<args.length; i++) {
            RubyString s = args[i].convertToString();
            s.setup_table(table, init);
            init = false;
        }

        int s = value.begin;
        int send = s + value.realSize;
        byte[]buf = value.bytes;
        int i = 0;

        while (s < send) if (table[buf[s++] & 0xff]) i++;

        return getRuntime().newFixnum(i);
    }

    /** rb_str_delete
     *
     */
    @JRubyMethod(name = "delete", required = 1, rest = true)
    public IRubyObject delete(ThreadContext context, IRubyObject[] args) {
        RubyString str = strDup(context.getRuntime());
        str.delete_bang(args);
        return str;
    }

    /** rb_str_delete_bang
     *
     */
    @JRubyMethod(name = "delete!", required = 1, rest = true)
    public IRubyObject delete_bang(IRubyObject[] args) {
        if (args.length < 1) throw getRuntime().newArgumentError("wrong number of arguments");
        
        boolean[]squeeze = new boolean[TRANS_SIZE];

        boolean init = true;
        for (int i=0; i<args.length; i++) {
            RubyString s = args[i].convertToString();
            s.setup_table(squeeze, init);
            init = false;
        }
        
        modify();
        
        if (value.realSize == 0) return getRuntime().getNil();
        int s = value.begin;
        int t = s;
        int send = s + value.realSize;
        byte[]buf = value.bytes;
        boolean modify = false;
        
        while (s < send) {
            if (squeeze[buf[s] & 0xff]) {
                modify = true;
            } else {
                buf[t++] = buf[s];
            }
            s++;
        }
        value.realSize = t - value.begin;
        
        if (modify) return this;
        return getRuntime().getNil();
    }

    /** rb_str_squeeze
     *
     */
    @JRubyMethod(name = "squeeze", rest = true)
    public IRubyObject squeeze(ThreadContext context, IRubyObject[] args) {
        RubyString str = strDup(context.getRuntime());
        str.squeeze_bang(args);
        return str;
    }

    /** rb_str_squeeze_bang
     *
     */
    @JRubyMethod(name = "squeeze!", rest = true)
    public IRubyObject squeeze_bang(IRubyObject[] args) {
        if (value.realSize == 0) {
            modifyCheck();
            return getRuntime().getNil();
        }

        final boolean squeeze[] = new boolean[TRANS_SIZE];

        if (args.length == 0) {
            for (int i=0; i<TRANS_SIZE; i++) squeeze[i] = true;
        } else {
            boolean init = true;
            for (int i=0; i<args.length; i++) {
                RubyString s = args[i].convertToString();
                s.setup_table(squeeze, init);
                init = false;
            }
        }

        modify();

        int s = value.begin;
        int t = s;
        int send = s + value.realSize;
        byte[]buf = value.bytes;
        int save = -1;

        while (s < send) {
            int c = buf[s++] & 0xff;
            if (c != save || !squeeze[c]) buf[t++] = (byte)(save = c);
        }

        if (t - value.begin != value.realSize) { // modified
            value.realSize = t - value.begin; 
            return this;
        }

        return getRuntime().getNil();
    }

    /** rb_str_tr
     *
     */
    @JRubyMethod
    public IRubyObject tr(ThreadContext context, IRubyObject src, IRubyObject repl) {
        RubyString str = strDup(context.getRuntime());
        str.tr_trans(src, repl, false);
        return str;
    }
    
    /** rb_str_tr_bang
    *
    */
    @JRubyMethod(name = "tr!")
    public IRubyObject tr_bang(IRubyObject src, IRubyObject repl) {
        return tr_trans(src, repl, false);
    }    
    
    private static final class TR {
        int gen, now, max;
        int p, pend;
        byte[]buf;
    }

    private static final int TRANS_SIZE = 256;
    
    /** tr_setup_table
     * 
     */
    private final void setup_table(boolean[]table, boolean init) {
        final boolean[]buf = new boolean[TRANS_SIZE];
        final TR tr = new TR();
        int c;
        
        boolean cflag = false;
        
        tr.p = value.begin;
        tr.pend = value.begin + value.realSize;
        tr.buf = value.bytes;
        tr.gen = tr.now = tr.max = 0;
        
        if (value.realSize > 1 && value.bytes[value.begin] == '^') {
            cflag = true;
            tr.p++;
        }
        
        if (init) for (int i=0; i<TRANS_SIZE; i++) table[i] = true;
        
        for (int i=0; i<TRANS_SIZE; i++) buf[i] = cflag;
        while ((c = trnext(tr)) >= 0) buf[c & 0xff] = !cflag;
        for (int i=0; i<TRANS_SIZE; i++) table[i] = table[i] && buf[i];
    }
    
    /** tr_trans
    *
    */    
    private final IRubyObject tr_trans(IRubyObject src, IRubyObject repl, boolean sflag) {
        if (value.realSize == 0) return getRuntime().getNil();
        
        ByteList replList = repl.convertToString().value;
        
        if (replList.realSize == 0) return delete_bang(new IRubyObject[]{src});

        ByteList srcList = src.convertToString().value;
        
        final TR trsrc = new TR();
        final TR trrepl = new TR();
        
        boolean cflag = false;
        boolean modify = false;
        
        trsrc.p = srcList.begin;
        trsrc.pend = srcList.begin + srcList.realSize;
        trsrc.buf = srcList.bytes;
        if (srcList.realSize >= 2 && srcList.bytes[srcList.begin] == '^') {
            cflag = true;
            trsrc.p++;
        }       
        
        trrepl.p = replList.begin;
        trrepl.pend = replList.begin + replList.realSize;
        trrepl.buf = replList.bytes;
        
        trsrc.gen = trrepl.gen = 0;
        trsrc.now = trrepl.now = 0;
        trsrc.max = trrepl.max = 0;
        
        int c;
        final int[]trans = new int[TRANS_SIZE];
        if (cflag) {
            for (int i=0; i<TRANS_SIZE; i++) trans[i] = 1;
            while ((c = trnext(trsrc)) >= 0) trans[c & 0xff] = -1;
            while ((c = trnext(trrepl)) >= 0); 
            for (int i=0; i<TRANS_SIZE; i++) {
                if (trans[i] >= 0) trans[i] = trrepl.now;
            }
        } else {
            for (int i=0; i<TRANS_SIZE; i++) trans[i] = -1;
            while ((c = trnext(trsrc)) >= 0) {
                int r = trnext(trrepl);
                if (r == -1) r = trrepl.now;
                trans[c & 0xff] = r;
            }
        }
        
        modify();
        
        int s = value.begin;
        int send = s + value.realSize;
        byte sbuf[] = value.bytes;
        
        if (sflag) {
            int t = s;
            int c0, last = -1;
            while (s < send) {
                c0 = sbuf[s++];
                if ((c = trans[c0 & 0xff]) >= 0) {
                    if (last == c) continue;
                    last = c;
                    sbuf[t++] = (byte)(c & 0xff);
                    modify = true;
                } else {
                    last = -1;
                    sbuf[t++] = (byte)c0;
                }
            }
            
            if (value.realSize > (t - value.begin)) {
                value.realSize = t - value.begin;
                modify = true;
            }
        } else {
            while (s < send) {
                if ((c = trans[sbuf[s] & 0xff]) >= 0) {
                    sbuf[s] = (byte)(c & 0xff);
                    modify = true;
                }
                s++;
            }
        }
        
        if (modify) return this;
        return getRuntime().getNil();
    }

    /** trnext
    *
    */    
    private final int trnext(TR t) {
        byte [] buf = t.buf;
        
        for (;;) {
            if (t.gen == 0) {
                if (t.p == t.pend) return -1;
                if (t.p < t.pend -1 && buf[t.p] == '\\') t.p++;
                t.now = buf[t.p++];
                if (t.p < t.pend - 1 && buf[t.p] == '-') {
                    t.p++;
                    if (t.p < t.pend) {
                        if (t.now > ((int)buf[t.p] & 0xFF)) {
                            t.p++;
                            continue;
                        }
                        t.gen = 1;
                        t.max = (int)buf[t.p++] & 0xFF;
                    }
                }
                return t.now & 0xff;
            } else if (++t.now < t.max) {
                return t.now & 0xff;
            } else {
                t.gen = 0;
                return t.max & 0xff;
            }
        }
    }    

    /** rb_str_tr_s
     *
     */
    @JRubyMethod
    public IRubyObject tr_s(ThreadContext context, IRubyObject src, IRubyObject repl) {
        RubyString str = strDup(context.getRuntime());
        str.tr_trans(src, repl, true);
        return str;
    }

    /** rb_str_tr_s_bang
     *
     */
    @JRubyMethod(name = "tr_s!")
    public IRubyObject tr_s_bang(IRubyObject src, IRubyObject repl) {
        return tr_trans(src, repl, true);
    }

    /** rb_str_each_line
     *
     */
    @JRubyMethod(name = {"each_line", "each"}, required = 0, optional = 1, frame = true)
    public IRubyObject each_line(ThreadContext context, IRubyObject[] args, Block block) {
        byte newline;
        int p = value.begin;
        int pend = p + value.realSize;
        int s;
        int ptr = p;
        int len = value.realSize;
        int rslen;
        IRubyObject line;
        

        IRubyObject _rsep;
        if (args.length == 0) {
            _rsep = getRuntime().getGlobalVariables().get("$/");
        } else {
            _rsep = args[0];
        }

        if(_rsep.isNil()) {
            block.yield(context, this);
            return this;
        }
        
        RubyString rsep = stringValue(_rsep);
        ByteList rsepValue = rsep.value;
        byte[] strBytes = value.bytes;

        rslen = rsepValue.realSize;
        
        if(rslen == 0) {
            newline = '\n';
        } else {
            newline = rsepValue.bytes[rsepValue.begin + rslen-1];
        }

        s = p;
        p+=rslen;

        for(; p < pend; p++) {
            if(rslen == 0 && strBytes[p] == '\n') {
                if(strBytes[++p] != '\n') {
                    continue;
                }
                while(p < pend && strBytes[p] == '\n') {
                    p++;
                }
            }
            if(ptr<p && strBytes[p-1] == newline &&
               (rslen <= 1 || 
                ByteList.memcmp(rsepValue.bytes, rsepValue.begin, rslen, strBytes, p-rslen, rslen) == 0)) {
                line = RubyString.newStringShared(getRuntime(), getMetaClass(), this.value.makeShared(s-ptr, p-s));
                line.infectBy(this);
                block.yield(context, line);
                modifyCheck(strBytes,len);
                s = p;
            }
        }

        if(s != pend) {
            if(p > pend) {
                p = pend;
            }
            line = RubyString.newStringShared(getRuntime(), getMetaClass(), this.value.makeShared(s-ptr, p-s));
            line.infectBy(this);
            block.yield(context, line);
        }

        return this;
    }

    /**
     * rb_str_each_byte
     */
    @JRubyMethod(name = "each_byte", frame = true, compat = CompatVersion.RUBY1_8)
    public RubyString each_byte(ThreadContext context, Block block) {
        Ruby runtime = getRuntime();
        // Check the length every iteration, since
        // the block can modify this string.
        for (int i = 0; i < value.length(); i++) {
            block.yield(context, runtime.newFixnum(value.get(i) & 0xFF));
        }
        return this;
    }

    @JRubyMethod(name = "each_byte", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject each_byte19(ThreadContext context, Block block) {
        return block.isGiven() ? each_byte(context, block) : enumeratorize(context.getRuntime(), this, "each_byte");
    }

    /** rb_str_each_char
     * 
     */
    @JRubyMethod(name = "each_char", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject each_char(ThreadContext context, Block block) {
        if (!block.isGiven()) return enumeratorize(context.getRuntime(), this, "each_char");

        byte bytes[] = value.bytes;
        int p = value.begin;
        int end = p + value.realSize;
        Encoding enc = value.encoding;

        while (p < end) {
            int n = StringSupport.length(enc, bytes, p, end);
            block.yield(context, substr(p, n)); // TODO: 1.9 version of substr.
            p += n;
        }
        return this;
    }

    /** rb_str_each_codepoint
     * 
     */
    @JRubyMethod(name = "each_codepoint", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject each_codepoint(ThreadContext context, Block block) {
        if (singleByteOptimizable()) return each_byte19(context, block);
        if (!block.isGiven()) return enumeratorize(context.getRuntime(), this, "each_codepoint");

        Ruby runtime = context.getRuntime();
        byte bytes[] = value.bytes;
        int p = value.begin;
        int end = p + value.realSize;
        Encoding enc = value.encoding;

        while (p < end) {
            int c = codePoint(runtime, enc, bytes, p, end);
            int n = codeLength(runtime, enc, c);
            block.yield(context, runtime.newFixnum(c));
            p += n;
        }
        return this;
    }

    /** rb_str_intern
     *
     */
    public RubySymbol intern() {
        String s = toString();
        if (s.length() == 0) {
            throw getRuntime().newArgumentError("interning empty string");
        }
        if (s.indexOf('\0') >= 0) {
            throw getRuntime().newArgumentError("symbol string may not contain '\\0'");
        }
        return getRuntime().newSymbol(s);
    }

    @JRubyMethod(name = {"to_sym", "intern"})
    public RubySymbol to_sym() {
        return intern();
    }

    @JRubyMethod(name = "sum", optional = 1)
    public RubyInteger sum(IRubyObject[] args) {
        if (args.length > 1) {
            throw getRuntime().newArgumentError("wrong number of arguments (" + args.length + " for 1)");
        }
        
        long bitSize = 16;
        if (args.length == 1) {
            long bitSizeArg = ((RubyInteger) args[0].convertToInteger()).getLongValue();
            if (bitSizeArg > 0) {
                bitSize = bitSizeArg;
            }
        }

        long result = 0;
        for (int i = 0; i < value.length(); i++) {
            result += value.get(i) & 0xFF;
        }
        return getRuntime().newFixnum(bitSize == 0 ? result : result % (long) Math.pow(2, bitSize));
    }
    
    /** string_to_c
     * 
     */
    @JRubyMethod(name = "to_c", reads = BACKREF, writes = BACKREF, compat = CompatVersion.RUBY1_9)
    public IRubyObject to_c(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        Frame frame = context.getCurrentFrame();
        IRubyObject backref = frame.getBackRef();
        if (backref != null && backref instanceof RubyMatchData) ((RubyMatchData)backref).use();

        IRubyObject s = RuntimeHelpers.invoke(
                context, this, "gsub",
                RubyRegexp.newRegexp(runtime, Numeric.ComplexPatterns.underscores_pat),
                runtime.newString(new ByteList(new byte[]{'_'})));

        RubyArray a = RubyComplex.str_to_c_internal(context, s);

        frame.setBackRef(backref);

        if (!a.eltInternal(0).isNil()) {
            return a.eltInternal(0);
        } else {
            return RubyComplex.newComplexCanonicalize(context, RubyFixnum.zero(runtime));
        }
    }

    /** string_to_r
     * 
     */
    @JRubyMethod(name = "to_r", reads = BACKREF, writes = BACKREF, compat = CompatVersion.RUBY1_9)
    public IRubyObject to_r(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        Frame frame = context.getCurrentFrame();
        IRubyObject backref = frame.getBackRef();
        if (backref != null && backref instanceof RubyMatchData) ((RubyMatchData)backref).use();

        IRubyObject s = RuntimeHelpers.invoke(
                context, this, "gsub",
                RubyRegexp.newRegexp(runtime, Numeric.ComplexPatterns.underscores_pat),
                runtime.newString(new ByteList(new byte[]{'_'})));

        RubyArray a = RubyRational.str_to_r_internal(context, s);

        frame.setBackRef(backref);

        if (!a.eltInternal(0).isNil()) {
            return a.eltInternal(0);
        } else {
            return RubyRational.newRationalCanonicalize(context, RubyFixnum.zero(runtime));
        }
    }    

    public static RubyString unmarshalFrom(UnmarshalStream input) throws java.io.IOException {
        RubyString result = newString(input.getRuntime(), input.unmarshalString());
        input.registerLinkTarget(result);
        return result;
    }

    /**
     * @see org.jruby.util.Pack#unpack
     */
    @JRubyMethod
    public RubyArray unpack(IRubyObject obj) {
        return Pack.unpack(getRuntime(), this.value, stringValue(obj).value);
    }

    public void empty() {
        value = ByteList.EMPTY_BYTELIST;
        shareLevel = SHARE_LEVEL_BYTELIST;
    }

    @JRubyMethod(name = "encoding", compat = CompatVersion.RUBY1_9)
    public IRubyObject encoding(ThreadContext context) {
        return context.getRuntime().getEncodingService().getEncoding(value.encoding);
    }
    
    @JRubyMethod(name = "force_encoding", compat = CompatVersion.RUBY1_9)
    public IRubyObject force_encoding(ThreadContext context, IRubyObject enc) {
        modify();
        associateEncoding(enc.convertToString().toEncoding(context.getRuntime()));
        return this;
    }

    @JRubyMethod(name = "valid_encoding?", compat = CompatVersion.RUBY1_9)
    public IRubyObject valid_encoding_p(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        return isCodeRangeBroken() ? runtime.getFalse() : runtime.getTrue();
    }
    
    @JRubyMethod(name = "ascii_only?", compat = CompatVersion.RUBY1_9)
    public IRubyObject ascii_only_p(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        return isCodeRangeAsciiOnly() ? runtime.getFalse() : runtime.getTrue();
    }

    /**
     * Mutator for internal string representation.
     *
     * @param value The new java.lang.String this RubyString should encapsulate
     * @deprecated
     */
    public void setValue(CharSequence value) {
        view(ByteList.plain(value));
    }

    public void setValue(ByteList value) {
        view(value);
    }

    public CharSequence getValue() {
        return toString();
    }

    public byte[] getBytes() {
        return value.bytes();
    }

    public ByteList getByteList() {
        return value;
    }

    /** used by ar-jdbc
     * 
     */
    public String getUnicodeValue() {
        try {
            return new String(value.bytes, value.begin, value.realSize, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("Something's seriously broken with encodings", e);
        }
    }

    @Override
    public IRubyObject to_java() {
        return MiniJava.javaToRuby(getRuntime(), new String(getBytes()));
    }
}
