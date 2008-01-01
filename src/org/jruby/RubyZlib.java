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
 * Copyright (C) 2006 Ola Bini <ola@ologix.com>
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

import java.io.InputStream;
import java.io.IOException;

import java.util.List;
import java.util.ArrayList;

import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.jruby.anno.JRubyMethod;

import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.Arity;

import org.jruby.runtime.Block;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;

import org.jruby.util.IOInputStream;
import org.jruby.util.IOOutputStream;
import org.jruby.util.CRC32Ext;
import org.jruby.util.Adler32Ext;
import org.jruby.util.ZlibInflate;
import org.jruby.util.ZlibDeflate;

import org.jruby.util.ByteList;

public class RubyZlib {
    /** Create the Zlib module and add it to the Ruby runtime.
     * 
     */
    public static RubyModule createZlibModule(Ruby runtime) {
        RubyModule result = runtime.defineModule("Zlib");

        RubyClass gzfile = result.defineClassUnder("GzipFile", runtime.getObject(), RubyGzipFile.GZIPFILE_ALLOCATOR);
        gzfile.defineAnnotatedMethods(RubyGzipFile.class);
        
        RubyClass gzreader = result.defineClassUnder("GzipReader", gzfile, RubyGzipReader.GZIPREADER_ALLOCATOR);
        gzreader.includeModule(runtime.getEnumerable());
        gzreader.defineAnnotatedMethods(RubyGzipReader.class);
        
        RubyClass standardError = runtime.fastGetClass("StandardError");
        RubyClass zlibError = result.defineClassUnder("Error", standardError, standardError.getAllocator());
        gzreader.defineClassUnder("Error", zlibError, zlibError.getAllocator());

        RubyClass gzwriter = result.defineClassUnder("GzipWriter", gzfile, RubyGzipWriter.GZIPWRITER_ALLOCATOR);
        gzwriter.defineAnnotatedMethods(RubyGzipWriter.class);

        result.defineConstant("ZLIB_VERSION",runtime.newString("1.2.1"));
        result.defineConstant("VERSION",runtime.newString("0.6.0"));

        result.defineConstant("BINARY",runtime.newFixnum(0));
        result.defineConstant("ASCII",runtime.newFixnum(1));
        result.defineConstant("UNKNOWN",runtime.newFixnum(2));

        result.defineConstant("DEF_MEM_LEVEL",runtime.newFixnum(8));
        result.defineConstant("MAX_MEM_LEVEL",runtime.newFixnum(9));

        result.defineConstant("OS_UNIX",runtime.newFixnum(3));
        result.defineConstant("OS_UNKNOWN",runtime.newFixnum(255));
        result.defineConstant("OS_CODE",runtime.newFixnum(11));
        result.defineConstant("OS_ZSYSTEM",runtime.newFixnum(8));
        result.defineConstant("OS_VMCMS",runtime.newFixnum(4));
        result.defineConstant("OS_VMS",runtime.newFixnum(2));
        result.defineConstant("OS_RISCOS",runtime.newFixnum(13));
        result.defineConstant("OS_MACOS",runtime.newFixnum(7));
        result.defineConstant("OS_OS2",runtime.newFixnum(6));
        result.defineConstant("OS_AMIGA",runtime.newFixnum(1));
        result.defineConstant("OS_QDOS",runtime.newFixnum(12));
        result.defineConstant("OS_WIN32",runtime.newFixnum(11));
        result.defineConstant("OS_ATARI",runtime.newFixnum(5));
        result.defineConstant("OS_MSDOS",runtime.newFixnum(0));
        result.defineConstant("OS_CPM",runtime.newFixnum(9));
        result.defineConstant("OS_TOPS20",runtime.newFixnum(10));

        result.defineConstant("DEFAULT_STRATEGY",runtime.newFixnum(0));
        result.defineConstant("FILTERED",runtime.newFixnum(1));
        result.defineConstant("HUFFMAN_ONLY",runtime.newFixnum(2));

        result.defineConstant("NO_FLUSH",runtime.newFixnum(0));
        result.defineConstant("SYNC_FLUSH",runtime.newFixnum(2));
        result.defineConstant("FULL_FLUSH",runtime.newFixnum(3));
        result.defineConstant("FINISH",runtime.newFixnum(4));

        result.defineConstant("NO_COMPRESSION",runtime.newFixnum(0));
        result.defineConstant("BEST_SPEED",runtime.newFixnum(1));
        result.defineConstant("DEFAULT_COMPRESSION",runtime.newFixnum(-1));
        result.defineConstant("BEST_COMPRESSION",runtime.newFixnum(9));

        result.defineConstant("MAX_WBITS",runtime.newFixnum(15));

        result.defineAnnotatedMethods(RubyZlib.class);

        result.defineClassUnder("StreamEnd",zlibError, zlibError.getAllocator());
        result.defineClassUnder("StreamError",zlibError, zlibError.getAllocator());
        result.defineClassUnder("BufError",zlibError, zlibError.getAllocator());
        result.defineClassUnder("NeedDict",zlibError, zlibError.getAllocator());
        result.defineClassUnder("MemError",zlibError, zlibError.getAllocator());
        result.defineClassUnder("VersionError",zlibError, zlibError.getAllocator());
        result.defineClassUnder("DataError",zlibError, zlibError.getAllocator());

        RubyClass gzError = gzfile.defineClassUnder("Error",zlibError, zlibError.getAllocator());
        gzfile.defineClassUnder("CRCError",gzError, gzError.getAllocator());
        gzfile.defineClassUnder("NoFooter",gzError, gzError.getAllocator());
        gzfile.defineClassUnder("LengthError",gzError, gzError.getAllocator());

        // ZStream actually *isn't* allocatable
        RubyClass zstream = result.defineClassUnder("ZStream", runtime.getObject(), ObjectAllocator.NOT_ALLOCATABLE_ALLOCATOR);
        zstream.defineAnnotatedMethods(ZStream.class);
        zstream.undefineMethod("new");

        RubyClass infl = result.defineClassUnder("Inflate", zstream, Inflate.INFLATE_ALLOCATOR);
        infl.defineAnnotatedMethods(Inflate.class);

        RubyClass defl = result.defineClassUnder("Deflate", zstream, Deflate.DEFLATE_ALLOCATOR);
        defl.defineAnnotatedMethods(Deflate.class);

        runtime.getKernel().callMethod(runtime.getCurrentContext(),"require",runtime.newString("stringio"));

        return result;
    }

