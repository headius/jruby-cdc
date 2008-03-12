require 'test/unit'
require 'compiler/duby/compiler'
require 'jruby'

class TestCompiler < Test::Unit::TestCase
  def setup
    @file_builder = Compiler::FileBuilder.new(__FILE__)
  end
  
  def test_class
    node = JRuby.parse "
      class TestClass
        def initialize
        end

        def self.foo
        end
      end
      class TestClass2
        def foo
          @a = 1
        end
      end
    "
    node.compile(@file_builder)
    
    assert_equal(2, @file_builder.class_builders.size)
    class_builder1 = @file_builder.class_builders["TestClass"]
    class_builder2 = @file_builder.class_builders["TestClass2"]
    
    assert_equal("TestClass", class_builder1.class_name)
    assert_equal("TestClass2", class_builder2.class_name)
    
    assert_equal(0, class_builder1.instance_methods.size)
    assert_equal(1, class_builder2.instance_methods.size)
    assert_equal(1, class_builder1.static_methods.size)
    assert_equal(0, class_builder2.static_methods.size)
    assert_not_nil(class_builder1.constructor)
    assert_nil(class_builder2.constructor)
    
    assert_equal(0, class_builder1.fields.size)
    # fields do not get declared until compile phase right now :(
    #assert_equal(1, class_builder2.fields.size)
    #assert_equal("int", class_builder2.fields["a"].type.to_s)
  end
end