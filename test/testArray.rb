require 'test/minirunit'
test_check "Test Array:"
arr = ["zero", "first"]

arr.unshift "second", "third"
test_equal(["second", "third", "zero", "first"], arr)
test_equal(["first"], arr[-1..-1])
test_equal(["first"], arr[3..3])
test_equal([], arr[3..2])
test_equal(nil, arr[3..1])
test_ok(["third", "zero", "first"] == arr[1..4])
test_ok('["third", "zero", "first"]' == arr[1..4].inspect)

arr << "fourth"

test_ok("fourth" == arr.pop());
test_ok("second" == arr.shift());

test_ok(Array == ["zero", "first"].type)
test_ok("Array" == Array.to_s)
if defined? Java
#  Java::import "org.jruby.test"
#  array = TestHelper::createArray(4)
#  array.each {		# this should not generate an exception
#    |test|
#    true
#  }
#  test_equal(array.length,  4)
end

arr = [1, 2, 3]
arr2 = arr.dup
arr2.reverse!
test_equal([1,2,3], arr)
test_equal([3,2,1], arr2)

test_equal([1,2,3], [1,2,3,1,2,3,1,1,1,2,3,2,1].uniq)

test_equal([1,2,3,4], [[[1], 2], [3, [4]]].flatten)
test_equal(nil, [].flatten!)

arr = []
arr << [[[arr]]]
test_exception(ArgumentError) {
  arr.flatten
}
#test_ok(! arr.empty?, "flatten() shouldn't destroy the list")
#test_exception(ArgumentError) {
#  arr.flatten!
#}

#arr = []
#test_equal([1,2], arr.push(1, 2))
#test_exception(ArgumentError) { arr.push() }