    @JRubyMethod(name = "zlib_version", module = true, visibility = Visibility.PRIVATE)
    public static IRubyObject zlib_version(IRubyObject recv) {
        return ((RubyModule)recv).fastGetConstant("ZLIB_VERSION");
    }

    @JRubyMethod(name = "version", module = true, visibility = Visibility.PRIVATE)
    public static IRubyObject version(IRubyObject recv) {
        return ((RubyModule)recv).fastGetConstant("VERSION");
    }

    @JRubyMethod(name = "crc32", optional = 2, module = true, visibility = Visibility.PRIVATE)
    public static IRubyObject crc32(IRubyObject recv, IRubyObject[] args) throws Exception {
        args = Arity.scanArgs(recv.getRuntime(),args,0,2);
        int crc = 0;
        ByteList bytes = null;
        
        if (!args[0].isNil()) bytes = args[0].convertToString().getByteList();
        if (!args[1].isNil()) crc = RubyNumeric.fix2int(args[1]);

        CRC32Ext ext = new CRC32Ext(crc);
        if (bytes != null) {
            ext.update(bytes.unsafeBytes(), bytes.begin(), bytes.length());
        }
        
        return recv.getRuntime().newFixnum(ext.getValue());
    }

    @JRubyMethod(name = "adler32", optional = 2, module = true, visibility = Visibility.PRIVATE)
    public static IRubyObject adler32(IRubyObject recv, IRubyObject[] args) throws Exception {
        args = Arity.scanArgs(recv.getRuntime(),args,0,2);
        int adler = 1;
        ByteList bytes = null;
        if (!args[0].isNil()) bytes = args[0].convertToString().getByteList();
        if (!args[1].isNil()) adler = RubyNumeric.fix2int(args[1]);

        Adler32Ext ext = new Adler32Ext(adler);
        if (bytes != null) {
            ext.update(bytes.unsafeBytes(), bytes.begin(), bytes.length()); // it's safe since adler.update doesn't modify the array
        }
        return recv.getRuntime().newFixnum(ext.getValue());
    }

