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
 * Copyright (C) 2002 Jason Voegele <jason@jvoegele.com>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2002-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004-2005 Charles O Nutter <headius@headius.com>
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
package org.jruby;

import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Map;

import java.util.Set;
import org.jruby.common.IRubyWarnings.ID;
import org.jruby.exceptions.RaiseException;
import org.jruby.exceptions.ThreadKill;
import org.jruby.internal.runtime.FutureThread;
import org.jruby.internal.runtime.NativeThread;
import org.jruby.internal.runtime.RubyNativeThread;
import org.jruby.internal.runtime.RubyRunnable;
import org.jruby.internal.runtime.ThreadLike;
import org.jruby.internal.runtime.ThreadService;
import org.jruby.runtime.Block;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.ObjectMarshal;
import org.jruby.runtime.Visibility;

/**
 * Implementation of Ruby's <code>Thread</code> class.  Each Ruby thread is
 * mapped to an underlying Java Virtual Machine thread.
 * <p>
 * Thread encapsulates the behavior of a thread of execution, including the main
 * thread of the Ruby script.  In the descriptions that follow, the parameter
 * <code>aSymbol</code> refers to a symbol, which is either a quoted string or a
 * <code>Symbol</code> (such as <code>:name</code>).
 * 
 * Note: For CVS history, see ThreadClass.java.
 */
public class RubyThread extends RubyObject {
    private ThreadLike threadImpl;
    private RubyFixnum priority;
    private final Map<IRubyObject, IRubyObject> threadLocalVariables = new HashMap<IRubyObject, IRubyObject>();
    private boolean abortOnException;
    private IRubyObject finalResult;
    private RaiseException exitingException;
    private IRubyObject receivedException;
    private RubyThreadGroup threadGroup;

    private final ThreadService threadService;
    private volatile boolean isStopped = false;
    public Object stopLock = new Object();
    
    private volatile boolean killed = false;
    public Object killLock = new Object();
    
    public final ReentrantLock lock = new ReentrantLock();
    
    private static final boolean DEBUG = false;

    protected RubyThread(Ruby runtime, RubyClass type) {
        super(runtime, type);
        this.threadService = runtime.getThreadService();
        // set to default thread group
        RubyThreadGroup defaultThreadGroup = (RubyThreadGroup)runtime.getThreadGroup().fastGetConstant("Default");
        defaultThreadGroup.add(this, Block.NULL_BLOCK);
        finalResult = runtime.getNil();
    }
    
    /**
     * Dispose of the current thread by removing it from its parent ThreadGroup.
     */
    public void dispose() {
        threadGroup.remove(this);
    }
   
    public static RubyClass createThreadClass(Ruby runtime) {
        // FIXME: In order for Thread to play well with the standard 'new' behavior,
        // it must provide an allocator that can create empty object instances which
        // initialize then fills with appropriate data.
        RubyClass threadClass = runtime.defineClass("Thread", runtime.getObject(), ObjectAllocator.NOT_ALLOCATABLE_ALLOCATOR);
        runtime.setThread(threadClass);

        threadClass.defineAnnotatedMethods(RubyThread.class);

        RubyThread rubyThread = new RubyThread(runtime, threadClass);
        // TODO: need to isolate the "current" thread from class creation
        rubyThread.threadImpl = new NativeThread(rubyThread, Thread.currentThread());
        runtime.getThreadService().setMainThread(rubyThread);
        
        threadClass.setMarshal(ObjectMarshal.NOT_MARSHALABLE_MARSHAL);
        
        return threadClass;
    }

