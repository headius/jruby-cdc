require 'test/unit'
require 'bigdecimal'

class TestArray < Test::Unit::TestCase

  def test_no_singleton_methods_on_bigdecimal
    num = BigDecimal.new("0.001")
    assert_raise(TypeError) { class << num ; def amethod ; end ; end }
    assert_raise(TypeError) { def num.amethod ; end }
  end
  
  def test_can_instantiate_big_decimal
    assert_nothing_raised {BigDecimal.new("4")}
    assert_nothing_raised {BigDecimal.new("3.14159")}
  end
  
  def test_can_implicitly_instantiate_big_decimal
    # JRUBY-153 issues
    assert_nothing_raised {BigDecimal("4")}
    assert_nothing_raised {BigDecimal("3.14159")}
  end

  def test_reject_arguments_not_responding_to_to_str
    assert_raise(TypeError) { BigDecimal.new(4) }
    assert_raise(TypeError) { BigDecimal.new(3.14159) }
    assert_raise(TypeError) { BigDecimal(4) }
    assert_raise(TypeError) { BigDecimal(3.14159) }
  end

  def test_alphabetic_args_return_zero
    assert_equal( BigDecimal("0.0"), BigDecimal("XXX"),
                  'Big Decimal objects instanitiated with a value that starts
                  with a letter should have a value of 0.0' )
  end
  
  class X
    def to_str; "3.14159" end
  end
  
  def test_can_accept_arbitrary_objects_as_arguments
    # as log as the object has a #to_str method...
    x = X.new
    assert_nothing_raised { BigDecimal.new(x) }
    assert_nothing_raised { BigDecimal(x) }
  end
  
  require "bigdecimal/newton"
  include Newton
  
  class Function
    def initialize()
      @zero = BigDecimal::new("0.0")
      @one  = BigDecimal::new("1.0")
      @two  = BigDecimal::new("2.0")
      @ten  = BigDecimal::new("10.0")
      @eps  = BigDecimal::new("1.0e-16")
    end
    def zero;@zero;end
    def one ;@one ;end
    def two ;@two ;end
    def ten ;@ten ;end
    def eps ;@eps ;end
    def values(x) # <= defines functions solved
      f = []
      f1 = x[0]*x[0] + x[1]*x[1] - @two # f1 = x**2 + y**2 - 2 => 0
      f2 = x[0] - x[1]                  # f2 = x    - y        => 0
      f <<= f1
      f <<= f2
      f
    end
  end
  
  def test_newton_extension
    f = BigDecimal::limit(100)
    f = Function.new
    x = [f.zero,f.zero]      # Initial values
    n = nlsolve(f,x)
    expected = [BigDecimal('0.1000000000262923315461642086010446338567975310185638386446002778855192224707966221794469725479649528E1'),
                BigDecimal('0.1000000000262923315461642086010446338567975310185638386446002778855192224707966221794469725479649528E1')]
    assert_equal expected, x
  end
  
  require "bigdecimal/math.rb"
  include BigMath
  
  def test_math_extension
    expected = BigDecimal('0.31415926535897932384626433832795028841971693993751058209749445923078164062862089986280348253421170679821480865132823066453462141417033006060218E1')
    # this test fails under C Ruby
    # ruby 1.8.6 (2007-03-13 patchlevel 0) [i686-darwin8.9.1]
    assert_equal expected, PI(100)
    
    one = BigDecimal("1")
    
    assert_equal one * 1, one
    assert_equal one / 1, one
    assert_equal one + 1, BigDecimal("2")
    assert_equal one - 1, BigDecimal("0")
    
    assert_equal 1*one, one
    assert_equal 1/one, one
    assert_equal 1+one, BigDecimal("2")
    assert_equal 1-one, BigDecimal("0")
    
    assert_equal one * 1.0, 1.0
    assert_equal one / 1.0, 1.0
    assert_equal one + 1.0, 2.0
    assert_equal one - 1.0, 0.0
    
    assert_equal 1.0*one, 1.0
    assert_equal 1.0/one, 1.0
    assert_equal 1.0+one, 2.0
    assert_equal 1.0-one, 0.0
    
    assert_equal("1.0", BigDecimal.new('1.0').to_s('F'))
    assert_equal("0.0", BigDecimal.new('0.0').to_s)
    
    assert_equal(BigDecimal("2"), BigDecimal("1.5").round)
    assert_equal(BigDecimal("15"), BigDecimal("15").round)
    assert_equal(BigDecimal("20"), BigDecimal("15").round(-1))
    assert_equal(BigDecimal("0"), BigDecimal("15").round(-2))
    assert_equal(BigDecimal("-10"), BigDecimal("-15").round(-1, BigDecimal::ROUND_CEILING))
    assert_equal(BigDecimal("10"), BigDecimal("15").round(-1, BigDecimal::ROUND_HALF_DOWN))
    assert_equal(BigDecimal("20"), BigDecimal("25").round(-1, BigDecimal::ROUND_HALF_EVEN))
    assert_equal(BigDecimal("15.99"), BigDecimal("15.993").round(2))
    
    assert_equal(BigDecimal("1"), BigDecimal("1.8").round(0, BigDecimal::ROUND_DOWN))
    assert_equal(BigDecimal("2"), BigDecimal("1.2").round(0, BigDecimal::ROUND_UP))
    assert_equal(BigDecimal("-1"), BigDecimal("-1.5").round(0, BigDecimal::ROUND_CEILING))
    assert_equal(BigDecimal("-2"), BigDecimal("-1.5").round(0, BigDecimal::ROUND_FLOOR))
    assert_equal(BigDecimal("-2"), BigDecimal("-1.5").round(0, BigDecimal::ROUND_FLOOR))
    assert_equal(BigDecimal("1"), BigDecimal("1.5").round(0, BigDecimal::ROUND_HALF_DOWN))
    assert_equal(BigDecimal("2"), BigDecimal("1.5").round(0, BigDecimal::ROUND_HALF_EVEN))
    assert_equal(BigDecimal("2"), BigDecimal("2.5").round(0, BigDecimal::ROUND_HALF_EVEN))
  end
    
  def test_big_decimal_power
    n = BigDecimal("10")
    assert_equal(n.power(0), BigDecimal("1"))
    assert_equal(n.power(1), n)
    assert_equal(n.power(2), BigDecimal("100"))
    assert_equal(n.power(-1), BigDecimal("0.1"))
    assert_raises(TypeError) { n.power(1.1) }
  end
end
