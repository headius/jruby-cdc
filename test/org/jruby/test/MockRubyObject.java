package org.jruby.test;

import org.jruby.IRuby;
import org.jruby.RubyClass;
import org.jruby.RubyObject;
import org.jruby.runtime.Block;
import org.jruby.runtime.builtin.IRubyObject;

public class MockRubyObject extends RubyObject {

	private final IRuby runtime;

	private static class TestMeta extends RubyClass {

		protected TestMeta(IRuby runtime) {
            // This null doesn't feel right
			super(runtime, runtime.getObject(), null);
		}
	}
	
	public MockRubyObject(IRuby runtime) {
		super(runtime, new TestMeta(runtime));
		this.runtime = runtime;
	}
	
	public IRuby getRuntime() {
		return runtime;
	}
	
	public static void throwException(IRubyObject recv, Block block) {
		throw new RuntimeException("x");
	}

}
