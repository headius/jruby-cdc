package org.jruby.javasupport;

import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyFixnum;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

public class ArrayJavaAddons {
    @JRubyMethod
    public static IRubyObject copy_data(
            ThreadContext context, IRubyObject rubyArray, IRubyObject javaArray,
            IRubyObject fillValue) {
        JavaArray javaArrayJavaObj = (JavaArray)javaArray.dataGetStruct();
        Object fillJavaObject = null;
        int javaLength = (int)javaArrayJavaObj.length().getLongValue();
        Class targetType = javaArrayJavaObj.getComponentType();
        JavaUtil.RubyConverter converter = JavaUtil.getArrayConverter(targetType);
        
        if (!fillValue.isNil()) {
            fillJavaObject = converter.convert(context, fillValue);
        }
        
        RubyArray array = null;
        int rubyLength;
        if (rubyArray instanceof RubyArray) {
            array = (RubyArray)rubyArray;
            rubyLength = ((RubyArray)rubyArray).getLength();
        } else {
            rubyLength = 0;
            fillJavaObject = converter.convert(context, rubyArray);
        }
        
        int i = 0;
        for (; i < rubyLength && i < javaLength; i++) {
            javaArrayJavaObj.setWithExceptionHandling(i, converter.convert(context, array.entry(i)));
        }
        
        if (i < javaLength && fillJavaObject != null) {
            javaArrayJavaObj.fillWithExceptionHandling(i, javaLength, fillJavaObject);
        }
        
        return javaArray;
    }
    
    @JRubyMethod
    public static IRubyObject copy_data_simple(
            ThreadContext context, IRubyObject rubyArray, IRubyObject javaArray) {
        JavaArray javaArrayJavaObj = (JavaArray)javaArray.dataGetStruct();
        int javaLength = (int)javaArrayJavaObj.length().getLongValue();
        Class targetType = javaArrayJavaObj.getComponentType();
        JavaUtil.RubyConverter converter = JavaUtil.getArrayConverter(targetType);
        
        RubyArray array = null;
        int rubyLength;
        array = (RubyArray)rubyArray;
        rubyLength = ((RubyArray)rubyArray).getLength();
        
        int i = 0;
        for (; i < rubyLength && i < javaLength; i++) {
            javaArrayJavaObj.setWithExceptionHandling(i, converter.convert(context, array.entry(i)));
        }
        
        return javaArray;
    }
    
    @JRubyMethod
    public static IRubyObject dimensions(ThreadContext context, IRubyObject maybeArray) {
        Ruby runtime = context.getRuntime();
        if (!(maybeArray instanceof RubyArray)) {
            return runtime.newEmptyArray();
        }
        RubyArray rubyArray = (RubyArray)maybeArray;
        RubyArray dims = runtime.newEmptyArray();
        
        return dimsRecurse(context, rubyArray, dims, 0);
    }
    
    @JRubyMethod
    public static IRubyObject dimensions(ThreadContext context, IRubyObject maybeArray, IRubyObject dims) {
        Ruby runtime = context.getRuntime();
        if (!(maybeArray instanceof RubyArray)) {
            return runtime.newEmptyArray();
        }
        assert dims instanceof RubyArray;
        
        RubyArray rubyArray = (RubyArray)maybeArray;
        
        return dimsRecurse(context, rubyArray, (RubyArray)dims, 0);
    }
    
    @JRubyMethod
    public static IRubyObject dimensions(ThreadContext context, IRubyObject maybeArray, IRubyObject dims, IRubyObject index) {
        Ruby runtime = context.getRuntime();
        if (!(maybeArray instanceof RubyArray)) {
            return runtime.newEmptyArray();
        }
        assert dims instanceof RubyArray;
        assert index instanceof RubyFixnum;
        
        RubyArray rubyArray = (RubyArray)maybeArray;
        
        return dimsRecurse(context, rubyArray, (RubyArray)dims, (int)((RubyFixnum)index).getLongValue());
    }
    
    private static RubyArray dimsRecurse(ThreadContext context, RubyArray rubyArray, RubyArray dims, int index) {
        Ruby runtime = context.getRuntime();

        while (dims.size() <= index) {
            dims.append(RubyFixnum.zero(runtime));
        }
        
        if (rubyArray.size() > ((RubyFixnum)dims.eltInternal(index)).getLongValue()) {
            dims.eltInternalSet(index, RubyFixnum.newFixnum(runtime, rubyArray.size()));
        }
        
        for (int i = 0; i < rubyArray.size(); i++) {
            if (rubyArray.eltInternal(i) instanceof RubyArray) {
                dimsRecurse(context, (RubyArray)rubyArray.eltInternal(i), dims, 1);
            }
        }
        
        return dims;
    }
}
