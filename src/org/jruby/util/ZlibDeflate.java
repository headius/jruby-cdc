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
 * Copyright (C) 2006 Ola Bini <ola.bini@ki.se>
 * Copyright (C) 2006 Dave Brosius <dbrosius@mebigfatguy.com>
 * Copyright (C) 2006 Peter K Chan <peter@oaktop.com>
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
package org.jruby.util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;

import org.jruby.IRuby;
import org.jruby.RubyString;
import org.jruby.runtime.builtin.IRubyObject;

public class ZlibDeflate {
    private Deflater flater;
    private byte[] collected;
    private IRuby runtime;

    public final static int DEF_MEM_LEVEL = 8;
    public final static int MAX_MEM_LEVEL = 9;

    public final static int MAX_WBITS = 15;

    public final static int NO_FLUSH = 0;
    public final static int SYNC_FLUSH = 2;
    public final static int FULL_FLUSH = 3;
    public final static int FINISH = 4;

    public ZlibDeflate(IRubyObject caller, int level, int win_bits, int memlevel, int strategy) {
        super();
        flater = new Deflater(level,false);
        flater.setStrategy(strategy);
        collected = new byte[0];
        runtime = caller.getRuntime();
    }

    public static IRubyObject s_deflate(IRubyObject caller, byte[] str, int level) 
    	throws DataFormatException, IOException {
        ZlibDeflate zstream = new ZlibDeflate(caller, level, MAX_WBITS, DEF_MEM_LEVEL, Deflater.DEFAULT_STRATEGY);
        IRubyObject result = zstream.deflate(str, FINISH);
        zstream.close();
        
        return result;
    }

    public Deflater getDeflater() {
        return flater;
    }

    public void append(IRubyObject obj) throws IOException, UnsupportedEncodingException {
        append(obj.convertToString().getBytes());
    }

    public void append(byte[] obj) throws IOException {
        collected = ZlibInflate.append(collected, obj);
    }

    public void params(int level, int strategy) {
        flater.setLevel(level);
        flater.setStrategy(strategy);
    }

    public IRubyObject set_dictionary(IRubyObject str) throws UnsupportedEncodingException {
        flater.setDictionary(str.convertToString().getBytes());
        return str;
    }

    public IRubyObject flush(int flush) throws IOException {
        return deflate(new byte[0], flush);
    }

    public IRubyObject deflate(byte[] str, int flush) throws IOException {
        if (null == str) {
            byte[] result = new byte[0];
            byte[] outp = new byte[1024];
            byte[] buf = collected;
            collected = new byte[0];
            flater.setInput(buf);
            flater.finish();
            int resultLength = -1;
            while (!flater.finished() && resultLength != 0) {
                resultLength = flater.deflate(outp);
                result = ZlibInflate.append(result,outp,resultLength);
            }
            return RubyString.newString(runtime, result);
        } else {
            append(str);
            if (flush == FINISH) {
                byte[] result = new byte[0];
                byte[] outp = new byte[1024];
                byte[] buf = collected;
                collected = new byte[0];
                flater.setInput(buf);
                flater.finish();
                int resultLength = -1;
                while (!flater.finished() && resultLength != 0) {
                    resultLength = flater.deflate(outp);
                    result = ZlibInflate.append(result,outp,resultLength);
                }
                return RubyString.newString(runtime, result);
            }
            return runtime.newString("");
        }
    }
    
    public IRubyObject finish() throws Exception {
        StringBuffer result = new StringBuffer();
        byte[] outp = new byte[1024];
        byte[] buf = collected;
        collected = new byte[0];
        flater.setInput(buf);
        flater.finish();
        int resultLength = -1;
        while (!flater.finished() && resultLength != 0) {
            resultLength = flater.deflate(outp);
            result.append(new String(outp, 0, resultLength,"PLAIN"));
        }
        return runtime.newString(result.toString());
    }
    
    public void close() {
    }
}