    /**
     * <code>Thread.new</code>
     * <p>
     * Thread.new( <i>[ arg ]*</i> ) {| args | block } -> aThread
     * <p>
     * Creates a new thread to execute the instructions given in block, and
     * begins running it. Any arguments passed to Thread.new are passed into the
     * block.
     * <pre>
     * x = Thread.new { sleep .1; print "x"; print "y"; print "z" }
     * a = Thread.new { print "a"; print "b"; sleep .2; print "c" }
     * x.join # Let the threads finish before
     * a.join # main thread exits...
     * </pre>
     * <i>produces:</i> abxyzc
     */
    @JRubyMethod(name = {"new", "fork"}, rest = true, frame = true, meta = true)
    public static IRubyObject newInstance(IRubyObject recv, IRubyObject[] args, Block block) {
        return startThread(recv, args, true, block);
    }

    /**
     * Basically the same as Thread.new . However, if class Thread is
     * subclassed, then calling start in that subclass will not invoke the
     * subclass's initialize method.
     */
    @JRubyMethod(name = "start", rest = true, frame = true, meta = true)
    public static RubyThread start(IRubyObject recv, IRubyObject[] args, Block block) {
        return startThread(recv, args, false, block);
    }
    
    public static RubyThread adopt(IRubyObject recv, Thread t) {
        return adoptThread(recv, t, Block.NULL_BLOCK);
    }

    private static RubyThread adoptThread(final IRubyObject recv, Thread t, Block block) {
        final Ruby runtime = recv.getRuntime();
        final RubyThread rubyThread = new RubyThread(runtime, (RubyClass) recv);
        
        rubyThread.threadImpl = new NativeThread(rubyThread, t);
        ThreadContext context = runtime.getThreadService().registerNewThread(rubyThread);
        
        context.preAdoptThread();
        
        return rubyThread;
    }
    
    @JRubyMethod(name = "initialize", rest = true, frame = true, visibility = Visibility.PRIVATE)
    public IRubyObject initialize(IRubyObject[] args, Block block) {
        if (!block.isGiven()) throw getRuntime().newThreadError("must be called with a block");

        if (RubyInstanceConfig.POOLING_ENABLED) {
            threadImpl = new FutureThread(this, new RubyRunnable(this, args, block));
        } else {
            threadImpl = new NativeThread(this, new RubyNativeThread(this, args, block));
        }
        threadImpl.start();
        
        return this;
    }
    
    private static RubyThread startThread(final IRubyObject recv, final IRubyObject[] args, boolean callInit, Block block) {
        RubyThread rubyThread = new RubyThread(recv.getRuntime(), (RubyClass) recv);
        
        if (callInit) {
            rubyThread.callInit(args, block);
        } else {
            // for Thread::start, which does not call the subclass's initialize
            rubyThread.initialize(args, block);
        }
        
        return rubyThread;
    }
    
    private void ensureCurrent(ThreadContext context) {
        if (this != context.getThread()) {
            throw new RuntimeException("internal thread method called from another thread");
        }
    }
    
    private void ensureNotCurrent() {
        if (this == getRuntime().getCurrentContext().getThread()) {
            throw new RuntimeException("internal thread method called from another thread");
        }
    }
    
    public synchronized void cleanTerminate(IRubyObject result) {
        finalResult = result;
        isStopped = true;
    }

    public void pollThreadEvents() {
        pollThreadEvents(getRuntime().getCurrentContext());
    }
    
    public void pollThreadEvents(ThreadContext context) {
        // check for criticalization *before* locking ourselves
        threadService.waitForCritical();

        ensureCurrent(context);

        if (DEBUG) System.out.println("thread " + Thread.currentThread() + " before");
        if (killed) throw new ThreadKill();

        if (DEBUG) System.out.println("thread " + Thread.currentThread() + " after");
        if (receivedException != null) {
            // clear this so we don't keep re-throwing
            IRubyObject raiseException = receivedException;
            receivedException = null;
            RubyModule kernelModule = getRuntime().getKernel();
            if (DEBUG) System.out.println("thread " + Thread.currentThread() + " before propagating exception: " + killed);
            kernelModule.callMethod(context, "raise", raiseException);
        }
    }

