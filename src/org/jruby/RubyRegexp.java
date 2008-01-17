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
 * Copyright (C) 2004-2005 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2005 David Corbin <dcorbin@users.sourceforge.net>
 * Copyright (C) 2006 Nick Sieger <nicksieger@gmail.com>
 * Copyright (C) 2006 Miguel Covarrubias <mlcovarrubias@gmail.com>
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

import org.joni.Matcher;
import org.joni.Option;
import org.joni.Regex;
import org.joni.Region;
import org.joni.Syntax;
import org.joni.WarnCallback;
import org.joni.encoding.Encoding;
import org.jruby.anno.JRubyMethod;
import org.jruby.parser.ReOptions;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.ClassIndex;
import org.jruby.runtime.Frame;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.marshal.MarshalStream;
import org.jruby.runtime.marshal.UnmarshalStream;
import org.jruby.util.ByteList;
import org.jruby.util.KCode;
import org.jruby.util.TypeConverter;

/**
 *
 */
public class RubyRegexp extends RubyObject implements ReOptions, WarnCallback {
    private KCode kcode;
    private Regex pattern;
    private ByteList str;

    private final static byte[] PIPE = new byte[]{'|'};
    private final static byte[] DASH = new byte[]{'-'};
    private final static byte[] R_PAREN = new byte[]{')'};
    private final static byte[] COLON = new byte[]{':'};
    private final static byte[] M_CHAR = new byte[]{'m'};
    private final static byte[] I_CHAR = new byte[]{'i'};
    private final static byte[] X_CHAR = new byte[]{'x'};

    private static final int REGEXP_LITERAL_F =     1 << 11;
    private static final int REGEXP_KCODE_DEFAULT = 1 << 12;
    private static final int REGEXP_QUOTED =        1 << 13;
    
    public void setLiteral() {
        flags |= REGEXP_LITERAL_F;
    }
    
    public void clearLiteral() {
        flags &= ~REGEXP_LITERAL_F;
    }
    
    public boolean isLiteral() {
        return (flags & REGEXP_LITERAL_F) != 0;
    }

    public void setKCodeDefault() {
        flags |= REGEXP_KCODE_DEFAULT;
    }
    
    public void clearKCodeDefault() {
        flags &= ~REGEXP_KCODE_DEFAULT;
    }
    
    public boolean isKCodeDefault() {
        return (flags & REGEXP_KCODE_DEFAULT) != 0;
    }
    
    public void setQuoted() {
        flags |= REGEXP_QUOTED;
    }
    
    public void clearQuoted() {
        flags &= ~REGEXP_QUOTED;
    }
    
    public boolean isQuoted() {
        return (flags & REGEXP_QUOTED) != 0;
    }    

    public KCode getKCode() {
        return kcode;
    }
    
    public static RubyClass createRegexpClass(Ruby runtime) {
        RubyClass regexpClass = runtime.defineClass("Regexp", runtime.getObject(), REGEXP_ALLOCATOR);
        runtime.setRegexp(regexpClass);
        regexpClass.index = ClassIndex.REGEXP;
        regexpClass.kindOf = new RubyModule.KindOf() {
                public boolean isKindOf(IRubyObject obj, RubyModule type) {
                    return obj instanceof RubyRegexp;
                }
            };
        
        CallbackFactory callbackFactory = runtime.callbackFactory(RubyRegexp.class);
        
        regexpClass.defineConstant("IGNORECASE", runtime.newFixnum(RE_OPTION_IGNORECASE));
        regexpClass.defineConstant("EXTENDED", runtime.newFixnum(RE_OPTION_EXTENDED));
        regexpClass.defineConstant("MULTILINE", runtime.newFixnum(RE_OPTION_MULTILINE));
        
        regexpClass.defineAnnotatedMethods(RubyRegexp.class);
        regexpClass.dispatcher = callbackFactory.createDispatcher(regexpClass);

        return regexpClass;
    }
    
    private static ObjectAllocator REGEXP_ALLOCATOR = new ObjectAllocator() {
        public IRubyObject allocate(Ruby runtime, RubyClass klass) {
            RubyRegexp instance = new RubyRegexp(runtime, klass);
            return instance;
        }
    };

    /** used by allocator
     * 
     */
    private RubyRegexp(Ruby runtime, RubyClass klass) {
        super(runtime, klass);
    }

    /** default constructor
     * 
     */
    private RubyRegexp(Ruby runtime) {
        super(runtime, runtime.getRegexp());
    }

