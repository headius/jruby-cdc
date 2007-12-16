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
 * Copyright (C) 2007 Charles O Nutter <headius@headius.com>
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

import org.joni.encoding.Encoding;
import org.joni.encoding.specific.ASCIIEncoding;
import org.joni.encoding.specific.EUCJPEncoding;
import org.joni.encoding.specific.SJISEncoding;
import org.joni.encoding.specific.UTF8Encoding;
import org.jruby.Ruby;
import org.jruby.runtime.builtin.IRubyObject;

public final class KCode {
    public static final KCode NIL = new KCode(null, 0, ASCIIEncoding.INSTANCE);
    public static final KCode NONE = new KCode("NONE", 0, ASCIIEncoding.INSTANCE);
    public static final KCode UTF8 = new KCode("UTF8", 64, UTF8Encoding.INSTANCE);
    public static final KCode SJIS = new KCode("SJIS", 48, SJISEncoding.INSTANCE);
    public static final KCode EUC = new KCode("EUC", 32, EUCJPEncoding.INSTANCE);

    private String kcode;
    private Encoding encoding;

    private int code;

    private KCode(String kcode, int code, Encoding encoding) {
        this.kcode = kcode;
        this.code = code;
        this.encoding = encoding;
    }

    public static KCode create(Ruby runtime, String lang) {
        if(lang == null) return NIL;

        switch(lang.charAt(0)) {
        case 'E':
        case 'e':
            return EUC;
        case 'S':
        case 's':
            return SJIS;
        case 'U':
        case 'u':
            return UTF8;
        case 'N':
        case 'n':
        case 'A':
        case 'a':
            return NONE;
        }
        return NIL;
    }

    public IRubyObject kcode(Ruby runtime) {
        return kcode == null ? runtime.getNil() : runtime.newString(kcode); 
    }
    
    public String getKCode() {
        return kcode;
    }

    public int bits() {
        return code;
    }

    public String name() {
        if(kcode != null) {
            return kcode.toLowerCase();
        }
        return null;
    }

    public Encoding getEncoding() {
        return encoding;
    }
}
	