    /**
     * Returns the status of the global ``abort on exception'' condition. The
     * default is false. When set to true, will cause all threads to abort (the
     * process will exit(0)) if an exception is raised in any thread. See also
     * Thread.abort_on_exception= .
     */
    @JRubyMethod(name = "abort_on_exception", meta = true)
    public static RubyBoolean abort_on_exception_x(IRubyObject recv) {
    	Ruby runtime = recv.getRuntime();
        return runtime.isGlobalAbortOnExceptionEnabled() ? runtime.getTrue() : runtime.getFalse();
    }

    @JRubyMethod(name = "abort_on_exception=", required = 1, meta = true)
    public static IRubyObject abort_on_exception_set_x(IRubyObject recv, IRubyObject value) {
        recv.getRuntime().setGlobalAbortOnExceptionEnabled(value.isTrue());
        return value;
    }

    @JRubyMethod(name = "current", meta = true)
    public static RubyThread current(IRubyObject recv) {
        return recv.getRuntime().getCurrentContext().getThread();
    }

    @JRubyMethod(name = "main", meta = true)
    public static RubyThread main(IRubyObject recv) {
        return recv.getRuntime().getThreadService().getMainThread();
    }

    @JRubyMethod(name = "pass", meta = true)
    public static IRubyObject pass(IRubyObject recv) {
        Ruby runtime = recv.getRuntime();
        ThreadService ts = runtime.getThreadService();
        boolean critical = ts.getCritical();
        
        ts.setCritical(false);
        
        Thread.yield();
        
        ts.setCritical(critical);
        
        return recv.getRuntime().getNil();
    }

    @JRubyMethod(name = "list", meta = true)
    public static RubyArray list(IRubyObject recv) {
    	RubyThread[] activeThreads = recv.getRuntime().getThreadService().getActiveRubyThreads();
        
        return recv.getRuntime().newArrayNoCopy(activeThreads);
    }
    
    private IRubyObject getSymbolKey(IRubyObject originalKey) {
        if (originalKey instanceof RubySymbol) {
            return originalKey;
        } else if (originalKey instanceof RubyString) {
            return getRuntime().newSymbol(originalKey.asJavaString());
        } else if (originalKey instanceof RubyFixnum) {
            getRuntime().getWarnings().warn(ID.FIXNUMS_NOT_SYMBOLS, "Do not use Fixnums as Symbols");
            throw getRuntime().newArgumentError(originalKey + " is not a symbol");
        } else {
            throw getRuntime().newTypeError(originalKey + " is not a symbol");
        }
    }

    @JRubyMethod(name = "[]", required = 1)
    public IRubyObject op_aref(IRubyObject key) {
        IRubyObject value;
        if ((value = threadLocalVariables.get(getSymbolKey(key))) != null) {
            return value;
        }
        return getRuntime().getNil();
    }

    @JRubyMethod(name = "[]=", required = 2)
    public IRubyObject op_aset(IRubyObject key, IRubyObject value) {
        key = getSymbolKey(key);
        
        threadLocalVariables.put(key, value);
        return value;
    }

    @JRubyMethod(name = "abort_on_exception")
    public RubyBoolean abort_on_exception() {
        return abortOnException ? getRuntime().getTrue() : getRuntime().getFalse();
    }

    @JRubyMethod(name = "abort_on_exception=", required = 1)
    public IRubyObject abort_on_exception_set(IRubyObject val) {
        abortOnException = val.isTrue();
        return val;
    }

    @JRubyMethod(name = "alive?")
    public RubyBoolean alive_p() {
        return threadImpl.isAlive() ? getRuntime().getTrue() : getRuntime().getFalse();
    }

