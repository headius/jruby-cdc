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
 * Copyright (C) 2006 MenTaLguY <mental@rydia.net>
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

/* Portions loosely based on public-domain JSR-166 code by Doug Lea et al. */

package org.jruby.libraries;

import java.io.IOException;
import java.util.List;
import java.util.Iterator;
import java.util.Collections;
import java.util.LinkedList;

import org.jruby.IRuby;
import org.jruby.RubyObject;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.RubyBoolean;
import org.jruby.RubyArray;
import org.jruby.RubyThread;
import org.jruby.RubyInteger;
import org.jruby.RubyNumeric;
import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.load.Library;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * @author <a href="mailto:mental@rydia.net">MenTaLguY</a>
 */
public class ThreadLibrary implements Library {
    public void load(final IRuby runtime) throws IOException {
        Mutex.setup(runtime);
        ConditionVariable.setup(runtime);
        Queue.setup(runtime);
        SizedQueue.setup(runtime);
    }

    public static class Mutex extends RubyObject {
        private RubyThread owner = null;

        public static Mutex newInstance(IRubyObject recv, IRubyObject[] args) {
            Mutex result = new Mutex(recv.getRuntime(), (RubyClass)recv);
            result.callInit(args);
            return result;
        }

        public Mutex(IRuby runtime, RubyClass type) {
            super(runtime, type);
        }

        public static void setup(IRuby runtime) {
            RubyClass cMutex = runtime.defineClass("Mutex", runtime.getClass("Object"), new ObjectAllocator() {
                public IRubyObject allocate(IRuby runtime, RubyClass klass) {
                    return new Mutex(runtime, klass);
                }
            });
            CallbackFactory cb = runtime.callbackFactory(Mutex.class);
            cMutex.defineSingletonMethod("new", cb.getOptSingletonMethod("newInstance"));
            cMutex.defineMethod("locked?", cb.getMethod("locked_p"));
            cMutex.defineMethod("try_lock", cb.getMethod("try_lock"));
            cMutex.defineMethod("lock", cb.getMethod("lock"));
            cMutex.defineMethod("unlock", cb.getMethod("unlock"));
            cMutex.defineMethod("synchronize", cb.getMethod("synchronize"));
        }

        public synchronized RubyBoolean locked_p() {
            return ( owner != null ? getRuntime().getTrue() : getRuntime().getFalse() );
        }

        public RubyBoolean try_lock() throws InterruptedException {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            synchronized (this) {
                if ( owner != null ) {
                    return getRuntime().getFalse();
                }
                lock();
            }
            return getRuntime().getTrue();
        }

        public IRubyObject lock() throws InterruptedException {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            synchronized (this) {
                try {
                    while ( owner != null ) {
                        wait();
                    }
                    owner = getRuntime().getCurrentContext().getThread();
                } catch (InterruptedException ex) {
                    if ( owner == null ) {
                        notify();
                    }
                    throw ex;
                }
            }
            return this;
        }

        public synchronized RubyBoolean unlock() {
            if ( owner != null ) {
                owner = null;
                notify();
                return getRuntime().getTrue();
            } else {
                return getRuntime().getFalse();
            }
        }

        public IRubyObject synchronize() throws InterruptedException {
            ThreadContext tc = getRuntime().getCurrentContext();
            try {
                lock();
                return getRuntime().getCurrentContext().yield(null);
            } finally {
                unlock();
            }
        }
    }

    public static class ConditionVariable extends RubyObject {
        public static ConditionVariable newInstance(IRubyObject recv, IRubyObject[] args) {
            ConditionVariable result = new ConditionVariable(recv.getRuntime(), (RubyClass)recv);
            result.callInit(args);
            return result;
        }

        public ConditionVariable(IRuby runtime, RubyClass type) {
            super(runtime, type);
        }

        public static void setup(IRuby runtime) {
            RubyClass cConditionVariable = runtime.defineClass("ConditionVariable", runtime.getClass("Object"), new ObjectAllocator() {
                public IRubyObject allocate(IRuby runtime, RubyClass klass) {
                    return new ConditionVariable(runtime, klass);
                }
            });
            CallbackFactory cb = runtime.callbackFactory(ConditionVariable.class);
            cConditionVariable.defineSingletonMethod("new", cb.getOptSingletonMethod("newInstance"));
            cConditionVariable.defineMethod("wait", cb.getMethod("wait_ruby", Mutex.class));
            cConditionVariable.defineMethod("broadcast", cb.getMethod("broadcast"));
            cConditionVariable.defineMethod("signal", cb.getMethod("signal"));
        }

        public IRubyObject wait_ruby(Mutex mutex) throws InterruptedException {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            try {
                synchronized (this) {
                    mutex.unlock();
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        notify();
                        throw e;
                    }
                }
            } finally {
                mutex.lock();
            }
            return getRuntime().getNil();
        }

        public synchronized IRubyObject broadcast() {
            notifyAll();
            return getRuntime().getNil();
        }