    // used only by the compiler
    public static RubyRegexp newRegexp(Ruby runtime, String pattern, int options) {
        return newRegexp(runtime, ByteList.create(pattern), options);
    }

    public static RubyRegexp newRegexp(Ruby runtime, ByteList pattern, int options) {
        RubyRegexp regexp = new RubyRegexp(runtime);
        regexp.initialize(pattern, options);
        return regexp;
    }    
    
    public void warn(String message) {
        getRuntime().getWarnings().warn(message);
    }

    @JRubyMethod(name = "kcode")
    public IRubyObject kcode() {
        return !isKCodeDefault() ? getRuntime().newString(kcode.name()) : getRuntime().getNil();
    }

    public int getNativeTypeIndex() {
        return ClassIndex.REGEXP;
    }
    
    public Regex getPattern() {
        return pattern;
    }

    private void check() {
        if (pattern == null || str == null) throw getRuntime().newTypeError("uninitialized Regexp");
    }

    @JRubyMethod(name = "hash")
    public RubyFixnum hash() {
        check();
        int hashval = (int)pattern.getOptions();
        int len = this.str.realSize;
        int p = this.str.begin;
        while (len-->0) {
            hashval = hashval * 33 + str.bytes[p++];
        }
        hashval = hashval + (hashval>>5);
        return getRuntime().newFixnum(hashval);
    }

    @JRubyMethod(name = {"==", "eql?"}, required = 1)
    public IRubyObject op_equal(IRubyObject other) {
        if(this == other) return getRuntime().getTrue();
        if(!(other instanceof RubyRegexp)) return getRuntime().getFalse();
        RubyRegexp otherRegex = (RubyRegexp)other;
        
        check();
        otherRegex.check();
        
        if(str.equal(otherRegex.str) && kcode == otherRegex.kcode &&
                                      pattern.getOptions() == otherRegex.pattern.getOptions()) {
            return getRuntime().getTrue();
        }
        return getRuntime().getFalse();
    }

    @JRubyMethod(name = "~")
    public IRubyObject op_match2() {
        IRubyObject line = getRuntime().getCurrentContext().getCurrentFrame().getLastLine();
        if(!(line instanceof RubyString)) {
            getRuntime().getCurrentContext().getCurrentFrame().setBackRef(getRuntime().getNil());
            return getRuntime().getNil();
        }
        int start = search((RubyString)line, 0, false);
        if(start < 0) {
            return getRuntime().getNil();
        } else {
            return getRuntime().newFixnum(start);
        }
    }

    /** rb_reg_eqq
     * 
     */
    @JRubyMethod(name = "===", required = 1)
    public IRubyObject eqq(IRubyObject str) {
        if(!(str instanceof RubyString)) str = str.checkStringType();

        if(str.isNil()) {
            getRuntime().getCurrentContext().getCurrentFrame().setBackRef(getRuntime().getNil());
            return getRuntime().getFalse();
        }
        int start = search((RubyString)str, 0, false);
        return (start < 0) ? getRuntime().getFalse() : getRuntime().getTrue();
    }
    
    public void initialize(ByteList regex, int options) {
        if(isTaint() && getRuntime().getSafeLevel() >= 4) {
            throw getRuntime().newSecurityError("Insecure: can't modify regexp");
        }
        
        checkFrozen();
        if (isLiteral()) throw getRuntime().newSecurityError("can't modify literal regexp");

        clearKCodeDefault();
        switch(options & ~0xf) {
        case 0:
        default:
            setKCodeDefault();
            kcode = getRuntime().getKCode();
            break;
        case 16:
            kcode = KCode.NONE;
            break;
        case 32:
            kcode = KCode.EUC;
            break;
        case 48:
            kcode = KCode.SJIS;
            break;
        case 64:
            kcode = KCode.UTF8;
            break;
        }

        pattern = makeRegexp(regex, regex.begin, regex.realSize, options & 0xf, kcode.getEncoding());
        str = regex.makeShared(0, regex.realSize);
    }

    private final Regex makeRegexp(ByteList s, int start, int len, int flags, Encoding enc) {
        try {
            return new Regex(s.bytes, start, start + len, flags, enc, Syntax.DEFAULT, this);
        } catch(Exception e) {
            rb_reg_raise(s.bytes, start, len, e.getMessage(), flags);
        }
        return null;
    }

    private final void rb_reg_raise(byte[] s, int start, int len, String err,int flags) {
        throw getRuntime().newRegexpError(err + ": " + rb_reg_desc(s,start, len,flags));
    }