    @JRubyMethod(name = "join", optional = 1)
    public IRubyObject join(IRubyObject[] args) {
        long timeoutMillis = 0;
        if (args.length > 0) {
            if (args.length > 1) {
                throw getRuntime().newArgumentError(args.length,1);
            }
            // MRI behavior: value given in seconds; converted to Float; less
            // than or equal to zero returns immediately; returns nil
            timeoutMillis = (long)(1000.0D * args[0].convertToFloat().getValue());
            if (timeoutMillis <= 0) {
	        // TODO: not sure that we should skip caling join() altogether.
		// Thread.join() has some implications for Java Memory Model, etc.
	        if (threadImpl.isAlive()) {
		   return getRuntime().getNil();
		} else {   
                   return this;
		}
            }
        }
        if (isCurrent()) {
            throw getRuntime().newThreadError("thread tried to join itself");
        }
        try {
            if (threadService.getCritical()) {
                // If the target thread is sleeping or stopped, wake it
                synchronized (stopLock) {
                    stopLock.notify();
                }
                
                // interrupt the target thread in case it's blocking or waiting
                // WARNING: We no longer interrupt the target thread, since this usually means
                // interrupting IO and with NIO that means the channel is no longer usable.
                // We either need a new way to handle waking a target thread that's waiting
                // on IO, or we need to accept that we can't wake such threads and must wait
                // for them to complete their operation.
                //threadImpl.interrupt();
            }
            threadImpl.join(timeoutMillis);
        } catch (InterruptedException ie) {
            ie.printStackTrace();
            assert false : ie;
        } catch (TimeoutException ie) {
            ie.printStackTrace();
            assert false : ie;
        } catch (ExecutionException ie) {
            ie.printStackTrace();
            assert false : ie;
        }

        if (exitingException != null) {
            throw exitingException;
        }

        if (threadImpl.isAlive()) {
            return getRuntime().getNil();
        } else {
            return this;
	}
    }

    @JRubyMethod(name = "value")
    public IRubyObject value() {
        join(new IRubyObject[0]);
        synchronized (this) {
            return finalResult;
        }
    }

    @JRubyMethod(name = "group")
    public IRubyObject group() {
        if (threadGroup == null) {
        	return getRuntime().getNil();
        }
        
        return threadGroup;
    }
    
    void setThreadGroup(RubyThreadGroup rubyThreadGroup) {
    	threadGroup = rubyThreadGroup;
    }
    
    @JRubyMethod(name = "inspect")
    public IRubyObject inspect() {
        // FIXME: There's some code duplication here with RubyObject#inspect
        StringBuffer part = new StringBuffer();
        String cname = getMetaClass().getRealClass().getName();
        part.append("#<").append(cname).append(":0x");
        part.append(Integer.toHexString(System.identityHashCode(this)));
        
        if (threadImpl.isAlive()) {
            if (isStopped) {
                part.append(getRuntime().newString(" sleep"));
            } else if (killed) {
                part.append(getRuntime().newString(" aborting"));
            } else {
                part.append(getRuntime().newString(" run"));
            }
        } else {
            part.append(" dead");
        }
        
        part.append(">");
        return getRuntime().newString(part.toString());
    }

    @JRubyMethod(name = "key?", required = 1)
    public RubyBoolean key_p(IRubyObject key) {
        key = getSymbolKey(key);
        
        return getRuntime().newBoolean(threadLocalVariables.containsKey(key));
    }

    @JRubyMethod(name = "keys")
    public RubyArray keys() {
        IRubyObject[] keys = new IRubyObject[threadLocalVariables.size()];
        
        return RubyArray.newArrayNoCopy(getRuntime(), (IRubyObject[])threadLocalVariables.keySet().toArray(keys));
    }
    
    @JRubyMethod(name = "critical=", required = 1, meta = true)
    public static IRubyObject critical_set(IRubyObject receiver, IRubyObject value) {
    	receiver.getRuntime().getThreadService().setCritical(value.isTrue());
    	
    	return value;
    }