    private final static long[] crctab = new long[]{
        0L, 1996959894L, 3993919788L, 2567524794L, 124634137L, 1886057615L, 3915621685L, 2657392035L, 249268274L, 2044508324L, 3772115230L, 2547177864L, 162941995L, 
        2125561021L, 3887607047L, 2428444049L, 498536548L, 1789927666L, 4089016648L, 2227061214L, 450548861L, 1843258603L, 4107580753L, 2211677639L, 325883990L, 
        1684777152L, 4251122042L, 2321926636L, 335633487L, 1661365465L, 4195302755L, 2366115317L, 997073096L, 1281953886L, 3579855332L, 2724688242L, 1006888145L, 
        1258607687L, 3524101629L, 2768942443L, 901097722L, 1119000684L, 3686517206L, 2898065728L, 853044451L, 1172266101L, 3705015759L, 2882616665L, 651767980L, 
        1373503546L, 3369554304L, 3218104598L, 565507253L, 1454621731L, 3485111705L, 3099436303L, 671266974L, 1594198024L, 3322730930L, 2970347812L, 795835527L, 
        1483230225L, 3244367275L, 3060149565L, 1994146192L, 31158534L, 2563907772L, 4023717930L, 1907459465L, 112637215L, 2680153253L, 3904427059L, 2013776290L, 
        251722036L, 2517215374L, 3775830040L, 2137656763L, 141376813L, 2439277719L, 3865271297L, 1802195444L, 476864866L, 2238001368L, 4066508878L, 1812370925L, 
        453092731L, 2181625025L, 4111451223L, 1706088902L, 314042704L, 2344532202L, 4240017532L, 1658658271L, 366619977L, 2362670323L, 4224994405L, 1303535960L, 
        984961486L, 2747007092L, 3569037538L, 1256170817L, 1037604311L, 2765210733L, 3554079995L, 1131014506L, 879679996L, 2909243462L, 3663771856L, 1141124467L, 
        855842277L, 2852801631L, 3708648649L, 1342533948L, 654459306L, 3188396048L, 3373015174L, 1466479909L, 544179635L, 3110523913L, 3462522015L, 1591671054L, 
        702138776L, 2966460450L, 3352799412L, 1504918807L, 783551873L, 3082640443L, 3233442989L, 3988292384L, 2596254646L, 62317068L, 1957810842L, 3939845945L, 
        2647816111L, 81470997L, 1943803523L, 3814918930L, 2489596804L, 225274430L, 2053790376L, 3826175755L, 2466906013L, 167816743L, 2097651377L, 4027552580L, 
        2265490386L, 503444072L, 1762050814L, 4150417245L, 2154129355L, 426522225L, 1852507879L, 4275313526L, 2312317920L, 282753626L, 1742555852L, 4189708143L, 
        2394877945L, 397917763L, 1622183637L, 3604390888L, 2714866558L, 953729732L, 1340076626L, 3518719985L, 2797360999L, 1068828381L, 1219638859L, 3624741850L, 
        2936675148L, 906185462L, 1090812512L, 3747672003L, 2825379669L, 829329135L, 1181335161L, 3412177804L, 3160834842L, 628085408L, 1382605366L, 3423369109L, 
        3138078467L, 570562233L, 1426400815L, 3317316542L, 2998733608L, 733239954L, 1555261956L, 3268935591L, 3050360625L, 752459403L, 1541320221L, 2607071920L, 
        3965973030L, 1969922972L, 40735498L, 2617837225L, 3943577151L, 1913087877L, 83908371L, 2512341634L, 3803740692L, 2075208622L, 213261112L, 2463272603L, 
        3855990285L, 2094854071L, 198958881L, 2262029012L, 4057260610L, 1759359992L, 534414190L, 2176718541L, 4139329115L, 1873836001L, 414664567L, 2282248934L, 
        4279200368L, 1711684554L, 285281116L, 2405801727L, 4167216745L, 1634467795L, 376229701L, 2685067896L, 3608007406L, 1308918612L, 956543938L, 2808555105L, 
        3495958263L, 1231636301L, 1047427035L, 2932959818L, 3654703836L, 1088359270L, 936918000L, 2847714899L, 3736837829L, 1202900863L, 817233897L, 3183342108L, 
        3401237130L, 1404277552L, 615818150L, 3134207493L, 3453421203L, 1423857449L, 601450431L, 3009837614L, 3294710456L, 1567103746L, 711928724L, 3020668471L, 
        3272380065L, 1510334235L, 755167117};

    @JRubyMethod(name = "crc_table", module = true, visibility = Visibility.PRIVATE)
    public static IRubyObject crc_table(IRubyObject recv) {
        List<IRubyObject> ll = new ArrayList<IRubyObject>(crctab.length);
        for(int i=0;i<crctab.length;i++) {
            ll.add(recv.getRuntime().newFixnum(crctab[i]));
        }
        return recv.getRuntime().newArray(ll);
    }


    public static abstract class ZStream extends RubyObject {
        protected boolean closed = false;
        protected boolean ended = false;
        protected boolean finished = false;

        protected abstract int internalTotalOut();
        protected abstract boolean internalStreamEndP();
        protected abstract void internalEnd();
        protected abstract void internalReset();
        protected abstract int internalAdler();
        protected abstract IRubyObject internalFinish() throws Exception;
        protected abstract int internalTotalIn();
        protected abstract void internalClose();

        public ZStream(Ruby runtime, RubyClass type) {
            super(runtime, type);
        }

        @JRubyMethod(name = "initialize", frame = true, visibility = Visibility.PRIVATE)
        public IRubyObject initialize(Block unusedBlock) {
            return this;
        }

        @JRubyMethod(name = "flust_next_out")
        public IRubyObject flush_next_out() {
            return getRuntime().getNil();
        }

        @JRubyMethod(name = "total_out")
        public IRubyObject total_out() {
            return getRuntime().newFixnum(internalTotalOut());
        }

        @JRubyMethod(name = "stream_end?")
        public IRubyObject stream_end_p() {
            return internalStreamEndP() ? getRuntime().getTrue() : getRuntime().getFalse();
        }

        @JRubyMethod(name = "data_type")
        public IRubyObject data_type() {
            return getRuntime().fastGetModule("Zlib").fastGetConstant("UNKNOWN");
        }

        @JRubyMethod(name = "closed?")
        public IRubyObject closed_p() {
            return closed ? getRuntime().getTrue() : getRuntime().getFalse();
        }

