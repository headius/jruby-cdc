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
 * Copyright (C) 2001 Chad Fowler <chadfowler@chadfowler.com>
 * Copyright (C) 2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2002-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
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
package org.jruby.test;

import junit.framework.TestCase;
import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyTime;
import org.jruby.runtime.builtin.IRubyObject;

import java.util.Date;

/**
 * 
 * @author chadfowler
 */
public class TestRubyTime extends TestCase {
    private Ruby runtime;
    private RubyClass rubyTime;
    private RubyTime nineTeenSeventy;

    public TestRubyTime(String name) {
        super(name);
    }

    public void setUp() {
        if (runtime == null) {
            runtime = Ruby.getDefaultInstance();
        }
        rubyTime = runtime.getClasses().getTimeClass();
        IRubyObject[] args = new IRubyObject[1];
        args[0] = runtime.newFixnum(18000000);
        nineTeenSeventy = RubyTime.s_at(rubyTime, args);
    }

    public void testTimeCreated() {
        assertTrue(rubyTime != null);
        assertEquals(rubyTime.getName(), "Time");
    }

    public void testTimeNow() {
        RubyTime myTime = RubyTime.s_new(rubyTime);
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            fail("Unexpected InterruptedException");
        }
        Date now = new Date();
        assertTrue(now.after(myTime.getJavaDate()));
    }

    public void testTimeAt() {
        Date myDate = new Date(18000000);
        assertEquals(myDate, nineTeenSeventy.getJavaDate());
    }

    public void testGmtimeAndZone() {
        assertEquals(RubyTime.UTC, nineTeenSeventy.gmtime().zone().getValue());
    }
}