    @JRubyMethod(name = "critical", meta = true)
    public static IRubyObject critical(IRubyObject receiver) {
    	return receiver.getRuntime().newBoolean(receiver.getRuntime().getThreadService().getCritical());
    }
    
    @JRubyMethod(name = "stop", meta = true)
    public static IRubyObject stop(IRubyObject receiver) {
        RubyThread rubyThread = receiver.getRuntime().getThreadService().getCurrentContext().getThread();
        Object stopLock = rubyThread.stopLock;
        
        synchronized (stopLock) {
            rubyThread.pollThreadEvents();
            try {
                rubyThread.isStopped = true;
                // attempt to decriticalize all if we're the critical thread
                receiver.getRuntime().getThreadService().setCritical(false);
                
                stopLock.wait();
            } catch (InterruptedException ie) {
                rubyThread.pollThreadEvents();
            }
            rubyThread.isStopped = false;
        }
        
        return receiver.getRuntime().getNil();
    }
    
    @JRubyMethod(name = "kill", required = 1, frame = true, meta = true)
    public static IRubyObject kill(IRubyObject receiver, IRubyObject rubyThread, Block block) {
        if (!(rubyThread instanceof RubyThread)) throw receiver.getRuntime().newTypeError(rubyThread, receiver.getRuntime().getThread());
        return ((RubyThread)rubyThread).kill();
    }
    
    @JRubyMethod(name = "exit", frame = true, meta = true)
    public static IRubyObject s_exit(IRubyObject receiver, Block block) {
        RubyThread rubyThread = receiver.getRuntime().getThreadService().getCurrentContext().getThread();
        
        rubyThread.killed = true;
        // attempt to decriticalize all if we're the critical thread
        receiver.getRuntime().getThreadService().setCritical(false);
        
        throw new ThreadKill();
    }

    @JRubyMethod(name = "stop?")
    public RubyBoolean stop_p() {
    	// not valid for "dead" state
    	return getRuntime().newBoolean(isStopped);
    }
    
    @JRubyMethod(name = "wakeup")
    public RubyThread wakeup() {
    	synchronized (stopLock) {
    		stopLock.notifyAll();
    	}
    	
    	return this;
    }
    
    @JRubyMethod(name = "priority")
    public RubyFixnum priority() {
        return priority;
    }

    @JRubyMethod(name = "priority=", required = 1)
    public IRubyObject priority_set(IRubyObject priority) {
        // FIXME: This should probably do some translation from Ruby priority levels to Java priority levels (until we have green threads)
        int iPriority = RubyNumeric.fix2int(priority);
        
        if (iPriority < Thread.MIN_PRIORITY) {
            iPriority = Thread.MIN_PRIORITY;
        } else if (iPriority > Thread.MAX_PRIORITY) {
            iPriority = Thread.MAX_PRIORITY;
        }
        
        this.priority = RubyFixnum.newFixnum(getRuntime(), iPriority);
        
        if (threadImpl.isAlive()) {
            threadImpl.setPriority(iPriority);
        }
        return this.priority;
    }

    @JRubyMethod(name = "raise", optional = 2, frame = true)
    public IRubyObject raise(IRubyObject[] args, Block block) {
        ensureNotCurrent();
        Ruby runtime = getRuntime();
        
        if (DEBUG) System.out.println("thread " + Thread.currentThread() + " before raising");
        RubyThread currentThread = getRuntime().getCurrentContext().getThread();
        try {
            while (!(currentThread.lock.tryLock() && this.lock.tryLock())) {
                if (currentThread.lock.isHeldByCurrentThread()) currentThread.lock.unlock();
            }

            currentThread.pollThreadEvents();
            if (DEBUG) System.out.println("thread " + Thread.currentThread() + " raising");
            receivedException = prepareRaiseException(runtime, args, block);
            
            // If the target thread is sleeping or stopped, wake it
            synchronized (stopLock) {
                stopLock.notify();
            }

            // interrupt the target thread in case it's blocking or waiting
            // WARNING: We no longer interrupt the target thread, since this usually means
            // interrupting IO and with NIO that means the channel is no longer usable.
            // We either need a new way to handle waking a target thread that's waiting
            // on IO, or we need to accept that we can't wake such threads and must wait
            // for them to complete their operation.
            //threadImpl.interrupt();
            
            // new interrupt, to hopefully wake it out of any blocking IO
            this.interrupt();
        } finally {
            if (currentThread.lock.isHeldByCurrentThread()) currentThread.lock.unlock();
            if (this.lock.isHeldByCurrentThread()) this.lock.unlock();
        }

        return this;
    }