        @JRubyMethod(name = "ended?")
        public IRubyObject ended_p() {
            return ended ? getRuntime().getTrue() : getRuntime().getFalse();
        }

        @JRubyMethod(name = "end")
        public IRubyObject end() {
            if(!ended) {
                internalEnd();
                ended = true;
            }
            return getRuntime().getNil();
        }

        @JRubyMethod(name = "reset")
        public IRubyObject reset() {
            internalReset();
            return getRuntime().getNil();
        }

        @JRubyMethod(name = "avail_out")
        public IRubyObject avail_out() {
            return RubyFixnum.zero(getRuntime());
        }

        @JRubyMethod(name = "avail_out=", required = 1)
        public IRubyObject set_avail_out(IRubyObject p1) {
            return p1;
        }

        @JRubyMethod(name = "adler")
        public IRubyObject adler() {
            return getRuntime().newFixnum(internalAdler());
        }

        @JRubyMethod(name = "finish")
        public IRubyObject finish() throws Exception {
            if(!finished) {
                finished = true;
                return internalFinish();
            }
            return getRuntime().newString("");
        }

        @JRubyMethod(name = "avail_in")
        public IRubyObject avail_in() {
            return RubyFixnum.zero(getRuntime());
        }

        @JRubyMethod(name = "flush_next_in")
        public IRubyObject flush_next_in() {
            return getRuntime().getNil();
        }

        @JRubyMethod(name = "total_in")
        public IRubyObject total_in() {
            return getRuntime().newFixnum(internalTotalIn());
        }

        @JRubyMethod(name = "finished?")
        public IRubyObject finished_p() {
            return finished ? getRuntime().getTrue() : getRuntime().getFalse();
        }

        @JRubyMethod(name = "close")
        public IRubyObject close() {
            if(!closed) {
                internalClose();
                closed = true;
            }
            return getRuntime().getNil();
        }
    }

    public static class Inflate extends ZStream {
        protected static ObjectAllocator INFLATE_ALLOCATOR = new ObjectAllocator() {
            public IRubyObject allocate(Ruby runtime, RubyClass klass) {
                return new Inflate(runtime, klass);
            }
        };

        @JRubyMethod(name = "inflate", required = 1, meta = true)
        public static IRubyObject s_inflate(IRubyObject recv, IRubyObject string) throws Exception {
            return ZlibInflate.s_inflate(recv,string.convertToString().getByteList());
        }

        public Inflate(Ruby runtime, RubyClass type) {
            super(runtime, type);
        }

        private ZlibInflate infl;

        @JRubyMethod(name = "initialize", rest = true, visibility = Visibility.PRIVATE)
        public IRubyObject _initialize(IRubyObject[] args) throws Exception {
            infl = new ZlibInflate(this);
            return this;
        }

        @JRubyMethod(name = "<<", required = 1)
        public IRubyObject append(IRubyObject arg) {
            infl.append(arg);
            return this;
        }

        @JRubyMethod(name = "sync_point?")
        public IRubyObject sync_point_p() {
            return infl.sync_point();
        }

        @JRubyMethod(name = "set_dictionary", required = 1)
        public IRubyObject set_dictionary(IRubyObject arg) throws Exception {
            return infl.set_dictionary(arg);
        }

        @JRubyMethod(name = "inflate", required = 1)
        public IRubyObject inflate(IRubyObject string) throws Exception {
            return infl.inflate(string.convertToString().getByteList());
        }

        @JRubyMethod(name = "sync", required = 1)
        public IRubyObject sync(IRubyObject string) {
            return infl.sync(string);
        }

        protected int internalTotalOut() {
            return infl.getInflater().getTotalOut();
        }

        protected boolean internalStreamEndP() {
            return infl.getInflater().finished();
        }

        protected void internalEnd() {
            infl.getInflater().end();
        }

        protected void internalReset() {
            infl.getInflater().reset();
        }

        protected int internalAdler() {
            return infl.getInflater().getAdler();
        }

        protected IRubyObject internalFinish() throws Exception {
            infl.finish();
            return getRuntime().getNil();
        }

        public IRubyObject finished_p() {
            return infl.getInflater().finished() ? getRuntime().getTrue() : getRuntime().getFalse();
        }

        protected int internalTotalIn() {
            return infl.getInflater().getTotalIn();
        }

        protected void internalClose() {
            infl.close();
        }
    }

    public static class Deflate extends ZStream {
        protected static ObjectAllocator DEFLATE_ALLOCATOR = new ObjectAllocator() {
            public IRubyObject allocate(Ruby runtime, RubyClass klass) {
                return new Deflate(runtime, klass);
            }
        };

        @JRubyMethod(name = "deflate", required = 1, optional = 1, meta = true)
        public static IRubyObject s_deflate(IRubyObject recv, IRubyObject[] args) throws Exception {
            args = Arity.scanArgs(recv.getRuntime(),args,1,1);
            int level = -1;
            if(!args[1].isNil()) {
                level = RubyNumeric.fix2int(args[1]);
            }
            return ZlibDeflate.s_deflate(recv,args[0].convertToString().getByteList(),level);
        }

