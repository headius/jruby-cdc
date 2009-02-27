package org.jruby.management;

import java.lang.ref.SoftReference;
import org.jruby.Ruby;

public class ParserStats implements ParserStatsMBean {
    private final SoftReference<Ruby> ruby;
    private volatile int totalParseTime = 0;
    private volatile int totalParsedBytes = 0;
    private volatile int totalEvalParses = 0;
    private volatile int totalLoadParses = 0;
    private volatile int totalJRubyModuleParses = 0;
    
    public ParserStats(Ruby ruby) {
        this.ruby = new SoftReference<Ruby>(ruby);
    }

    public void addParseTime(int time) {
        totalParseTime += time;
    }

    public void addParsedBytes(int bytes) {
        totalParsedBytes += bytes;
    }

    public void addEvalParse() {
        totalEvalParses++;
    }

    public void addLoadParse() {
        totalLoadParses++;
    }

    public void addJRubyModuleParse() {
        totalJRubyModuleParses++;
    }

    public double getTotalParseTime() {
        Ruby runtime = ruby.get();
        if (runtime == null) return 0;
        return runtime.getParser().getTotalTime() / 1000000000.0;
    }

    public int getTotalParsedBytes() {
        Ruby runtime = ruby.get();
        if (runtime == null) return 0;
        return runtime.getParser().getTotalBytes();
    }

    public double getParseTimePerKB() {
        int totalBytes = getTotalParsedBytes();
        if (totalBytes == 0) return 0;
        return getTotalParseTime() / (totalBytes / 1000.0);
    }

    public int getNumberOfEvalParses() {
        return totalEvalParses;
    }

    public int getNumberOfLoadParses() {
        return totalLoadParses;
    }
}
