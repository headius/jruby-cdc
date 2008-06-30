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
import org.jruby.Ruby;
import org.jruby.runtime.builtin.IRubyObject;

public final class KCode {
    public static final KCode NIL = new KCode(null, "ASCII", 0);
    public static final KCode NONE = new KCode("NONE", "ASCII", 0);
    public static final KCode UTF8 = new KCode("UTF8", "UTF8", 64);
    public static final KCode SJIS = new KCode("SJIS", "SJIS", 48);
    public static final KCode EUC = new KCode("EUC", "EUCJP", 32);

    private final String kcode;
    private final String encodingName;
    private final int code;

    private volatile Encoding encoding;

    private KCode(String kcode, String encodingName, int code) {
        this.kcode = kcode;
        this.encodingName = encodingName;
        this.code = code;
    }

    public static KCode create(Ruby runtime, String lang) {
        if (lang == null) return NIL;
        if (lang.isEmpty()) return NONE;

        switch (lang.charAt(0)) {
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
        return kcode != null ? kcode.toLowerCase() : null;
    }

    public Encoding getEncoding() {
        if (encoding == null) {
            encoding = Encoding.load(encodingName);
        }
        return encoding;
    }
}
