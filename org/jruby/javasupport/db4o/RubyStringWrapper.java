package org.jruby.javasupport.db4o;

import java.util.*;

import org.jruby.*;

public class RubyStringWrapper extends RubyObjectWrapper {
	public String value;
	
	public RubyStringWrapper() {
	}
	
	public RubyStringWrapper(RubyString rubyObject) {
		super(rubyObject);
		
		this.value = rubyObject.getValue();
	}
	
	public RubyString createRubyString(Ruby ruby) {
	    RubyClass type = ruby.getRubyClass(typeName);
	    
	    RubyString newString = RubyString.newString(ruby, value);
	    newString.setRubyClass(type);
	    
	    Iterator iter = instanceVariables.values().iterator();
	    while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry)iter.next();
            
	    	newString.setInstanceVar((String)entry.getKey(), (RubyObject)entry.getValue());
        }
	    
	    return newString;
	}
}