    private final StringBuffer rb_reg_desc(byte[] s, int start, int len,int flags) {
        StringBuffer sb = new StringBuffer("/");
        rb_reg_expr_str(sb, s, start, len);
        sb.append("/");

        if((flags & ReOptions.RE_OPTION_MULTILINE) != 0) {
            sb.append("m");
        }
        if((flags & ReOptions.RE_OPTION_IGNORECASE) != 0) {
            sb.append("i");
        }
        if((flags & ReOptions.RE_OPTION_EXTENDED) != 0) {
            sb.append("x");
        }

        if(kcode != null && !isKCodeDefault()) {
            sb.append(kcode.name().charAt(0));
        }
        return sb;
    }

    private final void rb_reg_expr_str(StringBuffer sb, byte[] s, int start, int len) {
        int p,pend;
        boolean need_escape = false;
        p = start;
        pend = start+len;
        Encoding enc = kcode.getEncoding();
        while(p<pend) {
            if(s[p] == '/' || (!(' ' == s[p] || (!Character.isWhitespace(s[p]) && 
                                                 !Character.isISOControl(s[p]))) && 
                               enc.length(s[p])==1)) {
                need_escape = true;
                break;
            }
            p += enc.length(s[p]);
        }
        if(!need_escape) {
            sb.append(new ByteList(s,start,len,false).toString());
        } else {
            p = 0;
            while(p < pend) {
                if(s[p] == '\\') {
                    int n = enc.length(s[p+1]) + 1;
                    sb.append(new ByteList(s,p,n,false).toString());
                    p += n;
                    continue;
                } else if(s[p] == '/') {
                    sb.append("\\/");
                } else if(enc.length(s[p])!=1) {
                    sb.append(new ByteList(s,p,enc.length(s[p]),false).toString());
                    p += enc.length(s[p]);
                    continue;
                } else if((' ' == s[p] || (!Character.isWhitespace(s[p]) && 
                                           !Character.isISOControl(s[p])))) {
                    sb.append((char)(s[p]&0xFF));
                } else if(!Character.isWhitespace((char)(s[p]&0xFF))) {
                    sb.append('\\');
                    sb.append(Integer.toString((int)(s[p]&0377),8));
                } else {
                    sb.append((char)(s[p]&0xFF));
                }
                p++;
            }
        }
    }    

    /** rb_reg_init_copy
     */
    @JRubyMethod(name = "initialize_copy", required = 1)
    public IRubyObject initialize_copy(IRubyObject re) {
        if(this == re) {
            return this;
        }
        checkFrozen();

        if(getMetaClass().getRealClass() != re.getMetaClass().getRealClass()) {
            throw getRuntime().newTypeError("wrong argument type");
	    }

        ((RubyRegexp)re).check();

        initialize(((RubyRegexp)re).str, ((RubyRegexp)re).rb_reg_options());

        return this;
    }

    private int rb_reg_get_kcode() {
        if(kcode == KCode.NONE) {
            return 16;
        } else if(kcode == KCode.EUC) {
            return 32;
        } else if(kcode == KCode.SJIS) {
            return 48;
        } else if(kcode == KCode.UTF8) {
            return 64;
        }
        return 0;
    }

    /** rb_reg_options
     */
    private int rb_reg_options() {
        check();
        int options = (int)(pattern.getOptions() & (RE_OPTION_IGNORECASE|RE_OPTION_MULTILINE|RE_OPTION_EXTENDED));
        if(!isKCodeDefault()) {
            options |= rb_reg_get_kcode();
        }
        return options;
    }

