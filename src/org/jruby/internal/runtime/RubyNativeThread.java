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
 * Copyright (C) 2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
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
package org.jruby.internal.runtime;

import org.jruby.Ruby;
import org.jruby.RubyProc;
import org.jruby.RubyThread;
import org.jruby.RubyThreadGroup;
import org.jruby.exceptions.JumpException;
import org.jruby.exceptions.MainExitException;
import org.jruby.exceptions.RaiseException;
import org.jruby.exceptions.ThreadKill;
import org.jruby.runtime.Block;
import org.jruby.runtime.Frame;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

@Deprecated
public class RubyNativeThread extends Thread {
    private Ruby runtime;
    private Frame currentFrame;
    private RubyProc proc;
    private IRubyObject[] arguments;
    private RubyThread rubyThread;
    private boolean setContextCC;
    
    public RubyNativeThread(RubyThread rubyThread, IRubyObject[] args, Block currentBlock, boolean setContextCC) {
        throw new RuntimeException("RubyNativeThread is deprecated; do not use it");
    }
    
    public RubyThread getRubyThread() {
        return rubyThread;
    }
    
    private static boolean warnedAboutTC = false;
    
    public void run() {
        ThreadContext context = runtime.getThreadService().registerNewThread(rubyThread);
        
        // set thread context JRuby classloader here, for Ruby-owned thread
        try {
            Thread.currentThread().setContextClassLoader(runtime.getJRubyClassLoader());
        } catch (SecurityException se) {
            // can't set TC classloader
            if (!warnedAboutTC && runtime.getInstanceConfig().isVerbose()) {
                System.err.println("WARNING: Security restrictions disallowed setting context classloader for Ruby threads.");
            }
        }
        
        context.preRunThread(currentFrame);

        // Call the thread's code
        try {
            IRubyObject result = proc.call(context, arguments);
            rubyThread.cleanTerminate(result);
        } catch (ThreadKill tk) {
            // notify any killer waiting on our thread that we're going bye-bye
            synchronized (rubyThread.killLock) {
                rubyThread.killLock.notifyAll();
            }
        } catch (JumpException.ReturnJump rj) {
            rubyThread.exceptionRaised(runtime.newThreadError("return can't jump across threads"));
        } catch (RaiseException e) {
            rubyThread.exceptionRaised(e);
        } catch (MainExitException mee) {
            // Someone called exit!, so we need to kill the main thread
            runtime.getThreadService().getMainThread().kill();
        } finally {
            runtime.getThreadService().setCritical(false);
            runtime.getThreadService().unregisterThread(rubyThread);
            
            // synchronize on the RubyThread object for threadgroup updates
            synchronized (rubyThread) {
                ((RubyThreadGroup)rubyThread.group()).remove(rubyThread);
            }
        }
    }
}