    private IRubyObject prepareRaiseException(Ruby runtime, IRubyObject[] args, Block block) {
        if(args.length == 0) {
            IRubyObject lastException = runtime.getGlobalVariables().get("$!");
            if(lastException.isNil()) {
                return new RaiseException(runtime, runtime.fastGetClass("RuntimeError"), "", false).getException();
            } 
            return lastException;
        }

        IRubyObject exception;
        ThreadContext context = getRuntime().getCurrentContext();
        
        if(args.length == 1) {
            if(args[0] instanceof RubyString) {
                return runtime.fastGetClass("RuntimeError").newInstance(context, args, block);
            }
            
            if(!args[0].respondsTo("exception")) {
                return runtime.newTypeError("exception class/object expected").getException();
            }
            exception = args[0].callMethod(context, "exception");
        } else {
            if (!args[0].respondsTo("exception")) {
                return runtime.newTypeError("exception class/object expected").getException();
            }
            
            exception = args[0].callMethod(context, "exception", args[1]);
        }
        
        if (!runtime.getException().isInstance(exception)) {
            return runtime.newTypeError("exception object expected").getException();
        }
        
        if (args.length == 3) {
            ((RubyException) exception).set_backtrace(args[2]);
        }
        
        return exception;
    }
    
    @JRubyMethod(name = "run")
    public IRubyObject run() {
        // if stopped, unstop
        synchronized (stopLock) {
            if (isStopped) {
                isStopped = false;
                stopLock.notifyAll();
            }
        }
    	
    	return this;
    }
    
    public void sleep(long millis) throws InterruptedException {
        ensureCurrent(getRuntime().getCurrentContext());
        synchronized (stopLock) {
            pollThreadEvents();
            try {
                isStopped = true;
                stopLock.wait(millis);
            } finally {
                isStopped = false;
                pollThreadEvents();
            }
        }
    }

    @JRubyMethod(name = "status")
    public IRubyObject status() {
        if (threadImpl.isAlive()) {
            if (isStopped || currentSelector != null && currentSelector.isOpen()) {
            	return getRuntime().newString("sleep");
            } else if (killed) {
                return getRuntime().newString("aborting");
            }
        	
            return getRuntime().newString("run");
        } else if (exitingException != null) {
            return getRuntime().getNil();
        } else {
            return getRuntime().newBoolean(false);
        }
    }

    @JRubyMethod(name = {"kill", "exit", "terminate"})
    public IRubyObject kill() {
    	// need to reexamine this
        RubyThread currentThread = getRuntime().getCurrentContext().getThread();
        
        try {
            if (DEBUG) System.out.println("thread " + Thread.currentThread() + " trying to kill");
            while (!(currentThread.lock.tryLock() && this.lock.tryLock())) {
                if (currentThread.lock.isHeldByCurrentThread()) currentThread.lock.unlock();
            }

            currentThread.pollThreadEvents();

            if (DEBUG) System.out.println("thread " + Thread.currentThread() + " succeeded with kill");
            killed = true;
            
            // If the target thread is sleeping or stopped, wake it
            synchronized (stopLock) {
                stopLock.notify();
            }

            // interrupt the target thread in case it's blocking or waiting
            // WARNING: We no longer interrupt the target thread, since this usually means
            // interrupting IO and with NIO that means the channel is no longer usable.
            // We either need a new way to handle waking a target thread that's waiting
            // on IO, or we need to accept that we can't wake such threads and must wait
            // for them to complete their operation.
            //threadImpl.interrupt();
            
            // new interrupt, to hopefully wake it out of any blocking IO
            this.interrupt();
        } finally {
            if (currentThread.lock.isHeldByCurrentThread()) currentThread.lock.unlock();
            if (this.lock.isHeldByCurrentThread()) this.lock.unlock();
        }
        
        try {
            threadImpl.join();
        } catch (InterruptedException ie) {
            // we were interrupted, check thread events again
            currentThread.pollThreadEvents();
        } catch (ExecutionException ie) {
            // we were interrupted, check thread events again
            currentThread.pollThreadEvents();
        }
        
        return this;
    }
    