    /** rb_reg_initialize_m
     */
    @JRubyMethod(name = "initialize", optional = 3, visibility = Visibility.PRIVATE)
    public IRubyObject initialize_m(IRubyObject[] args) {
        ByteList s;
        int regexFlags = 0;

        if(args[0] instanceof RubyRegexp) {
            if(args.length > 1) {
                getRuntime().getWarnings().warn("flags" +((args.length == 3)?" and encoding":"")+ " ignored");
            }
            ((RubyRegexp)args[0]).check();
            RubyRegexp r = (RubyRegexp)args[0];
            regexFlags = (int)r.pattern.getOptions() & 0xF;
            if(!r.isKCodeDefault() && r.kcode != null && r.kcode != KCode.NIL) {
                if(r.kcode == KCode.NONE) {
                    regexFlags |= 16;
                } else if(r.kcode == KCode.EUC) {
                    regexFlags |= 32;
                } else if(r.kcode == KCode.SJIS) {
                    regexFlags |= 48;
                } else if(r.kcode == KCode.UTF8) {
                    regexFlags |= 64;
                }
            }
            s = r.str;
        } else {
            if(args.length >= 2) {
                if(args[1] instanceof RubyFixnum) {
                    regexFlags = RubyNumeric.fix2int(args[1]);
                } else if(args[1].isTrue()) {
                    regexFlags = RE_OPTION_IGNORECASE;
                }
            }
            if(args.length == 3 && !args[2].isNil()) {
                char first = args[2].convertToString().getByteList().charAt(0);
                regexFlags &= ~0x70;
                switch(first) {
                case 'n': case 'N':
                    regexFlags |= 16;
                    break;
                case 'e': case 'E':
                    regexFlags |= 32;
                    break;
                case 's': case 'S':
                    regexFlags |= 48;
                    break;
                case 'u': case 'U':
                    regexFlags |= 64;
                    break;
                default:
                    break;
                }
            }
            ByteList bl = args[0].convertToString().getByteList();
            s = bl;
        }

        initialize(s, regexFlags);

        return this;
    }

    @JRubyMethod(name = {"new", "compile"}, required = 1, optional = 2, meta = true)
    public static RubyRegexp newInstance(IRubyObject recv, IRubyObject[] args) {
        RubyClass klass = (RubyClass)recv;
        
        RubyRegexp re = (RubyRegexp) klass.allocate();
        re.callInit(args, Block.NULL_BLOCK);
        
        return re;
    }

    @JRubyMethod(name = "options")
    public IRubyObject options() {
        return getRuntime().newFixnum(rb_reg_options());
    }
    
    /** rb_reg_search
     */
    public int search(RubyString str, int pos, boolean reverse) {
        Ruby runtime = getRuntime();
        Frame frame = runtime.getCurrentContext().getCurrentFrame();

        ByteList value = str.getByteList();
        if (pos > value.realSize || pos < 0) {
            frame.setBackRef(runtime.getNil());
            return -1;
        }
        
        check();
        
        int range = reverse ? -pos : value.realSize - pos;
        
        Matcher matcher = pattern.matcher(value.bytes, value.begin, value.begin + value.realSize);
        
        int result = matcher.search(value.begin + pos, 
                                    value.begin + pos + range,
                                    Option.NONE);
        
        if (result < 0) {
            frame.setBackRef(runtime.getNil());
            return result;
        }
        
        updateBackRef(str, frame, matcher);

        return result;
    }
    
    final RubyMatchData updateBackRef(RubyString str, Frame frame, Matcher matcher) {
        IRubyObject backref = frame.getBackRef();
        final RubyMatchData match;
        if (backref == null || backref.isNil() || ((RubyMatchData)backref).used()) {
            match = new RubyMatchData(getRuntime());
        } else {
            match = (RubyMatchData)backref;
            if(getRuntime().getSafeLevel() >= 3) {
                match.setTaint(true);
            } else {
                match.setTaint(false);
            }
        }
        
        match.regs = matcher.getRegion(); // lazy, null when no groups defined
        match.begin = matcher.getBegin();
        match.end = matcher.getEnd();
        
        match.str = (RubyString)str.strDup().freeze();
        match.pattern = pattern;

        frame.setBackRef(match);
        
        match.infectBy(this);
        match.infectBy(str);
        return match;
    }

    /** rb_reg_match
     * 
     */
    @JRubyMethod(name = "=~", required = 1)
    public IRubyObject op_match(IRubyObject str) {
        int start;
        if(str.isNil()) {
            getRuntime().getCurrentContext().getCurrentFrame().setBackRef(getRuntime().getNil());
            return str;
        }
        
        start = search(str.convertToString(), 0, false);

        if(start < 0) return getRuntime().getNil();

        return RubyFixnum.newFixnum(getRuntime(), start);
    }

    /** rb_reg_match_m
     * 
     */
    @JRubyMethod(name = "match", required = 1)
    public IRubyObject match_m(IRubyObject str) {
        if(op_match(str).isNil()) {
            return getRuntime().getNil();
        }
        IRubyObject result =  getRuntime().getCurrentContext().getCurrentFrame().getBackRef();
        if(result instanceof RubyMatchData) {
            ((RubyMatchData)result).use();
        }
        return result;
    }


