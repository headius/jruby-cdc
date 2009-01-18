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
import static org.jruby.util.StringSupport.CR_7BIT;
import static org.jruby.util.StringSupport.CR_BROKEN;
import static org.jruby.util.StringSupport.CR_MASK;
import static org.jruby.util.StringSupport.CR_UNKNOWN;
import static org.jruby.util.StringSupport.CR_VALID;
import static org.jruby.util.StringSupport.codeLength;
import static org.jruby.util.StringSupport.codePoint;
import static org.jruby.util.StringSupport.codeRangeScan;
import static org.jruby.util.StringSupport.searchNonAscii;
import static org.jruby.util.StringSupport.strLengthWithCodeRange;
import static org.jruby.util.StringSupport.toLower;
import static org.jruby.util.StringSupport.toUpper;
import static org.jruby.util.StringSupport.unpackArg;
import static org.jruby.util.StringSupport.unpackResult;

import java.io.UnsupportedEncodingException;
import java.util.Locale;

import org.jcodings.Encoding;
import org.jcodings.EncodingDB.Entry;
import org.jcodings.ascii.AsciiTables;
import org.jcodings.constants.CharacterType;
import org.jcodings.specific.ASCIIEncoding;
import org.jcodings.specific.USASCIIEncoding;
import org.jcodings.specific.UTF8Encoding;
import org.jcodings.util.IntHash;
import org.joni.Matcher;
import org.joni.Option;
import org.joni.Regex;
import org.joni.Region;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
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
import org.jruby.util.TypeConverter;
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
        if (!runtime.is1_9()) stringClass.includeModule(runtime.getEnumerable());
        stringClass.defineAnnotatedMethods(RubyString.class);

        return stringClass;
    }

    private static ObjectAllocator STRING_ALLOCATOR = new ObjectAllocator() {
        public IRubyObject allocate(Ruby runtime, RubyClass klass) {
            return RubyString.newEmptyString(runtime, klass);
        }
    };

    public Encoding getEncoding() {
        return value.encoding;
    }

    public void associateEncoding(Encoding enc) {
        if (value.encoding != enc) {
            if (!isCodeRangeAsciiOnly() || !enc.isAsciiCompatible()) clearCodeRange();
            value.encoding = enc;
        }
    }

    public final void setEncodingAndCodeRange(Encoding enc, int cr) {
        value.encoding = enc;
        setCodeRange(cr);
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
        return flags & CR_MASK;
    }

    public final void setCodeRange(int codeRange) {
        flags |= codeRange & CR_MASK;
    }

    public final void clearCodeRange() {
        flags &= ~CR_MASK;
    }

    private void keepCodeRange() {
        if (getCodeRange() == CR_BROKEN) clearCodeRange();
    }

    // ENC_CODERANGE_ASCIIONLY
    public final boolean isCodeRangeAsciiOnly() {
        return getCodeRange() == CR_7BIT;
    }

    // rb_enc_str_asciionly_p
    public final boolean isAsciiOnly() {
        return value.encoding.isAsciiCompatible() && scanForCodeRange() == CR_7BIT;
    }

    public final boolean isCodeRangeValid() {
        return (flags & CR_VALID) != 0;
    }

    public final boolean isCodeRangeBroken() {
        return (flags & CR_BROKEN) != 0;
    }

    static int codeRangeAnd(int cr1, int cr2) {
        if (cr1 == CR_7BIT) return cr2;
        if (cr1 == CR_VALID) return cr2 == CR_7BIT ? CR_VALID : cr2;
        return CR_UNKNOWN;
    }

    private void copyCodeRangeForSubstr(RubyString from, Encoding enc) {
        int fromCr = from.getCodeRange();
        if (fromCr == CR_7BIT) {
            setCodeRange(fromCr);
        } else if (fromCr == CR_VALID) {
            if (!enc.isAsciiCompatible() || searchNonAscii(value) != -1) {
                setCodeRange(CR_VALID);
            } else {
                setCodeRange(CR_7BIT);
            }
        } else{ 
            if (value.realSize == 0) {
                setCodeRange(!enc.isAsciiCompatible() ? CR_VALID : CR_7BIT);
            }
        }
    }

    private void copyCodeRange(RubyString from) {
        value.encoding = from.value.encoding;
        setCodeRange(from.getCodeRange());
    }

    // rb_str_is_ascii_only_p
    final int scanForCodeRange() {
        int cr = getCodeRange();
        if (cr == CR_UNKNOWN) {
            cr = codeRangeScan(value.encoding, value);
            setCodeRange(cr);
        }
        return cr;
    }

    private boolean singleByteOptimizable() {
        return getCodeRange() == CR_7BIT || value.encoding.isSingleByte();
    }

    private boolean singleByteOptimizable(Encoding enc) {
        return getCodeRange() == CR_7BIT || enc.isSingleByte();
    }

    private Encoding isCompatibleWith(RubyString other) { 
        Encoding enc1 = value.encoding;;
        Encoding enc2 = other.value.encoding;

        if (enc1 == enc2) return enc1;

        if (other.value.realSize == 0) return enc1;
        if (value.realSize == 0) return enc2;

        if (!enc1.isAsciiCompatible() || !enc2.isAsciiCompatible()) return null;

        return RubyEncoding.areCompatible(enc1, scanForCodeRange(), enc2, other.scanForCodeRange());
    }

    final Encoding isCompatibleWith(EncodingCapable other) {
        if (other instanceof RubyString) return checkEncoding((RubyString)other);
        Encoding enc1 = value.encoding;;
        Encoding enc2 = other.getEncoding();

        if (enc1 == enc2) return enc1;
        if (value.realSize == 0) return enc2;
        if (!enc1.isAsciiCompatible() || !enc2.isAsciiCompatible()) return null;
        if (enc2 instanceof USASCIIEncoding) return enc1;
        if (scanForCodeRange() == CR_7BIT) return enc2;
        return null;
    }

    final Encoding checkEncoding(RubyString other) {
        Encoding enc = isCompatibleWith(other);
        if (enc == null) throw getRuntime().newEncodingCompatibilityError("incompatible character encodings: " + 
                                value.encoding + " and " + other.value.encoding);
        return enc;
    }

    final Encoding checkEncoding(EncodingCapable other) {
        Encoding enc = isCompatibleWith(other);
        if (enc == null) throw getRuntime().newEncodingCompatibilityError("incompatible character encodings: " + 
                                value.encoding + " and " + other.getEncoding());
        return enc;
    }

    private Encoding checkDummyEncoding() {
        Encoding enc = value.encoding;
        if (enc.isDummy()) throw getRuntime().newEncodingCompatibilityError(
                "incompatible encoding with this operation: " + enc);
        return enc;
    }

    private boolean isComparableWith(RubyString other) {
        ByteList otherValue = other.value;
        if (value.encoding == otherValue.encoding || 
            value.realSize == 0 || otherValue.realSize == 0) return true;
        return isComparableViaCodeRangeWith(other);
    }

    private boolean isComparableViaCodeRangeWith(RubyString other) {
        int cr1 = scanForCodeRange();
        int cr2 = other.scanForCodeRange();

        if (cr1 == CR_7BIT && (cr2 == CR_7BIT || other.value.encoding.isAsciiCompatible())) return true;
        if (cr2 == CR_7BIT && value.encoding.isAsciiCompatible()) return true;
        return false;
    }

    private int strLength(Encoding enc) {
        if (singleByteOptimizable(enc)) return value.realSize;
        return strLength(value, enc);
    }

    final int strLength() {
        if (singleByteOptimizable()) return value.realSize;
        return strLength(value);
    }

    private int strLength(ByteList bytes) {
        return strLength(bytes, bytes.encoding);
    }

    private int strLength(ByteList bytes, Encoding enc) {
        long lencr = strLengthWithCodeRange(bytes, enc);
        int cr = unpackArg(lencr);
        if (cr != 0) setCodeRange(cr);
        return unpackResult(lencr);
    }

    private int subLength(int pos) {
        if (singleByteOptimizable() || pos < 0) return pos;
        return StringSupport.strLength(value.encoding, value.bytes, value.begin, value.begin + pos);
    }

    /** short circuit for String key comparison
     * 
     */
    @Override
    public final boolean eql(IRubyObject other) {
        Ruby runtime = getRuntime();
        if (other.getMetaClass() != runtime.getString()) return super.eql(other);
        return runtime.is1_9() ? eql19(runtime, other) : eql18(runtime, other);
    }

    private boolean eql18(Ruby runtime, IRubyObject other) {
        return value.equal(((RubyString)other).value);
    }

    private boolean eql19(Ruby runtime, IRubyObject other) {
        RubyString otherString = (RubyString)other;
        return isComparableWith(otherString) && value.equal(((RubyString)other).value);
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

    protected RubyString(Ruby runtime, RubyClass rubyClass, ByteList value, Encoding enc, int cr) {
        this(runtime, rubyClass, value);
        value.encoding = enc;
        flags |= cr;
    }

    protected RubyString(Ruby runtime, RubyClass rubyClass, ByteList value, Encoding enc) {
        this(runtime, rubyClass, value);
        value.encoding = enc;
    }

    protected RubyString(Ruby runtime, RubyClass rubyClass, ByteList value, int cr) {
        this(runtime, rubyClass, value);
        flags |= cr;
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

    public static RubyString newStringLight(Ruby runtime, int size) {
        return new RubyString(runtime, runtime.getString(), new ByteList(size), false);
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
    private static final class EmptyByteListHolder {
        final ByteList bytes;
        final int cr;
        EmptyByteListHolder(Encoding enc) {
            this.bytes = new ByteList(ByteList.NULL_ARRAY, enc);
            this.cr = bytes.encoding.isAsciiCompatible() ? CR_7BIT : CR_VALID;
        }
    }

    private static final EmptyByteListHolder EMPTY_BYTELISTS[] = new EmptyByteListHolder[4];

    static EmptyByteListHolder getEmptyByteList(Encoding enc) {
        int index = enc.getIndex();
        EmptyByteListHolder bytes;
        if (index < EMPTY_BYTELISTS.length && (bytes = EMPTY_BYTELISTS[index]) != null) {
            return bytes;
        }
        return prepareEmptyByteList(enc);
    }

    private static EmptyByteListHolder prepareEmptyByteList(Encoding enc) {
        int index = enc.getIndex();
        if (index >= EMPTY_BYTELISTS.length) {
            EmptyByteListHolder tmp[] = new EmptyByteListHolder[index + 4];
            System.arraycopy(EMPTY_BYTELISTS,0, tmp, 0, EMPTY_BYTELISTS.length);
        }
        return EMPTY_BYTELISTS[index] = new EmptyByteListHolder(enc);
    }

    public static RubyString newEmptyString(Ruby runtime, RubyClass metaClass, Encoding enc) {
        EmptyByteListHolder holder = getEmptyByteList(enc);
        RubyString empty = new RubyString(runtime, metaClass, holder.bytes, holder.cr);
        empty.shareLevel = SHARE_LEVEL_BYTELIST;
        return empty;
    }

    public static RubyString newEmptyString(Ruby runtime, Encoding enc) {
        return newEmptyString(runtime, runtime.getString(), enc);
    }

    public static RubyString newStringNoCopy(Ruby runtime, RubyClass clazz, ByteList bytes, Encoding enc, int cr) {
        return new RubyString(runtime, clazz, bytes, enc, cr);
    }

    public static RubyString newStringNoCopy(Ruby runtime, ByteList bytes, Encoding enc, int cr) {
        return newStringNoCopy(runtime, runtime.getString(), bytes, enc, cr);
    }

    public static RubyString newUsAsciiStringNoCopy(Ruby runtime, ByteList bytes) {
        return newStringNoCopy(runtime, bytes, USASCIIEncoding.INSTANCE, CR_7BIT);
    }

    public static RubyString newUsAsciiStringShared(Ruby runtime, ByteList bytes) {
        RubyString str = newStringNoCopy(runtime, bytes, USASCIIEncoding.INSTANCE, CR_7BIT);
        str.shareLevel = SHARE_LEVEL_BYTELIST;
        return str;
    }
    
    public static RubyString newUsAsciiStringShared(Ruby runtime, byte[] bytes, int start, int length) {
        byte[] copy = new byte[length];
        System.arraycopy(bytes, start, copy, 0, length);
        return newUsAsciiStringShared(runtime, new ByteList(copy, false));
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
        dup.flags |= flags & (CR_MASK | TAINTED_F);

        return dup;
    }

    /* rb_str_subseq */
    public final RubyString makeShared(Ruby runtime, int index, int len) {
        if (len == 0) {
            RubyString empty = newEmptyString(runtime, getMetaClass());
            empty.infectBy(this);
            return empty;
        }

        if (shareLevel == SHARE_LEVEL_NONE) shareLevel = SHARE_LEVEL_BUFFER;
        RubyString shared = new RubyString(runtime, getMetaClass(), value.makeShared(index, len));
        shared.shareLevel = SHARE_LEVEL_BUFFER;

        shared.infectBy(this);
        return shared;
    }

    public final RubyString makeShared19(Ruby runtime, int index, int len) {
        return makeShared19(runtime, value, index, len);
    }

    private RubyString makeShared19(Ruby runtime, ByteList value, int index, int len) {
        Encoding enc = value.encoding;
        if (len == 0) {
            RubyString empty = newEmptyString(runtime, getMetaClass(), enc);
            empty.infectBy(this);
            return empty;
        }

        if (shareLevel == SHARE_LEVEL_NONE) shareLevel = SHARE_LEVEL_BUFFER;
        RubyString shared = new RubyString(runtime, getMetaClass(), value.makeShared(index, len));
        shared.shareLevel = SHARE_LEVEL_BUFFER;

        shared.copyCodeRangeForSubstr(this, enc); // no need to assign encoding, same bytelist shared
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

    private final void modifyCheck(byte[] b, int len, Encoding enc) {
        if (value.bytes != b || value.realSize != len || value.encoding != enc) throw getRuntime().newRuntimeError("string modified");
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

    public final void modify19() {
        modify();
        clearCodeRange();
    }

    private void modifyAndKeepCodeRange() {
        modify();
        keepCodeRange();
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
    
    public final void modify19(int length) {
        modify(length);
        clearCodeRange();
    }
    
    final void view(ByteList bytes) {
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

    @Override
    public RubyString asString() {
        return this;
    }

    @Override
    public IRubyObject checkStringType() {
        return this;
    }

    @JRubyMethod(name = "try_convert", meta = true, compat = CompatVersion.RUBY1_9)
    public static IRubyObject try_convert(ThreadContext context, IRubyObject recv, IRubyObject str) {
        return str.checkStringType();
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
    @JRubyMethod(name = "<=>", compat = CompatVersion.RUBY1_8)
    public IRubyObject op_cmp(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyString) {
            return context.getRuntime().newFixnum(op_cmp((RubyString)other));
        }
        return op_cmpCommon(context, other);
    }

    @JRubyMethod(name = "<=>", compat = CompatVersion.RUBY1_9)
    public IRubyObject op_cmp19(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyString) {
            return context.getRuntime().newFixnum(op_cmp19((RubyString)other));
        }
        return op_cmpCommon(context, other);
    }

    private IRubyObject op_cmpCommon(ThreadContext context, IRubyObject other) {
        Ruby runtime = context.getRuntime();
        // deal with case when "other" is not a string
        if (other.respondsTo("to_str") && other.respondsTo("<=>")) {
            IRubyObject result = other.callMethod(context, "<=>", this);
            if (result.isNil()) return result;
            if (result instanceof RubyFixnum) {
                return RubyFixnum.newFixnum(runtime, -((RubyFixnum)result).getLongValue());
            } else {
                return RubyFixnum.zero(runtime).callMethod(context, "-", result);
            }
        }
        return runtime.getNil();        
    }
        
    /** rb_str_equal
     * 
     */
    @JRubyMethod(name = "==", compat = CompatVersion.RUBY1_8)
    @Override
    public IRubyObject op_equal(ThreadContext context, IRubyObject other) {
        Ruby runtime = context.getRuntime();
        if (this == other) return runtime.getTrue();
        if (other instanceof RubyString) {
            return value.equal(((RubyString)other).value) ? runtime.getTrue() : runtime.getFalse();    
        }
        return op_equalCommon(context, other);
    }

    @JRubyMethod(name = "==", compat = CompatVersion.RUBY1_9)
    public IRubyObject op_equal19(ThreadContext context, IRubyObject other) {
        Ruby runtime = context.getRuntime();
        if (this == other) return runtime.getTrue();
        if (other instanceof RubyString) {
            RubyString otherString = (RubyString)other;
            return isComparableWith(otherString) && value.equal(otherString.value) ? runtime.getTrue() : runtime.getFalse();    
        }
        return op_equalCommon(context, other);
    }

    private IRubyObject op_equalCommon(ThreadContext context, IRubyObject other) {
        Ruby runtime = context.getRuntime();
        if (!other.respondsTo("to_str")) return runtime.getFalse();
        return other.callMethod(context, "==", this).isTrue() ? runtime.getTrue() : runtime.getFalse();
    }

    @JRubyMethod(name = "+", required = 1, compat = CompatVersion.RUBY1_8)
    public IRubyObject op_plus(ThreadContext context, IRubyObject other) {
        RubyString str = other.convertToString();
        RubyString resultStr = newString(context.getRuntime(), addByteLists(value, str.value));
        if (isTaint() || str.isTaint()) resultStr.setTaint(true);
        return resultStr;
    }

    @JRubyMethod(name = "+", required = 1, compat = CompatVersion.RUBY1_9)
    public IRubyObject op_plus19(ThreadContext context, IRubyObject other) {
        RubyString str = other.convertToString();
        Encoding enc = checkEncoding(str);
        RubyString resultStr = newStringNoCopy(context.getRuntime(), addByteLists(value, str.value),
                                    enc, codeRangeAnd(getCodeRange(), str.getCodeRange()));
        if (isTaint() || str.isTaint()) resultStr.setTaint(true);
        return resultStr;
    }

    private ByteList addByteLists(ByteList value1, ByteList value2) {
        ByteList result = new ByteList(value1.realSize + value2.realSize);
        result.realSize = value1.realSize + value2.realSize;
        System.arraycopy(value1.bytes, value1.begin, result.bytes, 0, value1.realSize);
        System.arraycopy(value2.bytes, value2.begin, result.bytes, value1.realSize, value2.realSize);
        return result;
    }

    @JRubyMethod(name = "*", required = 1, compat = CompatVersion.RUBY1_8)
    public IRubyObject op_mul(ThreadContext context, IRubyObject other) {
        return multiplyByteList(context, other);
    }

    @JRubyMethod(name = "*", required = 1, compat = CompatVersion.RUBY1_9)
    public IRubyObject op_mul19(ThreadContext context, IRubyObject other) {
        RubyString result = multiplyByteList(context, other);
        result.copyCodeRangeForSubstr(this, result.value.encoding = value.encoding);
        return result;
    }

    private RubyString multiplyByteList(ThreadContext context, IRubyObject arg) {
        int len = RubyNumeric.num2int(arg);
        if (len < 0) throw context.getRuntime().newArgumentError("negative argument");

        // we limit to int because ByteBuffer can only allocate int sizes
        if (len > 0 && Integer.MAX_VALUE / len < value.realSize) {
            throw context.getRuntime().newArgumentError("argument too big");
        }

        ByteList bytes = new ByteList(len *= value.realSize);
        if (len > 0) {
            bytes.realSize = len;
            int n = value.realSize;
            System.arraycopy(value.bytes, value.begin, bytes.bytes, 0, n);
            while (n <= len >> 1) {
                System.arraycopy(bytes.bytes, 0, bytes.bytes, n, n);
                n <<= 1;
            }
            System.arraycopy(bytes.bytes, 0, bytes.bytes, n, len - n);
        }
        RubyString result = new RubyString(context.getRuntime(), getMetaClass(), bytes);
        result.setTaint(isTaint());
        return result;
    }

    @JRubyMethod(name = "%", required = 1)
    public IRubyObject op_format(ThreadContext context, IRubyObject arg) {
        IRubyObject tmp = arg.checkArrayType();
        if (tmp.isNil()) tmp = arg;

        // FIXME: Should we make this work with platform's locale,
        // or continue hardcoding US?
        ByteList out = new ByteList(value.realSize);
        boolean tainted = Sprintf.sprintf(out, Locale.US, value, tmp);
        RubyString str = newString(context.getRuntime(), out);

        str.setTaint(tainted || isTaint());
        return str;
    }

    @JRubyMethod(name = "hash")
    @Override
    public RubyFixnum hash() {
        return getRuntime().newFixnum(value.hashCode());
    }

    @Override
    public int hashCode() {
        // TODO: encoding should affect hashCode
        return value.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;

        if (other instanceof RubyString) {
            if (((RubyString) other).value.equal(value)) return true;
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
    public final int op_cmp(RubyString other) {
        return value.cmp(other.value);
    }

    public final int op_cmp19(RubyString other) {
        int ret = value.cmp(other.value);
        if (ret == 0 && !isComparableWith(other)) {
            return value.encoding.getIndex() > other.value.encoding.getIndex() ? 1 : -1;
        }
        return ret;
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

    public final RubyString cat(byte[] str) {
        modify(value.realSize + str.length);
        System.arraycopy(str, 0, value.bytes, value.begin + value.realSize, str.length);
        value.realSize += str.length;
        return this;
    }

    public final RubyString cat(byte[] str, int beg, int len) {
        modify(value.realSize + len);        
        System.arraycopy(str, beg, value.bytes, value.begin + value.realSize, len);
        value.realSize += len;
        return this;
    }

    public final RubyString cat(RubyString str) {
        ByteList strValue = str.value;
        modify(value.realSize + strValue.realSize);
        int strCr = str.getCodeRange();
        strCr = cat(strValue.bytes, strValue.begin, strValue.realSize, strValue.encoding, strCr, strCr);
        infectBy(str);
        str.setCodeRange(strCr);
        return this;
    }

    public final RubyString cat(ByteList str) {
        modify(value.realSize + str.realSize);
        System.arraycopy(str.bytes, str.begin, value.bytes, value.begin + value.realSize, str.realSize);
        value.realSize += str.realSize;
        return this;
    }

    public final RubyString cat(byte ch) {
        modify(value.realSize + 1);        
        value.bytes[value.begin + value.realSize] = ch;
        value.realSize++;
        return this;
    }

    public final RubyString cat(int ch) {
        return cat((byte)ch);
    }

    public final RubyString cat(int code, Encoding enc) {
        int n = codeLength(getRuntime(), enc, code);
        modify(value.realSize + n);
        enc.codeToMbc(code, value.bytes, value.begin + value.realSize);
        value.realSize += n;
        return this;
    }

    public final int cat(byte[]bytes, int p, int len, Encoding enc, int cr, int cr2) {
        modify(value.realSize + len);
        int toCr = getCodeRange();
        Encoding toEnc = value.encoding;
        
        if (toEnc == enc) {
            if (toCr == CR_UNKNOWN || (toEnc == ASCIIEncoding.INSTANCE && toCr != CR_7BIT)) { 
                cr = CR_UNKNOWN;
            } else if (cr == CR_UNKNOWN) {
                cr = codeRangeScan(enc, bytes, p, len);
            }
        } else {
            if (!toEnc.isAsciiCompatible() || !enc.isAsciiCompatible()) {
                if (len == 0) return cr2;
                if (value.realSize == 0) {
                    System.arraycopy(bytes, p, value.bytes, value.begin + value.realSize, len);
                    value.realSize += len;
                    setEncodingAndCodeRange(enc, cr);
                    return cr2;
                }
                throw getRuntime().newEncodingCompatibilityError("incompatible character encodings: " + toEnc + " and " + enc);
            }
            if (cr == CR_UNKNOWN) cr = codeRangeScan(enc, bytes, p, len);
            if (toCr == CR_UNKNOWN) {
                if (toEnc == ASCIIEncoding.INSTANCE || cr != CR_7BIT) toCr = scanForCodeRange(); 
            }
        }
        if (cr2 != 0) cr2 = cr;

        if (toEnc != enc && toCr != CR_7BIT && cr != CR_7BIT) {        
            throw getRuntime().newEncodingCompatibilityError("incompatible character encodings: " + toEnc + " and " + enc);
        }
        
        final int resCr;
        final Encoding resEnc;
        if (toCr == CR_UNKNOWN) {
            resEnc = toEnc;
            resCr = CR_UNKNOWN;
        } else if (toCr == CR_7BIT) {
            if (cr == CR_7BIT) {
                resEnc = toEnc == ASCIIEncoding.INSTANCE ? toEnc : enc;
                resCr = CR_7BIT;
            } else {
                resEnc = enc;
                resCr = cr;
            }
        } else if (toCr == CR_VALID) {
            resEnc = toEnc;
            resCr = toCr;
        } else {
            resEnc = toEnc;
            resCr = len > 0 ? CR_BROKEN : toCr;
        }
        
        if (len < 0) throw getRuntime().newArgumentError("negative string size (or size too big)");            

        System.arraycopy(bytes, p, value.bytes, value.begin + value.realSize, len);
        value.realSize += len;
        setEncodingAndCodeRange(resEnc, resCr);

        return cr2;
    }

    public final int cat(byte[]bytes, int p, int len, Encoding enc) {
        return cat(bytes, p, len, enc, CR_UNKNOWN, 0);
    }

    public final RubyString catAscii(byte[]bytes, int p, int len) {
        Encoding enc = value.encoding;
        if (enc.isAsciiCompatible()) {
            cat(bytes, p, len, enc, CR_7BIT, 0);
        } else {
            byte buf[] = new byte[enc.maxLength()];
            int end = p + len;
            while (p < end) {
                int c = bytes[p];
                int cl = codeLength(getRuntime(), enc, c);
                enc.codeToMbc(c, buf, 0);
                cat(buf, 0, cl, enc, CR_VALID, 0);
                p++;
            }
        }
        return this;
    }

    /** rb_str_replace_m
     *
     */
    @JRubyMethod(name = {"replace", "initialize_copy"}, required = 1, compat = CompatVersion.RUBY1_8)
    public IRubyObject replace(IRubyObject other) {
        if (this == other) return this;
        replaceCommon(other);
        return this;
    }

    @JRubyMethod(name = {"replace", "initialize_copy"}, required = 1, compat = CompatVersion.RUBY1_9)
    public RubyString replace19(IRubyObject other) {
        if (this == other) return this;
        setCodeRange(replaceCommon(other).getCodeRange()); // encoding doesn't have to be copied.
        return this;
    }

    private RubyString replaceCommon(IRubyObject other) {
        modifyCheck();
        RubyString otherStr =  other.convertToString();
        otherStr.shareLevel = shareLevel = SHARE_LEVEL_BYTELIST;
        value = otherStr.value;
        infectBy(other);
        return otherStr;
    }

    @JRubyMethod(name = "clear", compat = CompatVersion.RUBY1_9)
    public RubyString clear() {
        Encoding enc = value.encoding;

        EmptyByteListHolder holder = getEmptyByteList(enc); 
        value = holder.bytes;
        shareLevel = SHARE_LEVEL_BYTELIST;
        setCodeRange(holder.cr);
        return this;
    }

    @JRubyMethod(name = "reverse", compat = CompatVersion.RUBY1_8)
    public IRubyObject reverse(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        if (value.realSize <= 1) return strDup(context.getRuntime());

        byte[]bytes = value.bytes;
        int p = value.begin;
        int len = value.realSize;
        byte[]obytes = new byte[len];

        for (int i = 0; i <= len >> 1; i++) {
            obytes[i] = bytes[p + len - i - 1];
            obytes[len - i - 1] = bytes[p + i];
        }

        return new RubyString(runtime, getMetaClass(), new ByteList(obytes, false)).infectBy(this);
    }

    @JRubyMethod(name = "reverse", compat = CompatVersion.RUBY1_9)
    public IRubyObject reverse19(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        if (value.realSize <= 1) return strDup(context.getRuntime());

        byte[]bytes = value.bytes;
        int p = value.begin;
        int len = value.realSize;
        byte[]obytes = new byte[len];

        boolean single = true;
        Encoding enc = value.encoding;
        // this really needs to be inlined here
        if (singleByteOptimizable(enc)) {
            for (int i = 0; i <= len >> 1; i++) {
                obytes[i] = bytes[p + len - i - 1];
                obytes[len - i - 1] = bytes[p + i];
            }
        } else {
            int end = p + len;
            int op = len;
            while (p < end) {
                int cl = StringSupport.length(enc, bytes, p, end);
                if (cl > 1 || (bytes[p] & 0x80) != 0) {
                    single = false;
                    op -= cl;
                    System.arraycopy(bytes, p, obytes, op, cl);
                    p += cl;
                } else {
                    obytes[--op] = bytes[p++];
                }
            }
        }

        RubyString result = new RubyString(runtime, getMetaClass(), new ByteList(obytes, false));

        if (getCodeRange() == CR_UNKNOWN) setCodeRange(single ? CR_7BIT : CR_VALID);
        result.copyCodeRangeForSubstr(this, result.value.encoding = value.encoding);
        return result.infectBy(this);
    }

    @JRubyMethod(name = "reverse!", compat = CompatVersion.RUBY1_8)
    public RubyString reverse_bang(ThreadContext context) {
        if (value.realSize > 1) {
            modify();
            byte[]bytes = value.bytes;
            int p = value.begin;
            int len = value.realSize;
            for (int i = 0; i < len >> 1; i++) {
                byte b = bytes[p + i];
                bytes[p + i] = bytes[p + len - i - 1];
                bytes[p + len - i - 1] = b;
            }
        }

        return this;
    }

    @JRubyMethod(name = "reverse!", compat = CompatVersion.RUBY1_9)
    public RubyString reverse_bang19(ThreadContext context) {
        if (value.realSize > 1) {
            modify();
            byte[]bytes = value.bytes;
            int p = value.begin;
            int len = value.realSize;
            
            Encoding enc = value.encoding;
            // this really needs to be inlined here
            if (singleByteOptimizable(enc)) {
                for (int i = 0; i < len >> 1; i++) {
                    byte b = bytes[p + i];
                    bytes[p + i] = bytes[p + len - i - 1];
                    bytes[p + len - i - 1] = b;
                }
            } else {
                int end = p + len;
                int op = len;
                byte[]obytes = new byte[len];
                boolean single = true;
                while (p < end) {
                    int cl = StringSupport.length(enc, bytes, p, end);
                    if (cl > 1 || (bytes[p] & 0x80) != 0) {
                        single = false;
                        op -= cl;
                        System.arraycopy(bytes, p, obytes, op, cl);
                        p += cl;
                    } else {
                        obytes[--op] = bytes[p++];
                    }
                }
                value.bytes = obytes;
                if (getCodeRange() == CR_UNKNOWN) setCodeRange(single ? CR_7BIT : CR_VALID);
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

    @JRubyMethod(name = "casecmp", compat = CompatVersion.RUBY1_8)
    public IRubyObject casecmp(ThreadContext context, IRubyObject other) {
        return RubyFixnum.newFixnum(context.getRuntime(), value.caseInsensitiveCmp(other.convertToString().value));
    }

    @JRubyMethod(name = "casecmp", compat = CompatVersion.RUBY1_9)
    public IRubyObject casecmp19(ThreadContext context, IRubyObject other) {
        Ruby runtime = context.getRuntime();
        RubyString otherStr = other.convertToString();
        Encoding enc = isCompatibleWith(otherStr);
        if (enc == null) return runtime.getNil();
        
        if (singleByteOptimizable() && otherStr.singleByteOptimizable()) {
            return RubyFixnum.newFixnum(runtime, value.caseInsensitiveCmp(otherStr.value));
        } else {
            return multiByteCasecmp(runtime, enc, value, otherStr.value);
        }
    }

    private IRubyObject multiByteCasecmp(Ruby runtime, Encoding enc, ByteList value, ByteList otherValue) {
        byte[]bytes = value.bytes;
        int p = value.begin;
        int end = p + value.realSize;

        byte[]obytes = otherValue.bytes;
        int op = otherValue.begin;
        int oend = op + otherValue.realSize;

        while (p < end && op < oend) {
            final int c, oc;
            if (enc.isAsciiCompatible()) {
                c = bytes[p] & 0xff;
                oc = obytes[op] & 0xff;
            } else {
                c = StringSupport.preciseCodePoint(enc, bytes, p, end);
                oc = StringSupport.preciseCodePoint(enc, obytes, op, oend);                
            }

            int cl, ocl;
            if (Encoding.isAscii(c) && Encoding.isAscii(oc)) {
                if (AsciiTables.ToUpperCaseTable[c] != AsciiTables.ToUpperCaseTable[oc]) {
                    return c < oc ? RubyFixnum.minus_one(runtime) : RubyFixnum.one(runtime); 
                }
                cl = ocl = 1;
            } else {
                cl = StringSupport.length(enc, bytes, p, end);
                ocl = StringSupport.length(enc, obytes, op, oend);
                // TODO: opt for 2 and 3 ?
                int ret = StringSupport.caseCmp(bytes, p, obytes, op, cl < ocl ? cl : ocl);
                if (ret != 0) return ret < 0 ? RubyFixnum.minus_one(runtime) : RubyFixnum.one(runtime);
                if (cl != ocl) return cl < ocl ? RubyFixnum.minus_one(runtime) : RubyFixnum.one(runtime);
            }

            p += cl;
            op += ocl;
        }
        if (end - p == oend - op) return RubyFixnum.zero(runtime);
        return end - p > oend - op ? RubyFixnum.one(runtime) : RubyFixnum.minus_one(runtime);
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
    @JRubyMethod(name = "match", compat = CompatVersion.RUBY1_8)
    public IRubyObject match(ThreadContext context, IRubyObject pattern) {
        return getPattern(pattern).callMethod(context, "match", this);
    }

    @JRubyMethod(name = "match", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject match19(ThreadContext context, IRubyObject pattern, Block block) {
        IRubyObject result = getPattern(pattern).callMethod(context, "match", this);
        return block.isGiven() ? block.yield(context, result) : result;
    }

    /** rb_str_capitalize / rb_str_capitalize_bang
     *
     */
    @JRubyMethod(name = "capitalize", compat = CompatVersion.RUBY1_8)
    public IRubyObject capitalize(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.capitalize_bang(context);
        return str;
    }

    @JRubyMethod(name = "capitalize!", compat = CompatVersion.RUBY1_8)
    public IRubyObject capitalize_bang(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) {
            modifyCheck();
            return runtime.getNil();
        }

        modify();

        int s = value.begin;
        int end = s + value.realSize;
        byte[]bytes = value.bytes;
        boolean modify = false;
        
        int c = bytes[s] & 0xff;
        if (ASCII.isLower(c)) {
            bytes[s] = AsciiTables.ToUpperCaseTable[c];
            modify = true;
        }

        while (++s < end) {
            c = bytes[s] & 0xff;
            if (ASCII.isUpper(c)) {
                bytes[s] = AsciiTables.ToLowerCaseTable[c];
                modify = true;
            }
        }

        return modify ? this : runtime.getNil();
    }

    @JRubyMethod(name = "capitalize", compat = CompatVersion.RUBY1_9)
    public IRubyObject capitalize19(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.capitalize_bang19(context);
        return str;
    }

    @JRubyMethod(name = "capitalize!", compat = CompatVersion.RUBY1_9)
    public IRubyObject capitalize_bang19(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        Encoding enc = checkDummyEncoding();

        if (value.realSize == 0) {
            modifyCheck();
            return runtime.getNil();
        }

        modifyAndKeepCodeRange();

        int s = value.begin;
        int end = s + value.realSize;
        byte[]bytes = value.bytes;
        boolean modify = false;

        int c = codePoint(runtime, enc, bytes, s, end);
        if (enc.isLower(c)) {
            enc.codeToMbc(toUpper(enc, c), bytes, s);
            modify = true;
        }

        s += codeLength(runtime, enc, c);
        while (s < end) {
            c = codePoint(runtime, enc, bytes, s, end);
            if (enc.isUpper(c)) {
                enc.codeToMbc(toLower(enc, c), bytes, s);
                modify = true;
            }
            s += codeLength(runtime, enc, c);
        }

        return modify ? this : runtime.getNil();
    }

    @JRubyMethod(name = ">=", compat = CompatVersion.RUBY1_8)
    public IRubyObject op_ge(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyString) return context.getRuntime().newBoolean(op_cmp((RubyString) other) >= 0);
        return RubyComparable.op_ge(context, this, other);
    }

    @JRubyMethod(name = ">=", compat = CompatVersion.RUBY1_9)
    public IRubyObject op_ge19(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyString) return context.getRuntime().newBoolean(op_cmp19((RubyString) other) >= 0);
        return RubyComparable.op_ge(context, this, other);
    }

    @JRubyMethod(name = ">", compat = CompatVersion.RUBY1_8)
    public IRubyObject op_gt(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyString) return context.getRuntime().newBoolean(op_cmp((RubyString) other) > 0);
        return RubyComparable.op_gt(context, this, other);
    }

    @JRubyMethod(name = ">", compat = CompatVersion.RUBY1_9)
    public IRubyObject op_gt19(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyString) return context.getRuntime().newBoolean(op_cmp19((RubyString) other) > 0);
        return RubyComparable.op_gt(context, this, other);
    }

    @JRubyMethod(name = "<=", compat = CompatVersion.RUBY1_8)
    public IRubyObject op_le(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyString) return context.getRuntime().newBoolean(op_cmp((RubyString) other) <= 0);
        return RubyComparable.op_le(context, this, other);
    }

    @JRubyMethod(name = "<=", compat = CompatVersion.RUBY1_9)
    public IRubyObject op_le19(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyString) return context.getRuntime().newBoolean(op_cmp19((RubyString) other) <= 0);
        return RubyComparable.op_le(context, this, other);
    }

    @JRubyMethod(name = "<", compat = CompatVersion.RUBY1_8)
    public IRubyObject op_lt(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyString) return context.getRuntime().newBoolean(op_cmp((RubyString) other) < 0);
        return RubyComparable.op_lt(context, this, other);
    }

    @JRubyMethod(name = "<", compat = CompatVersion.RUBY1_9)
    public IRubyObject op_lt19(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyString) return context.getRuntime().newBoolean(op_cmp19((RubyString) other) < 0);
        return RubyComparable.op_lt(context, this, other);
    }

    @JRubyMethod(name = "eql?", compat = CompatVersion.RUBY1_8)
    public IRubyObject str_eql_p(ThreadContext context, IRubyObject other) {
        Ruby runtime = context.getRuntime();
        if (other instanceof RubyString && value.equal(((RubyString)other).value)) return runtime.getTrue();
        return runtime.getFalse();
    }

    @JRubyMethod(name = "eql?", compat = CompatVersion.RUBY1_9)
    public IRubyObject str_eql_p19(ThreadContext context, IRubyObject other) {
        Ruby runtime = context.getRuntime();
        if (other instanceof RubyString) {
            RubyString otherString = (RubyString)other;
            if (isComparableWith(otherString) && value.equal(otherString.value)) return runtime.getTrue();
        }
        return runtime.getFalse();
    }

    /** rb_str_upcase / rb_str_upcase_bang
     *
     */
    @JRubyMethod(name = "upcase", compat = CompatVersion.RUBY1_8)
    public RubyString upcase(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.upcase_bang(context);
        return str;
    }

    @JRubyMethod(name = "upcase!", compat = CompatVersion.RUBY1_8)
    public IRubyObject upcase_bang(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) {
            modifyCheck();
            return runtime.getNil();
        }
        modify();
        return singleByteUpcase(runtime, value.bytes, value.begin, value.begin + value.realSize); 
    }

    @JRubyMethod(name = "upcase", compat = CompatVersion.RUBY1_9)
    public RubyString upcase19(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.upcase_bang19(context);
        return str;
    }

    @JRubyMethod(name = "upcase!", compat = CompatVersion.RUBY1_9)
    public IRubyObject upcase_bang19(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        Encoding enc = checkDummyEncoding();

        if (value.realSize == 0) {
            modifyCheck();
            return runtime.getNil();
        }

        modifyAndKeepCodeRange();

        int s = value.begin;
        int end = s + value.realSize;
        byte[]bytes = value.bytes;

        if (singleByteOptimizable(enc)) {
            return singleByteUpcase(runtime, bytes, s, end);
        } else {
            return multiByteUpcase(runtime, enc, bytes, s, end);
        }
    }

    private IRubyObject singleByteUpcase(Ruby runtime, byte[]bytes, int s, int end) {
        boolean modify = false;
        while (s < end) {
            int c = bytes[s] & 0xff;
            if (ASCII.isLower(c)) {
                bytes[s] = AsciiTables.ToUpperCaseTable[c];
                modify = true;
            }
            s++;
        }
        return modify ? this : runtime.getNil();
    }

    private IRubyObject multiByteUpcase(Ruby runtime, Encoding enc, byte[]bytes, int s, int end) {
        boolean modify = false;
        int c;
        while (s < end) {
            if (enc.isAsciiCompatible() && Encoding.isAscii(c = bytes[s] & 0xff)) {
                if (ASCII.isLower(c)) {
                    bytes[s] = AsciiTables.ToUpperCaseTable[c];
                    modify = true;
                }
                s++;
            } else {
                c = codePoint(runtime, enc, bytes, s, end);
                if (enc.isLower(c)) {
                    enc.codeToMbc(toUpper(enc, c), bytes, s);
                    modify = true;
                }
                s += codeLength(runtime, enc, c);
            }
        }
        return modify ? this : runtime.getNil();        
    }

    /** rb_str_downcase / rb_str_downcase_bang
     *
     */
    @JRubyMethod(name = "downcase", compat = CompatVersion.RUBY1_8)
    public RubyString downcase(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.downcase_bang(context);
        return str;
    }

    @JRubyMethod(name = "downcase!", compat = CompatVersion.RUBY1_8)
    public IRubyObject downcase_bang(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) {
            modifyCheck();
            return runtime.getNil();
        }

        modify();
        return singleByteDowncase(runtime, value.bytes, value.begin, value.begin + value.realSize);
    }

    @JRubyMethod(name = "downcase", compat = CompatVersion.RUBY1_9)
    public RubyString downcase19(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.downcase_bang19(context);
        return str;
    }

    @JRubyMethod(name = "downcase!", compat = CompatVersion.RUBY1_9)
    public IRubyObject downcase_bang19(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        Encoding enc = checkDummyEncoding();

        if (value.realSize == 0) {
            modifyCheck();
            return runtime.getNil();
        }

        modifyAndKeepCodeRange();

        int s = value.begin;
        int end = s + value.realSize;
        byte[]bytes = value.bytes;

        if (singleByteOptimizable(enc)) {
            return singleByteDowncase(runtime, bytes, s, end);
        } else {
            return multiByteDowncase(runtime, enc, bytes, s, end);
        }
    }

    private IRubyObject singleByteDowncase(Ruby runtime, byte[]bytes, int s, int end) {
        boolean modify = false;
        while (s < end) {
            int c = bytes[s] & 0xff;
            if (ASCII.isUpper(c)) {
                bytes[s] = AsciiTables.ToLowerCaseTable[c];
                modify = true;
            }
            s++;
        }
        return modify ? this : runtime.getNil();
    }

    private IRubyObject multiByteDowncase(Ruby runtime, Encoding enc, byte[]bytes, int s, int end) {
        boolean modify = false;
        int c;
        while (s < end) {
            if (enc.isAsciiCompatible() && Encoding.isAscii(c = bytes[s] & 0xff)) {
                if (ASCII.isUpper(c)) {
                    bytes[s] = AsciiTables.ToLowerCaseTable[c];
                    modify = true;
                }
                s++;
            } else {
                c = codePoint(runtime, enc, bytes, s, end);
                if (enc.isUpper(c)) {
                    enc.codeToMbc(toLower(enc, c), bytes, s);
                    modify = true;
                }
                s += codeLength(runtime, enc, c);
            }
        }
        return modify ? this : runtime.getNil();        
    }


    /** rb_str_swapcase / rb_str_swapcase_bang
     *
     */
    @JRubyMethod(name = "swapcase", compat = CompatVersion.RUBY1_8)
    public RubyString swapcase(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.swapcase_bang(context);
        return str;
    }

    @JRubyMethod(name = "swapcase!", compat = CompatVersion.RUBY1_8)
    public IRubyObject swapcase_bang(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) {
            modifyCheck();
            return runtime.getNil();        
        }
        modify();
        return singleByteSwapcase(runtime, value.bytes, value.begin, value.begin + value.realSize);
    }

    @JRubyMethod(name = "swapcase", compat = CompatVersion.RUBY1_9)
    public RubyString swapcase19(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.swapcase_bang19(context);
        return str;
    }

    @JRubyMethod(name = "swapcase!", compat = CompatVersion.RUBY1_9)
    public IRubyObject swapcase_bang19(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        Encoding enc = checkDummyEncoding();
        if (value.realSize == 0) {
            modifyCheck();
            return runtime.getNil();        
        }
        modifyAndKeepCodeRange();

        int s = value.begin;
        int end = s + value.realSize;
        byte[]bytes = value.bytes;

        if (singleByteOptimizable(enc)) {
            return singleByteSwapcase(runtime, bytes, s, end);
        } else {
            return multiByteSwapcase(runtime, enc, bytes, s, end);
        }
    }

    private IRubyObject singleByteSwapcase(Ruby runtime, byte[]bytes, int s, int end) {
        boolean modify = false;
        while (s < end) {
            int c = bytes[s] & 0xff;
            if (ASCII.isUpper(c)) {
                bytes[s] = AsciiTables.ToLowerCaseTable[c];
                modify = true;
            } else if (ASCII.isLower(c)) {
                bytes[s] = AsciiTables.ToUpperCaseTable[c];
                modify = true;
            }
            s++;
        }

        return modify ? this : runtime.getNil();
    }

    private IRubyObject multiByteSwapcase(Ruby runtime, Encoding enc, byte[]bytes, int s, int end) {
        boolean modify = false;
        while (s < end) {
            int c = codePoint(runtime, enc, bytes, s, end);
            if (enc.isUpper(c)) {
                enc.codeToMbc(toLower(enc, c), bytes, s);
                modify = true;
            } else if (enc.isLower(c)) {
                enc.codeToMbc(toUpper(enc, c), bytes, s);
                modify = true;
            }
            s += codeLength(runtime, enc, c);
        }

        return modify ? this : runtime.getNil();
    }

    /** rb_str_dump
     *
     */
    @JRubyMethod(name = "dump", compat = CompatVersion.RUBY1_8)
    public IRubyObject dump() {
        return dumpCommon(false);
    }

    @JRubyMethod(name = "dump", compat = CompatVersion.RUBY1_9)
    public IRubyObject dump19() {
        return dumpCommon(true);
    }

    private IRubyObject dumpCommon(boolean is1_9) {
        Ruby runtime = getRuntime();
        ByteList buf = null;
        Encoding enc = value.encoding;

        int p = value.begin;
        int end = p + value.realSize;
        byte[]bytes = value.bytes;
        
        int len = 2;
        while (p < end) {
            int c = bytes[p++] & 0xff;

            switch (c) {
            case '"':case '\\':case '\n':case '\r':case '\t':case '\f':
            case '\013': case '\010': case '\007': case '\033':
                len += 2;
                break;
            case '#':
                len += isEVStr(bytes, p, end) ? 2 : 1;
                break;
            default:
                if (ASCII.isPrint(c)) {
                    len++;
                } else {
                    if (is1_9 && enc instanceof UTF8Encoding) {
                        int n = StringSupport.preciseLength(enc, bytes, p - 1, end) - 1;
                        if (n > 0) {
                            if (buf == null) buf = new ByteList();
                            int cc = codePoint(runtime, enc, bytes, p - 1, end);
                            Sprintf.sprintf(runtime, buf, "%x", cc);
                            len += buf.realSize + 4;
                            buf.realSize = 0;
                            p += n;
                            break;
                        }
                    }
                    len += 4;
                }
                break;
            }
        }

        if (is1_9 && !enc.isAsciiCompatible()) {
            len += ".force_encoding(\"".length() + enc.getName().length + "\")".length();
        }

        ByteList outBytes = new ByteList(len);
        byte out[] = outBytes.bytes;
        int q = 0;
        p = value.begin;
        end = p + value.realSize;

        out[q++] = '"';
        while (p < end) {
            int c = bytes[p++] & 0xff;
            if (c == '"' || c == '\\') {
                out[q++] = '\\';
                out[q++] = (byte)c;
            } else if (c == '#') {
                if (isEVStr(bytes, p, end)) out[q++] = '\\';
                out[q++] = '#';
            } else if (!is1_9 && ASCII.isPrint(c)) {
                out[q++] = (byte)c;
            } else if (c == '\n') {
                out[q++] = '\\';
                out[q++] = 'n';
            } else if (c == '\r') {
                out[q++] = '\\';
                out[q++] = 'r';
            } else if (c == '\t') {
                out[q++] = '\\';
                out[q++] = 't';
            } else if (c == '\f') {
                out[q++] = '\\';
                out[q++] = 'f';
            } else if (c == '\013') {
                out[q++] = '\\';
                out[q++] = 'v';
            } else if (c == '\010') {
                out[q++] = '\\';
                out[q++] = 'b';
            } else if (c == '\007') {
                out[q++] = '\\';
                out[q++] = 'a';
            } else if (c == '\033') {
                out[q++] = '\\';
                out[q++] = 'e';
            } else if (is1_9 && ASCII.isPrint(c)) {
                out[q++] = (byte)c;
            } else {
                out[q++] = '\\';
                if (is1_9) {
                    if (enc instanceof UTF8Encoding) {
                        int n = StringSupport.preciseLength(enc, bytes, p - 1, end) - 1;
                        if (n > 0) {
                            int cc = codePoint(runtime, enc, bytes, p - 1, end);
                            p += n;
                            outBytes.realSize = q;
                            Sprintf.sprintf(runtime, outBytes, "u{%x}", cc);
                            q = outBytes.realSize;
                            continue;
                        }
                    }
                    outBytes.realSize = q;
                    Sprintf.sprintf(runtime, outBytes, "x%02X", c);
                    q = outBytes.realSize;
                } else {
                    outBytes.realSize = q;
                    Sprintf.sprintf(runtime, outBytes, "%03o", c);
                    q = outBytes.realSize;
                }
            }
        }
        out[q++] = '"';
        outBytes.realSize = q;
        assert out == outBytes.bytes; // must not reallocate

        final RubyString result = new RubyString(runtime, getMetaClass(), outBytes);
        if (is1_9) {
            if (!enc.isAsciiCompatible()) {
                result.cat(".force_encoding(\"".getBytes());
                result.cat(enc.getName());
                result.cat((byte)'"').cat((byte)')');
                enc = ASCII;
            }
            result.associateEncoding(enc);
            result.setCodeRange(CR_7BIT);
        }
        return result.infectBy(this);
    }

    @JRubyMethod(name = "insert")
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
    @JRubyMethod(name = "inspect", compat = CompatVersion.RUBY1_8)
    @Override
    public IRubyObject inspect() {
        return inspectCommon(false);
    }

    @JRubyMethod(name = "inspect", compat = CompatVersion.RUBY1_9)
    public IRubyObject inspect19() {
        return inspectCommon(true);
    }

    private void prefixEscapeCat(int c) {
        cat('\\');
        cat(c);
    }

    private void escapeCodePointCat(Ruby runtime, byte[]bytes, int p , int n) {
        modify();
        for (int q = p - n; q < p; q++) {
            Sprintf.sprintf(runtime, value, "\\x%02X", bytes[q] & 0377);
        }
    }

    final IRubyObject inspectCommon(final boolean is1_9) {
        Ruby runtime = getRuntime();

        byte bytes[] = value.bytes;
        int p = value.begin;
        int end = p + value.realSize;
        RubyString result = new RubyString(runtime, runtime.getString(), new ByteList(end - p));

        final Encoding enc;
        if (is1_9) {
            enc = value.encoding.isAsciiCompatible() ? value.encoding : USASCIIEncoding.INSTANCE;
            result.associateEncoding(enc);
        } else {
            enc = runtime.getKCode().getEncoding();
        }

        result.cat('"');
        while (p < end) {
            int c, n;

            if (is1_9) {
                n = StringSupport.preciseLength(enc, bytes, p, end);
                if (n <= 0) { // Illegal combination
                    p++;
                    n = 1;
                    result.escapeCodePointCat(runtime, bytes, p, n);
                    continue;
                }
                c = codePoint(runtime, enc, bytes, p, end);
                n = codeLength(runtime, enc, c);
                p += n;
            } else {
                c = bytes[p++] & 0xff;
                n = enc.length((byte)c);
            }

            if (!is1_9 && n > 1 && p < end) {
                result.cat(bytes, p - 1, n);
                p += n - 1;
                continue;
            } else if (c == '"'|| c == '\\') {
                result.prefixEscapeCat(c);
                continue;
            } else if (c == '#') {
                if (is1_9) {
                    int cc;
                    if (p < end && StringSupport.preciseLength(enc, bytes, p, end) > 0 &&
                            isEVStr(cc = codePoint(runtime, enc, bytes, p, end))) {
                        result.prefixEscapeCat(cc);
                        continue;
                    }
                } else {
                    if (isEVStr(bytes, p, end)) {
                        result.prefixEscapeCat(c);
                        continue;
                    }
                }
            }

            if (!is1_9 && ASCII.isPrint(c)) {
                result.cat(c);
            } else if (c == '\n') {
                result.prefixEscapeCat('n');
            } else if (c == '\r') {
                result.prefixEscapeCat('r');
            } else if (c == '\t') {
                result.prefixEscapeCat('t');
            } else if (c == '\f') {
                result.prefixEscapeCat('f');
            } else if (c == '\013') {
                result.prefixEscapeCat('v');
            } else if (c == '\010') {
                result.prefixEscapeCat('b');
            } else if (c == '\007') {
                result.prefixEscapeCat('a');
            } else if (c == '\033') {
                result.prefixEscapeCat('e');
            } else if (is1_9 && enc.isPrint(c)) {
                result.cat(bytes, p - n, n, enc);
            } else {
                if (!is1_9) {
                    Sprintf.sprintf(runtime, result.value, "\\%03o", c & 0377);
                } else {
                    result.escapeCodePointCat(runtime, bytes, p, n);
                }
            }
        }
        result.cat('"');
        return result.infectBy(this);
    }

    private boolean isEVStr(byte[]bytes, int p, int end) {
        return p < end ? isEVStr(bytes[p] & 0xff) : false;
    }

    public boolean isEVStr(int c) {
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

    public RubyString append19(IRubyObject other) {
        RubyString otherStr = other.convertToString();
        ByteList otherValue = otherStr.value;

        if (otherValue.realSize > 0) {
            Encoding enc = checkEncoding(otherStr);
            int cr = getCodeRange();
            int otherCr = otherStr.getCodeRange();
            if (otherCr > cr) cr = otherCr;
            cat(otherValue);
            associateEncoding(enc);
            setCodeRange(cr);
            infectBy(other);
            return this;
        }
        return cat(otherStr); // rb_str_buf_append
    }

    /** rb_str_concat
     *
     */
    @JRubyMethod(name = {"concat", "<<"}, compat = CompatVersion.RUBY1_8)
    public RubyString concat(IRubyObject other) {
        if (other instanceof RubyFixnum) {
            long value = ((RubyFixnum) other).getLongValue();
            if (value >= 0 && value < 256) return cat((byte) value);
        }
        return append(other);
    }

    @JRubyMethod(name = {"concat", "<<"}, compat = CompatVersion.RUBY1_9)
    public RubyString concat19(ThreadContext context, IRubyObject other) {
        if (other instanceof RubyFixnum) {
            Encoding enc = value.encoding;
            int c = RubyNumeric.num2int(other);
            int cl = codeLength(context.getRuntime(), enc, c);
            modify(value.realSize + cl);
            enc.codeToMbc(c, value.bytes, value.begin + value.realSize);
            value.realSize += cl;
            return this;
        }
        return append19(other);
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

    /** rb_str_sub
     *
     */
    @JRubyMethod(name = "sub", frame = true, compat = CompatVersion.RUBY1_8)
    public IRubyObject sub(ThreadContext context, IRubyObject arg0, Block block) {
        RubyString str = strDup(context.getRuntime());
        str.sub_bang(context, arg0, block);
        return str;
    }

    @JRubyMethod(name = "sub", frame = true, compat = CompatVersion.RUBY1_8)
    public IRubyObject sub(ThreadContext context, IRubyObject arg0, IRubyObject arg1, Block block) {
        RubyString str = strDup(context.getRuntime());
        str.sub_bang(context, arg0, arg1, block);
        return str;
    }

    /** rb_str_sub_bang
     *
     */
    @JRubyMethod(name = "sub!", frame = true, reads = BACKREF, writes = BACKREF, compat = CompatVersion.RUBY1_8)
    public IRubyObject sub_bang(ThreadContext context, IRubyObject arg0, Block block) {
        if (block.isGiven()) return subBangIter(context, getQuotedPattern(arg0), block);
        throw context.getRuntime().newArgumentError(1, 2);
    }

    /** rb_str_sub_bang
     *
     */
    @JRubyMethod(name = "sub!", frame = true, reads = BACKREF, writes = BACKREF, compat = CompatVersion.RUBY1_8)
    public IRubyObject sub_bang(ThreadContext context, IRubyObject arg0, IRubyObject arg1, Block block) {
        return subBangNoIter(context, getQuotedPattern(arg0), arg1.convertToString());
    }

    private IRubyObject subBangIter(ThreadContext context, Regex pattern, Block block) {
        int range = value.begin + value.realSize;
        Matcher matcher = pattern.matcher(value.bytes, value.begin, range);

        Frame frame = context.getPreviousFrame();
        if (matcher.search(value.begin, range, Option.NONE) >= 0) {
            byte[] bytes = value.bytes;
            int size = value.realSize;
            RubyMatchData match = RubyRegexp.updateBackRef(context, this, frame, matcher, pattern);
            RubyString repl = objAsString(context, block.yield(context, 
                    substr(context.getRuntime(), matcher.getBegin(), matcher.getEnd() - matcher.getBegin())));
            modifyCheck(bytes, size);
            frozenCheck();
            frame.setBackRef(match);
            return subBangCommon(context, pattern, matcher, repl, false);
        } else {
            return frame.setBackRef(context.getRuntime().getNil());
        }
    }

    private IRubyObject subBangNoIter(ThreadContext context, Regex pattern, RubyString repl) {
        boolean tained = repl.isTaint();
        int range = value.begin + value.realSize;
        Matcher matcher = pattern.matcher(value.bytes, value.begin, range);

        Frame frame = context.getPreviousFrame();
        if (matcher.search(value.begin, range, Option.NONE) >= 0) {
            repl = RubyRegexp.regsub(repl, this, matcher, context.getRuntime().getKCode().getEncoding());
            RubyRegexp.updateBackRef(context, this, frame, matcher, pattern);
            return subBangCommon(context, pattern, matcher, repl, tained);
        } else {
            return frame.setBackRef(context.getRuntime().getNil());
        }
    }

    private IRubyObject subBangCommon(ThreadContext context, Regex pattern, Matcher matcher, RubyString repl, boolean tainted) {
        final int beg = matcher.getBegin();
        final int plen = matcher.getEnd() - beg;

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

    /** rb_str_sub
    *
    */
    @JRubyMethod(name = "sub", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject sub19(ThreadContext context, IRubyObject arg0, Block block) {
        RubyString str = strDup(context.getRuntime());
        str.sub_bang19(context, arg0, block);
        return str;
    }

    @JRubyMethod(name = "sub", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject sub19(ThreadContext context, IRubyObject arg0, IRubyObject arg1, Block block) {
        RubyString str = strDup(context.getRuntime());
        str.sub_bang19(context, arg0, arg1, block);
        return str;
    }

    @JRubyMethod(name = "sub!", frame = true, reads = BACKREF, writes = BACKREF, compat = CompatVersion.RUBY1_9)
    public IRubyObject sub_bang19(ThreadContext context, IRubyObject arg0, Block block) {
        Ruby runtime = context.getRuntime();
        final Regex pattern, pat;
        final RubyRegexp regexp;
        if (arg0 instanceof RubyRegexp) {
            regexp = (RubyRegexp)arg0;
            pattern = regexp.getPattern();
            pat = regexp.preparePattern(this);
        } else {
            regexp = null;
            pattern = getStringPattern19(runtime, arg0);
            pat = RubyRegexp.preparePattern(runtime, pattern, this);
        }
        
        int begin = value.begin;
        int range = begin + value.realSize;
        final Matcher matcher = pat.matcher(value.bytes, begin, range);
        
        if (block.isGiven()) return subBangIter19(runtime, context, pattern, matcher, null, block, range, regexp);
        throw context.getRuntime().newArgumentError(1, 2);
    }

    @JRubyMethod(name = "sub!", frame = true, reads = BACKREF, writes = BACKREF, compat = CompatVersion.RUBY1_9)
    public IRubyObject sub_bang19(ThreadContext context, IRubyObject arg0, IRubyObject arg1, Block block) {
        Ruby runtime = context.getRuntime();
        IRubyObject hash = TypeConverter.convertToTypeWithCheck(arg1, runtime.getHash(), "to_hash");

        final Regex pattern, pat;
        final RubyRegexp regexp;
        if (arg0 instanceof RubyRegexp) {
            regexp = (RubyRegexp)arg0;
            pattern = regexp.getPattern();
            pat = regexp.preparePattern(this);
        } else {
            regexp = null;
            pattern = getStringPattern19(runtime, arg0);
            pat = RubyRegexp.preparePattern(runtime, pattern, this);
        }
        
        int begin = value.begin;
        int range = begin + value.realSize;
        final Matcher matcher = pat.matcher(value.bytes, begin, range);

        if (hash.isNil()) {
            return subBangNoIter19(runtime, context, pattern, matcher, arg1.convertToString(), range, regexp);
        } else {
            return subBangIter19(runtime, context, pattern, matcher, (RubyHash)hash, block, range, regexp);
        }
    }

    private IRubyObject subBangIter19(Ruby runtime, ThreadContext context, Regex pattern, Matcher matcher, RubyHash hash, Block block, int range, RubyRegexp regexp) {
        Frame frame = context.getPreviousFrame();
        if (matcher.search(value.begin, range, Option.NONE) >= 0) {
            byte[] bytes = value.bytes;
            int size = value.realSize;
            RubyMatchData match = RubyRegexp.updateBackRef19(context, this, frame, matcher, pattern);
            match.regexp = regexp;
            final RubyString repl;
            final boolean tainted;
            IRubyObject subStr = makeShared19(runtime, matcher.getBegin(), matcher.getEnd() - matcher.getBegin());
            if (hash == null) {
                tainted = false;
                repl = objAsString(context, block.yield(context, subStr));
            } else {
                tainted = hash.isTaint();
                repl = objAsString(context, hash.op_aref(context, subStr));
            }

            modifyCheck(bytes, size);
            frozenCheck();
            frame.setBackRef(match);
            return subBangCommon19(context, pattern, matcher, repl, tainted);
        } else {
            return frame.setBackRef(runtime.getNil());
        }
    }

    private IRubyObject subBangNoIter19(Ruby runtime, ThreadContext context, Regex pattern, Matcher matcher, RubyString repl, int range, RubyRegexp regexp) {
        boolean tained = repl.isTaint();

        Frame frame = context.getPreviousFrame();
        if (matcher.search(value.begin, range, Option.NONE) >= 0) {
            repl = RubyRegexp.regsub19(repl, this, matcher, pattern);
            RubyMatchData match = RubyRegexp.updateBackRef19(context, this, frame, matcher, pattern);
            match.regexp = regexp;
            return subBangCommon19(context, pattern, matcher, repl, tained);
        } else {
            return frame.setBackRef(runtime.getNil());
        }
    }

    private IRubyObject subBangCommon19(ThreadContext context, Regex pattern, Matcher matcher, RubyString repl, boolean tainted) {
        final int beg = matcher.getBegin();       
        final int end = matcher.getEnd();

        Encoding enc = isCompatibleWith(repl); 
        if (enc == null) enc = subBangVerifyEncoding(context, repl, beg, end);

        final int plen = end - beg;
        ByteList replValue = repl.value;
        if (replValue.realSize > plen) {
            modify19(value.realSize + replValue.realSize - plen);
        } else {
            modify19();
        }

        associateEncoding(enc);
        if (repl.isTaint()) tainted = true;

        int cr = getCodeRange();
        if (cr > CR_UNKNOWN && cr < CR_BROKEN) {
            int cr2 = repl.getCodeRange();
            if (cr2 == CR_BROKEN || (cr == CR_VALID && cr2 == CR_7BIT)) {
                cr = CR_UNKNOWN;
            } else {
                cr = cr2;
            }
        }

        if (replValue.realSize != plen) {
            int src = value.begin + beg + plen;
            int dst = value.begin + beg + replValue.realSize;
            int length = value.realSize - beg - plen;
            System.arraycopy(value.bytes, src, value.bytes, dst, length);
        }
        System.arraycopy(replValue.bytes, replValue.begin, value.bytes, value.begin + beg, replValue.realSize);
        value.realSize += replValue.realSize - plen;
        setCodeRange(cr);
        if (tainted) setTaint(true);
        return this;
    }

    private Encoding subBangVerifyEncoding(ThreadContext context, RubyString repl, int beg, int end) {
        byte[]bytes = value.bytes;
        int p = value.begin;
        int len = value.realSize;
        Encoding strEnc = value.encoding;
        if (codeRangeScan(strEnc, bytes, p, beg) != CR_7BIT ||
            codeRangeScan(strEnc, bytes, p + end, len - end) != CR_7BIT) {
            throw context.getRuntime().newArgumentError(
                    "incompatible character encodings " + strEnc + " and " + repl.value.encoding);
        }
        return repl.value.encoding;        
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
            return gsubCommon(context, bang, getQuotedPattern(arg0), block, null, false);
        } else {
            throw context.getRuntime().newArgumentError("wrong number of arguments (1 for 2)");
        }
    }

    private final IRubyObject gsub(ThreadContext context, IRubyObject arg0, IRubyObject arg1, Block block, final boolean bang) {
        RubyString repl = arg1.convertToString();
        return gsubCommon(context, bang, getQuotedPattern(arg0), block, repl, repl.isTaint());
    }

    private IRubyObject gsubCommon(ThreadContext context, final boolean bang, Regex pattern, Block block, RubyString repl, boolean tainted) {
        Ruby runtime = context.getRuntime();
        Frame frame = context.getPreviousFrame();

        int begin = value.begin;
        int range = begin + value.realSize;
        Matcher matcher = pattern.matcher(value.bytes, begin, range);

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
            int begz = matcher.getBegin();
            int endz = matcher.getEnd();

            if (repl == null) { // block given
                byte[] bytes = value.bytes;
                int size = value.realSize;
                match = RubyRegexp.updateBackRef(context, this, frame, matcher, pattern);
                val = objAsString(context, block.yield(context, substr(runtime, begz, endz - begz)));
                modifyCheck(bytes, size);
                if (bang) frozenCheck();
            } else {
                val = RubyRegexp.regsub(repl, this, matcher, runtime.getKCode().getEncoding());
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
                if (value.realSize <= endz) break;
                len = pattern.getEncoding().length(value.bytes, begin + endz, range);
                System.arraycopy(value.bytes, begin + endz, dest.bytes, bp, len);
                bp += len;
                offset = endz + len;
            }
            cp = begin + offset;
            if (offset > value.realSize) break;
            beg = matcher.search(cp, range, Option.NONE);
        }

        if (repl == null) { // block given
            frame.setBackRef(match);
        } else {
            RubyRegexp.updateBackRef(context, this, frame, matcher, pattern);
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
    @JRubyMethod(name = "index", reads = BACKREF, writes = BACKREF, compat = CompatVersion.RUBY1_8)
    public IRubyObject index(ThreadContext context, IRubyObject arg0) {
        return indexCommon(context.getRuntime(), context, arg0, 0);
    }

    @JRubyMethod(name = "index", reads = BACKREF, writes = BACKREF, compat = CompatVersion.RUBY1_8)
    public IRubyObject index(ThreadContext context, IRubyObject arg0, IRubyObject arg1) {
        int pos = RubyNumeric.num2int(arg1);
        Ruby runtime = context.getRuntime();
        if (pos < 0) {
            pos += value.realSize;
            if (pos < 0) {
                if (arg0 instanceof RubyRegexp) {
                    context.getPreviousFrame().setBackRef(runtime.getNil());
                }
                return runtime.getNil();
            }
        }
        return indexCommon(runtime, context, arg0, pos);
    }

    private IRubyObject indexCommon(Ruby runtime, ThreadContext context, IRubyObject sub, int pos) {
        if (sub instanceof RubyRegexp) {
            RubyRegexp regSub = (RubyRegexp) sub;

            pos = regSub.adjustStartPos(this, pos, false);
            pos = regSub.search(context, this, pos, false);
        } else if (sub instanceof RubyFixnum) {
            int c_int = RubyNumeric.fix2int(sub);
            if (c_int < 0x00 || c_int > 0xFF) {
                // out of byte range
                // there will be no match for sure
                return runtime.getNil();
            }
            byte c = (byte) c_int;
            byte[] bytes = value.bytes;
            int end = value.begin + value.realSize;

            pos += value.begin;
            for (; pos < end; pos++) {
                if (bytes[pos] == c) return RubyFixnum.newFixnum(runtime, pos - value.begin);
            }
            return runtime.getNil();
        } else if (sub instanceof RubyString) {
            pos = strIndex((RubyString) sub, pos);
        } else {
            IRubyObject tmp = sub.checkStringType();
            if (tmp.isNil()) throw runtime.newTypeError("type mismatch: " + sub.getMetaClass().getName() + " given");
            pos = strIndex((RubyString) tmp, pos);
        }

        return pos == -1 ? runtime.getNil() : RubyFixnum.newFixnum(runtime, pos);
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

    @JRubyMethod(name = "index", reads = BACKREF, writes = BACKREF, compat = CompatVersion.RUBY1_9)
    public IRubyObject index19(ThreadContext context, IRubyObject arg0) {
        return indexCommon19(context.getRuntime(), context, arg0, 0);
    }
    
    @JRubyMethod(name = "index", reads = BACKREF, writes = BACKREF, compat = CompatVersion.RUBY1_9)
    public IRubyObject index19(ThreadContext context, IRubyObject arg0, IRubyObject arg1) {
        int pos = RubyNumeric.num2int(arg1);
        Ruby runtime = context.getRuntime();
        if (pos < 0) {
            pos += strLength();
            if (pos < 0) {
                if (arg0 instanceof RubyRegexp) context.getPreviousFrame().setBackRef(runtime.getNil());
                return runtime.getNil();
            }
        }
        return indexCommon19(runtime, context, arg0, pos);
    }

    private IRubyObject indexCommon19(Ruby runtime, ThreadContext context, IRubyObject sub, int pos) {
        if (sub instanceof RubyRegexp) {
            RubyRegexp regSub = (RubyRegexp) sub;
            pos = singleByteOptimizable() ? value.begin + pos : 
                    StringSupport.nth(checkEncoding(regSub), 
                            value.bytes,
                            value.begin,
                            value.begin + value.realSize,
                            pos);
            pos = regSub.adjustStartPos19(this, pos, false);
            pos = regSub.search19(context, this, pos, false);
            pos = subLength(pos);
        } else if (sub instanceof RubyString) {
            pos = strIndex19((RubyString) sub, pos);
            pos = subLength(pos);
        } else {
            IRubyObject tmp = sub.checkStringType();
            if (tmp.isNil()) throw runtime.newTypeError("type mismatch: " + sub.getMetaClass().getName() + " given");
            pos = strIndex19((RubyString) tmp, pos);
            pos = subLength(pos);
        }

        return pos == -1 ? runtime.getNil() : RubyFixnum.newFixnum(runtime, pos);
    }

    private int strIndex19(RubyString sub, int offset) {
        Encoding enc = checkEncoding(sub);
        if (sub.scanForCodeRange() == CR_BROKEN) return -1;
        int len = strLength(enc);
        int slen = sub.strLength(enc);
        if (offset < 0) {
            offset += len;
            if (offset < 0) return -1;
        }

        if (len - offset < slen) return -1;
        byte[]bytes = value.bytes;
        int p = value.begin;
        int end = p + value.realSize;
        if (offset != 0) {
            offset = singleByteOptimizable() ? p + offset : StringSupport.offset(enc, bytes, p, end, offset);
            p += offset;
        }
        if (slen == 0) return offset;

        while (true) {
            int pos = value.indexOf(sub.value, p - value.begin);
            if (pos < 0) return pos;
            int t = enc.rightAdjustCharHead(bytes, p, p + pos, end);
            if (t == p + pos) return pos + offset;;
            if ((len -= t - p) <= 0) return -1;
            offset += t - p;
            p = t;
        }
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
    @JRubyMethod(name = "rindex", reads = BACKREF, writes = BACKREF, compat = CompatVersion.RUBY1_8)
    public IRubyObject rindex(ThreadContext context, IRubyObject arg0) {
        return rindexCommon(context.getRuntime(), context, arg0, value.realSize);
    }

    @JRubyMethod(name = "rindex", reads = BACKREF, writes = BACKREF, compat = CompatVersion.RUBY1_8)
    public IRubyObject rindex(ThreadContext context, IRubyObject arg0, IRubyObject arg1) {
        int pos = RubyNumeric.num2int(arg1);
        Ruby runtime = context.getRuntime();
        if (pos < 0) {
            pos += value.realSize;
            if (pos < 0) {
                if (arg0 instanceof RubyRegexp) context.getPreviousFrame().setBackRef(runtime.getNil());
                return runtime.getNil();
            }
        }            
        if (pos > value.realSize) pos = value.realSize;
        return rindexCommon(runtime, context, arg0, pos);
    }

    private IRubyObject rindexCommon(Ruby runtime, ThreadContext context, final IRubyObject sub, int pos) {
        if (sub instanceof RubyRegexp) {
            RubyRegexp regSub = (RubyRegexp) sub;
            if (regSub.length() > 0) {
                pos = regSub.adjustStartPos(this, pos, true);
                pos = regSub.search(context, this, pos, true);
            }
        } else if (sub instanceof RubyString) {
            pos = strRindex((RubyString) sub, pos);
        } else if (sub instanceof RubyFixnum) {
            int c_int = RubyNumeric.fix2int(sub);
            if (c_int < 0x00 || c_int > 0xFF) {
                // out of byte range
                // there will be no match for sure
                return runtime.getNil();
            }
            byte c = (byte) c_int;

            byte[] bytes = value.bytes;
            int pbeg = value.begin;
            int p = pbeg + pos;

            if (pos == value.realSize) {
                if (pos == 0) return runtime.getNil();
                --p;
            }
            while (pbeg <= p) {
                if (bytes[p] == c) return RubyFixnum.newFixnum(runtime, p - value.begin);
                p--;
            }
            return runtime.getNil();
        } else {
            IRubyObject tmp = sub.checkStringType();
            if (tmp.isNil()) throw runtime.newTypeError("type mismatch: " + sub.getMetaClass().getName() + " given");
            pos = strRindex((RubyString) tmp, pos);
        }
        if (pos >= 0) return RubyFixnum.newFixnum(runtime, pos);
        return runtime.getNil();
    }

    private int strRindex(RubyString sub, int pos) {
        int subLength = sub.value.realSize;
        
        /* substring longer than string */
        if (value.realSize < subLength) return -1;
        if (value.realSize - pos < subLength) pos = value.realSize - subLength;

        return value.lastIndexOf(sub.value, pos);
    }

    @JRubyMethod(name = "rindex", reads = BACKREF, writes = BACKREF, compat = CompatVersion.RUBY1_9)
    public IRubyObject rindex19(ThreadContext context, IRubyObject arg0) {
        return rindexCommon19(context.getRuntime(), context, arg0, strLength());
    }

    @JRubyMethod(name = "rindex", reads = BACKREF, writes = BACKREF, compat = CompatVersion.RUBY1_9)
    public IRubyObject rindex19(ThreadContext context, IRubyObject arg0, IRubyObject arg1) {
        int pos = RubyNumeric.num2int(arg1);
        Ruby runtime = context.getRuntime();
        int length = strLength();
        if (pos < 0) {
            pos += length;
            if (pos < 0) {
                if (arg0 instanceof RubyRegexp) context.getPreviousFrame().setBackRef(runtime.getNil());
                return runtime.getNil();
            }
        }            
        if (pos > length) pos = length;
        return rindexCommon19(runtime, context, arg0, pos);
    }

    private IRubyObject rindexCommon19(Ruby runtime, ThreadContext context, final IRubyObject sub, int pos) {
        if (sub instanceof RubyRegexp) {
            RubyRegexp regSub = (RubyRegexp) sub;
            pos = singleByteOptimizable() ? value.begin + pos :
                    StringSupport.nth(value.encoding, 
                            value.bytes,
                            value.begin,
                            value.begin + value.realSize,
                            pos);
            if (regSub.length() > 0) {
                pos = regSub.adjustStartPos(this, pos, true);
                pos = regSub.search(context, this, pos, true);
                pos = subLength(pos);
            }
        } else if (sub instanceof RubyString) {
            pos = strRindex19((RubyString) sub, pos);
        } else {
            IRubyObject tmp = sub.checkStringType();
            if (tmp.isNil()) throw runtime.newTypeError("type mismatch: " + sub.getMetaClass().getName() + " given");
            pos = strRindex19((RubyString) tmp, pos);
        }
        if (pos >= 0) return RubyFixnum.newFixnum(runtime, pos);
        return runtime.getNil();
    }

    private int strRindex19(RubyString sub, int pos) {
        Encoding enc = checkEncoding(sub);
        if (sub.scanForCodeRange() == CR_BROKEN) return -1;
        int len = strLength(enc);
        int slen = sub.strLength(enc);

        if (len < slen) return -1;
        if (len - pos < slen) pos = len - slen;
        if (len == 0) return pos;

        byte[]bytes = value.bytes;
        int p = value.begin;
        int end = p + value.realSize;

        byte[]sbytes = sub.value.bytes;
        int sp = sub.value.begin;
        slen = sub.value.realSize;

        boolean singlebyte = singleByteOptimizable();
        while (true) {
            int s = singlebyte ? p + pos : StringSupport.nth(enc, bytes, p, end, pos);
            if (s == -1) return -1;
            if (ByteList.memcmp(bytes, s, sbytes, sp, slen) == 0) return pos;
            if (pos == 0) return -1;
            pos--;
        }
    }

    @Deprecated
    public final IRubyObject substr(int beg, int len) {
        return substr(getRuntime(), beg, len);
    }

    /* rb_str_substr */
    public final IRubyObject substr(Ruby runtime, int beg, int len) {    
        int length = value.length();
        if (len < 0 || beg > length) return runtime.getNil();

        if (beg < 0) {
            beg += length;
            if (beg < 0) return runtime.getNil();
        }
        
        int end = Math.min(length, beg + len);
        return makeShared(runtime, beg, end - beg);
    }

    public final IRubyObject substr19(Ruby runtime, int beg, int len) {
        if (len < 0) return runtime.getNil();
        int length = value.realSize;
        if (length == 0) len = 0; 
        
        int p;
        Encoding enc = value.encoding;
        if (singleByteOptimizable(enc)) {
            if (beg > length) return runtime.getNil();
            if (beg < 0) {
                beg += length;
                if (beg < 0) return runtime.getNil();
            }
            if (beg + len > length) len = length - beg;
            if (len <= 0) {
                len = 0;
                p = 0;
            } else {
                p = value.begin + beg;
            }
            return makeShared19(runtime, p, len);
        } else {
            int s = value.begin;
            int end = s + length;
            byte[]bytes = value.bytes;
            if (beg < 0) {
                if (len > -beg) len = -beg;
                if (-beg * enc.maxLength() < length >>> 3) {
                    beg = -beg;
                    int e = end;
                    while (beg-- > len && (e = enc.prevCharHead(bytes, s, e, e)) != -1); // nothing
                    p = e;
                    if (p == -1) return runtime.getNil();
                    while (len-- > 0 && (p = enc.prevCharHead(bytes, s, p, e)) != -1); // nothing
                    if (p == -1) return runtime.getNil();
                    len = e - p;
                    return makeShared19(runtime, p, len);
                } else {
                    beg += StringSupport.strLength(enc, bytes, s, end);
                    if (beg < 0) return runtime.getNil();
                }
            } else if (beg > 0 && beg > StringSupport.strLength(enc, bytes, s, end)) {
                return runtime.getNil();
            }
            if (len == 0) {
                p = 0;
            } else if (enc.isFixedWidth()) {
                int w = enc.maxLength();
                p = s + beg * w;
                if (p > end) {
                    p = end;
                    len = 0;
                } else if (len * w > end - p) {
                    len = end - p;
                } else {
                    len *= w;
                }
            } else if ((p = StringSupport.nth(enc, bytes, s, end, beg)) == end) {
                len = 0;
            } else {
                len = StringSupport.offset(enc, bytes, p, end, len); 
            }
            return makeShared19(runtime, p, len);
        }
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
    @JRubyMethod(name = {"[]", "slice"}, reads = BACKREF, writes = BACKREF, compat = CompatVersion.RUBY1_8)
    public IRubyObject op_aref(ThreadContext context, IRubyObject arg1, IRubyObject arg2) {
        Ruby runtime = context.getRuntime();
        if (arg1 instanceof RubyRegexp) return subpat(runtime, context, (RubyRegexp)arg1, RubyNumeric.fix2int(arg2));
        return substr(runtime, RubyNumeric.fix2int(arg1), RubyNumeric.fix2int(arg2));
    }

    @JRubyMethod(name = {"[]", "slice"}, reads = BACKREF, writes = BACKREF, compat = CompatVersion.RUBY1_8)
    public IRubyObject op_aref(ThreadContext context, IRubyObject arg) {
        Ruby runtime = context.getRuntime();
        if (arg instanceof RubyRegexp) {
            return subpat(runtime, context, (RubyRegexp)arg, 0);
        } else if (arg instanceof RubyString) {
            return value.indexOf(stringValue(arg).value) != -1 ? arg : runtime.getNil();
        } else if (arg instanceof RubyRange) {
            int[] begLen = ((RubyRange) arg).begLenInt(value.length(), 0);
            return begLen == null ? runtime.getNil() : substr(runtime, begLen[0], begLen[1]);
        }
        int idx = (int) arg.convertToInteger().getLongValue();
        
        if (idx < 0) idx += value.length();
        if (idx < 0 || idx >= value.length()) return runtime.getNil();

        return runtime.newFixnum(value.get(idx) & 0xFF);
    }

    @JRubyMethod(name = {"[]", "slice"}, reads = BACKREF, writes = BACKREF, compat = CompatVersion.RUBY1_9)
    public IRubyObject op_aref19(ThreadContext context, IRubyObject arg1, IRubyObject arg2) {
        Ruby runtime = context.getRuntime();
        if (arg1 instanceof RubyRegexp) return subpat19(runtime, context, (RubyRegexp)arg1, RubyNumeric.fix2int(arg2));
        return substr19(runtime, RubyNumeric.fix2int(arg1), RubyNumeric.fix2int(arg2));
    }

    @JRubyMethod(name = {"[]", "slice"}, reads = BACKREF, writes = BACKREF, compat = CompatVersion.RUBY1_9)
    public IRubyObject op_aref19(ThreadContext context, IRubyObject arg) {
        Ruby runtime = context.getRuntime();
        if (arg instanceof RubyFixnum) {
            IRubyObject str = substr19(runtime, RubyNumeric.fix2int(arg), 1);
            return !str.isNil() && ((RubyString)str).value.realSize == 0 ? runtime.getNil() : str;
        } else if (arg instanceof RubyRegexp) {
            return subpat19(runtime, context, (RubyRegexp)arg, 0);
        } else if (arg instanceof RubyString) {
            RubyString str = (RubyString)arg;
            return strIndex19(str, 0) != -1 ? str.strDup(runtime) : runtime.getNil();
        } else if (arg instanceof RubyRange) {
            int len = strLength();
            int[] begLen = ((RubyRange) arg).begLenInt(len, 0);
            return begLen == null ? runtime.getNil() : substr19(runtime, begLen[0], begLen[1]);
        } else {
            IRubyObject str = substr19(runtime, RubyNumeric.num2int(arg), 1);
            return !str.isNil() && ((RubyString)str).value.realSize == 0 ? runtime.getNil() : str;
        }
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

    private IRubyObject subpat(Ruby runtime, ThreadContext context, RubyRegexp regex, int nth) {
        if (regex.search(context, this, 0, false) >= 0) {
            return RubyRegexp.nth_match(nth, context.getCurrentFrame().getBackRef());
        }
        return runtime.getNil();
    }
    
    private IRubyObject subpat19(Ruby runtime, ThreadContext context, RubyRegexp regex, int nth) {
        if (regex.search19(context, this, 0, false) >= 0) {
            return RubyRegexp.nth_match(nth, context.getCurrentFrame().getBackRef());
        }
        return runtime.getNil();
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

    @JRubyMethod(name = {"succ", "next"}, compat = CompatVersion.RUBY1_8)
    public IRubyObject succ(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.succ_bang();
        return str;
    }

    @JRubyMethod(name = {"succ!", "next!"}, compat = CompatVersion.RUBY1_8)
    public IRubyObject succ_bang() {
        if (value.realSize == 0) {
            modifyCheck();
            return this;
        }

        modify();

        boolean alnumSeen = false;
        int pos = -1, n = 0;
        int p = value.begin;
        int end = p + value.realSize;
        byte[]bytes = value.bytes;
        
        for (int i = end - 1; i >= p; i--) {
            int c = bytes[i] & 0xff;
            if (ASCII.isAlnum(c)) {
                alnumSeen = true;
                if ((ASCII.isDigit(c) && c < '9') || (ASCII.isLower(c) && c < 'z') || (ASCII.isUpper(c) && c < 'Z')) {
                    bytes[i] = (byte)(c + 1);
                    pos = -1;
                    break;
                }
                pos = i;
                n = ASCII.isDigit(c) ? '1' : (ASCII.isLower(c) ? 'a' : 'A');
                bytes[i] = ASCII.isDigit(c) ? (byte)'0' : ASCII.isLower(c) ? (byte)'a' : (byte)'A';
            }
        }
        if (!alnumSeen) {
            for (int i = end - 1; i >= p; i--) {
                int c = bytes[i] & 0xff;
                if (c < 0xff) {
                    bytes[i] = (byte)(c + 1);
                    pos = -1;
                    break;
                }
                pos = i;
                n = '\u0001';
                bytes[i] = 0;
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

    private static enum NeighborChar {NOT_CHAR, FOUND, WRAPPED}

    private static NeighborChar succChar(Encoding enc, byte[]bytes, int p, int len) {
        while (true) {
            int i = len - 1;
            for (; i >= 0 && bytes[p + i] == (byte)0xff; i--) bytes[p + i] = 0;
            if (i < 0) return NeighborChar.WRAPPED;
            bytes[p + i] = (byte)((bytes[p + i] & 0xff) + 1);
            int cl = StringSupport.preciseLength(enc, bytes, p, p + len);
            if (cl > 0) {
                if (cl == len) {
                    return NeighborChar.FOUND;
                } else {
                    for (int j = p + cl; j < p + len - cl; j++) bytes[j] = (byte)0xff;
                }
            }
            if (cl == -1 && i < len - 1) {
                int len2 = len - 1;
                for (; len2 > 0; len2--) {
                    if (StringSupport.preciseLength(enc, bytes, p, p + len2) != -1) break;
                }
                for (int j = p + len2 + 1; j < p + len - (len2 + 1); j++) bytes[j] = (byte)0xff;
            }
        }
    }
    
    private static NeighborChar predChar(Encoding enc, byte[]bytes, int p, int len) {
        while (true) {
            int i = len - 1;
            for (; i >= 0 && bytes[p + i] == 0; i--) bytes[p + i] = (byte)0xff;
            if (i < 0) return NeighborChar.WRAPPED;
            bytes[p + i] = (byte)((bytes[p + i] & 0xff) - 1);
            int cl = StringSupport.preciseLength(enc, bytes, p, p + len);
            if (cl > 0) {
                if (cl == len) {
                    return NeighborChar.FOUND;
                } else {
                    for (int j = p + cl; j < p + len - cl; j++) bytes[j] = 0;
                }
            }
            if (cl == -1 && i < len - 1) {
                int len2 = len - 1;
                for (; len2 > 0; len2--) {
                    if (StringSupport.preciseLength(enc, bytes, p, p + len2) != -1) break;
                }
                for (int j = p + len2 + 1; j < p + len - (len2 + 1); j++) bytes[j] = 0;
            }
        }
    }

    private static NeighborChar succAlnumChar(Encoding enc, byte[]bytes, int p, int len, byte[]carry, int carryP) {
        byte save[] = new byte[org.jcodings.Config.ENC_CODE_TO_MBC_MAXLEN];
        int c = enc.mbcToCode(bytes, p, p + len);

        final int cType;
        if (enc.isDigit(c)) {
            cType = CharacterType.DIGIT;
        } else if (enc.isAlpha(c)) {
            cType = CharacterType.ALPHA;
        } else {
            return NeighborChar.NOT_CHAR;
        }

        System.arraycopy(bytes, p, save, 0, len);
        NeighborChar ret = succChar(enc, bytes, p, len);
        if (ret == NeighborChar.FOUND) {
            c = enc.mbcToCode(bytes, p, p + len);
            if (enc.isCodeCType(c, cType)) return NeighborChar.FOUND;
        }

        System.arraycopy(save, 0, bytes, p, len);
        int range = 1;

        while (true) {
            System.arraycopy(bytes, p, save, 0, len);
            ret = predChar(enc, bytes, p, len);
            if (ret == NeighborChar.FOUND) {
                c = enc.mbcToCode(bytes, p, p + len);
                if (!enc.isCodeCType(c, cType)) {
                    System.arraycopy(save, 0, bytes, p, len);
                    break;
                }
            } else {
                System.arraycopy(save, 0, bytes, p, len);
                break;
            }
            range++;
        }

        if (range == 1) return NeighborChar.NOT_CHAR;

        if (cType != CharacterType.DIGIT) {
            System.arraycopy(bytes, p, carry, carryP, len);
            return NeighborChar.WRAPPED;
        }

        System.arraycopy(bytes, p, carry, carryP, len);
        succChar(enc, carry, carryP, len);
        return NeighborChar.WRAPPED;
    }

    @JRubyMethod(name = {"succ", "next"}, compat = CompatVersion.RUBY1_9)
    public IRubyObject succ19(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        final RubyString str;
        if (value.realSize > 0) {
            str = new RubyString(runtime, getMetaClass(), succCommon19(value));
            // TODO: rescan code range ?
        } else {
            str = newEmptyString(runtime, value.encoding);
        }
        return str.infectBy(this);
    }

    @JRubyMethod(name = {"succ!", "next!"}, compat = CompatVersion.RUBY1_9)
    public IRubyObject succ_bang19() {
        modifyCheck();
        if (value.realSize > 0) {
            value = succCommon19(value);
            shareLevel = SHARE_LEVEL_NONE;
            // TODO: rescan code range ?
        }
        return this;
    }

    private ByteList succCommon19(ByteList original) {
        byte carry[] = new byte[org.jcodings.Config.ENC_CODE_TO_MBC_MAXLEN];
        int carryP = 0;
        carry[0] = 1;
        int carryLen = 1;

        ByteList value = new ByteList(original);
        value.encoding = original.encoding;
        Encoding enc = original.encoding;
        int p = value.begin;
        int end = p + value.realSize;
        int s = end;
        byte[]bytes = value.bytes;

        NeighborChar neighbor = NeighborChar.FOUND;
        int lastAlnum = -1;
        boolean alnumSeen = false;
        while ((s = enc.prevCharHead(bytes, p, s, end)) != -1) {
            if (neighbor == NeighborChar.NOT_CHAR && lastAlnum != -1) {
                if (ASCII.isAlpha(bytes[lastAlnum] & 0xff) ?
                        ASCII.isDigit(bytes[s] & 0xff) :
                        ASCII.isDigit(bytes[lastAlnum] & 0xff) ?
                                ASCII.isAlpha(bytes[s] & 0xff) : false) {
                    s = lastAlnum;
                    break;
                }
            }

            int cl = StringSupport.preciseLength(enc, bytes, s, end);
            if (cl <= 0) continue;
            switch (neighbor = succAlnumChar(enc, bytes, s, cl, carry, 0)) {
            case NOT_CHAR: continue;
            case FOUND:    return value;
            case WRAPPED:  lastAlnum = s;
            }
            alnumSeen = true;
            carryP = s - p;
            carryLen = cl;
        }

        if (!alnumSeen) {
            s = end;
            while ((s = enc.prevCharHead(bytes, p, s, end)) != -1) {
                int cl = StringSupport.preciseLength(enc, bytes, s, end);
                if (cl <= 0) continue;
                neighbor = succChar(enc, bytes, s, cl);
                if (neighbor == NeighborChar.FOUND) return value;
                if (StringSupport.preciseLength(enc, bytes, s, s + 1) != cl) succChar(enc, bytes, s, cl); /* wrapped to \0...\0.  search next valid char. */
                if (!enc.isAsciiCompatible()) {
                    System.arraycopy(bytes, s, carry, 0, cl);
                    carryLen = cl;
                }
                carryP = s - p;
            }
        }
        value.ensure(value.begin + value.realSize + carryLen);
        s = value.begin + carryP;
        System.arraycopy(value.bytes, s, value.bytes, s + carryLen, value.realSize - carryP);
        System.arraycopy(carry, 0, value.bytes, s, carryLen);
        value.realSize += carryLen;
        return value;
    }

    /** rb_str_upto_m
     *
     */
    @JRubyMethod(name = "upto", frame = true, compat = CompatVersion.RUBY1_8)
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

    @JRubyMethod(name = "upto", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject upto19(ThreadContext context, IRubyObject end, Block block) {
        Ruby runtime = context.getRuntime();
        return block.isGiven() ? upto19Common(context, end, false, block) : enumeratorize(runtime, this, "upto", end);
    }

    @JRubyMethod(name = "upto", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject upto19(ThreadContext context, IRubyObject end, IRubyObject excl, Block block) {
        Ruby runtime = context.getRuntime();
        return block.isGiven() ? upto19Common(context, end, excl.isTrue(), block) : 
            enumeratorize(runtime, this, "upto", new IRubyObject[]{end, excl});
    }

    private IRubyObject upto19Common(ThreadContext context, IRubyObject arg, boolean excl, Block block) {
        Ruby runtime = context.getRuntime();
        RubyString end = arg.convertToString();
        Encoding enc = checkEncoding(end);

        if (value.realSize == 1 && end.value.realSize == 1 &&
            scanForCodeRange() == CR_7BIT && end.scanForCodeRange() == CR_7BIT) {
            byte c = value.bytes[value.begin]; 
            byte e = end.value.bytes[end.value.begin];
            if (c > e || (excl && c == e)) return this;
            while (true) {
                RubyString s = new RubyString(runtime, runtime.getString(), RubyInteger.SINGLE_CHAR_BYTELISTS[c & 0xff],
                                                                            enc, CR_7BIT);
                s.shareLevel = SHARE_LEVEL_BYTELIST;
                block.yield(context, s); 

                if (!excl && c == e) break;
                c++;
                if (excl && c == e) break;
            }
        } else {
            int n = op_cmp19(end);
            if (n > 0 || (excl && n == 0)) return this;

            IRubyObject afterEnd = end.callMethod(context, "succ");
            RubyString current = this;
            while (!current.op_equal19(context, afterEnd).isTrue()) {
                block.yield(context, current);
                if (!excl && current.op_equal19(context, end).isTrue()) break;
                current = current.callMethod(context, "succ").convertToString();
                if (excl && current.op_equal19(context, end).isTrue()) break;
                if (current.value.realSize > end.value.realSize || current.value.realSize == 0) break;
            }
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

    @JRubyMethod(name = "chr", compat = CompatVersion.RUBY1_9)
    public IRubyObject chr(ThreadContext context) {
        return substr19(context.getRuntime(), 0, 1);
    }

    @JRubyMethod(name = "getbyte", compat = CompatVersion.RUBY1_9)
    public IRubyObject getbyte(ThreadContext context, IRubyObject index) {
        Ruby runtime = context.getRuntime();
        int i = RubyNumeric.num2int(index);
        if (i < 0) i += value.realSize;
        if (i < 0 || i >= value.realSize) return runtime.getNil();
        return RubyFixnum.newFixnum(runtime, value.bytes[value.begin + i] & 0xff);
    }

    @JRubyMethod(name = "setbyte", compat = CompatVersion.RUBY1_9)
    public IRubyObject setbyte(ThreadContext context, IRubyObject index, IRubyObject val) {
        Ruby runtime = context.getRuntime();
        int i = RubyNumeric.num2int(index);
        int b = RubyNumeric.num2int(val);

        if (i < -value.realSize || i >= value.realSize) throw runtime.newIndexError("index " + i + "out of string");
        if (i < 0) i += value.realSize;
        value.bytes[i] = (byte)b;
        return val;
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
    @JRubyMethod(name = "oct", compat = CompatVersion.RUBY1_8)
    public IRubyObject oct(ThreadContext context) {
        if (isEmpty()) return context.getRuntime().newFixnum(0);

        int base = 8;
        int ix = value.begin;
        while (ix < value.begin + value.realSize && ASCII.isSpace(value.bytes[ix] & 0xff)) {
            ix++;
        }

        int pos = (value.bytes[ix] == '-' || value.bytes[ix] == '+') ? ix + 1 : ix;
        if (pos + 1 < value.begin+value.realSize && value.bytes[pos] == '0') {
            if (value.bytes[pos+1] == 'x' || value.bytes[pos+1] == 'X') {
                base = 16;
            } else if (value.bytes[pos+1] == 'b' || value.bytes[pos+1] == 'B') {
                base = 2;
            } else if (value.bytes[pos+1] == 'd' || value.bytes[pos+1] == 'D') {
                base = 10;
            }
        }
        return RubyNumeric.str2inum(context.getRuntime(), this, base);
    }

    @JRubyMethod(name = "oct", compat = CompatVersion.RUBY1_9)
    public IRubyObject oct19(ThreadContext context) {
        if (!value.encoding.isAsciiCompatible()) {
            throw context.getRuntime().newEncodingCompatibilityError("ASCII incompatible encoding: " + value.encoding);
        }
        return oct(context);
    }

    /** rb_str_hex
     *
     */
    @JRubyMethod(name = "hex", compat = CompatVersion.RUBY1_8)
    public IRubyObject hex(ThreadContext context) {
        return RubyNumeric.str2inum(context.getRuntime(), this, 16);
    }

    @JRubyMethod(name = "hex", compat = CompatVersion.RUBY1_9)
    public IRubyObject hex19(ThreadContext context) {
        if (!value.encoding.isAsciiCompatible()) {
            throw context.getRuntime().newEncodingCompatibilityError("ASCII incompatible encoding: " + value.encoding);
        }
        return hex(context);
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

    @JRubyMethod(writes = BACKREF)
    public RubyArray split(ThreadContext context, IRubyObject arg0) {
        return splitCommon(arg0, false, 0, 0, context);
    }

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

        final Regex pattern = getQuotedPattern(pat);

        int begin = value.begin;
        final Matcher matcher = pattern.matcher(value.bytes, begin, begin + value.realSize);

        RubyArray result = runtime.newArray();
        final Encoding enc = pattern.getEncoding();

        int beg = regexSplit(runtime, result, matcher, enc, limit, lim, i, pattern.numberOfCaptures() != 0);

        // only this case affects backrefs 
        context.getCurrentFrame().setBackRef(runtime.getNil());

        if (value.realSize > 0 && (limit || value.realSize > beg || lim < 0)) {
            if (value.realSize == beg) {
                result.append(newEmptyString(runtime, getMetaClass()));
            } else {
                result.append(substr(runtime, beg, value.realSize - beg));
            }
        }
        return result;
    }

    private int regexSplit(Ruby runtime, RubyArray result, Matcher matcher, Encoding enc, boolean limit, int lim, int i, boolean captures) {
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
                result.append(substr(runtime, beg, end - beg));
                beg = matcher.getEnd();
                start = begin + matcher.getEnd();
            }
            lastNull = false;

            if (captures) populateCapturesForSplit(runtime, result, matcher);

            if (limit && lim <= ++i) break;
        }
        return beg;
    }

    private void populateCapturesForSplit(Ruby runtime, RubyArray result, Matcher matcher) {
        Region region = matcher.getRegion();
        for (int i = 1; i < region.numRegs; i++) {
            if (region.beg[i] == -1) continue;
            if (region.beg[i] == region.end[i]) {
                result.append(newEmptyString(runtime, getMetaClass()));
            } else {
                result.append(substr(runtime , region.beg[i], region.end[i] - region.beg[i]));
            }
        }
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

    private RubyString getStringForPattern(IRubyObject obj) {
        if (obj instanceof RubyString) return (RubyString)obj;
        IRubyObject val = obj.checkStringType();
        if (val.isNil()) throw getRuntime().newTypeError("wrong argument type " + obj.getMetaClass() + " (expected Regexp)");
        return (RubyString)val;
    }

    /** get_pat (used by match/match19)
     * 
     */
    private RubyRegexp getPattern(IRubyObject obj) {
        if (obj instanceof RubyRegexp) return (RubyRegexp)obj;
        return RubyRegexp.newRegexp(getRuntime(), getStringForPattern(obj).value);
    }

    private Regex getQuotedPattern(IRubyObject obj) {
        if (obj instanceof RubyRegexp) return ((RubyRegexp)obj).getPattern();
        Ruby runtime = getRuntime();
        return RubyRegexp.getQuotedRegexpFromCache(runtime, getStringForPattern(obj).value, runtime.getKCode().getEncoding(), 0);
    }

    private Regex getStringPattern(Ruby runtime, Encoding enc, IRubyObject obj) {
        return RubyRegexp.getQuotedRegexpFromCache(runtime, getStringForPattern(obj).value, enc, 0);
    }

    private Regex getStringPattern19(Ruby runtime, IRubyObject obj) {
        RubyString str = getStringForPattern(obj);
        if (str.scanForCodeRange() == CR_BROKEN) {
            throw runtime.newRegexpError("invalid multybyte character: " +
                    RubyRegexp.regexpDescription19(runtime, str.value, 0, str.value.encoding).toString());
        }
        if (str.value.encoding.isDummy()) {
            throw runtime.newArgumentError("can't make regexp with dummy encoding");
        }
        
        return RubyRegexp.getQuotedRegexpFromCache19(runtime, str.value, 0, str.isAsciiOnly());
    }

    /** rb_str_scan
     *
     */
    @JRubyMethod(name = "scan", required = 1, frame = true, reads = BACKREF, writes = BACKREF, compat = CompatVersion.RUBY1_8)
    public IRubyObject scan(ThreadContext context, IRubyObject arg, Block block) {
        Ruby runtime = context.getRuntime();
        Encoding enc = runtime.getKCode().getEncoding();
        final Regex pattern;
        final boolean tainted;
        if (arg instanceof RubyRegexp) {
            pattern = ((RubyRegexp)arg).getPattern();
            tainted = arg.isTaint();
        } else {
            pattern = getStringPattern(runtime, enc, arg);
            tainted = false;
        }

        int begin = value.begin;
        int range = begin + value.realSize;
        final Matcher matcher = pattern.matcher(value.bytes, begin, range);

        if (block.isGiven()) {
            return scanIter(context, pattern, matcher, enc, block, begin, range, tainted);
        } else {
            return scanNoIter(context, pattern, matcher, enc, begin, range, tainted);
        }
    }

    private IRubyObject scanIter(ThreadContext context, Regex pattern, Matcher matcher, Encoding enc, Block block, int begin, int range, boolean tainted) {
        Ruby runtime = context.getRuntime();
        byte[]bytes = value.bytes;
        int size = value.realSize;
        RubyMatchData match = null;
        Frame frame = context.getPreviousFrame();

        int end = 0;
        if (pattern.numberOfCaptures() == 0) {
            while (matcher.search(begin + end, range, Option.NONE) >= 0) {
                end = positionEnd(matcher, enc, begin, range);
                match = RubyRegexp.updateBackRef(context, this, frame, matcher, pattern);
                IRubyObject substr = substr(runtime, matcher.getBegin(), matcher.getEnd() - matcher.getBegin());
                if (tainted) { 
                    substr.setTaint(true);
                    match.setTaint(true);
                }
                block.yield(context, substr);
                modifyCheck(bytes, size);
            }
        } else {
            while (matcher.search(begin + end, range, Option.NONE) >= 0) {
                end = positionEnd(matcher, enc, begin, range);
                match = RubyRegexp.updateBackRef(context, this, frame, matcher, pattern);
                if (tainted) match.setTaint(true);
                block.yield(context, populateCapturesForScan(runtime, matcher, range, tainted));
                modifyCheck(bytes, size);
            }
        }
        frame.setBackRef(match == null ? runtime.getNil() : match);
        return this;
    }

    private IRubyObject scanNoIter(ThreadContext context, Regex pattern, Matcher matcher, Encoding enc, int begin, int range, boolean tainted) {
        Ruby runtime = context.getRuntime();
        RubyArray ary = runtime.newArray();

        int end = 0;
        if (pattern.numberOfCaptures() == 0) {
            while (matcher.search(begin + end, range, Option.NONE) >= 0) {
                end = positionEnd(matcher, enc, begin, range);
                IRubyObject substr = substr(runtime, matcher.getBegin(), matcher.getEnd() - matcher.getBegin());
                if (tainted) substr.setTaint(true);
                ary.append(substr);
            }
        } else {
            while (matcher.search(begin + end, range, Option.NONE) >= 0) {
                end = positionEnd(matcher, enc, begin, range);
                ary.append(populateCapturesForScan(runtime, matcher, range, tainted));
            }
        }

        Frame frame = context.getPreviousFrame();
        if (ary.size() > 0) {
            RubyMatchData match = RubyRegexp.updateBackRef(context, this, frame, matcher, pattern);
            if (tainted) match.setTaint(true);
        } else {
            frame.setBackRef(runtime.getNil());
        }
        return ary;
    }

    private int positionEnd(Matcher matcher, Encoding enc, int begin, int range) {
        int end = matcher.getEnd();
        if (matcher.getBegin() == end) {
            if (value.realSize > end) {
                return end + enc.length(value.bytes, begin + end, range);
            } else {
                return end + 1;
            }
        } else {
            return end;
        }
    }

    private IRubyObject populateCapturesForScan(Ruby runtime, Matcher matcher, int range, boolean tainted) {
        Region region = matcher.getRegion();
        RubyArray result = getRuntime().newArray(region.numRegs);
        for (int i=1; i<region.numRegs; i++) {
            int beg = region.beg[i]; 
            if (beg == -1) {
                result.append(runtime.getNil());
            } else {
                IRubyObject substr = substr(runtime, beg, region.end[i] - beg);
                if (tainted) substr.setTaint(true);
                result.append(substr);
            }
        }
        return result;
    }

    @JRubyMethod(name = "scan", required = 1, frame = true, reads = BACKREF, writes = BACKREF, compat = CompatVersion.RUBY1_9)
    public IRubyObject scan19(ThreadContext context, IRubyObject arg, Block block) {
        Ruby runtime = context.getRuntime();
        Encoding enc = value.encoding;
        final Regex pattern, pat;
        final RubyRegexp regexp;
        if (arg instanceof RubyRegexp) {
            regexp = (RubyRegexp)arg;
            pattern = regexp.getPattern();
            pat = regexp.preparePattern(this);
        } else {
            regexp = null;
            pattern = getStringPattern19(runtime, arg);
            pat = RubyRegexp.preparePattern(runtime, pattern, this);
        }
        int begin = value.begin;
        int range = begin + value.realSize;
        final Matcher matcher = pat.matcher(value.bytes, begin, range);

        if (block.isGiven()) {
            return scanIter19(context, pattern, matcher, enc, block, begin, range, regexp);
        } else {
            return scanNoIter19(context, pattern, matcher, enc, begin, range, regexp);
        }
    }

    private IRubyObject scanIter19(ThreadContext context, Regex pattern, Matcher matcher, Encoding enc, Block block, int begin, int range, RubyRegexp regexp) {
        Ruby runtime = context.getRuntime();
        byte[]bytes = value.bytes;
        int size = value.realSize;
        Frame frame = context.getPreviousFrame();
        boolean tainted = regexp != null && regexp.isTaint();

        int end = 0;
        RubyMatchData match = null;
        if (pattern.numberOfCaptures() == 0) {
            while (matcher.search(begin + end, range, Option.NONE) >= 0) {
                end = positionEnd(matcher, enc, begin, range);
                match = RubyRegexp.updateBackRef19(context, this, frame, matcher, pattern);
                match.regexp = regexp;
                IRubyObject substr = substr(runtime, matcher.getBegin(), matcher.getEnd() - matcher.getBegin());
                if (tainted) {
                    substr.setTaint(true);
                    match.setTaint(true);
                }
                block.yield(context, substr);
                modifyCheck(bytes, size, enc);
            }
        } else {
            while (matcher.search(begin + end, range, Option.NONE) >= 0) {
                end = positionEnd(matcher, enc, begin, range);
                match = RubyRegexp.updateBackRef19(context, this, frame, matcher, pattern);
                match.regexp = regexp;
                if (tainted) match.setTaint(true);
                block.yield(context, populateCapturesForScan(runtime, matcher, range, tainted));
                modifyCheck(bytes, size, enc);
            }
        }
        frame.setBackRef(match == null ? runtime.getNil() : match);
        return this;
    }

    private IRubyObject scanNoIter19(ThreadContext context, Regex pattern, Matcher matcher, Encoding enc, int begin, int range, RubyRegexp regexp) {
        Ruby runtime = context.getRuntime();
        RubyArray ary = runtime.newArray();
        boolean tainted = regexp != null && regexp.isTaint();

        int end = 0;
        if (pattern.numberOfCaptures() == 0) {
            while (matcher.search(begin + end, range, Option.NONE) >= 0) {
                end = positionEnd(matcher, enc, begin, range);
                IRubyObject substr = substr(runtime, matcher.getBegin(), matcher.getEnd() - matcher.getBegin());
                if (tainted) substr.setTaint(true);
                ary.append(substr);
            }
        } else {
            while (matcher.search(begin + end, range, Option.NONE) >= 0) {
                end = positionEnd(matcher, enc, begin, range);
                ary.append(populateCapturesForScan(runtime, matcher, range, tainted));
            }
        }

        Frame frame = context.getPreviousFrame();
        if (ary.size() > 0) {
            RubyMatchData match = RubyRegexp.updateBackRef19(context, this, frame, matcher, pattern);
            match.regexp = regexp;
            if (tainted) match.setTaint(true);
        } else {
            frame.setBackRef(runtime.getNil());
        }
        return ary;
    }

    @JRubyMethod(name = "start_with?", compat = CompatVersion.RUBY1_9)
    public IRubyObject start_with_p(ThreadContext context) {
        return context.getRuntime().getFalse();
    }

    @JRubyMethod(name = "start_with?", compat = CompatVersion.RUBY1_9)
    public IRubyObject start_with_p(ThreadContext context, IRubyObject arg) {
        return start_with_pCommon(arg) ? context.getRuntime().getTrue() : context.getRuntime().getFalse();
    }

    @JRubyMethod(name = "start_with?", rest = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject start_with_p(ThreadContext context, IRubyObject[]args) {
        for (int i = 0; i < args.length; i++) {
            if (start_with_pCommon(args[i])) return context.getRuntime().getTrue();
        }
        return context.getRuntime().getFalse();
    }

    private boolean start_with_pCommon(IRubyObject arg) {
        IRubyObject tmp = arg.checkStringType();
        if (tmp.isNil()) return false;
        RubyString otherString = (RubyString)tmp;
        checkEncoding(otherString);
        if (value.realSize < otherString.value.realSize) return false;
        return value.startsWith(otherString.value);
    }

    @JRubyMethod(name = "end_with?", compat = CompatVersion.RUBY1_9)
    public IRubyObject end_with_p(ThreadContext context) {
        return context.getRuntime().getFalse();
    }

    @JRubyMethod(name = "end_with?", compat = CompatVersion.RUBY1_9)
    public IRubyObject end_with_p(ThreadContext context, IRubyObject arg) {
        return end_with_pCommon(arg) ? context.getRuntime().getTrue() : context.getRuntime().getFalse();
    }

    @JRubyMethod(name = "end_with?", rest = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject end_with_p(ThreadContext context, IRubyObject[]args) {
        for (int i = 0; i < args.length; i++) {
            if (end_with_pCommon(args[i])) return context.getRuntime().getTrue();
        }
        return context.getRuntime().getFalse();
    }

    private boolean end_with_pCommon(IRubyObject arg) {
        IRubyObject tmp = arg.checkStringType();
        if (tmp.isNil()) return false;
        RubyString otherString = (RubyString)tmp;
        Encoding enc = checkEncoding(otherString);
        if (value.realSize < otherString.value.realSize) return false;;
        int p = value.begin;
        int end = p + value.realSize;
        int s = end - otherString.value.realSize;
        if (enc.leftAdjustCharHead(value.bytes, p, s, end) != s) return false;
        return value.endsWith(otherString.value);
    }

    private static final ByteList SPACE_BYTELIST = new ByteList(ByteList.plain(" "));

    private IRubyObject justify(IRubyObject arg0, int jflag) {
        Ruby runtime = getRuntime();
        return justifyCommon(runtime, SPACE_BYTELIST, RubyFixnum.num2int(arg0), jflag);
    }

    private IRubyObject justify(IRubyObject arg0, IRubyObject arg1, int jflag) {
        Ruby runtime = getRuntime();
        ByteList pad = arg1.convertToString().value;
        if (pad.realSize == 0) throw runtime.newArgumentError("zero width padding");
        return justifyCommon(runtime, pad, RubyFixnum.num2int(arg0), jflag).infectBy(arg1);
    }

    private IRubyObject justifyCommon(Ruby runtime, ByteList pad, int width, int jflag) {
        if (width < 0 || value.realSize >= width) return strDup(runtime);

        ByteList res = new ByteList(width);
        res.realSize = width;

        int padP = pad.begin;
        int padLen = pad.realSize; 
        byte padBytes[] = pad.bytes;

        int p = res.begin;
        byte bytes[] = res.bytes;

        if (jflag != 'l') {
            int n = width - value.realSize;
            int end = p + ((jflag == 'r') ? n : n / 2);
            if (padLen <= 1) {
                while (p < end) {
                    bytes[p++] = padBytes[padP];
                }
            } else {
                int q = padP;
                while (p + padLen <= end) {
                    System.arraycopy(padBytes, padP, bytes, p, padLen);
                    p += padLen;
                }
                while (p < end) {
                    bytes[p++] = padBytes[q++];
                }
            }
        }

        System.arraycopy(value.bytes, value.begin, bytes, p, value.realSize);

        if (jflag != 'r') {
            p += value.realSize;
            int end = res.begin + width;
            if (padLen <= 1) {
                while (p < end) {
                    bytes[p++] = padBytes[padP];
                }
            } else {
                while (p + padLen <= end) {
                    System.arraycopy(padBytes, padP, bytes, p, padLen);
                    p += padLen;
                }
                while (p < end) {
                    bytes[p++] = padBytes[padP++];
                }
            }
        }

        return new RubyString(runtime, getMetaClass(), res).infectBy(this);
    }

    private IRubyObject justify19(IRubyObject arg0, int jflag) {
        Ruby runtime = getRuntime();
        RubyString result = justifyCommon(runtime, SPACE_BYTELIST, 
                                                   1,
                                                   true,
                                                   value.encoding, RubyFixnum.num2int(arg0), jflag);
        if (getCodeRange() != CR_BROKEN) result.setCodeRange(getCodeRange());
        return result;
    }

    private IRubyObject justify19(IRubyObject arg0, IRubyObject arg1, int jflag) {
        Ruby runtime = getRuntime();
        RubyString padStr = arg1.convertToString();
        ByteList pad = padStr.value;
        Encoding enc = checkEncoding(padStr);
        int padCharLen = padStr.strLength(enc);
        if (pad.realSize == 0 || padCharLen == 0) throw runtime.newArgumentError("zero width padding");
        RubyString result = justifyCommon(runtime, pad, 
                                                   padCharLen, 
                                                   padStr.singleByteOptimizable(), 
                                                   enc, RubyFixnum.num2int(arg0), jflag);
        result.infectBy(padStr);
        int cr = codeRangeAnd(getCodeRange(), padStr.getCodeRange());
        if (cr != CR_BROKEN) result.setCodeRange(cr);
        return result;
    }

    private RubyString justifyCommon(Ruby runtime, ByteList pad, int padCharLen, boolean padSinglebyte, Encoding enc, int width, int jflag) {
        int len = strLength(enc);
        if (width < 0 || len >= width) return strDup(runtime);
        int n = width - len;

        int llen = (jflag == 'l') ? 0 : ((jflag == 'r') ? n : n / 2);
        int rlen = n - llen;

        int padP = pad.begin;
        int padLen = pad.realSize;
        byte padBytes[] = pad.bytes;

        ByteList res = new ByteList(value.realSize + n * padLen / padCharLen + 2);

        int p = res.begin;
        byte bytes[] = res.bytes;

        while (llen > 0) {
            if (padLen <= 1) { 
                bytes[p++] = padBytes[padP];
                llen--;
            } else if (llen > padCharLen) {
                System.arraycopy(padBytes, padP, bytes, p, padLen);
                p += padLen;
                llen -= padCharLen;
            } else {
                int padPP = padSinglebyte ? padP + llen : StringSupport.nth(enc, padBytes, padP, padP + padLen, llen);
                n = padPP - padP;
                System.arraycopy(padBytes, padP, bytes, p, n);
                p += n;
                break;
            }
        }

        System.arraycopy(value.bytes, value.begin, bytes, p, value.realSize);
        p += value.realSize;

        while (rlen > 0) {
            if (padLen <= 1) { 
                bytes[p++] = padBytes[padP];
                rlen--;
            } else if (rlen > padCharLen) {
                System.arraycopy(padBytes, padP, bytes, p, padLen);
                p += padLen;
                rlen -= padCharLen;
            } else {
                int padPP = padSinglebyte ? padP + rlen : StringSupport.nth(enc, padBytes, padP, padP + padLen, rlen);
                n = padPP - padP;
                System.arraycopy(padBytes, padP, bytes, p, n);
                p += n;
                break;
            }
        }
        
        res.realSize = p;

        RubyString result = new RubyString(runtime, getMetaClass(), res);
        result.infectBy(this);
        result.associateEncoding(enc);
        return result;
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
    @JRubyMethod(name = "ljust", compat = CompatVersion.RUBY1_8)
    public IRubyObject ljust(IRubyObject arg0) {
        return justify(arg0, 'l');
    }

    @JRubyMethod(name = "ljust", compat = CompatVersion.RUBY1_8)
    public IRubyObject ljust(IRubyObject arg0, IRubyObject arg1) {
        return justify(arg0, arg1, 'l');
    }

    @JRubyMethod(name = "ljust", compat = CompatVersion.RUBY1_9)
    public IRubyObject ljust19(IRubyObject arg0) {
        return justify19(arg0, 'l');
    }

    @JRubyMethod(name = "ljust", compat = CompatVersion.RUBY1_9)
    public IRubyObject ljust19(IRubyObject arg0, IRubyObject arg1) {
        return justify19(arg0, arg1, 'l');
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
    @JRubyMethod(name = "rjust", compat = CompatVersion.RUBY1_8)
    public IRubyObject rjust(IRubyObject arg0) {
        return justify(arg0, 'r');
    }

    @JRubyMethod(name = "rjust", compat = CompatVersion.RUBY1_8)
    public IRubyObject rjust(IRubyObject arg0, IRubyObject arg1) {
        return justify(arg0, arg1, 'r');
    }

    @JRubyMethod(name = "rjust", compat = CompatVersion.RUBY1_9)
    public IRubyObject rjust19(IRubyObject arg0) {
        return justify19(arg0, 'r');
    }

    @JRubyMethod(name = "rjust", compat = CompatVersion.RUBY1_9)
    public IRubyObject rjust19(IRubyObject arg0, IRubyObject arg1) {
        return justify19(arg0, arg1, 'r');
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
    @JRubyMethod(name = "center", compat = CompatVersion.RUBY1_8)
    public IRubyObject center(IRubyObject arg0) {
        return justify(arg0, 'c');
    }

    @JRubyMethod(name = "center", compat = CompatVersion.RUBY1_8)
    public IRubyObject center(IRubyObject arg0, IRubyObject arg1) {
        return justify(arg0, arg1, 'c');
    }

    @JRubyMethod(name = "center", compat = CompatVersion.RUBY1_9)
    public IRubyObject center19(IRubyObject arg0) {
        return justify19(arg0, 'c');
    }

    @JRubyMethod(name = "center", compat = CompatVersion.RUBY1_9)
    public IRubyObject center19(IRubyObject arg0, IRubyObject arg1) {
        return justify19(arg0, arg1, 'c');
    }

    @JRubyMethod(name = "partition", compat = CompatVersion.RUBY1_9)
    public IRubyObject partition(ThreadContext context, IRubyObject arg) {
        Ruby runtime = context.getRuntime();
        final int pos;
        final RubyString sep;
        if (arg instanceof RubyRegexp) {
            RubyRegexp regex = (RubyRegexp)arg;
            pos = regex.search19(context, this, 0, false);
            if (pos < 0) return partitionMismatch(runtime);
            sep = (RubyString)subpat19(runtime, context, regex, 0);
            if (pos == 0 && sep.value.realSize == 0) return partitionMismatch(runtime);
        } else {
            IRubyObject tmp = arg.checkStringType();
            if (tmp.isNil()) throw runtime.newTypeError("type mismatch: " + arg.getMetaClass().getName() + " given");
            sep = (RubyString)tmp;
            pos = strIndex19(sep, 0);
            if (pos < 0) return partitionMismatch(runtime);
        }

        return RubyArray.newArray(runtime, new IRubyObject[]{
                makeShared19(runtime, 0, pos),
                sep,
                makeShared19(runtime, pos + sep.value.realSize, value.realSize - pos - sep.value.realSize)});
    }

    private IRubyObject partitionMismatch(Ruby runtime) {
        return RubyArray.newArray(runtime, new IRubyObject[]{this, newEmptyString(runtime), newEmptyString(runtime)});
    }
    
    @JRubyMethod(name = "rpartition", compat = CompatVersion.RUBY1_9)
    public IRubyObject rpartition(ThreadContext context, IRubyObject arg) {
        Ruby runtime = context.getRuntime();
        final int pos;
        final RubyString sep;
        if (arg instanceof RubyRegexp) {
            RubyRegexp regex = (RubyRegexp)arg;
            pos = regex.search19(context, this, value.realSize, true);
            if (pos < 0) return rpartitionMismatch(runtime);
            sep = (RubyString)RubyRegexp.nth_match(0, context.getCurrentFrame().getBackRef());
        } else {
            IRubyObject tmp = arg.checkStringType();
            if (tmp.isNil()) throw runtime.newTypeError("type mismatch: " + arg.getMetaClass().getName() + " given");
            sep = (RubyString)tmp;
            pos = strRindex19(sep, subLength(value.realSize));
            if (pos < 0) return rpartitionMismatch(runtime);
        }

        return RubyArray.newArray(runtime, new IRubyObject[]{
                substr19(runtime, 0, pos),
                sep,
                substr19(runtime, pos + sep.strLength(), value.realSize)});
    }

    private IRubyObject rpartitionMismatch(Ruby runtime) {
        return RubyArray.newArray(runtime, new IRubyObject[]{newEmptyString(runtime), newEmptyString(runtime), this});
    }
    
    /** rb_str_chop / rb_str_chop_bang
     * 
     */
    @JRubyMethod(name = "chop", compat = CompatVersion.RUBY1_8)
    public IRubyObject chop(ThreadContext context) {
        if (value.realSize == 0) return newEmptyString(context.getRuntime(), getMetaClass()).infectBy(this);
        return makeShared(context.getRuntime(), 0, choppedLength());
    }

    @JRubyMethod(name = "chop!", compat = CompatVersion.RUBY1_8)
    public IRubyObject chop_bang(ThreadContext context) {
        if (value.realSize == 0) return context.getRuntime().getNil();
        view(0, choppedLength());
        return this;
    }

    private int choppedLength() {
        int end = value.realSize - 1;
        if ((value.bytes[value.begin + end]) == '\n') {
            if (end > 0 && (value.bytes[value.begin + end - 1]) == '\r') end--;
        }
        return end;
    }

    @JRubyMethod(name = "chop", compat = CompatVersion.RUBY1_9)
    public IRubyObject chop19(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) return newEmptyString(runtime, getMetaClass(), value.encoding).infectBy(this);
        return makeShared19(runtime, 0, choppedLength19(runtime));
    }

    @JRubyMethod(name = "chop!", compat = CompatVersion.RUBY1_9)
    public IRubyObject chop_bang19(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) return runtime.getNil();
        keepCodeRange();
        view(0, choppedLength19(runtime));
        return this;
    }

    private int choppedLength19(Ruby runtime) {
        int p = value.begin;
        int end = p + value.realSize;

        if (p > end) return 0;
        byte bytes[] = value.bytes;
        Encoding enc = value.encoding;

        int s = enc.prevCharHead(bytes, p, end, end);
        if (s == -1) return 0;
        if (s > p && codePoint(runtime, enc, bytes, s, end) == '\n') {
            int s2 = enc.prevCharHead(bytes, p, s, end);
            if (s2 != -1 && codePoint(runtime, enc, bytes, s2, end) == '\r') s = s2;
        }
        return s - p;
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
            return chomp(getRuntime().getCurrentContext());
        case 1:
            return chomp(getRuntime().getCurrentContext(), args[0]);
        default:
            Arity.raiseArgumentError(getRuntime(), args.length, 0, 1);
            return null; // not reached
        }
    }

    public IRubyObject chomp_bang(IRubyObject[] args) {
        switch (args.length) {
        case 0:
            return chomp_bang(getRuntime().getCurrentContext());
        case 1:
            return chomp_bang(getRuntime().getCurrentContext(), args[0]);
        default:
            Arity.raiseArgumentError(getRuntime(), args.length, 0, 1);
            return null; // not reached
        }
    }

    /** rb_str_chop
     * 
     */
    @JRubyMethod(name = "chomp", compat = CompatVersion.RUBY1_8)
    public RubyString chomp(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.chomp_bang(context);
        return str;
    }

    @JRubyMethod(name = "chomp", compat = CompatVersion.RUBY1_8)
    public RubyString chomp(ThreadContext context, IRubyObject arg0) {
        RubyString str = strDup(context.getRuntime());
        str.chomp_bang(context, arg0);
        return str;
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
    @JRubyMethod(name = "chomp!", compat = CompatVersion.RUBY1_8)
    public IRubyObject chomp_bang(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) return runtime.getNil();

        IRubyObject rsObj = runtime.getGlobalVariables().get("$/");

        if (rsObj == runtime.getGlobalVariables().getDefaultSeparator()) return smartChopBangCommon(runtime);
        return chompBangCommon(runtime, rsObj);
    }

    @JRubyMethod(name = "chomp!", compat = CompatVersion.RUBY1_8)
    public IRubyObject chomp_bang(ThreadContext context, IRubyObject arg0) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) return runtime.getNil();
        return chompBangCommon(runtime, arg0);
    }

    private IRubyObject chompBangCommon(Ruby runtime, IRubyObject rsObj) {
        if (rsObj.isNil()) return rsObj;

        RubyString rs = rsObj.convertToString();
        int p = value.begin;
        int len = value.realSize;
        byte[] bytes = value.bytes;

        int rslen = rs.value.realSize;
        if (rslen == 0) {
            while (len > 0 && bytes[p + len - 1] == (byte)'\n') {
                len--;
                if (len > 0 && bytes[p + len - 1] == (byte)'\r') len--;
            }
            if (len < value.realSize) {
                view(0, len);
                return this;
            }
            return runtime.getNil();
        }

        if (rslen > len) return runtime.getNil();
        byte newline = rs.value.bytes[rslen - 1];
        if (rslen == 1 && newline == (byte)'\n') return smartChopBangCommon(runtime);

        if (bytes[p + len - 1] == newline && rslen <= 1 || value.endsWith(rs.value)) {
            view(0, value.realSize - rslen);
            return this;
        }
        return runtime.getNil();
    }
    
    private IRubyObject smartChopBangCommon(Ruby runtime) {
        int len = value.realSize;
        int p = value.begin;
        byte[]bytes = value.bytes;
        if (bytes[p + len - 1] == (byte)'\n') {
            len--;
            if (len > 0 && bytes[p + len - 1] == (byte)'\r') len--;
            view(0, len);
        } else if (bytes[p + len - 1] == (byte)'\r') {
            len--;
            view(0, len);
        } else {
            modifyCheck();
            return runtime.getNil();
        }
        return this; 
    }

    @JRubyMethod(name = "chomp", compat = CompatVersion.RUBY1_9)
    public RubyString chomp19(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.chomp_bang19(context);
        return str;
    }

    @JRubyMethod(name = "chomp", compat = CompatVersion.RUBY1_9)
    public RubyString chomp19(ThreadContext context, IRubyObject arg0) {
        RubyString str = strDup(context.getRuntime());
        str.chomp_bang19(context, arg0);
        return str;
    }

    @JRubyMethod(name = "chomp!", compat = CompatVersion.RUBY1_9)
    public IRubyObject chomp_bang19(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) return runtime.getNil();

        IRubyObject rsObj = runtime.getGlobalVariables().get("$/");

        if (rsObj == runtime.getGlobalVariables().getDefaultSeparator()) return smartChopBangCommon19(runtime);
        return chompBangCommon19(runtime, rsObj);
    }

    @JRubyMethod(name = "chomp!", compat = CompatVersion.RUBY1_9)
    public IRubyObject chomp_bang19(ThreadContext context, IRubyObject arg0) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) return runtime.getNil();
        return chompBangCommon19(runtime, arg0);
    }
    
    private IRubyObject chompBangCommon19(Ruby runtime, IRubyObject rsObj) {
        if (rsObj.isNil()) return rsObj;

        RubyString rs = rsObj.convertToString();
        int p = value.begin;
        int len = value.realSize;
        int end = p + len;  
        byte[] bytes = value.bytes;

        int rslen = rs.value.realSize;
        if (rslen == 0) {
            while (len > 0 && bytes[p + len - 1] == (byte)'\n') {
                len--;
                if (len > 0 && bytes[p + len - 1] == (byte)'\r') len--;
            }
            if (len < value.realSize) {
                keepCodeRange();
                view(0, len);
                return this;
            }
            return runtime.getNil();
        }

        if (rslen > len) return runtime.getNil();
        byte newline = rs.value.bytes[rslen - 1];
        if (rslen == 1 && newline == (byte)'\n') return smartChopBangCommon19(runtime);

        Encoding enc = checkEncoding(rs);
        if (rs.scanForCodeRange() == CR_BROKEN) return runtime.getNil();

        int pp = end - rslen; 
        if (bytes[p + len - 1] == newline && rslen <= 1 || value.endsWith(rs.value)) {
            if (enc.leftAdjustCharHead(bytes, p, pp, end) != pp) return runtime.getNil();
            keepCodeRange();
            view(0, value.realSize - rslen);
            return this;
        }
        return runtime.getNil();
    }
    
    private IRubyObject smartChopBangCommon19(Ruby runtime) {
        final int p = value.begin;
        int len = value.realSize;
        int end = p + len;
        byte bytes[] = value.bytes;
        Encoding enc = value.encoding;

        keepCodeRange();
        if (enc.minLength() > 1) {
            int pp = enc.leftAdjustCharHead(bytes, p, end - enc.minLength(), end);
            if (enc.isNewLine(bytes, pp, end)) end = pp;
            pp = end - enc.minLength();
            if (pp >= p) {
                pp = enc.leftAdjustCharHead(bytes, p, pp, end);
                if (StringSupport.preciseLength(enc, bytes, pp, end) > 0 && 
                        enc.mbcToCode(bytes, pp, end) == '\r') end = pp;
            }
            if (end == p + value.realSize) {
                modifyCheck();
                return runtime.getNil();
            }
            len = end - p;
            view(0, len);
        } else {
            if (bytes[p + len - 1] == (byte)'\n') {
                len--;
                if (len > 0 && bytes[p + len - 1] == (byte)'\r') len--;
                view(0, len);
            } else if (bytes[p + len - 1] == (byte)'\r') {
                len--;
                view(0, len);
            } else {
                modifyCheck();
                return runtime.getNil();
            }
        }
        return this;
    }

    /** rb_str_lstrip / rb_str_lstrip_bang
     * 
     */
    @JRubyMethod(name = "lstrip", compat = CompatVersion.RUBY1_8)
    public IRubyObject lstrip(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.lstrip_bang(context);
        return str;
    }

    @JRubyMethod(name = "lstrip!", compat = CompatVersion.RUBY1_8)
    public IRubyObject lstrip_bang(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) return runtime.getNil();
        return singleByteLStrip(runtime, ASCII, value.bytes, value.begin, value.begin + value.realSize);
    }

    @JRubyMethod(name = "lstrip", compat = CompatVersion.RUBY1_9)
    public IRubyObject lstrip19(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.lstrip_bang19(context);
        return str;
    }

    @JRubyMethod(name = "lstrip!", compat = CompatVersion.RUBY1_9)
    public IRubyObject lstrip_bang19(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) {
            modifyCheck();
            return runtime.getNil();
        }

        Encoding enc = value.encoding;
        int s = value.begin;
        int end = s + value.realSize;
        byte[]bytes = value.bytes;

        final IRubyObject result;
        if (singleByteOptimizable(enc)) {
            result = singleByteLStrip(runtime, enc, bytes, s, end);
        } else {
            result = multiByteLStrip(runtime, enc, bytes, s, end);
        }
        keepCodeRange();
        return result;
    }

    private IRubyObject singleByteLStrip(Ruby runtime, Encoding enc, byte[]bytes, int s, int end) {
        int p = s;
        while (p < end && enc.isSpace(bytes[p] & 0xff)) p++;
        if (p > s) {
            view(p - s, end - p);
            return this;
        }
        return runtime.getNil();
    }
    
    private IRubyObject multiByteLStrip(Ruby runtime, Encoding enc, byte[]bytes, int s, int end) {
        int p = s;
        int c;
        while (p < end && enc.isSpace(c = codePoint(runtime, enc, bytes, p, end))) p += codeLength(runtime, enc, c);
        if (p > s) {
            view(p - s, end - p);
            return this;
        }
        return runtime.getNil();
    }

    /** rb_str_rstrip / rb_str_rstrip_bang
     *  
     */
    @JRubyMethod(name = "rstrip", compat = CompatVersion.RUBY1_8)
    public IRubyObject rstrip(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.rstrip_bang(context);
        return str;
    }

    @JRubyMethod(name = "rstrip!", compat = CompatVersion.RUBY1_8)
    public IRubyObject rstrip_bang(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) return runtime.getNil();
        return singleByteRStrip(runtime, ASCII, value.bytes, value.begin, value.begin + value.realSize);
    }

    @JRubyMethod(name = "rstrip", compat = CompatVersion.RUBY1_9)
    public IRubyObject rstrip19(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.rstrip_bang19(context);
        return str;
    }

    @JRubyMethod(name = "rstrip!", compat = CompatVersion.RUBY1_9)
    public IRubyObject rstrip_bang19(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) {
            modifyCheck();
            return runtime.getNil();
        }

        Encoding enc = value.encoding;
        int s = value.begin;
        int end = s + value.realSize;
        byte[]bytes = value.bytes;

        final IRubyObject result;
        if (singleByteOptimizable(enc)) {
            result = singleByteRStrip(runtime, enc, bytes, s, end);
        } else {
            result = multiByteRStrip(runtime, enc, bytes, s, end);
        }
        keepCodeRange();
        return result;
    }

    private IRubyObject singleByteRStrip2(Ruby runtime, Encoding enc, byte[]bytes, int s, int end) {
        int endp = end;
        while (endp - 1 >= s && bytes[endp - 1] == 0) endp--;
        while (endp - 1 >= s && enc.isSpace(bytes[endp - 1] & 0xff)) endp--;

        if (endp < end) {
            view(0, endp - s);
            return this;
        }
        return runtime.getNil();
    }

    private IRubyObject singleByteRStrip(Ruby runtime, Encoding enc, byte[]bytes, int s, int end) {
        int endp = end - 1;
        while (endp >= s && bytes[endp] == 0) endp--;
        while (endp >= s && enc.isSpace(bytes[endp] & 0xff)) endp--;

        if (endp < end - 1) {
            view(0, endp - s + 1);
            return this;
        }
        return runtime.getNil();
    }

    private IRubyObject multiByteRStrip(Ruby runtime, Encoding enc, byte[]bytes, int s, int end) {
        int endp = end;
        int prev;
        while ((prev = enc.prevCharHead(bytes, s, endp, end)) != -1) {
            if (!enc.isSpace(codePoint(runtime, enc, bytes, prev, end))) break;
            endp = prev;
        }

        if (prev < end) {
            view(0, prev - s + 1);
            return this;
        }
        return runtime.getNil();
    }

    /** rb_str_strip / rb_str_strip_bang
     *
     */
    @JRubyMethod(name = "strip", compat = CompatVersion.RUBY1_8)
    public IRubyObject strip(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.strip_bang(context);
        return str;
    }

    @JRubyMethod(name = "strip!", compat = CompatVersion.RUBY1_8)
    public IRubyObject strip_bang(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) return runtime.getNil();
        return singleByteStrip(runtime, ASCII, value.bytes, value.begin, value.begin + value.realSize);
    }

    @JRubyMethod(name = "strip", compat = CompatVersion.RUBY1_9)
    public IRubyObject strip19(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.strip_bang19(context);
        return str;
    }

    @JRubyMethod(name = "strip!", compat = CompatVersion.RUBY1_9)
    public IRubyObject strip_bang19(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) {
            modifyCheck();
            return runtime.getNil();
        }

        Encoding enc = value.encoding;
        int s = value.begin;
        int end = s + value.realSize;
        byte[]bytes = value.bytes;

        final IRubyObject result;
        if (singleByteOptimizable(enc)) {
            result = singleByteStrip(runtime, enc, bytes, s, end);
        } else {
            result = multiByteStrip(runtime, enc, bytes, s, end);
        }
        keepCodeRange();
        return result;
    }

    private IRubyObject singleByteStrip(Ruby runtime, Encoding enc, byte[]bytes, int s, int end) {
        int p = s;
        while (p < end && enc.isSpace(bytes[p] & 0xff)) p++;
        int endp = end - 1;
        while (endp >= p && bytes[endp] == 0) endp--;
        while (endp >= p && enc.isSpace(bytes[endp] & 0xff)) endp--;

        if (p > s || endp < end - 1) {
            view(p - s, endp - p + 1);
            return this;
        }
        return runtime.getNil();
    }

    private IRubyObject multiByteStrip(Ruby runtime, Encoding enc, byte[]bytes, int s, int end) {
        int p = s;
        int c;
        while (p < end && enc.isSpace(c = codePoint(runtime, enc, bytes, p, end))) p += codeLength(runtime, enc, c);
        
        int endp = end;
        int prev;
        while ((prev = enc.prevCharHead(bytes, s, endp, end)) != -1) {
            if (!enc.isSpace(codePoint(runtime, enc, bytes, prev, end))) break;
            endp = prev;
        }
        if (p > s || prev < end) {
            view(p - s, endp - p);
            return this;
        }
        return runtime.getNil();
    }

    /** rb_str_count
     *
     */
    @JRubyMethod(name = "count", compat = CompatVersion.RUBY1_8)
    public IRubyObject count(ThreadContext context) {
        throw context.getRuntime().newArgumentError("wrong number of arguments");
    }

    @JRubyMethod(name = "count", compat = CompatVersion.RUBY1_8)
    public IRubyObject count(ThreadContext context, IRubyObject arg) {
        final boolean[]table = new boolean[TRANS_SIZE];
        arg.convertToString().trSetupTable(table, true);
        return countCommon(context.getRuntime(), table);
    }

    @JRubyMethod(name = "count", required = 1, rest = true, compat = CompatVersion.RUBY1_8)
    public IRubyObject count(ThreadContext context, IRubyObject[] args) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) return RubyFixnum.zero(runtime);

        final boolean[]table = new boolean[TRANS_SIZE];
        args[0].convertToString().trSetupTable(table, true);
        for (int i = 1; i<args.length; i++) {
            args[i].convertToString().trSetupTable(table, false);;
        }

        return countCommon(runtime, table);
    }

    private IRubyObject countCommon(Ruby runtime, boolean[]table) {
        int i = 0;
        byte[]bytes = value.bytes;
        int p = value.begin;
        int end = p + value.realSize;

        while (p < end) if (table[bytes[p++] & 0xff]) i++;
        return runtime.newFixnum(i);
    }

    @JRubyMethod(name = "count", compat = CompatVersion.RUBY1_9)
    public IRubyObject count19(ThreadContext context) {
        throw context.getRuntime().newArgumentError("wrong number of arguments");
    }

    @JRubyMethod(name = "count", compat = CompatVersion.RUBY1_9)
    public IRubyObject count19(ThreadContext context, IRubyObject arg) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) return RubyFixnum.zero(runtime);

        RubyString otherStr = arg.convertToString();
        Encoding enc = checkEncoding(otherStr);
        final boolean[]table = new boolean[TRANS_SIZE];
        TrTables tables = otherStr.trSetupTable(context.getRuntime(), table, null, true, enc);
        return countCommon19(runtime, table, tables, enc);
    }

    @JRubyMethod(name = "count", required = 1, rest = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject count19(ThreadContext context, IRubyObject[] args) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) return RubyFixnum.zero(runtime);

        RubyString otherStr = args[0].convertToString();
        Encoding enc = checkEncoding(otherStr);
        final boolean[]table = new boolean[TRANS_SIZE];
        TrTables tables = otherStr.trSetupTable(runtime, table, null, true, enc);
        for (int i = 1; i<args.length; i++) {
            otherStr = args[i].convertToString();
            enc = checkEncoding(otherStr);
            tables = otherStr.trSetupTable(runtime, table, tables, false, enc);
        }

        return countCommon19(runtime, table, tables, enc);
    }

    private IRubyObject countCommon19(Ruby runtime, boolean[]table, TrTables tables, Encoding enc) {
        int i = 0;
        byte[]bytes = value.bytes;
        int p = value.begin;
        int end = p + value.realSize;

        int c;
        while (p < end) {
            if (enc.isAsciiCompatible() && Encoding.isAscii(c = bytes[p] & 0xff)) {
                if (table[c]) i++;
                p++;
            } else {
                c = codePoint(runtime, enc, bytes, p, end);
                int cl = codeLength(runtime, enc, c);
                if (trFind(c, table, tables)) i++;
                p += cl;
            }
        }

        return runtime.newFixnum(i);
    }

    /** rb_str_delete / rb_str_delete_bang
     *
     */
    @JRubyMethod(name = "delete", compat = CompatVersion.RUBY1_8)
    public IRubyObject delete(ThreadContext context) {
        throw context.getRuntime().newArgumentError("wrong number of arguments");
    }

    @JRubyMethod(name = "delete", compat = CompatVersion.RUBY1_8)
    public IRubyObject delete(ThreadContext context, IRubyObject arg) {
        RubyString str = strDup(context.getRuntime());
        str.delete_bang(context, arg);
        return str;
    }

    @JRubyMethod(name = "delete", required = 1, rest = true, compat = CompatVersion.RUBY1_8)
    public IRubyObject delete(ThreadContext context, IRubyObject[] args) {
        RubyString str = strDup(context.getRuntime());
        str.delete_bang(context, args);
        return str;
    }

    @JRubyMethod(name = "delete!", compat = CompatVersion.RUBY1_8)
    public IRubyObject delete_bang(ThreadContext context) {
        throw context.getRuntime().newArgumentError("wrong number of arguments");
    }

    @JRubyMethod(name = "delete!", compat = CompatVersion.RUBY1_8)
    public IRubyObject delete_bang(ThreadContext context, IRubyObject arg) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) return runtime.getNil();
        final boolean[]squeeze = new boolean[TRANS_SIZE];
        arg.convertToString().trSetupTable(squeeze, true);
        return delete_bangCommon(runtime, squeeze);
    }

    @JRubyMethod(name = "delete!", required = 1, rest = true, compat = CompatVersion.RUBY1_8)
    public IRubyObject delete_bang(ThreadContext context, IRubyObject[] args) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) return runtime.getNil();
        boolean[]squeeze = new boolean[TRANS_SIZE];

        args[0].convertToString().trSetupTable(squeeze, true);
        for (int i=1; i<args.length; i++) {
            args[i].convertToString().trSetupTable(squeeze, false);
        }

        return delete_bangCommon(runtime, squeeze);
    }

    private IRubyObject delete_bangCommon(Ruby runtime, boolean[]squeeze) {
        modify();

        int s = value.begin;
        int t = s;
        int send = s + value.realSize;
        byte[]bytes = value.bytes;
        boolean modify = false;

        while (s < send) {
            if (squeeze[bytes[s] & 0xff]) {
                modify = true;
            } else {
                bytes[t++] = bytes[s];
            }
            s++;
        }
        value.realSize = t - value.begin;

        return modify ? this : runtime.getNil();        
    }

    @JRubyMethod(name = "delete", compat = CompatVersion.RUBY1_9)
    public IRubyObject delete19(ThreadContext context) {
        throw context.getRuntime().newArgumentError("wrong number of arguments");
    }

    @JRubyMethod(name = "delete", compat = CompatVersion.RUBY1_9)
    public IRubyObject delete19(ThreadContext context, IRubyObject arg) {
        RubyString str = strDup(context.getRuntime());
        str.delete_bang19(context, arg);
        return str;
    }

    @JRubyMethod(name = "delete", required = 1, rest = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject delete19(ThreadContext context, IRubyObject[] args) {
        RubyString str = strDup(context.getRuntime());
        str.delete_bang19(context, args);
        return str;
    }

    @JRubyMethod(name = "delete!", compat = CompatVersion.RUBY1_9)
    public IRubyObject delete_bang19(ThreadContext context) {
        throw context.getRuntime().newArgumentError("wrong number of arguments");
    }

    @JRubyMethod(name = "delete!", compat = CompatVersion.RUBY1_9)
    public IRubyObject delete_bang19(ThreadContext context, IRubyObject arg) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) return runtime.getNil();

        RubyString otherStr = arg.convertToString();
        Encoding enc = checkEncoding(otherStr);
        final boolean[]squeeze = new boolean[TRANS_SIZE];
        TrTables tables = otherStr.trSetupTable(runtime, squeeze, null, true, enc);
        return delete_bangCommon19(runtime, squeeze, tables, enc);
    }

    @JRubyMethod(name = "delete!", required = 1, rest = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject delete_bang19(ThreadContext context, IRubyObject[] args) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) return runtime.getNil();

        RubyString otherStr = args[0].convertToString();
        Encoding enc = checkEncoding(otherStr);
        boolean[]squeeze = new boolean[TRANS_SIZE];
        TrTables tables = otherStr.trSetupTable(runtime, squeeze, null, true, enc);
        for (int i=1; i<args.length; i++) {
            otherStr = args[i].convertToString();
            enc = checkEncoding(otherStr);
            tables = otherStr.trSetupTable(runtime, squeeze, tables, false, enc);
        }

        return delete_bangCommon19(runtime, squeeze, tables, enc);
    }

    private IRubyObject delete_bangCommon19(Ruby runtime, boolean[]squeeze, TrTables tables, Encoding enc) {
        modifyAndKeepCodeRange();

        int s = value.begin;
        int t = s;
        int send = s + value.realSize;
        byte[]bytes = value.bytes;
        boolean modify = false;
        int c;
        while (s < send) {
            if (enc.isAsciiCompatible() && Encoding.isAscii(c = bytes[s] & 0xff)) {
                if (squeeze[c]) {
                    modify = true;
                } else {
                    if (t != s) bytes[t] = (byte)c;
                    t++;
                }
                s++;
            } else {
                c = codePoint(runtime, enc, bytes, s, send);
                int cl = codeLength(runtime, enc, c);
                if (trFind(c, squeeze, tables)) {
                    modify = true;
                } else {
                    if (t != s) enc.codeToMbc(c, bytes, t);
                    t += cl;
                }
                s += cl;
            }
        }
        value.realSize = t - value.begin;

        return modify ? this : runtime.getNil();        
    }

    /** rb_str_squeeze / rb_str_squeeze_bang
     *
     */
    @JRubyMethod(name = "squeeze", compat = CompatVersion.RUBY1_8)
    public IRubyObject squeeze(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.squeeze_bang(context);
        return str;
    }

    @JRubyMethod(name = "squeeze", compat = CompatVersion.RUBY1_8)
    public IRubyObject squeeze(ThreadContext context, IRubyObject arg) {
        RubyString str = strDup(context.getRuntime());
        str.squeeze_bang(context, arg);
        return str;
    }

    @JRubyMethod(name = "squeeze", rest = true, compat = CompatVersion.RUBY1_8)
    public IRubyObject squeeze(ThreadContext context, IRubyObject[] args) {
        RubyString str = strDup(context.getRuntime());
        str.squeeze_bang(context, args);
        return str;
    }

    @JRubyMethod(name = "squeeze!", compat = CompatVersion.RUBY1_8)
    public IRubyObject squeeze_bang(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) {
            modifyCheck();
            return runtime.getNil();
        }
        final boolean squeeze[] = new boolean[TRANS_SIZE];
        for (int i=0; i<TRANS_SIZE; i++) squeeze[i] = true;
        modify();
        return squeezeCommon(runtime, squeeze);
    }

    @JRubyMethod(name = "squeeze!", compat = CompatVersion.RUBY1_8)
    public IRubyObject squeeze_bang(ThreadContext context, IRubyObject arg) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) {
            modifyCheck();
            return runtime.getNil();
        }
        final boolean squeeze[] = new boolean[TRANS_SIZE];
        arg.convertToString().trSetupTable(squeeze, true);
        modify();
        return squeezeCommon(runtime, squeeze);
    }

    @JRubyMethod(name = "squeeze!", rest = true, compat = CompatVersion.RUBY1_8)
    public IRubyObject squeeze_bang(ThreadContext context, IRubyObject[] args) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) {
            modifyCheck();
            return runtime.getNil();
        }

        final boolean squeeze[] = new boolean[TRANS_SIZE];
        args[0].convertToString().trSetupTable(squeeze, true);
        for (int i=1; i<args.length; i++) {
            args[i].convertToString().trSetupTable(squeeze, false);
        }

        modify();
        return squeezeCommon(runtime, squeeze);
    }

    private IRubyObject squeezeCommon(Ruby runtime, boolean squeeze[]) {
        int s = value.begin;
        int t = s;
        int send = s + value.realSize;
        byte[]bytes = value.bytes;
        int save = -1;

        while (s < send) {
            int c = bytes[s++] & 0xff;
            if (c != save || !squeeze[c]) bytes[t++] = (byte)(save = c);
        }

        if (t - value.begin != value.realSize) { // modified
            value.realSize = t - value.begin; 
            return this;
        }

        return runtime.getNil();        
    }

    @JRubyMethod(name = "squeeze", compat = CompatVersion.RUBY1_9)
    public IRubyObject squeeze19(ThreadContext context) {
        RubyString str = strDup(context.getRuntime());
        str.squeeze_bang19(context);
        return str;
    }

    @JRubyMethod(name = "squeeze", compat = CompatVersion.RUBY1_9)
    public IRubyObject squeeze19(ThreadContext context, IRubyObject arg) {
        RubyString str = strDup(context.getRuntime());
        str.squeeze_bang19(context, arg);
        return str;
    }

    @JRubyMethod(name = "squeeze", rest = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject squeeze19(ThreadContext context, IRubyObject[] args) {
        RubyString str = strDup(context.getRuntime());
        str.squeeze_bang19(context, args);
        return str;
    }

    @JRubyMethod(name = "squeeze!", compat = CompatVersion.RUBY1_9)
    public IRubyObject squeeze_bang19(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) {
            modifyCheck();
            return runtime.getNil();
        }
        final boolean squeeze[] = new boolean[TRANS_SIZE];
        for (int i=0; i<TRANS_SIZE; i++) squeeze[i] = true;

        modifyAndKeepCodeRange();
        if (singleByteOptimizable()) {
            return squeezeCommon(runtime, squeeze); // 1.8
        } else {
            return squeezeCommon19(runtime, squeeze, null, value.encoding, false);
        }
    }

    @JRubyMethod(name = "squeeze!", compat = CompatVersion.RUBY1_9)
    public IRubyObject squeeze_bang19(ThreadContext context, IRubyObject arg) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) {
            modifyCheck();
            return runtime.getNil();
        }

        RubyString otherStr = arg.convertToString();
        final boolean squeeze[] = new boolean[TRANS_SIZE];

        TrTables tables = otherStr.trSetupTable(runtime, squeeze, null, true, checkEncoding(otherStr));

        modifyAndKeepCodeRange();
        if (singleByteOptimizable() && otherStr.singleByteOptimizable()) {
            return squeezeCommon(runtime, squeeze); // 1.8
        } else {
            return squeezeCommon19(runtime, squeeze, tables, value.encoding, true);
        }
        
    }

    @JRubyMethod(name = "squeeze!", rest = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject squeeze_bang19(ThreadContext context, IRubyObject[] args) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) {
            modifyCheck();
            return runtime.getNil();
        }

        RubyString otherStr = args[0].convertToString();
        Encoding enc = checkEncoding(otherStr);
        final boolean squeeze[] = new boolean[TRANS_SIZE];
        TrTables tables = otherStr.trSetupTable(runtime, squeeze, null, true, enc);

        boolean singlebyte = singleByteOptimizable() && otherStr.singleByteOptimizable();
        for (int i=1; i<args.length; i++) {
            otherStr = args[i].convertToString();
            enc = checkEncoding(otherStr);
            singlebyte = singlebyte && otherStr.singleByteOptimizable();
            tables = otherStr.trSetupTable(runtime, squeeze, tables, false, enc);
        }

        modifyAndKeepCodeRange();
        if (singlebyte) {
            return squeezeCommon(runtime, squeeze); // 1.8
        } else {
            return squeezeCommon19(runtime, squeeze, tables, enc, true);
        }
    }

    private IRubyObject squeezeCommon19(Ruby runtime, boolean squeeze[], TrTables tables, Encoding enc, boolean isArg) {
        int s = value.begin;
        int t = s;
        int send = s + value.realSize;
        byte[]bytes = value.bytes;
        int save = -1;
        int c;

        while (s < send) {
            if (enc.isAsciiCompatible() && Encoding.isAscii(c = bytes[s] & 0xff)) {
                if (c != save || (isArg && !squeeze[c])) bytes[t++] = (byte)(save = c);
                s++;
            } else {
                c = codePoint(runtime, enc, bytes, s, send);
                int cl = codeLength(runtime, enc, c);
                if (c != save || (isArg && !trFind(c, squeeze, tables))) {
                    if (t != s) enc.codeToMbc(c, bytes, t);
                    save = c;
                    t += cl;
                }
                s += cl;
            }
        }

        if (t - value.begin != value.realSize) { // modified
            value.realSize = t - value.begin; 
            return this;
        }

        return runtime.getNil();   
    }

    /** rb_str_tr / rb_str_tr_bang
     *
     */
    @JRubyMethod(name = "tr", compat = CompatVersion.RUBY1_8)
    public IRubyObject tr(ThreadContext context, IRubyObject src, IRubyObject repl) {
        RubyString str = strDup(context.getRuntime());
        str.trTrans(context, src, repl, false);
        return str;
    }

    @JRubyMethod(name = "tr!", compat = CompatVersion.RUBY1_8)
    public IRubyObject tr_bang(ThreadContext context, IRubyObject src, IRubyObject repl) {
        return trTrans(context, src, repl, false);
    }    

    @JRubyMethod(name = "tr", compat = CompatVersion.RUBY1_9)
    public IRubyObject tr19(ThreadContext context, IRubyObject src, IRubyObject repl) {
        RubyString str = strDup(context.getRuntime());
        str.trTrans19(context, src, repl, false);
        return str;
    }

    @JRubyMethod(name = "tr!")
    public IRubyObject tr_bang19(ThreadContext context, IRubyObject src, IRubyObject repl) {
        return trTrans19(context, src, repl, false);
    }    

    private static final class TR {
        TR(ByteList bytes) {
            p = bytes.begin;
            pend = bytes.realSize + p;
            buf = bytes.bytes;
            now = max = 0;
            gen = false;
        }

        int p, pend, now, max;
        boolean gen;
        byte[]buf;
    }

    private static final int TRANS_SIZE = 256;

    /** tr_setup_table
     * 
     */
    private void trSetupTable(boolean[]table, boolean init) {
        final TR tr = new TR(value);
        boolean cflag = false;
        if (value.realSize > 1 && value.bytes[value.begin] == '^') {
            cflag = true;
            tr.p++;
        }

        if (init) for (int i=0; i<TRANS_SIZE; i++) table[i] = true;

        final boolean[]buf = new boolean[TRANS_SIZE];
        for (int i=0; i<TRANS_SIZE; i++) buf[i] = cflag;

        int c;
        while ((c = trNext(tr)) >= 0) buf[c & 0xff] = !cflag;
        for (int i=0; i<TRANS_SIZE; i++) table[i] = table[i] && buf[i];
    }

    private static final class TrTables {
        private IntHash<IRubyObject> del, noDel;
    }

    private TrTables trSetupTable(Ruby runtime, boolean[]table, TrTables tables, boolean init, Encoding enc) {
        final TR tr = new TR(value);
        boolean cflag = false;
        if (value.realSize > 1) {
            if (enc.isAsciiCompatible()) {
                if ((value.bytes[value.begin] & 0xff) == '^') {
                    cflag = true;
                    tr.p++;
                }
            } else {
                int l = StringSupport.preciseLength(enc, tr.buf, tr.p, tr.pend);
                if (enc.mbcToCode(tr.buf, tr.p, tr.pend) == '^') {
                    cflag = true;
                    tr.p += l;
                }
            }
        }

        if (init) for (int i=0; i<TRANS_SIZE; i++) table[i] = true;

        final boolean[]buf = new boolean[TRANS_SIZE];
        for (int i=0; i<TRANS_SIZE; i++) buf[i] = cflag;

        int c;
        IntHash<IRubyObject> hash = null, phash = null;
        while ((c = trNext(tr, runtime, enc)) >= 0) {
            if (c < TRANS_SIZE) {
                buf[c & 0xff] = !cflag;
            } else {
                if (hash == null) {
                    hash = new IntHash<IRubyObject>();
                    if (tables == null) tables = new TrTables();
                    if (cflag) {
                        phash = tables.noDel;
                        tables.noDel = hash;
                    } else {
                        phash  = tables.del;
                        tables.del = hash;
                    }
                }
                if (phash == null || phash.get(c) != null) hash.put(c, NEVER);
            }
        }

        for (int i=0; i<TRANS_SIZE; i++) table[i] = table[i] && buf[i];
        return tables;
    }

    private boolean trFind(int c, boolean[]table, TrTables tables) {
        return c < TRANS_SIZE ? table[c] : tables != null && 
                ((tables.del != null && tables.del.get(c) != null) &&
                (tables.noDel == null || tables.noDel.get(c) == null));
    }

    /** tr_trans
    *
    */    
    private IRubyObject trTrans(ThreadContext context, IRubyObject src, IRubyObject repl, boolean sflag) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) return runtime.getNil();

        ByteList replList = repl.convertToString().value;
        if (replList.realSize == 0) return delete_bang(context, src);

        ByteList srcList = src.convertToString().value;
        final TR trSrc = new TR(srcList);
        boolean cflag = false;
        if (srcList.realSize >= 2 && srcList.bytes[srcList.begin] == '^') {
            cflag = true;
            trSrc.p++;
        }       

        int c;
        final int[]trans = new int[TRANS_SIZE];
        final TR trRepl = new TR(replList);
        if (cflag) {
            for (int i=0; i<TRANS_SIZE; i++) trans[i] = 1;
            while ((c = trNext(trSrc)) >= 0) trans[c & 0xff] = -1;
            while ((c = trNext(trRepl)) >= 0); 
            for (int i=0; i<TRANS_SIZE; i++) {
                if (trans[i] >= 0) trans[i] = trRepl.now;
            }
        } else {
            for (int i=0; i<TRANS_SIZE; i++) trans[i] = -1;
            while ((c = trNext(trSrc)) >= 0) {
                int r = trNext(trRepl);
                if (r == -1) r = trRepl.now;
                trans[c & 0xff] = r;
            }
        }

        modify();

        int s = value.begin;
        int send = s + value.realSize;
        byte sbytes[] = value.bytes;
        boolean modify = false;
        if (sflag) {
            int t = s;
            int last = -1;
            while (s < send) {
                int c0 = sbytes[s++];
                if ((c = trans[c0 & 0xff]) >= 0) {
                    if (last == c) continue;
                    last = c;
                    sbytes[t++] = (byte)(c & 0xff);
                    modify = true;
                } else {
                    last = -1;
                    sbytes[t++] = (byte)c0;
                }
            }

            if (value.realSize > (t - value.begin)) {
                value.realSize = t - value.begin;
                modify = true;
            }
        } else {
            while (s < send) {
                if ((c = trans[sbytes[s] & 0xff]) >= 0) {
                    sbytes[s] = (byte)(c & 0xff);
                    modify = true;
                }
                s++;
            }
        }

        return modify ? this : runtime.getNil();
    }
    
    private IRubyObject trTrans19(ThreadContext context, IRubyObject src, IRubyObject repl, boolean sflag) {
        Ruby runtime = context.getRuntime();
        if (value.realSize == 0) return runtime.getNil();

        RubyString replStr = repl.convertToString();
        ByteList replList = replStr.value;
        if (replList.realSize == 0) return delete_bang19(context, src);

        RubyString srcStr = src.convertToString();
        ByteList srcList = srcStr.value;
        Encoding enc = checkEncoding(srcStr);
        enc = checkEncoding(replStr) == enc ? enc : srcStr.checkEncoding(replStr);

        int cr = getCodeRange();

        final TR trSrc = new TR(srcList);
        boolean cflag = false;
        if (value.realSize > 1) { 
            if (enc.isAsciiCompatible()) {
                if ((trSrc.buf[trSrc.p] & 0xff) == '^' && trSrc.p + 1 < trSrc.pend) {
                    cflag = true;
                    trSrc.p++;
                }
            } else {
                int cl = StringSupport.preciseLength(enc, trSrc.buf, trSrc.p, trSrc.pend);
                if (enc.mbcToCode(trSrc.buf, trSrc.p, trSrc.pend) == '^' && trSrc.p + cl < trSrc.pend) {
                    cflag = true;
                    trSrc.p += cl;
                }
            }            
        }

        boolean singlebyte = true;
        int c;
        final int[]trans = new int[TRANS_SIZE];
        IntHash<Integer> hash = null;
        final TR trRepl = new TR(replList);

        if (cflag) {
            for (int i=0; i<TRANS_SIZE; i++) trans[i] = 1;
            
            while ((c = trNext(trSrc, runtime, enc)) >= 0) {
                if (c < TRANS_SIZE) {
                    trans[c & 0xff] = -1;
                } else {
                    if (hash == null) hash = new IntHash<Integer>();
                    hash.put(c, 1); // QTRUE
                }
            }
            while ((c = trNext(trRepl, runtime, enc)) >= 0);  /* retrieve last replacer */;
            int last = trRepl.now;
            for (int i=0; i<TRANS_SIZE; i++) {
                if (trans[i] >= 0) trans[i] = last;
            }
        } else {
            for (int i=0; i<TRANS_SIZE; i++) trans[i] = -1;
            
            while ((c = trNext(trSrc, runtime, enc)) >= 0) {
                int r = trNext(trRepl, runtime, enc);
                if (r == -1) r = trRepl.now;
                if (c < TRANS_SIZE) {
                    trans[c & 0xff] = r;
                    if (r > TRANS_SIZE) singlebyte = false;
                } else {
                    if (hash == null) hash = new IntHash<Integer>();
                    hash.put(c, r);
                }
            }
        }

        modifyAndKeepCodeRange();
        int s = value.begin;
        int send = s + value.realSize;
        byte sbytes[] = value.bytes;
        int max = value.realSize;
        boolean modify = false;

        int last = -1;
        int clen, tlen, c0;

        if (sflag) {
            int save = -1;
            byte[]buf = new byte[max];
            int t = 0;
            while (s < send) {
                c0 = c = codePoint(runtime, enc, sbytes, s, send);
                tlen = clen = codeLength(runtime, enc, c);
                s += clen;
                c = trCode(c, trans, hash, cflag, last);

                if (c != -1) {
                    if (save == c) continue;
                    save = c;
                    tlen = codeLength(runtime, enc, c);
                    modify = true;
                } else {
                    save = -1;
                    c = c0;
                }

                while (t + tlen >= max) {
                    max <<= 1;
                    byte[]tbuf = new byte[max];
                    System.arraycopy(buf, 0, tbuf, 0, buf.length);
                    buf = tbuf;
                }
                enc.codeToMbc(c, buf, t);
                t += tlen;
            }
            value.bytes = buf;
            value.realSize = t;
        } else if (enc.isSingleByte() || (singlebyte && hash == null)) {
            while (s < send) {
                c = sbytes[s] & 0xff;
                if (trans[c] != -1) {
                    if (!cflag) {
                        c = trans[c];
                        sbytes[s] = (byte)c;
                    } else {
                        sbytes[s] = (byte)last;
                    }
                    modify = true;
                }
                s++;
            }
        } else {
            max += max >> 1;
            byte[]buf = new byte[max];
            int t = 0;

            while (s < send) {
                c0 = c = codePoint(runtime, enc, sbytes, s, send);
                tlen = clen = codeLength(runtime, enc, c);
                c = trCode(c, trans, hash, cflag, last);

                if (c != -1) {
                    tlen = codeLength(runtime, enc, c);
                } else {
                    c = c0;
                }
                modify = true;

                while (t + tlen >= max) {
                    max <<= 1;
                    byte[]tbuf = new byte[max];
                    System.arraycopy(buf, 0, tbuf, 0, buf.length);
                    buf = tbuf;
                }

                enc.codeToMbc(c, buf, t);
                s += clen;
                t += tlen;
            }
            value.bytes = buf;
            value.realSize = t;
        }

        if (modify) {
            cr = codeRangeAnd(cr, replStr.getCodeRange());
            if (cr != CR_BROKEN) setCodeRange(cr);
            associateEncoding(enc);
            return this;
        }
        return runtime.getNil();
    }

    private int trCode(int c, int[]trans, IntHash<Integer> hash, boolean cflag, int last) {
        if (c < TRANS_SIZE) {
            return trans[c];
        } else if (hash != null) {
            Integer tmp = hash.get(c);
            if (tmp == null) {
                return cflag ? last : -1;
            } else {
                return cflag ? -1 : tmp;
            }
        } else {
            return -1;
        }
    }

    /** trnext
    *
    */    
    private int trNext(TR t) {
        byte[]buf = t.buf;
        
        for (;;) {
            if (!t.gen) {
                if (t.p == t.pend) return -1;
                if (t.p < t.pend -1 && buf[t.p] == '\\') t.p++;
                t.now = buf[t.p++] & 0xff;
                if (t.p < t.pend - 1 && buf[t.p] == '-') {
                    t.p++;
                    if (t.p < t.pend) {
                        if (t.now > (buf[t.p] & 0xff)) {
                            t.p++;
                            continue;
                        }
                        t.gen = true;
                        t.max = buf[t.p++] & 0xff;
                    }
                }
                return t.now;
            } else if (++t.now < t.max) {
                return t.now;
            } else {
                t.gen = false;
                return t.max;
            }
        }
    }

    private int trNext(TR t, Ruby runtime, Encoding enc) {
        byte[]buf = t.buf;
        
        for (;;) {
            if (!t.gen) {
                if (t.p == t.pend) return -1;
                if (t.p < t.pend -1 && buf[t.p] == '\\') t.p++;
                t.now = codePoint(runtime, enc, buf, t.p, t.pend);
                t.p += codeLength(runtime, enc, t.now);
                if (t.p < t.pend - 1 && buf[t.p] == '-') {
                    t.p++;
                    if (t.p < t.pend) {
                        int c = codePoint(runtime, enc, buf, t.p, t.pend);
                        t.p += codeLength(runtime, enc, c);
                        if (t.now > c) continue;
                        t.gen = true;
                        t.max = c;
                    }
                }
                return t.now;
            } else if (++t.now < t.max) {
                return t.now;
            } else {
                t.gen = false;
                return t.max;
            }
        }
    }

    /** rb_str_tr_s / rb_str_tr_s_bang
     *
     */
    @JRubyMethod(name ="tr_s", compat = CompatVersion.RUBY1_8)
    public IRubyObject tr_s(ThreadContext context, IRubyObject src, IRubyObject repl) {
        RubyString str = strDup(context.getRuntime());
        str.trTrans(context, src, repl, true);
        return str;
    }

    @JRubyMethod(name = "tr_s!", compat = CompatVersion.RUBY1_8)
    public IRubyObject tr_s_bang(ThreadContext context, IRubyObject src, IRubyObject repl) {
        return trTrans(context, src, repl, true);
    }

    @JRubyMethod(name ="tr_s", compat = CompatVersion.RUBY1_9)
    public IRubyObject tr_s19(ThreadContext context, IRubyObject src, IRubyObject repl) {
        RubyString str = strDup(context.getRuntime());
        str.trTrans19(context, src, repl, true);
        return str;
    }

    @JRubyMethod(name = "tr_s!", compat = CompatVersion.RUBY1_9)
    public IRubyObject tr_s_bang19(ThreadContext context, IRubyObject src, IRubyObject repl) {
        return trTrans19(context, src, repl, true);
    }

    /** rb_str_each_line
     *
     */
    @JRubyMethod(name = {"each_line", "each"}, frame = true, compat = CompatVersion.RUBY1_8)
    public IRubyObject each_line(ThreadContext context, Block block) {
        return each_lineCommon(context, context.getRuntime().getGlobalVariables().get("$/"), block);
    }

    @JRubyMethod(name = {"each_line", "each"}, frame = true, compat = CompatVersion.RUBY1_8)
    public IRubyObject each_line(ThreadContext context, IRubyObject arg, Block block) {
        return each_lineCommon(context, arg, block);
    }

    public IRubyObject each_lineCommon(ThreadContext context, IRubyObject sep, Block block) {        
        Ruby runtime = context.getRuntime();
        if (sep.isNil()) {
            block.yield(context, this);
            return this;
        }

        RubyString sepStr = sep.convertToString();
        ByteList sepValue = sepStr.value;
        int rslen = sepValue.realSize;

        final byte newline;
        if (rslen == 0) {
            newline = '\n';
        } else {
            newline = sepValue.bytes[sepValue.begin + rslen - 1];
        }

        int p = value.begin;
        int end = p + value.realSize;
        int ptr = p, s = p;
        int len = value.realSize;
        byte[] bytes = value.bytes;

        p += rslen;

        for (; p < end; p++) {
            if (rslen == 0 && bytes[p] == '\n') {
                if (bytes[++p] != '\n') continue;
                while(p < end && bytes[p] == '\n') p++;
            }
            if (ptr < p && bytes[p - 1] == newline &&
               (rslen <= 1 || 
                ByteList.memcmp(sepValue.bytes, sepValue.begin, rslen, bytes, p - rslen, rslen) == 0)) {
                block.yield(context, makeShared(runtime, s - ptr, p - s).infectBy(this));
                modifyCheck(bytes, len);
                s = p;
            }
        }

        if (s != end) {
            if (p > end) p = end;
            block.yield(context, makeShared(runtime, s - ptr, p - s).infectBy(this));
        }

        return this;
    }

    @JRubyMethod(name = "each_line", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject each_line19(ThreadContext context, Block block) {
        return block.isGiven() ? each_lineCommon19(context, block) : 
            enumeratorize(context.getRuntime(), this, "each_line");
    }

    @JRubyMethod(name = "lines", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject lines(ThreadContext context, Block block) {
        return block.isGiven() ? each_lineCommon19(context, block) : 
            enumeratorize(context.getRuntime(), this, "lines");
    }

    @JRubyMethod(name = "each_line", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject each_line19(ThreadContext context, IRubyObject arg, Block block) {
        return block.isGiven() ? each_lineCommon19(context, arg, block) : 
            enumeratorize(context.getRuntime(), this, "each_line", arg);
    }

    @JRubyMethod(name = "lines", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject lines(ThreadContext context, IRubyObject arg, Block block) {
        return block.isGiven() ? each_lineCommon19(context, arg, block) : 
            enumeratorize(context.getRuntime(), this, "lines", arg);
    }

    private IRubyObject each_lineCommon19(ThreadContext context, Block block) {
        return each_lineCommon19(context, context.getRuntime().getGlobalVariables().get("$/"), block);
    }

    private IRubyObject each_lineCommon19(ThreadContext context, IRubyObject sep, Block block) {        
        Ruby runtime = context.getRuntime();
        if (sep.isNil()) {
            block.yield(context, this);
            return this;
        }

        ByteList val = value.shallowDup();
        int p = val.begin;
        int s = p;
        int len = val.realSize;
        int end = p + len;
        byte[]bytes = val.bytes;

        final Encoding enc;
        RubyString sepStr = sep.convertToString();
        if (sepStr == runtime.getGlobalVariables().getDefaultSeparator()) {
            enc = val.encoding;
            while (p < end) {
                if (bytes[p] == (byte)'\n') {
                    int p0 = enc.leftAdjustCharHead(bytes, s, p, end);
                    if (enc.isNewLine(bytes, p0, end)) {
                        p = p0 + StringSupport.length(enc, bytes, p0, end);
                        block.yield(context, makeShared19(runtime, val, s, p - s).infectBy(this));
                        s = p++;
                    }
                }
                p++;
            }
        } else {
            enc = checkEncoding(sepStr);
            ByteList sepValue = sepStr.value;
            final int newLine;
            int rslen = sepValue.realSize;
            if (rslen == 0) {
                newLine = '\n';
            } else {
                newLine = codePoint(runtime, enc, sepValue.bytes, sepValue.begin, sepValue.begin + sepValue.realSize);
            }

            while (p < end) {
                int c = codePoint(runtime, enc, bytes, p, end);
                again: do {
                    int n = codeLength(runtime, enc, c);
                    if (rslen == 0 && c == newLine) {
                        p += n;
                        if (p < end && (c = codePoint(runtime, enc, bytes, p, end)) != newLine) continue again;
                        while (p < end && codePoint(runtime, enc, bytes, p, end) == newLine) p += n;
                        p -= n;
                    }
                    if (c == newLine && (rslen <= 1 ||
                            ByteList.memcmp(sepValue.bytes, sepValue.begin, rslen, bytes, p, rslen) == 0)) {
                        block.yield(context, makeShared19(runtime, val, s, p - s + (rslen != 0 ? rslen : n)).infectBy(this));
                        s = p + (rslen != 0 ? rslen : n);
                    }
                    p += n;
                } while (false);
            }
        }

        if (s != end) {
            block.yield(context, makeShared19(runtime, val, s, end - s).infectBy(this));
        }
        return this;
    }

    /**
     * rb_str_each_byte
     */
    @JRubyMethod(name = "each_byte", frame = true, compat = CompatVersion.RUBY1_8)
    public RubyString each_byte(ThreadContext context, Block block) {
        Ruby runtime = context.getRuntime();
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

    @JRubyMethod(name = "bytes", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject bytes(ThreadContext context, Block block) {
        return block.isGiven() ? each_byte(context, block) : enumeratorize(context.getRuntime(), this, "bytes");
    }

    /** rb_str_each_char
     * 
     */
    @JRubyMethod(name = "each_char", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject each_char(ThreadContext context, Block block) {
        return block.isGiven() ? each_charCommon(context, block) : enumeratorize(context.getRuntime(), this, "each_char");
    }

    @JRubyMethod(name = "chars", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject chars(ThreadContext context, Block block) {
        return block.isGiven() ? each_charCommon(context, block) : enumeratorize(context.getRuntime(), this, "chars");
    }

    private IRubyObject each_charCommon(ThreadContext context, Block block) {
        byte bytes[] = value.bytes;
        int p = value.begin;
        int end = p + value.realSize;
        Encoding enc = value.encoding;

        Ruby runtime = context.getRuntime();
        ByteList val = value.shallowDup();
        while (p < end) {
            int n = StringSupport.length(enc, bytes, p, end);
            block.yield(context, makeShared19(runtime, val, p, n));
            p += n;
        }
        return this;
    }

    /** rb_str_each_codepoint
     * 
     */
    @JRubyMethod(name = "each_codepoint", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject each_codepoint(ThreadContext context, Block block) {
        if (!block.isGiven()) return enumeratorize(context.getRuntime(), this, "each_codepoint");
        return singleByteOptimizable() ? each_byte(context, block) : each_codepointCommon(context, block);
    }

    @JRubyMethod(name = "codepoints", frame = true, compat = CompatVersion.RUBY1_9)
    public IRubyObject codepoints(ThreadContext context, Block block) {
        if (!block.isGiven()) return enumeratorize(context.getRuntime(), this, "codepoints");
        return singleByteOptimizable() ? each_byte(context, block) : each_codepointCommon(context, block);
    }

    private IRubyObject each_codepointCommon(ThreadContext context, Block block) {
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
    private RubySymbol to_sym() {
        RubySymbol symbol = getRuntime().getSymbolTable().getSymbol(value);
        if (symbol.getBytes() == value) shareLevel = SHARE_LEVEL_BYTELIST;
        return symbol;
    }

    @JRubyMethod(name = {"to_sym", "intern"}, compat = CompatVersion.RUBY1_8)
    public RubySymbol intern() {
        if (value.realSize == 0) throw getRuntime().newArgumentError("interning empty string");
        for (int i = 0; i < value.realSize; i++) {
            if (value.bytes[value.begin + i] == 0) throw getRuntime().newArgumentError("symbol string may not contain '\\0'");
        }
        return to_sym();
    }

    @JRubyMethod(name = {"to_sym", "intern"}, compat = CompatVersion.RUBY1_9)
    public RubySymbol intern19() {
        return to_sym();
    }

    @JRubyMethod(name = "ord", compat = CompatVersion.RUBY1_9)
    public IRubyObject ord(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        return RubyFixnum.newFixnum(runtime, codePoint(runtime, value.encoding, 
                                                                value.bytes,
                                                                value.begin, 
                                                                value.begin + value.realSize));
    }

    @JRubyMethod(name = "sum")
    public IRubyObject sum(ThreadContext context) {
        return sumCommon(context, 16);
    }

    @JRubyMethod(name = "sum")
    public IRubyObject sum(ThreadContext context, IRubyObject arg) {
        return sumCommon(context, RubyNumeric.num2long(arg));
    }

    public IRubyObject sumCommon(ThreadContext context, long bits) {
        Ruby runtime = context.getRuntime();

        byte[]bytes = value.bytes;
        int p = value.begin;
        int len = value.realSize;
        int end = p + len; 

        if (bits >= 8 * 8) { // long size * bits in byte
            IRubyObject one = RubyFixnum.one(runtime);
            IRubyObject sum = RubyFixnum.zero(runtime);
            while (p < end) {
                modifyCheck(bytes, len);
                sum = sum.callMethod(context, "+", RubyFixnum.newFixnum(runtime, bytes[p++] & 0xff));
            }
            if (bits != 0) {
                IRubyObject mod = one.callMethod(context, "<<", RubyFixnum.newFixnum(runtime, bits));
                sum = sum.callMethod(context, "&", mod.callMethod(context, "-", one));
            }
            return sum;
        } else {
            long sum = 0;
            while (p < end) {
                modifyCheck(bytes, len);
                sum += bytes[p++] & 0xff;
            }
            return RubyFixnum.newFixnum(runtime, bits == 0 ? sum : sum & (1L << bits) - 1L);
        }
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
                RubyRegexp.newDummyRegexp(runtime, Numeric.ComplexPatterns.underscores_pat),
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
                RubyRegexp.newDummyRegexp(runtime, Numeric.ComplexPatterns.underscores_pat),
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
        return scanForCodeRange() == CR_BROKEN ? runtime.getFalse() : runtime.getTrue();
    }

    @JRubyMethod(name = "ascii_only?", compat = CompatVersion.RUBY1_9)
    public IRubyObject ascii_only_p(ThreadContext context) {
        Ruby runtime = context.getRuntime();
        return scanForCodeRange() == CR_7BIT ? runtime.getTrue() : runtime.getFalse();
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
