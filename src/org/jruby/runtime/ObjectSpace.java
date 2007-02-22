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
 * Copyright (C) 2002-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004-2006 Charles O Nutter <headius@headius.com>
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
package org.jruby.runtime;

import org.jruby.RubyModule;
import org.jruby.RubyProc;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.WeakIdentityHashMap;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * FIXME: This version is faster than the previous, but both suffer from a
 * crucial flaw: It is impossible to create an ObjectSpace with an iterator
 * that doesn't either: a. hold on to objects that might otherwise be collected
 * or b. have no way to guarantee that a call to hasNext() will be correct or
 * that a subsequent call to next() will produce an object. For our purposes,
 * for now, this may be acceptable.
 */
public class ObjectSpace {
    private ReferenceQueue deadReferences = new ReferenceQueue();
    private WeakReferenceListNode top;

    private ReferenceQueue deadIdentityReferences = new ReferenceQueue();
    private final Map identities = new HashMap();
    private final Map identitiesByObject = new WeakIdentityHashMap();

    private long maxId = 4; // Highest reserved id

    public long idOf(IRubyObject rubyObject) {
        synchronized (identities) {
            Long longId = (Long) identitiesByObject.get(rubyObject);
            if (longId == null) {
                longId = createId(rubyObject);
            }
            return longId.longValue();
        }
    }

    private Long createId(IRubyObject object) {
        cleanIdentities();
        maxId += 2; // id must always be even
        Long longMaxId = new Long(maxId);
        identities.put(longMaxId, new IdReference(object, maxId, deadIdentityReferences));
        identitiesByObject.put(object, longMaxId);
        return longMaxId;
    }
    
    public IRubyObject id2ref(long id) {
        synchronized (identities) {
            cleanIdentities();
            IdReference reference = (IdReference) identities.get(new Long(id));
            if (reference == null)
                return null;
            return (IRubyObject) reference.get();
        }
    }

    private void cleanIdentities() {
        IdReference ref;
        while ((ref = (IdReference) deadIdentityReferences.poll()) != null)
            identities.remove(new Long(ref.id()));
    }

    private Map finalizers         = new HashMap();
    private Map weakRefs           = new HashMap();
    private List finalizersToRun   = new ArrayList();
    private Thread finalizerThread = new FinalizerThread();

    {
        finalizerThread.start();
    }

    private class FinalizerThread extends Thread {
        public FinalizerThread() {
            super("Ruby Finalizer Thread");
            setDaemon(true);
        }
        public void run() {
            while(true) {
                try {
                    synchronized(finalizersToRun) {
                        while(finalizersToRun.isEmpty()) {
                            try {
                                finalizersToRun.wait();
                            } catch(InterruptedException e) {
                            }
                        }
                        while(!finalizersToRun.isEmpty()) {
                            ((Runnable)finalizersToRun.remove(0)).run();
                        }
                        finalizersToRun.notify();
                    }
                } catch(Exception e) {
                    //Swallow, since there's no useful action to take here.
                }
            }
        }
    }


    private class FinalizerEntry implements Runnable {
        private long id;
        private RubyProc proc;
        private IRubyObject fid;
        public FinalizerEntry(IRubyObject obj, long id, RubyProc proc) {
            this.id = id;
            this.proc = proc;
            this.fid = proc.getRuntime().newFixnum(id);
            synchronized(ObjectSpace.this) {
                FinalizerWeakReferenceListNode node = new FinalizerWeakReferenceListNode(obj,deadReferences,top,this);
                Long key = new Long(id);
                List refl = (List)weakRefs.get(key);
                if(null == refl) {
                    refl = new ArrayList();
                    weakRefs.put(key,refl);
                }
                refl.add(node);
                top = node;
            }
        }

        public void finalize(FinalizerWeakReferenceListNode obj) {
            synchronized(ObjectSpace.this) {
                List refl = (List)weakRefs.get(new Long(id));
                refl.remove(obj);
                obj.setRan();
            }
            _finalize();
        }