    public RubyString regsub(RubyString str, RubyString src, Matcher matcher) {
        Region regs = matcher.getRegion();
        int mbeg = matcher.getBegin();
        int mend = matcher.getEnd();
        
        int p,s,e;
        p = s = 0;
        int no = -1;
        ByteList bs = str.getByteList();
        ByteList srcbs = src.getByteList();
        e = bs.length();
        RubyString val = null;
        Encoding enc = kcode.getEncoding();

        int beg, end;
        while(s < e) {
            int ss = s;
            char c = bs.charAt(s++);
            if(enc.length((byte)c) != 1) {
                s += enc.length((byte)c) - 1;
                continue;
            }
            if (c != '\\' || s == e) continue;
            if (val == null) val = RubyString.newString(getRuntime(), new ByteList(ss - p));

            val.cat(bs.bytes, bs.begin + p, ss - p);
            c = bs.charAt(s++);
            p = s;
            
            switch(c) {
            case '0': case '1': case '2': case '3': case '4':
            case '5': case '6': case '7': case '8': case '9':
                no = c - '0';
                break;
            case '&':
                no = 0;
                break;
            case '`':
                beg = regs == null ? mbeg : regs.beg[0];
                val.cat(srcbs.bytes, srcbs.begin, beg);
                continue;

            case '\'':
                end = regs == null ? mend : regs.end[0];
                val.cat(srcbs.bytes, srcbs.begin + end, src.getByteList().realSize - end);
                continue;

            case '+':
                if (regs == null) {
                    if (mbeg == -1) {
                        no = 0;
                        continue;
                    }
                } else {
                    no = regs.numRegs-1;
                    while(regs.beg[no] == -1 && no > 0) no--;
                    if (no == 0) continue;
                }
                break;
            case '\\':
                val.cat(bs.bytes, s - 1, 1);
                continue;
            default:
                val.cat(bs.bytes, s - 2, 2);
                continue;
            }

            if (regs != null) {
                if (no >= 0) {
                    if (no >= regs.numRegs || regs.beg[no] == -1) continue;
                    val.cat(srcbs.bytes, srcbs.begin + regs.beg[no], regs.end[no] - regs.beg[no]);
                }
            } else {
                if (no != 0 || mbeg == -1) continue;
                val.cat(srcbs.bytes, srcbs.begin + mbeg, mend - mbeg);
            }
        }

        if(p < e) {
            if(val == null) {
                val = RubyString.newString(getRuntime(), bs.makeShared(p, e-p));
            } else {
                val.cat(bs.bytes, bs.begin + p, e - p);
            }
        }
        if (val == null) return str;

        return val;
    }

    final int adjustStartPos(RubyString str, int pos, boolean reverse) {
        check();
        ByteList value = str.getByteList();
        return pattern.adjustStartPosition(value.bytes, value.begin, value.realSize, pos, reverse);
    }

    @JRubyMethod(name = "casefold?")
    public IRubyObject casefold_p() {
        check();
        if ((pattern.getOptions() & RE_OPTION_IGNORECASE) != 0) {
            return getRuntime().getTrue();
        }
        return getRuntime().getFalse();
    }

    /** rb_reg_source
     * 
     */
    @JRubyMethod(name = "source")
    public IRubyObject source() {
        check();
        RubyString str = RubyString.newStringShared(getRuntime(), this.str);
        if(isTaint()) {
            str.taint();
        }
        return str;
    }

    final int length() {
        return str.realSize;
    }

    /** rb_reg_inspect
     *
     */
    @JRubyMethod(name = "inspect")
    public IRubyObject inspect() {
        check();
        return getRuntime().newString(ByteList.create(rb_reg_desc(str.bytes, str.begin, str.realSize, pattern.getOptions()).toString()));
    }

    private final static int EMBEDDABLE = RE_OPTION_MULTILINE|RE_OPTION_IGNORECASE|RE_OPTION_EXTENDED;

