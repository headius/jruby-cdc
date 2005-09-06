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
 * Copyright (C) 2001-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2005 Charles O Nutter <headius@headius.com>
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

import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.builtin.IRubyObject;

/** Implementation of the Comparable module.
 *
 * @author  jpetersen
 * @version $Revision$
 */
public class RubyComparable {
    public static RubyModule createComparable(Ruby runtime) {
        RubyModule comparableModule = runtime.defineModule("Comparable");
        CallbackFactory callbackFactory = runtime.callbackFactory(RubyComparable.class);
        comparableModule.defineMethod(
            "==",
            callbackFactory.getSingletonMethod("equal", IRubyObject.class));
        comparableModule.defineMethod(
            ">",
            callbackFactory.getSingletonMethod("op_gt", IRubyObject.class));
        comparableModule.defineMethod(
            ">=",
            callbackFactory.getSingletonMethod("op_ge", IRubyObject.class));
        comparableModule.defineMethod(
            "<",
            callbackFactory.getSingletonMethod("op_lt", IRubyObject.class));
        comparableModule.defineMethod(
            "<=",
            callbackFactory.getSingletonMethod("op_le", IRubyObject.class));
        comparableModule.defineMethod(
            "between?",
            callbackFactory.getSingletonMethod(
                "between_p",
                IRubyObject.class,
                IRubyObject.class));

        return comparableModule;
    }

    public static RubyBoolean equal(IRubyObject recv, IRubyObject other) {
        try {
            if (recv == other) {
                return recv.getRuntime().getTrue();
            }
            return (RubyNumeric.fix2int(recv.callMethod("<=>", other)) == 0) ? recv.getRuntime().getTrue() : recv.getRuntime().getFalse();
        } catch (RaiseException rnExcptn) {
        	// FIXME: heavy, find a better way to check
        	if (rnExcptn.getException().isKindOf(recv.getRuntime().getClass("NoMethodError"))) {
        		return recv.getRuntime().getFalse();
        	} else if (rnExcptn.getException().isKindOf(recv.getRuntime().getClass("NameError"))) {
        		return recv.getRuntime().getFalse();
        	}
        	throw rnExcptn;
        }
    }

    public static RubyBoolean op_gt(IRubyObject recv, IRubyObject other) {
        return RubyNumeric.fix2int(recv.callMethod("<=>", other)) > 0 ? recv.getRuntime().getTrue() : recv.getRuntime().getFalse();
    }

    public static RubyBoolean op_ge(IRubyObject recv, IRubyObject other) {
        return RubyNumeric.fix2int(recv.callMethod("<=>", other)) >= 0 ? recv.getRuntime().getTrue() : recv.getRuntime().getFalse();
    }

    public static RubyBoolean op_lt(IRubyObject recv, IRubyObject other) {
        return RubyNumeric.fix2int(recv.callMethod("<=>", other)) < 0 ? recv.getRuntime().getTrue() : recv.getRuntime().getFalse();
    }

    public static RubyBoolean op_le(IRubyObject recv, IRubyObject other) {
        return RubyNumeric.fix2int(recv.callMethod("<=>", other)) <= 0 ? recv.getRuntime().getTrue() : recv.getRuntime().getFalse();
    }

    public static RubyBoolean between_p(IRubyObject recv, IRubyObject first, IRubyObject second) {
        if (RubyNumeric.fix2int(recv.callMethod("<=>", first)) < 0) {
            return recv.getRuntime().getFalse();
        } else if (RubyNumeric.fix2int(recv.callMethod("<=>", second)) > 0) {
            return recv.getRuntime().getFalse();
        } else {
            return recv.getRuntime().getTrue();
        }
    }
}