        public void _finalize() {
            synchronized(finalizersToRun) {
                finalizersToRun.add(this);
                finalizersToRun.notifyAll();
            }
        }

        public void run() {
            proc.call(new IRubyObject[]{fid});
        }
    }

    public void finishFinalizers() {
        for(Iterator iter = finalizers.keySet().iterator();iter.hasNext();) {
            Object key = iter.next();
            for(Iterator iter2 = ((List)finalizers.get(key)).iterator();iter2.hasNext();) {
                ((FinalizerEntry)iter2.next())._finalize();
            }
        } 
        synchronized(finalizersToRun) {
            while(!finalizersToRun.isEmpty()) {
                finalizersToRun.notify();
                try {
                    finalizersToRun.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } 
    }

    public synchronized void addFinalizer(IRubyObject obj, long id, RubyProc proc) {
        List fins = (List)finalizers.get(new Long(id));
        if(fins == null) {
            fins = new ArrayList();
            finalizers.put(new Long(id),fins);
        }
        fins.add(new FinalizerEntry(obj,id,proc));
    }    

    public synchronized void removeFinalizers(long id) {
        finalizers.remove(new Long(id));
        List refl = (List)weakRefs.get(new Long(id));
        if(null != refl) {
            for(Iterator iter = refl.iterator();iter.hasNext();) {
                ((FinalizerWeakReferenceListNode)(iter.next())).setRan();
            }
        }
        weakRefs.remove(new Long(id));
    }

    public synchronized void add(IRubyObject object) {
        cleanup();
        top = new WeakReferenceListNode(object, deadReferences, top);
    }

    public synchronized Iterator iterator(RubyModule rubyClass) {
    	final List objList = new ArrayList();
    	WeakReferenceListNode current = top;
    	while (current != null) {
    		IRubyObject obj = (IRubyObject)current.get();
    	    if (obj != null && obj.isKindOf(rubyClass)) {
    	    	objList.add(current);
    	    }
    	    
    	    current = current.next;
    	}
    	
        return new Iterator() {
        	private Iterator iter = objList.iterator();
        	
			public boolean hasNext() {
			    throw new UnsupportedOperationException();
			}

			public Object next() {
                Object obj = null;
                while (iter.hasNext()) {
                    WeakReferenceListNode node = (WeakReferenceListNode)iter.next();

                    if(node instanceof FinalizerWeakReferenceListNode) {
                        continue;
                    }

                    obj = node.get();
                    
                    if (obj != null) break;
                }
				return obj;
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
        };
    }

    private synchronized void cleanup() {
        WeakReferenceListNode reference;
        while ((reference = (WeakReferenceListNode)deadReferences.poll()) != null) {
            reference.remove();
        }
    }

    private class WeakReferenceListNode extends WeakReference {
        public WeakReferenceListNode prev;
        public WeakReferenceListNode next;
        public WeakReferenceListNode(Object ref, ReferenceQueue queue, WeakReferenceListNode next) {
            super(ref, queue);

            this.next = next;
            if (next != null) {
                next.prev = this;
            }
        }

        public void remove() {
            synchronized (ObjectSpace.this) {
                if (prev != null) {
                    prev.next = next;
                }
                if (next != null) {
                    next.prev = prev;
                }
            }
        }
    }

    private class FinalizerWeakReferenceListNode extends WeakReferenceListNode {
        private FinalizerEntry entry;
        private boolean ran = false;
        public FinalizerWeakReferenceListNode(Object ref, ReferenceQueue queue, WeakReferenceListNode next, FinalizerEntry entry) {
            super(ref, queue, next);
            this.entry = entry;
        }
        public void remove() {
            super.remove();
            if(!ran) {
                entry.finalize(this);
            }
        }
        public void setRan() {
            this.ran = true;
        }
    }

    private static class IdReference extends WeakReference {
        private final long id;

        public IdReference(IRubyObject object, long id, ReferenceQueue queue) {
            super(object, queue);
            this.id = id;
        }

        public long id() {
            return id;
        }
    }
}
