require 'test/minirunit'
test_check "Test Range"

values = []
(5..10).each do |i| values.push i end
test_equal([5,6,7,8,9,10], values)

values = []
for i in 1 .. 3
  values << i
end
test_equal([1, 2, 3], values)

# Parser once choked on the range below
def testBreakWithInterval
  break unless 1..2
end

test_equal(1, (1..4).begin)
test_equal(4, (1..4).end)
test_equal(4.8, (4.8..7.2).begin)
test_equal(7.2, (4.8..7.2).end)

test_ok((1..4).include?(2.2))
test_ok((4.8..7.2).include?(5.5))
test_ok(!(4.8..7.2).include?(4))
test_ok(!(4.8..7.2).include?(7.3))