        public Deflate(Ruby runtime, RubyClass type) {
            super(runtime, type);
        }

        private ZlibDeflate defl;

        @JRubyMethod(name = "initialize", optional = 4, visibility = Visibility.PRIVATE)
        public IRubyObject _initialize(IRubyObject[] args) throws Exception {
            args = Arity.scanArgs(getRuntime(),args,0,4);
            int level = -1;
            int window_bits = 15;
            int memlevel = 8;
            int strategy = 0;
            if(!args[0].isNil()) {
                level = RubyNumeric.fix2int(args[0]);
            }
            if(!args[1].isNil()) {
                window_bits = RubyNumeric.fix2int(args[1]);
            }
            if(!args[2].isNil()) {
                memlevel = RubyNumeric.fix2int(args[2]);
            }
            if(!args[3].isNil()) {
                strategy = RubyNumeric.fix2int(args[3]);
            }
            defl = new ZlibDeflate(this,level,window_bits,memlevel,strategy);
            return this;
        }

        @JRubyMethod(name = "<<", required = 1)
        public IRubyObject append(IRubyObject arg) throws Exception {
            defl.append(arg);
            return this;
        }

        @JRubyMethod(name = "params", required = 2)
        public IRubyObject params(IRubyObject level, IRubyObject strategy) {
            defl.params(RubyNumeric.fix2int(level),RubyNumeric.fix2int(strategy));
            return getRuntime().getNil();
        }

        @JRubyMethod(name = "set_dictionary", required = 1)
        public IRubyObject set_dictionary(IRubyObject arg) throws Exception {
            return defl.set_dictionary(arg);
        }
        
        @JRubyMethod(name = "flush", optional = 1)
        public IRubyObject flush(IRubyObject[] args) throws Exception {
            int flush = 2; // SYNC_FLUSH
            if(Arity.checkArgumentCount(getRuntime(), args,0,1) == 1) {
                if(!args[0].isNil()) {
                    flush = RubyNumeric.fix2int(args[0]);
                }
            }
            return defl.flush(flush);
        }

        @JRubyMethod(name = "deflate", required = 1, optional = 1)
        public IRubyObject deflate(IRubyObject[] args) throws Exception {
            args = Arity.scanArgs(getRuntime(),args,1,1);
            int flush = 0; // NO_FLUSH
            if(!args[1].isNil()) {
                flush = RubyNumeric.fix2int(args[1]);
            }
            return defl.deflate(args[0].convertToString().getByteList(),flush);
        }

        protected int internalTotalOut() {
            return defl.getDeflater().getTotalOut();
        }

        protected boolean internalStreamEndP() {
            return defl.getDeflater().finished();
        }

        protected void internalEnd() {
            defl.getDeflater().end();
        }

        protected void internalReset() {
            defl.getDeflater().reset();
        }

        protected int internalAdler() {
            return defl.getDeflater().getAdler();
        }

        protected IRubyObject internalFinish() throws Exception {
            return defl.finish();
        }

        protected int internalTotalIn() {
            return defl.getDeflater().getTotalIn();
        }

        protected void internalClose() {
            defl.close();
        }
    }

    public static class RubyGzipFile extends RubyObject {
        @JRubyMethod(name = "wrap", required = 2, frame = true, meta = true)
        public static IRubyObject wrap(IRubyObject recv, IRubyObject io, IRubyObject proc, Block unusedBlock) throws IOException {
            if (!(io instanceof RubyGzipFile)) throw recv.getRuntime().newTypeError(io, (RubyClass)recv);
            if (!proc.isNil()) {
                try {
                    ((RubyProc)proc).call(new IRubyObject[]{io});
                } finally {
                    RubyGzipFile zipIO = (RubyGzipFile)io;
                    if (!zipIO.isClosed()) {
                        zipIO.close();
                    }
                }
                return recv.getRuntime().getNil();
            }

            return io;
        }
        
        protected static ObjectAllocator GZIPFILE_ALLOCATOR = new ObjectAllocator() {
            public IRubyObject allocate(Ruby runtime, RubyClass klass) {
                return new RubyGzipFile(runtime, klass);
            }
        };

        @JRubyMethod(name = "new", frame = true, meta = true)
        public static RubyGzipFile newInstance(IRubyObject recv, Block block) {
            RubyClass klass = (RubyClass)recv;
            
            RubyGzipFile result = (RubyGzipFile) klass.allocate();
            
            result.callInit(new IRubyObject[0], block);
            
            return result;
        }

        protected boolean closed = false;
        protected boolean finished = false;
        private int os_code = 255;
        private int level = -1;
        private String orig_name;
        private String comment;
        protected IRubyObject realIo;
        private IRubyObject mtime;

        public RubyGzipFile(Ruby runtime, RubyClass type) {
            super(runtime, type);
            mtime = runtime.getNil();
        }
        
        @JRubyMethod(name = "os_code")
        public IRubyObject os_code() {
            return getRuntime().newFixnum(os_code);
        }
        