    @JRubyMethod(name = "to_s")
    public IRubyObject to_s() {
        RubyString ss = getRuntime().newString("(?");
        check();
        int options = pattern.getOptions();
        int p = str.begin;
        int l = str.realSize;
        byte[] _str = str.bytes;


        again: do {
            if(l >= 4 && _str[p] == '(' && _str[p+1] == '?') {
                boolean err = true;
                p += 2;
                if((l -= 2) > 0) {
                    do {
                        if(_str[p] == 'm') {
                            options |= RE_OPTION_MULTILINE;
                        } else if(_str[p] == 'i') {
                            options |= RE_OPTION_IGNORECASE;
                        } else if(_str[p] == 'x') {
                            options |= RE_OPTION_EXTENDED;
                        } else {
                            break;
                        }
                        p++;
                    } while(--l > 0);
                }
                if(l > 1 && _str[p] == '-') {
                    ++p;
                    --l;
                    do {
                        if(_str[p] == 'm') {
                            options &= ~RE_OPTION_MULTILINE;
                        } else if(_str[p] == 'i') {
                            options &= ~RE_OPTION_IGNORECASE;
                        } else if(_str[p] == 'x') {
                            options &= ~RE_OPTION_EXTENDED;
                        } else {
                            break;
                        }
                        p++;
                    } while(--l > 0);
                }
                if(_str[p] == ')') {
                    --l;
                    ++p;
                    continue again;
                }
                if(_str[p] == ':' && _str[p+l-1] == ')') {
                    try {
                        new Regex(_str,++p,l-=2,Option.DEFAULT,kcode.getEncoding(),Syntax.DEFAULT);
                        err = false;
                    } catch(Exception e) {
                        err = true;
                    }
                }
                if(err) {
                    options = (int)pattern.getOptions();
                    p = str.begin;
                    l = str.realSize;
                }
            }

            if((options & RE_OPTION_MULTILINE)!=0) ss.cat(M_CHAR);
            if((options & RE_OPTION_IGNORECASE)!=0) ss.cat(I_CHAR);
            if((options & RE_OPTION_EXTENDED)!=0) ss.cat(X_CHAR);

            if((options&EMBEDDABLE) != EMBEDDABLE) {
                ss.cat(DASH);
                if((options & RE_OPTION_MULTILINE)==0) ss.cat(M_CHAR);
                if((options & RE_OPTION_IGNORECASE)==0) ss.cat(I_CHAR);
                if((options & RE_OPTION_EXTENDED)==0) ss.cat(X_CHAR);
            }
            ss.cat(COLON);
            rb_reg_expr_str(ss,p,l);
            ss.cat(R_PAREN);
            ss.infectBy(this);
            return ss;
        } while(true);
    }

    private final boolean ISPRINT(byte c) {
        return ISPRINT((char)(c&0xFF));
    }

    private final boolean ISPRINT(char c) {
        return (' ' == c || (!Character.isWhitespace(c) && !Character.isISOControl(c)));
    }

    private final void rb_reg_expr_str(RubyString ss, int s, int l) {
        int p = s;
        int pend = l;
        boolean need_escape = false;
        while(p<pend) {
            if(str.bytes[p] == '/' || (!ISPRINT(str.bytes[p]) && kcode.getEncoding().length(str.bytes[p]) == 1)) {
                need_escape = true;
                break;
            }
            p += kcode.getEncoding().length(str.bytes[p]);
        }
        if(!need_escape) {
            ss.cat(str.bytes,s,l);
        } else {
            p = s; 
            while(p<pend) {
                if(str.bytes[p] == '\\') {
                    int n = kcode.getEncoding().length(str.bytes[p+1]) + 1;
                    ss.cat(str.bytes,p,n);
                    p += n;
                    continue;
                } else if(str.bytes[p] == '/') {
                    char c = '\\';
                    ss.cat((byte)c);
                    ss.cat(str.bytes,p,1);
                } else if(kcode.getEncoding().length(str.bytes[p]) != 1) {
                    ss.cat(str.bytes,p,kcode.getEncoding().length(str.bytes[p]));
                    p += kcode.getEncoding().length(str.bytes[p]);
                    continue;
                } else if(ISPRINT(str.bytes[p])) {
                    ss.cat(str.bytes,p,1);
                } else if(!Character.isWhitespace(str.bytes[p])) {
                    ss.cat(ByteList.create(Integer.toString(str.bytes[p]&0377,8)));
                } else {
                    ss.cat(str.bytes,p,1);
                }
                p++;
            }
        }
    }

    /** rb_reg_s_quote
     * 
     */
    @JRubyMethod(name = {"quote", "escape"}, required = 1, optional = 1, meta = true)
    public static RubyString quote(IRubyObject recv, IRubyObject[] args) {
        IRubyObject kcode = null;
        if(args.length == 2) {
            kcode = args[1];
        }
        IRubyObject str = args[0];
        KCode code = recv.getRuntime().getKCode();
        if(kcode != null && !kcode.isNil()) {
            code = KCode.create(recv.getRuntime(), kcode.toString());
        }
        return quote(str.convertToString(), code);
    }

