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
 * Copyright (C) 2005 Thomas E. Enebo <enebo@acm.org>
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

import java.util.WeakHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.atomic.AtomicInteger;
import org.jruby.Ruby;
import org.jruby.util.collections.WeakHashSet;
import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.RubyModule;
import org.jruby.management.MethodCache;

/**
 * This class represents mappings between methods that have been cached and the classes which
 * have cached them.  Methods within RubyModule will update this cacheMap as needed.  Here is
 * a list of scenarios when cached methods will become invalid:
 * 
 *  1. Redefine a method in a base class
 *  2. Add an alias in a superclass that is the same name as a cached method in a base class
 *  3. Include a module that has a same-named method as one already caches in a base class
 *  4. Remove a method definition
 *  5. Add a same-named method in super class that has been cached in a super class
 *  
 * Concurrency is another concern with managing this structure.  Rather than synchronize this
 * we are going to rely on synchronization further upstream.  RubyModule methods that directly
 * call this is responsible for synchronization.
 */
public class CacheMap {
    private AtomicInteger addCount = new AtomicInteger(0);
    private AtomicInteger removeCount = new AtomicInteger(0);
    private AtomicInteger moduleIncludeCount = new AtomicInteger(0);
    private AtomicInteger moduleTriggeredRemoveCount = new AtomicInteger(0);
    private AtomicInteger flushTriggeredRemoveCount = new AtomicInteger(0);
    private AtomicInteger flushCount = new AtomicInteger(0);
    
    public CacheMap(Ruby ruby) {
        ruby.getBeanManager().register(new MethodCache(this));
    }
    
    public interface CacheSite {
        public void removeCachedMethod();
    }
    private final Map<DynamicMethod, Set<CacheSite>> mappings = new WeakHashMap<DynamicMethod, Set<CacheSite>>();
    
    public int getAddCount() {
        return addCount.get();
    }
    
    public int getRemoveCount() {
        return removeCount.get();
    }
    
    public int getModuleIncludeCount() {
        return moduleIncludeCount.get();
    }
    
    public int getModuleTriggeredRemoveCount() {
        return moduleTriggeredRemoveCount.get();
    }
    
    public int getFlushCount() {
        return flushCount.get();
    }
    
    public synchronized void flush() {
        // track total removed to add to remove count
        int totalRemoved = 0;
        
        for (DynamicMethod method : mappings.keySet()) {
            Set<CacheSite> cacheSites  = mappings.get(method);
            if (cacheSites == null) continue;
            
            for(CacheSite site : cacheSites) {
                totalRemoved++;
                site.removeCachedMethod();
            }
        }
        mappings.clear();
        
        flushTriggeredRemoveCount.addAndGet(totalRemoved);
        removeCount.addAndGet(totalRemoved);
        flushCount.incrementAndGet();
    }
    
    /**
     * Add another class to the list of classes which are caching the method.
     *
     * @param method which is cached
     * @param module which is caching method
     */
    public synchronized void add(DynamicMethod method, CacheSite site) {
        Set<CacheSite> siteList = mappings.get(method);
        
        if (siteList == null) {
            siteList = new WeakHashSet<CacheSite>();
            mappings.put(method, siteList);
        }

        siteList.add(site);
        
        addCount.incrementAndGet();
    }
    
    /**
     * Remove all method caches associated with the provided method.
     * 
     * @param method to remove all caches of
     */
    public synchronized void remove(DynamicMethod method) {
        Set<CacheSite> siteList = mappings.remove(method);
        
        // Removed method has never been used so it has not been cached
        if (siteList == null) {
            return;
        }
        for(Iterator<CacheSite> iter = siteList.iterator(); iter.hasNext();) {
            CacheSite site = iter.next();
            if (site != null) {
                site.removeCachedMethod();
            }
        }
        
        removeCount.incrementAndGet();
    }
    
    /**
     * Remove method caches for all methods in a module 
     */
    public synchronized void moduleIncluded(RubyModule targetModule, RubyModule includedModule) {
        // track total removed to add to remove count
        int totalRemoved = 0;
        
        for (String methodName : includedModule.getMethods().keySet()) {

            for(RubyModule current = targetModule; current != null; current = current.getSuperClass()) {
                if (current == includedModule) continue;
                DynamicMethod method = current.getMethods().get(methodName);
                if (method != null) {
                    Set<CacheSite> adapters = mappings.remove(method);
                    if (adapters != null) {
                        for(CacheSite adapter : adapters) {
                            totalRemoved++;
                            adapter.removeCachedMethod();
                        }
                    }
                }
            }
        }
        
        moduleTriggeredRemoveCount.addAndGet(totalRemoved);
        removeCount.addAndGet(totalRemoved);
        moduleIncludeCount.incrementAndGet();
    }
}