        @JRubyMethod(name = "closed?")
        public IRubyObject closed_p() {
            return closed ? getRuntime().getTrue() : getRuntime().getFalse();
        }
        
        protected boolean isClosed() {
            return closed;
        }
        
        @JRubyMethod(name = "orig_name")
        public IRubyObject orig_name() {
            return orig_name == null ? getRuntime().getNil() : getRuntime().newString(orig_name);
        }
        
        @JRubyMethod(name = "to_io")
        public IRubyObject to_io() {
            return realIo;
        }
        
        @JRubyMethod(name = "comment")
        public IRubyObject comment() {
            return comment == null ? getRuntime().getNil() : getRuntime().newString(comment);
        }
        
        @JRubyMethod(name = "crc")
        public IRubyObject crc() {
            return RubyFixnum.zero(getRuntime());
        }
        
        @JRubyMethod(name = "mtime")
        public IRubyObject mtime() {
            return mtime;
        }
        
        @JRubyMethod(name = "sync")
        public IRubyObject sync() {
            return getRuntime().getNil();
        }
        
        @JRubyMethod(name = "finish")
        public IRubyObject finish() throws IOException {
            if (!finished) {
                //io.finish();
            }
            finished = true;
            return realIo;
        }

        @JRubyMethod(name = "close")
        public IRubyObject close() throws IOException {
            return null;
        }
        
        @JRubyMethod(name = "level")
        public IRubyObject level() {
            return getRuntime().newFixnum(level);
        }
        
        @JRubyMethod(name = "sync=", required = 1)
        public IRubyObject set_sync(IRubyObject ignored) {
            return getRuntime().getNil();
        }
    }

    public static class RubyGzipReader extends RubyGzipFile {
        protected static ObjectAllocator GZIPREADER_ALLOCATOR = new ObjectAllocator() {
            public IRubyObject allocate(Ruby runtime, RubyClass klass) {
                return new RubyGzipReader(runtime, klass);
            }
        };
        
        @JRubyMethod(name = "new", rest = true, frame = true, meta = true)
        public static RubyGzipReader newInstance(IRubyObject recv, IRubyObject[] args, Block block) {
            RubyClass klass = (RubyClass)recv;
            RubyGzipReader result = (RubyGzipReader)klass.allocate();
            result.callInit(args, block);
            return result;
        }

        @JRubyMethod(name = "open", required = 1, frame = true, meta = true)
        public static IRubyObject open(IRubyObject recv, IRubyObject filename, Block block) throws IOException {
            Ruby runtime = recv.getRuntime();
            IRubyObject proc = block.isGiven() ? runtime.newProc(Block.Type.PROC, block) : runtime.getNil();
            RubyGzipReader io = newInstance(
                    recv,
                    new IRubyObject[]{ runtime.getFile().callMethod(
                            runtime.getCurrentContext(),
                            "open",
                            new IRubyObject[]{filename, runtime.newString("rb")})},
                            block);
            
            return RubyGzipFile.wrap(recv, io, proc, null);
        }

        public RubyGzipReader(Ruby runtime, RubyClass type) {
            super(runtime, type);
        }
        
        private int line;
        private InputStream io;
        
        @JRubyMethod(name = "initialize", required = 1, frame = true, visibility = Visibility.PRIVATE)
        public IRubyObject initialize(IRubyObject io, Block unusedBlock) {
            realIo = io;
            try {
                this.io = new GZIPInputStream(new IOInputStream(io));
            } catch (IOException e) {
                Ruby runtime = io.getRuntime();
                RubyClass errorClass = runtime.fastGetModule("Zlib").fastGetClass("GzipReader").fastGetClass("Error");
                throw new RaiseException(RubyException.newException(runtime, errorClass, e.getMessage()));
            }

            line = 1;
            
            return this;
        }
        
        @JRubyMethod(name = "rewind")
        public IRubyObject rewind() {
            return getRuntime().getNil();
        }
        
        @JRubyMethod(name = "lineno")
        public IRubyObject lineno() {
            return getRuntime().newFixnum(line);
        }

        @JRubyMethod(name = "readline")
        public IRubyObject readline() throws IOException {
            IRubyObject dst = gets(new IRubyObject[0]);
            if (dst.isNil()) {
                throw getRuntime().newEOFError();
            }
            return dst;
        }

        public IRubyObject internalGets(IRubyObject[] args) throws IOException {
            ByteList sep = ((RubyString)getRuntime().getGlobalVariables().get("$/")).getByteList();
            if (args.length > 0) {
                sep = args[0].convertToString().getByteList();
            }
            return internalSepGets(sep);
        }

        private IRubyObject internalSepGets(ByteList sep) throws IOException {
            ByteList result = new ByteList();
            int ce = io.read();
            while (ce != -1 && sep.indexOf(ce) == -1) {
                result.append((byte)ce);
                ce = io.read();
            }
            line++;
            result.append(sep);
            return RubyString.newString(getRuntime(),result);
        }