    /** rb_reg_quote
     *
     */
    public static RubyString quote(RubyString str, KCode kcode) {
        ByteList bs = str.getByteList();
        int tix = 0;
        int s = bs.begin;
        char c;
        int send = s+bs.length();
        Encoding enc = kcode.getEncoding();
        meta_found: do {
            for(; s<send; s++) {
                c = (char)(bs.bytes[s]&0xFF);
                if(enc.length((byte)c) != 1) {
                    int n = enc.length((byte)c);
                    while(n-- > 0 && s < send) {
                        s++;
                    }
                    s--;
                    continue;
                }
                switch (c) {
                case '[': case ']': case '{': case '}':
                case '(': case ')': case '|': case '-':
                case '*': case '.': case '\\':
                case '?': case '+': case '^': case '$':
                case ' ': case '#':
                case '\t': case '\f': case '\n': case '\r':
                    break meta_found;
                }
            }
            return str;
        } while(false);
        ByteList b1 = new ByteList(send*2);
        System.arraycopy(bs.bytes,bs.begin,b1.bytes,b1.begin,s-bs.begin);
        tix += (s-bs.begin);

        for(; s<send; s++) {
            c = (char)(bs.bytes[s]&0xFF);
            if(enc.length((byte)c) != 1) {
                int n = enc.length((byte)c);
                while(n-- > 0 && s < send) {
                    b1.bytes[tix++] = bs.bytes[s++];
                }
                s--;
                continue;
            }

            switch(c) {
            case '[': case ']': case '{': case '}':
            case '(': case ')': case '|': case '-':
            case '*': case '.': case '\\':
            case '?': case '+': case '^': case '$':
            case '#':
                b1.bytes[tix++] = '\\';
                break;
            case ' ':
                b1.bytes[tix++] = '\\';
                b1.bytes[tix++] = ' ';
                continue;
            case '\t':
                b1.bytes[tix++] = '\\';
                b1.bytes[tix++] = 't';
                 continue;
            case '\n':
                b1.bytes[tix++] = '\\';
                b1.bytes[tix++] = 'n';
                continue;
            case '\r':
                b1.bytes[tix++] = '\\';
                b1.bytes[tix++] = 'r';
                continue;
            case '\f':
                b1.bytes[tix++] = '\\';
                b1.bytes[tix++] = 'f';
                continue;
            }
            b1.bytes[tix++] = (byte)c;
        }
        b1.realSize = tix;
        RubyString tmp = RubyString.newString(str.getRuntime(),b1);
        tmp.infectBy(str);
        return tmp;
    }


    /** rb_reg_nth_match
     *
     */
    public static IRubyObject nth_match(int nth, IRubyObject match) {
        if (match.isNil()) return match;
        RubyMatchData m = (RubyMatchData)match;

        int start, end;
        
        if (m.regs == null) {
            if (nth >= 1) return match.getRuntime().getNil();
            if (nth < 0 && ++nth <= 0) return match.getRuntime().getNil();
            start = m.begin;
            end = m.end;
        } else {
            if (nth >= m.regs.numRegs) return match.getRuntime().getNil();
            if (nth < 0 && (nth+=m.regs.numRegs) <= 0) return match.getRuntime().getNil();
            start = m.regs.beg[nth];
            end = m.regs.end[nth];
        }
        
        if(start == -1) return match.getRuntime().getNil();

        RubyString str = m.str.makeShared(start, end - start);
        str.infectBy(match);
        return str;
    }

    /** rb_reg_last_match
     *
     */
    public static IRubyObject last_match(IRubyObject match) {
        return nth_match(0, match);
    }

    /** rb_reg_s_last_match
     *
     */
    @JRubyMethod(name = "last_match", optional = 1, meta = true)
    public static IRubyObject last_match_s(IRubyObject recv, IRubyObject[] args) {
        if (args.length == 1) {
            return nth_match(RubyNumeric.fix2int(args[0]), recv.getRuntime().getCurrentContext().getCurrentFrame().getBackRef());
        }

        IRubyObject result = recv.getRuntime().getCurrentContext().getCurrentFrame().getBackRef();

        if (result instanceof RubyMatchData) ((RubyMatchData)result).use();
        return result;
    }