    @JRubyMethod(name = {"kill!", "exit!", "terminate!"})
    public IRubyObject kill_bang() {
        throw getRuntime().newNotImplementedError("Thread#kill!, exit!, and terminate! are not safe and not supported");
    }
    
    @JRubyMethod(name = "safe_level")
    public IRubyObject safe_level() {
        throw getRuntime().newNotImplementedError("Thread-specific SAFE levels are not supported");
    }

    private boolean isCurrent() {
        return threadImpl.isCurrent();
    }

    public void exceptionRaised(RaiseException exception) {
        assert isCurrent();

        RubyException rubyException = exception.getException();
        Ruby runtime = rubyException.getRuntime();
        if (runtime.fastGetClass("SystemExit").isInstance(rubyException)) {
            threadService.getMainThread().raise(new IRubyObject[] {rubyException}, Block.NULL_BLOCK);
        } else if (abortOnException(runtime)) {
            // FIXME: printError explodes on some nullpointer
            //getRuntime().getRuntime().printError(exception.getException());
            RubyException systemExit = RubySystemExit.newInstance(runtime, 1);
            systemExit.message = rubyException.message;
            threadService.getMainThread().raise(new IRubyObject[] {systemExit}, Block.NULL_BLOCK);
            return;
        } else if (runtime.getDebug().isTrue()) {
            runtime.printError(exception.getException());
        }
        exitingException = exception;
    }

    private boolean abortOnException(Ruby runtime) {
        return (runtime.isGlobalAbortOnExceptionEnabled() || abortOnException);
    }

    public static RubyThread mainThread(IRubyObject receiver) {
        return receiver.getRuntime().getThreadService().getMainThread();
    }
    
    private Selector currentSelector;
    
    public boolean selectForAccept(RubyIO io) {
        Channel channel = io.getChannel();
        
        if (channel instanceof SelectableChannel) {
            SelectableChannel selectable = (SelectableChannel)channel;
            
            try {
                currentSelector = selectable.provider().openSelector();
            
                SelectionKey key = selectable.register(currentSelector, SelectionKey.OP_ACCEPT);

                int result = currentSelector.select();

                if (result == 1) {
                    Set<SelectionKey> keySet = currentSelector.selectedKeys();

                    if (keySet.iterator().next() == key) {
                        return true;
                    }
                }

                return false;
            } catch (IOException ioe) {
                throw io.getRuntime().newRuntimeError("Error with selector: " + ioe);
            } finally {
                if (currentSelector != null) {
                    try {
                        currentSelector.close();
                    } catch (IOException ioe) {
                        throw io.getRuntime().newRuntimeError("Could not close selector");
                    }
                }
                currentSelector = null;
            }
        } else {
            // can't select, just have to do a blocking call
            return true;
        }
    }
    
    public void interrupt() {
        if (currentSelector != null) {
            currentSelector.wakeup();
        }
    }
    
    public void beforeBlockingCall() {
        isStopped = true;
    }
    
    public void afterBlockingCall() {
        isStopped = false;
    }
}