        @JRubyMethod(name = "gets", optional = 1)
        public IRubyObject gets(IRubyObject[] args) throws IOException {
            IRubyObject result = internalGets(args);
            if (!result.isNil()) {
                getRuntime().getCurrentContext().getCurrentFrame().setLastLine(result);
            }
            return result;
        }

        private final static int BUFF_SIZE = 4096;
        
        @JRubyMethod(name = "read", optional = 1)
        public IRubyObject read(IRubyObject[] args) throws IOException {
            if (args.length == 0 || args[0].isNil()) {
                ByteList val = new ByteList(10);
                byte[] buffer = new byte[BUFF_SIZE];
                int read = io.read(buffer);
                while (read != -1) {
                    val.append(buffer,0,read);
                    read = io.read(buffer);
                }
                return RubyString.newString(getRuntime(),val);
            } 

            int len = RubyNumeric.fix2int(args[0]);
            if (len < 0) {
            	throw getRuntime().newArgumentError("negative length " + len + " given");
            } else if (len > 0) {
            	byte[] buffer = new byte[len];
            	int toRead = len;
            	int offset = 0;
            	int read = 0;
            	while (toRead > 0) {
            		read = io.read(buffer,offset,toRead);
            		if (read == -1) {
            			break;
            		}
            		toRead -= read;
            		offset += read;
            	} // hmm...
            	return RubyString.newString(getRuntime(),new ByteList(buffer,0,len-toRead,false));
            }
                
            return getRuntime().newString("");
        }

        @JRubyMethod(name = "lineno=", required = 1)
        public IRubyObject set_lineno(IRubyObject lineArg) {
            line = RubyNumeric.fix2int(lineArg);
            return lineArg;
        }

        @JRubyMethod(name = "pos")
        public IRubyObject pos() {
            return RubyFixnum.zero(getRuntime());
        }
        
        @JRubyMethod(name = "readchar")
        public IRubyObject readchar() throws IOException {
            int value = io.read();
            if (value == -1) {
                throw getRuntime().newEOFError();
            }
            return getRuntime().newFixnum(value);
        }

        @JRubyMethod(name = "getc")
        public IRubyObject getc() throws IOException {
            int value = io.read();
            return value == -1 ? getRuntime().getNil() : getRuntime().newFixnum(value);
        }

        private boolean isEof() throws IOException {
            return ((GZIPInputStream)io).available() != 1;
        }

        @JRubyMethod(name = "close")
        public IRubyObject close() throws IOException {
            if (!closed) {
                io.close();
            }
            this.closed = true;
            return getRuntime().getNil();
        }
        
        @JRubyMethod(name = "eof")
        public IRubyObject eof() throws IOException {
            return isEof() ? getRuntime().getTrue() : getRuntime().getFalse();
        }

        @JRubyMethod(name = "eof?")
        public IRubyObject eof_p() throws IOException {
            return eof();
        }

        @JRubyMethod(name = "unused")
        public IRubyObject unused() {
            return getRuntime().getNil();
        }

        @JRubyMethod(name = "tell")
        public IRubyObject tell() {
            return getRuntime().getNil();
        }

        @JRubyMethod(name = "each", optional = 1, frame = true)
        public IRubyObject each(IRubyObject[] args, Block block) throws IOException {
            ByteList sep = ((RubyString)getRuntime().getGlobalVariables().get("$/")).getByteList();
            
            if (args.length > 0 && !args[0].isNil()) {
                sep = args[0].convertToString().getByteList();
            }

            ThreadContext context = getRuntime().getCurrentContext();
            while (!isEof()) {
                block.yield(context, internalSepGets(sep));
            }
            
            return getRuntime().getNil();
        }
    
        @JRubyMethod(name = "ungetc", required = 1)
        public IRubyObject ungetc(IRubyObject arg) {
            return getRuntime().getNil();
        }

        @JRubyMethod(name = "readlines", optional = 1)
        public IRubyObject readlines(IRubyObject[] args) throws IOException {
            List<IRubyObject> array = new ArrayList<IRubyObject>();
            
            if (args.length != 0 && args[0].isNil()) {
                array.add(read(new IRubyObject[0]));
            } else {
                ByteList seperator = ((RubyString)getRuntime().getGlobalVariables().get("$/")).getByteList();
                if (args.length > 0) {
                    seperator = args[0].convertToString().getByteList();
                }
                while (!isEof()) {
                    array.add(internalSepGets(seperator));
                }
            }
            return getRuntime().newArray(array);
        }

        @JRubyMethod(name = "each_byte", frame = true)
        public IRubyObject each_byte(Block block) throws IOException {
            int value = io.read();

            ThreadContext context = getRuntime().getCurrentContext();
            while (value != -1) {
                block.yield(context, getRuntime().newFixnum(value));
                value = io.read();
            }
            
            return getRuntime().getNil();
        }
    }

    public static class RubyGzipWriter extends RubyGzipFile {
        protected static ObjectAllocator GZIPWRITER_ALLOCATOR = new ObjectAllocator() {
            public IRubyObject allocate(Ruby runtime, RubyClass klass) {
                return new RubyGzipWriter(runtime, klass);
            }
        };
        