        public synchronized IRubyObject signal() {
            notify();
            return getRuntime().getNil();
        }
    }

    public static class Queue extends RubyObject {
        private LinkedList entries;

        public static IRubyObject newInstance(IRubyObject recv, IRubyObject[] args) {
            Queue result = new Queue(recv.getRuntime(), (RubyClass)recv);
            result.callInit(args);
            return result;
        }

        public Queue(IRuby runtime, RubyClass type) {
            super(runtime, type);
            entries = new LinkedList();
        }

        public static void setup(IRuby runtime) {
            RubyClass cQueue = runtime.defineClass("Queue", runtime.getClass("Object"), new ObjectAllocator() {
                public IRubyObject allocate(IRuby runtime, RubyClass klass) {
                    return new Queue(runtime, klass);
                }
            });
            CallbackFactory cb = runtime.callbackFactory(Queue.class);
            cQueue.defineSingletonMethod("new", cb.getOptSingletonMethod("newInstance"));

            cQueue.defineMethod("clear", cb.getMethod("clear"));
            cQueue.defineMethod("empty?", cb.getMethod("empty_p"));
            cQueue.defineMethod("length", cb.getMethod("length"));
            cQueue.defineMethod("num_waiting", cb.getMethod("num_waiting"));
            cQueue.defineMethod("pop", cb.getOptMethod("pop"));
            cQueue.defineMethod("push", cb.getMethod("push", IRubyObject.class));
            
            cQueue.defineAlias("<<", "push");
            cQueue.defineAlias("deq", "pop");
            cQueue.defineAlias("shift", "pop");
            cQueue.defineAlias("size", "length");
        }

        public synchronized IRubyObject clear() {
            entries.clear();
            return getRuntime().getNil();
        }

        public synchronized RubyBoolean empty_p() {
            return ( entries.size() == 0 ? getRuntime().getTrue() : getRuntime().getFalse() );
        }

        public synchronized RubyNumeric length() {
            return RubyNumeric.int2fix(getRuntime(), entries.size());
        }

        public int num_waiting() { return 0; }

        public synchronized IRubyObject pop(IRubyObject[] args) {
            boolean should_block = true;
            if ( checkArgumentCount(args, 0, 1) == 1 ) {
                should_block = args[0].isTrue();
            }
            if ( !should_block && entries.size() == 0 ) {
                throw new RaiseException(getRuntime(), getRuntime().getClass("ThreadError"), "queue empty", false);
            }
            while ( entries.size() == 0 ) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
            return (IRubyObject)entries.removeFirst();
        }

        public synchronized IRubyObject push(IRubyObject value) {
            entries.addLast(value);
            notify();
            return getRuntime().getNil();
        }
    }


    public static class SizedQueue extends Queue {
        private int capacity;

        public static IRubyObject newInstance(IRubyObject recv, IRubyObject[] args) {
            SizedQueue result = new SizedQueue(recv.getRuntime(), (RubyClass)recv);
            result.callInit(args);
            return result;
        }

        public SizedQueue(IRuby runtime, RubyClass type) {
            super(runtime, type);
            capacity = 1;
        }

        public static void setup(IRuby runtime) {
            RubyClass cSizedQueue = runtime.defineClass("SizedQueue", runtime.getClass("Queue"), new ObjectAllocator() {
                public IRubyObject allocate(IRuby runtime, RubyClass klass) {
                    return new SizedQueue(runtime, klass);
                }
            });
            CallbackFactory cb = runtime.callbackFactory(SizedQueue.class);
            cSizedQueue.defineSingletonMethod("new", cb.getOptSingletonMethod("newInstance"));

            cSizedQueue.defineMethod("initialize", cb.getMethod("max_set", RubyInteger.class));

            cSizedQueue.defineMethod("clear", cb.getMethod("clear"));
            cSizedQueue.defineMethod("max", cb.getMethod("max"));
            cSizedQueue.defineMethod("max=", cb.getMethod("max_set", RubyInteger.class));
            cSizedQueue.defineMethod("pop", cb.getOptMethod("pop"));
            cSizedQueue.defineMethod("push", cb.getMethod("push", IRubyObject.class));

            cSizedQueue.defineAlias("<<", "push");
            cSizedQueue.defineAlias("deq", "pop");
            cSizedQueue.defineAlias("shift", "pop");
        }

        public synchronized IRubyObject clear() {
            super.clear();
            notifyAll();
            return getRuntime().getNil();
        }

        public synchronized RubyNumeric max() {
            return RubyNumeric.int2fix(getRuntime(), capacity);
        }

        public synchronized IRubyObject max_set(RubyInteger arg) {
            int new_capacity = RubyNumeric.fix2int(arg);
            if ( new_capacity <= 0 ) {
                getRuntime().newArgumentError("queue size must be positive");
            }
            int difference;
            if ( new_capacity > capacity ) {
                difference = new_capacity - capacity;
            } else {
                difference = 0;
            }
            capacity = new_capacity;
            if ( difference > 0 ) {
                notifyAll();
            }
            return getRuntime().getNil();
        }

        public synchronized IRubyObject pop(IRubyObject args[]) {
            IRubyObject result = super.pop(args);
            notifyAll();
            return result;
        }

        public synchronized IRubyObject push(IRubyObject value) {
            while ( RubyNumeric.fix2int(length()) >= capacity ) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
            super.push(value);
            notifyAll();
            return getRuntime().getNil();
        }
    }
}
