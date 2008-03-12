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
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
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
package org.jruby.internal.runtime;

import java.lang.ref.SoftReference;
import java.util.concurrent.locks.ReentrantLock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jruby.Ruby;
import org.jruby.RubyThread;
import org.jruby.runtime.ThreadContext;
import org.jruby.util.collections.WeakHashSet;

public class ThreadService {
    private Ruby runtime;
    private ThreadContext mainContext;
    private ThreadLocal<SoftReference> localContext;
    private ThreadGroup rubyThreadGroup;
    private Set rubyThreadList;
    private Thread mainThread;
    
    private ReentrantLock criticalLock = new ReentrantLock();

    public ThreadService(Ruby runtime) {
        this.runtime = runtime;
        this.mainContext = ThreadContext.newContext(runtime);
        this.localContext = new ThreadLocal();
        this.rubyThreadGroup = new ThreadGroup("Ruby Threads#" + runtime.hashCode());
        this.rubyThreadList = Collections.synchronizedSet(new WeakHashSet());
        
        // Must be called from main thread (it is currently, but this bothers me)
        mainThread = Thread.currentThread();
        localContext.set(new SoftReference(mainContext));
        rubyThreadList.add(mainThread);
    }

    public void disposeCurrentThread() {
        localContext.set(null);
    }

    /**
     * In order to provide an appropriate execution context for a given thread,
     * we store ThreadContext instances in a threadlocal. This method is a utility
     * to get at that threadlocal context from anywhere in the program it may
     * not be immediately available. This method should be used sparingly, and
     * if it is possible to pass ThreadContext on the argument list, it is
     * preferable.
     * 
     * <b>Description of behavior</b>
     * 
     * The threadlocal does not actually contain the ThreadContext directly;
     * instead, it contains a SoftReference that holds the ThreadContext. This
     * is to allow new threads to enter the system and execute Ruby code with
     * a valid context, but still allow that context to garbage collect if the
     * thread stays alive much longer. We use SoftReference here because
     * WeakReference is collected too quickly, resulting in very expensive
     * ThreadContext churn (and this originally lead to JRUBY-2261's leak of
     * adopted RubyThread instances).
     * 
     * @return The ThreadContext instance for the current thread, or a new one
     * if none has previously been created or the old ThreadContext has been
     * collected.
     */
    public ThreadContext getCurrentContext() {
        SoftReference sr = null;
        ThreadContext context = null;
        
        while (context == null) {
            // loop until a context is available, to clean up softrefs that might have been collected
            if ((sr = (SoftReference)localContext.get()) == null) {
                sr = adoptCurrentThread();
                context = (ThreadContext)sr.get();
            } else {
                context = (ThreadContext)sr.get();
            }
            
            // context is null, wipe out the SoftReference (this could be done with a reference queue)
            if (context == null) {
                localContext.set(null);
            }
        }

        return context;
    }
    
    private SoftReference adoptCurrentThread() {
        Thread current = Thread.currentThread();
        
        RubyThread.adopt(runtime.getThread(), current);
        
        return (SoftReference) localContext.get();
    }

    public RubyThread getMainThread() {
        return mainContext.getThread();
    }

    public void setMainThread(RubyThread thread) {
        mainContext.setThread(thread);
    }
    
    public synchronized RubyThread[] getActiveRubyThreads() {
    	// all threads in ruby thread group plus main thread

        synchronized(rubyThreadList) {
            List rtList = new ArrayList(rubyThreadList.size());
        
            for (Iterator iter = rubyThreadList.iterator(); iter.hasNext();) {
                Thread t = (Thread)iter.next();
            
                if (!t.isAlive()) continue;
            
                RubyThread rt = getRubyThreadFromThread(t);
                rtList.add(rt);
            }
        
            RubyThread[] rubyThreads = new RubyThread[rtList.size()];
            rtList.toArray(rubyThreads);
    	
            return rubyThreads;
        }
    }
    
    public ThreadGroup getRubyThreadGroup() {
    	return rubyThreadGroup;
    }

    public synchronized ThreadContext registerNewThread(RubyThread thread) {
        ThreadContext context = ThreadContext.newContext(runtime);
        localContext.set(new SoftReference(context));
        getCurrentContext().setThread(thread);
        // This requires register to be called from within the registree thread
        rubyThreadList.add(Thread.currentThread());
        return context;
    }
    
    public synchronized void unregisterThread(RubyThread thread) {
        rubyThreadList.remove(Thread.currentThread());
        getCurrentContext().setThread(null);
        localContext.set(null);
    }
    
    private RubyThread getRubyThreadFromThread(Thread activeThread) {
        RubyThread rubyThread;
        if (activeThread instanceof RubyNativeThread) {
            RubyNativeThread rubyNativeThread = (RubyNativeThread)activeThread;
            rubyThread = rubyNativeThread.getRubyThread();
        } else {
            // main thread
            rubyThread = mainContext.getThread();
        }
        return rubyThread;
    }
    
    public void setCritical(boolean critical) {
        if (criticalLock.isHeldByCurrentThread()) {
            if (critical) {
                // do nothing
            } else {
                criticalLock.unlock();
            }
        } else {
            if (critical) {
                criticalLock.lock();
            } else {
                // do nothing
            }
        }
    }
    
    public boolean getCritical() {
        return criticalLock.isHeldByCurrentThread();
    }
    
    public void waitForCritical() {
        if (criticalLock.isLocked()) {
            criticalLock.lock();
            criticalLock.unlock();
        }
    }

}
