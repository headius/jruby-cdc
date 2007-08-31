require 'test/unit'

class FlipTest < Test::Unit::TestCase
  # flip (taken from http://redhanded.hobix.com/inspect/hopscotchingArraysWithFlipFlops.html)
  def test_skip_one
    s = true
    a = (1..10).reject { true if (s = !s) .. (s) }
    assert_equal([1, 3, 5, 7, 9], a)
  end
  
  def test_skip_two
    s = true
    a = (1..10).reject { true if (s = !s) .. (s = !s) }
    assert_equal([1, 4, 7, 10], a)
  end
  
  def test_big_flip
    s = true;
    a = (1..10).inject([]) do |ary, v|
      ary << [] unless (s = !s) .. (s = !s)
      ary.last << v
      ary
    end
    assert_equal([[1, 2, 3], [4, 5, 6], [7, 8, 9], [10]], a)
  end
  
  def test_big_tripledot_flip
    s = true
    a = (1..64).inject([]) do |ary, v|
        unless (s ^= v[2].zero?)...(s ^= !v[1].zero?)
            ary << []
        end
        ary.last << v
        ary
    end
    expected = [[1, 2, 3, 4, 5, 6, 7, 8],
          [9, 10, 11, 12, 13, 14, 15, 16],
          [17, 18, 19, 20, 21, 22, 23, 24],
          [25, 26, 27, 28, 29, 30, 31, 32],
          [33, 34, 35, 36, 37, 38, 39, 40],
          [41, 42, 43, 44, 45, 46, 47, 48],
          [49, 50, 51, 52, 53, 54, 55, 56],
          [57, 58, 59, 60, 61, 62, 63, 64]]
    assert_equal(expected, a)
  end
end