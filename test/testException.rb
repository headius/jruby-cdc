require 'minirunit'
#test backtrace
if !(defined? $recurse)
test_check "Test Exception:"
	$recurse = false
end

begin
  if $recurse
	raise Exception, 'test'
  else
	$recurse=true
	load('test/testException.rb')
  end
rescue Exception => boom
  result =  boom.backtrace.collect {|trace| 
	res = trace.index(':')
	res = res.succ
	resend = trace.index(':',res)
	if resend
	  trace[res, resend-res].to_i  #return value from block
	else
	  trace[res, trace.length].to_i  #return value from block
	end
  }
  test_equal([10,13,13] , result.slice(0..2))
  #  p boom.backtrace
  #p result
end
unless $recurse
  Java::import "org.jruby.test"
  begin
	TestHelper:throwException
  rescue NativeException
	test_ok(true)
  end
end