        @JRubyMethod(name = "new", rest = true, frame = true, meta = true)
        public static RubyGzipWriter newGzipWriter(IRubyObject recv, IRubyObject[] args, Block block) {
            RubyClass klass = (RubyClass)recv;
            
            RubyGzipWriter result = (RubyGzipWriter)klass.allocate();
            result.callInit(args, block);
            return result;
        }

        @JRubyMethod(name = "open", required = 1, optional = 2, frame = true, meta = true)
        public static IRubyObject open(IRubyObject recv, IRubyObject[] args, Block block) throws IOException {
            Ruby runtime = recv.getRuntime();
            IRubyObject level = runtime.getNil();
            IRubyObject strategy = runtime.getNil();

            if (args.length > 1) {
                level = args[1];
                if (args.length > 2) strategy = args[2];
            }

            IRubyObject proc = block.isGiven() ? runtime.newProc(Block.Type.PROC, block) : runtime.getNil();
            RubyGzipWriter io = newGzipWriter(
                    recv,
                    new IRubyObject[]{ runtime.getFile().callMethod(
                            runtime.getCurrentContext(),
                            "open",
                            new IRubyObject[]{args[0],runtime.newString("wb")}),level,strategy},block);
            
            return RubyGzipFile.wrap(recv, io, proc, null);
        }

        public RubyGzipWriter(Ruby runtime, RubyClass type) {
            super(runtime, type);
        }

        private GZIPOutputStream io;
        
        @JRubyMethod(name = "initialize", required = 1, rest = true, frame = true, visibility = Visibility.PRIVATE)
        public IRubyObject initialize2(IRubyObject[] args, Block unusedBlock) throws IOException {
            realIo = (RubyObject)args[0];
            this.io = new GZIPOutputStream(new IOOutputStream(args[0]));
            
            return this;
        }

        @JRubyMethod(name = "close")
        public IRubyObject close() throws IOException {
            if (!closed) {
                io.close();
            }
            this.closed = true;
            
            return getRuntime().getNil();
        }

        @JRubyMethod(name = "append", required = 1)
        public IRubyObject append(IRubyObject p1) throws IOException {
            this.write(p1);
            return this;
        }

        @JRubyMethod(name = "printf", required = 1, rest = true)
        public IRubyObject printf(IRubyObject[] args) throws IOException {
            write(RubyKernel.sprintf(this, args));
            return getRuntime().getNil();
        }

        @JRubyMethod(name = "print", rest = true)
        public IRubyObject print(IRubyObject[] args) throws IOException {
            if (args.length != 0) {
                for (int i = 0, j = args.length; i < j; i++) {
                    write(args[i]);
                }
            }
            
            IRubyObject sep = getRuntime().getGlobalVariables().get("$\\");
            if (!sep.isNil()) {
                write(sep);
            }
            
            return getRuntime().getNil();
        }

        @JRubyMethod(name = "pos")
        public IRubyObject pos() {
            return getRuntime().getNil();
        }

        @JRubyMethod(name = "orig_name=", required = 1)
        public IRubyObject set_orig_name(IRubyObject ignored) {
            return getRuntime().getNil();
        }

        @JRubyMethod(name = "comment=", required = 1)
        public IRubyObject set_comment(IRubyObject ignored) {
            return getRuntime().getNil();
        }

        @JRubyMethod(name = "putc", required = 1)
        public IRubyObject putc(IRubyObject p1) throws IOException {
            io.write(RubyNumeric.fix2int(p1));
            return p1;
        }
        
        @JRubyMethod(name = "puts", rest = true)
        public IRubyObject puts(IRubyObject[] args) throws IOException {
            RubyStringIO sio = (RubyStringIO)getRuntime().fastGetClass("StringIO").newInstance(new IRubyObject[0], Block.NULL_BLOCK);
            sio.puts(args);
            write(sio.string());
            
            return getRuntime().getNil();
        }

        public IRubyObject finish() throws IOException {
            if (!finished) {
                io.finish();
            }
            finished = true;
            return realIo;
        }

        @JRubyMethod(name = "flush", optional = 1)
        public IRubyObject flush(IRubyObject[] args) throws IOException {
            if (args.length == 0 || args[0].isNil() || RubyNumeric.fix2int(args[0]) != 0) { // Zlib::NO_FLUSH
                io.flush();
            }
            return getRuntime().getNil();
        }

        @JRubyMethod(name = "mtime=", required = 1)
        public IRubyObject set_mtime(IRubyObject ignored) {
            return getRuntime().getNil();
        }

        @JRubyMethod(name = "tell")
        public IRubyObject tell() {
            return getRuntime().getNil();
        }

        @JRubyMethod(name = "write", required = 1)
        public IRubyObject write(IRubyObject p1) throws IOException {
            ByteList bytes = p1.convertToString().getByteList();
            io.write(bytes.unsafeBytes(), bytes.begin(), bytes.length());
            return getRuntime().newFixnum(bytes.length());
        }
    }
}
