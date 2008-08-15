package org.jruby.management;

import java.lang.ref.SoftReference;

import org.jruby.runtime.CacheMap;
import org.jruby.runtime.CallSite;

public class MethodCache implements MethodCacheMBean {
    private final SoftReference<CacheMap> cacheMap;
    
    public MethodCache(CacheMap cacheMap) {
        this.cacheMap = new SoftReference<CacheMap>(cacheMap);
    }
    
    public int getAddCount() {
        return cacheMap.get().getAddCount();
    }
    
    public int getRemoveCount() {
        return cacheMap.get().getRemoveCount();
    }
    
    public int getModuleIncludeCount() {
        return cacheMap.get().getModuleIncludeCount();
    }
    
    public int getModuleTriggeredRemoveCount() {
        return cacheMap.get().getModuleTriggeredRemoveCount();
    }
    
    public int getFlushCount() {
        return cacheMap.get().getFlushCount();
    }
    
    public int getCallSiteCount() {
        return CallSite.InlineCachingCallSite.totalCallSites;
    }
    
    public int getFailedCallSiteCount() {
        return CallSite.InlineCachingCallSite.failedCallSites;
    }
    
    public void flush() {
        cacheMap.get().flush();
    }
}