    /** rb_reg_match_pre
     *
     */
    public static IRubyObject match_pre(IRubyObject match) {
        if (match.isNil()) return match;
        RubyMatchData m = (RubyMatchData)match;
        
        int beg = m.regs == null ? m.begin : m.regs.beg[0];
        
        if(beg == -1) match.getRuntime().getNil(); 

        RubyString str = m.str.makeShared(0, beg);
        str.infectBy(match);
        return str;
    }

    /** rb_reg_match_post
     *
     */
    public static IRubyObject match_post(IRubyObject match) {
        if (match.isNil()) return match;
        RubyMatchData m = (RubyMatchData)match;

        int end;
        if (m.regs == null) {
            if (m.begin == -1) return match.getRuntime().getNil();
            end = m.end;
        } else {
            if (m.regs.beg[0] == -1) return match.getRuntime().getNil();
            end = m.regs.end[0];
        }
        
        RubyString str = m.str.makeShared(end, m.str.getByteList().realSize - end);
        str.infectBy(match);
        return str;
    }

    /** rb_reg_match_last
     *
     */
    public static IRubyObject match_last(IRubyObject match) {
        if (match.isNil()) return match;
        RubyMatchData m = (RubyMatchData)match;

        if (m.regs == null || m.regs.beg[0] == -1) return match.getRuntime().getNil();

        int i;
        for (i=m.regs.numRegs-1; m.regs.beg[i]==-1 && i>0; i--);
        if (i == 0) return match.getRuntime().getNil();
        
        return nth_match(i,match);
    }

    /** rb_reg_s_union
     *
     */
    @JRubyMethod(name = "union", rest = true, meta = true)
    public static IRubyObject union(IRubyObject recv, IRubyObject[] args) {
        if (args.length == 0) {
            return newRegexp(recv.getRuntime(), ByteList.create("(?!)"), 0);
        } else if (args.length == 1) {
            IRubyObject v = TypeConverter.convertToTypeWithCheck(args[0], recv.getRuntime().getRegexp(), 0, "to_regexp");
            if(!v.isNil()) {
                return v;
            } else {
                // newInstance here
                return newRegexp(recv.getRuntime(), quote(recv,args).getByteList(), 0);
            }
        } else {
            KCode kcode = null;
            IRubyObject kcode_re = recv.getRuntime().getNil();
            RubyString source = recv.getRuntime().newString("");
            IRubyObject[] _args = new IRubyObject[3];

            for(int i = 0; i < args.length; i++) {
                if(0 < i) {
                    source.cat(PIPE);
                }
                IRubyObject v = TypeConverter.convertToTypeWithCheck(args[i], recv.getRuntime().getRegexp(), 0, "to_regexp");
                if (!v.isNil()) {
                    if (!((RubyRegexp)v).isKCodeDefault()) {
                        if (kcode == null) {
                            kcode_re = v;
                            kcode = ((RubyRegexp)v).kcode;
                        } else if (((RubyRegexp)v).kcode != kcode) {
                            IRubyObject str1 = kcode_re.inspect();
                            IRubyObject str2 = v.inspect();
                            throw recv.getRuntime().newArgumentError("mixed kcode " + str1 + " and " + str2);
                        }
                    }
                    v = ((RubyRegexp)v).to_s();
                } else {
                    v = quote(recv, new IRubyObject[]{args[i]});
                }
                source.append(v);
            }

            _args[0] = source;
            _args[1] = recv.getRuntime().getNil();
            if (kcode == null) {
                _args[2] = recv.getRuntime().getNil();
            } else if (kcode == KCode.NONE) {
                _args[2] = recv.getRuntime().newString("n");
            } else if (kcode == KCode.EUC) {
                _args[2] = recv.getRuntime().newString("e");
            } else if (kcode == KCode.SJIS) {
                _args[2] = recv.getRuntime().newString("s");
            } else if (kcode == KCode.UTF8) {
                _args[2] = recv.getRuntime().newString("u");
            }
            return recv.callMethod(recv.getRuntime().getCurrentContext(),"new",_args);
        }
    }

    public static RubyRegexp unmarshalFrom(UnmarshalStream input) throws java.io.IOException {
        RubyRegexp result = newRegexp(input.getRuntime(), input.unmarshalString(), input.unmarshalInt());
        input.registerLinkTarget(result);
        return result;
    }

    public static void marshalTo(RubyRegexp regexp, MarshalStream output) throws java.io.IOException {
        output.registerLinkTarget(regexp);
        output.writeString(new String(regexp.str.bytes,regexp.str.begin,regexp.str.realSize));
        output.writeInt(regexp.pattern.getOptions() & EMBEDDABLE);
    }
}
