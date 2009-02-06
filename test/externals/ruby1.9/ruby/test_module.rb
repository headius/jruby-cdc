require 'test/unit'
require 'pp'
require_relative 'envutil'

$m0 = Module.nesting

class TestModule < Test::Unit::TestCase
  def _wrap_assertion
    yield
  end

  def assert_method_defined?(klass, mid, message="")
    message = build_message(message, "#{klass}\##{mid} expected to be defined.")
    _wrap_assertion do
      klass.method_defined?(mid) or
        raise Test::Unit::AssertionFailedError, message, caller(3)
    end
  end

  def assert_method_not_defined?(klass, mid, message="")
    message = build_message(message, "#{klass}\##{mid} expected to not be defined.")
    _wrap_assertion do
      klass.method_defined?(mid) and
        raise Test::Unit::AssertionFailedError, message, caller(3)
    end
  end

  def setup
    @verbose = $VERBOSE
    $VERBOSE = nil
  end

  def teardown
    $VERBOSE = @verbose
  end

  def test_LT_0
    assert_equal true, String < Object
    assert_equal false, Object < String
    assert_nil String < Array
    assert_equal true, Array < Enumerable
    assert_equal false, Enumerable < Array
    assert_nil Proc < Comparable
    assert_nil Comparable < Proc
  end

  def test_GT_0
    assert_equal false, String > Object
    assert_equal true, Object > String
    assert_nil String > Array
    assert_equal false, Array > Enumerable
    assert_equal true, Enumerable > Array
    assert_nil Comparable > Proc
    assert_nil Proc > Comparable
  end

  def test_CMP_0
    assert_equal -1, (String <=> Object)
    assert_equal 1, (Object <=> String)
    assert_nil(Array <=> String)
  end

  ExpectedException = NoMethodError

  # Support stuff

  def remove_pp_mixins(list)
    list.reject {|c| c == PP::ObjectMixin }
  end

  def remove_json_mixins(list)
    list.reject {|c| c.to_s.start_with?("JSON") }
  end

  module Mixin
    MIXIN = 1
    def mixin
    end
  end

  module User
    USER = 2
    include Mixin
    def user
    end
  end

  module Other
    def other
    end
  end

  class AClass
    def AClass.cm1
      "cm1"
    end
    def AClass.cm2
      cm1 + "cm2" + cm3
    end
    def AClass.cm3
      "cm3"
    end
    
    private_class_method :cm1, "cm3"

    def aClass
    end

    def aClass1
    end

    def aClass2
    end

    private :aClass1
    protected :aClass2
  end

  class BClass < AClass
    def bClass1
    end

    private

    def bClass2
    end

    protected
    def bClass3
    end
  end

  MyClass = AClass.clone
  class MyClass
    public_class_method :cm1
  end

  # -----------------------------------------------------------

  def test_CMP # '<=>'
    assert_equal( 0, Mixin <=> Mixin)
    assert_equal(-1, User <=> Mixin)
    assert_equal( 1, Mixin <=> User)

    assert_equal( 0, Object <=> Object)
    assert_equal(-1, String <=> Object)
    assert_equal( 1, Object <=> String)
  end

  def test_GE # '>='
    assert(Mixin >= User)
    assert(Mixin >= Mixin)
    assert(!(User >= Mixin))

    assert(Object >= String)
    assert(String >= String)
    assert(!(String >= Object))
  end

  def test_GT # '>'
    assert(Mixin   > User)
    assert(!(Mixin > Mixin))
    assert(!(User  > Mixin))

    assert(Object > String)
    assert(!(String > String))
    assert(!(String > Object))
  end

  def test_LE # '<='
    assert(User <= Mixin)
    assert(Mixin <= Mixin)
    assert(!(Mixin <= User))

    assert(String <= Object)
    assert(String <= String)
    assert(!(Object <= String))
  end

  def test_LT # '<'
    assert(User < Mixin)
    assert(!(Mixin < Mixin))
    assert(!(Mixin < User))

    assert(String < Object)
    assert(!(String < String))
    assert(!(Object < String))
  end

  def test_VERY_EQUAL # '==='
    assert(Object === self)
    assert(Test::Unit::TestCase === self)
    assert(TestModule === self)
    assert(!(String === self))
  end

  def test_ancestors
    assert_equal([User, Mixin],      User.ancestors)
    assert_equal([Mixin],            Mixin.ancestors)

    assert_equal([Object, Kernel, BasicObject],
                 remove_json_mixins(remove_pp_mixins(Object.ancestors)))
    assert_equal([String, Comparable, Object, Kernel, BasicObject],
                 remove_json_mixins(remove_pp_mixins(String.ancestors)))
  end

  def test_class_eval
    Other.class_eval("CLASS_EVAL = 1")
    assert_equal(1, Other::CLASS_EVAL)
    assert(Other.constants.include?(:CLASS_EVAL))
  end

  def test_const_defined?
    assert(Math.const_defined?(:PI))
    assert(Math.const_defined?("PI"))
    assert(!Math.const_defined?(:IP))
    assert(!Math.const_defined?("IP"))
  end

  def test_const_get
    assert_equal(Math::PI, Math.const_get("PI"))
    assert_equal(Math::PI, Math.const_get(:PI))
  end

  def test_const_set
    assert(!Other.const_defined?(:KOALA))
    Other.const_set(:KOALA, 99)
    assert(Other.const_defined?(:KOALA))
    assert_equal(99, Other::KOALA)
    Other.const_set("WOMBAT", "Hi")
    assert_equal("Hi", Other::WOMBAT)
  end

  def test_constants
    assert_equal([:MIXIN], Mixin.constants)
    assert_equal([:MIXIN, :USER], User.constants.sort)
  end

  def test_included_modules
    assert_equal([], Mixin.included_modules)
    assert_equal([Mixin], User.included_modules)
    assert_equal([Kernel],
                 remove_json_mixins(remove_pp_mixins(Object.included_modules)))
    assert_equal([Comparable, Kernel],
                 remove_json_mixins(remove_pp_mixins(String.included_modules)))
  end

  def test_instance_methods
    assert_equal([:user], User.instance_methods(false))
    assert_equal([:user, :mixin].sort, User.instance_methods(true).sort)
    assert_equal([:mixin], Mixin.instance_methods)
    assert_equal([:mixin], Mixin.instance_methods(true))
    # Ruby 1.8 feature change:
    # #instance_methods includes protected methods.
    #assert_equal([:aClass], AClass.instance_methods(false))
    assert_equal([:aClass, :aClass2], AClass.instance_methods(false).sort)
    assert_equal([:aClass, :aClass2],
        (AClass.instance_methods(true) - Object.instance_methods(true)).sort)
  end

  def test_method_defined?
    assert_method_not_defined?(User, :wombat)
    assert_method_defined?(User, :user)
    assert_method_defined?(User, :mixin)
    assert_method_not_defined?(User, :wombat)
    assert_method_defined?(User, :user)
    assert_method_defined?(User, :mixin)
  end

  def module_exec_aux
    Proc.new do
      def dynamically_added_method_3; end
    end
  end
  def module_exec_aux_2(&block)
    User.module_exec(&block)
  end

  def test_module_exec
    User.module_exec do
      def dynamically_added_method_1; end
    end
    assert_method_defined?(User, :dynamically_added_method_1)

    block = Proc.new do
      def dynamically_added_method_2; end
    end
    User.module_exec(&block)
    assert_method_defined?(User, :dynamically_added_method_2)

    User.module_exec(&module_exec_aux)
    assert_method_defined?(User, :dynamically_added_method_3)

    module_exec_aux_2 do
      def dynamically_added_method_4; end
    end
    assert_method_defined?(User, :dynamically_added_method_4)
  end

  def test_module_eval
    User.module_eval("MODULE_EVAL = 1")
    assert_equal(1, User::MODULE_EVAL)
    assert(User.constants.include?(:MODULE_EVAL))
    User.instance_eval("remove_const(:MODULE_EVAL)")
    assert(!User.constants.include?(:MODULE_EVAL))
  end

  def test_name
    assert_equal("Fixnum", Fixnum.name)
    assert_equal("TestModule::Mixin",  Mixin.name)
    assert_equal("TestModule::User",   User.name)
  end

  def test_private_class_method
    assert_raise(ExpectedException) { AClass.cm1 }
    assert_raise(ExpectedException) { AClass.cm3 }
    assert_equal("cm1cm2cm3", AClass.cm2)
  end

  def test_private_instance_methods
    assert_equal([:aClass1], AClass.private_instance_methods(false))
    assert_equal([:bClass2], BClass.private_instance_methods(false))
    assert_equal([:aClass1, :bClass2],
        (BClass.private_instance_methods(true) -
         Object.private_instance_methods(true)).sort)
  end

  def test_protected_instance_methods
    assert_equal([:aClass2], AClass.protected_instance_methods)
    assert_equal([:bClass3], BClass.protected_instance_methods(false))
    assert_equal([:bClass3, :aClass2].sort,
                 (BClass.protected_instance_methods(true) -
                  Object.protected_instance_methods(true)).sort)
  end

  def test_public_class_method
    assert_equal("cm1",       MyClass.cm1)
    assert_equal("cm1cm2cm3", MyClass.cm2)
    assert_raise(ExpectedException) { eval "MyClass.cm3" }
  end

  def test_public_instance_methods
    assert_equal([:aClass],  AClass.public_instance_methods(false))
    assert_equal([:bClass1], BClass.public_instance_methods(false))
  end

  def test_s_constants
    c1 = Module.constants
    Object.module_eval "WALTER = 99"
    c2 = Module.constants
    assert_equal([:WALTER], c2 - c1)
  end

  module M1
    $m1 = Module.nesting
    module M2
      $m2 = Module.nesting
    end
  end

  def test_s_nesting
    assert_equal([],                               $m0)
    assert_equal([TestModule::M1, TestModule],     $m1)
    assert_equal([TestModule::M1::M2,
                  TestModule::M1, TestModule],     $m2)
  end

  def test_s_new
    m = Module.new
    assert_instance_of(Module, m)
  end

  def test_freeze
    m = Module.new
    m.freeze
    assert_raise(RuntimeError) do
      m.module_eval do
        def foo; end
      end
    end
  end

  def test_attr_obsoleted_flag
    c = Class.new
    c.class_eval do
      def initialize
        @foo = :foo
        @bar = :bar
      end
      attr :foo, true
      attr :bar, false
    end
    o = c.new
    assert_equal(true, o.respond_to?(:foo))
    assert_equal(true, o.respond_to?(:foo=))
    assert_equal(true, o.respond_to?(:bar))
    assert_equal(false, o.respond_to?(:bar=))
  end

  def test_const_get2
    c1 = Class.new
    c2 = Class.new(c1)

    eval("c1::Foo = :foo")
    assert_equal(:foo, c1::Foo)
    assert_equal(:foo, c2::Foo)
    assert_equal(:foo, c2.const_get(:Foo))
    assert_raise(NameError) { c2.const_get(:Foo, false) }

    eval("c1::Foo = :foo")
    assert_raise(NameError) { c1::Bar }
    assert_raise(NameError) { c2::Bar }
    assert_raise(NameError) { c2.const_get(:Bar) }
    assert_raise(NameError) { c2.const_get(:Bar, false) }

    c1.instance_eval do
      def const_missing(x)
        x
      end
    end

    assert_equal(:Bar, c1::Bar)
    assert_equal(:Bar, c2::Bar)
    assert_equal(:Bar, c2.const_get(:Bar))
    assert_equal(:Bar, c2.const_get(:Bar, false))

    assert_raise(NameError) { c1.const_get(:foo) }
  end

  def test_const_set2
    c1 = Class.new
    assert_raise(NameError) { c1.const_set(:foo, :foo) }
  end

  def test_const_get3
    c1 = Class.new
    assert_raise(NameError) { c1.const_defined?(:foo) }
  end

  def test_class_variable_get
    c = Class.new
    c.class_eval { @@foo = :foo }
    assert_equal(:foo, c.class_variable_get(:@@foo))
    assert_raise(NameError) { c.class_variable_get(:@@bar) } # c.f. instance_variable_get
    assert_raise(NameError) { c.class_variable_get(:foo) }
  end

  def test_class_variable_set
    c = Class.new
    c.class_variable_set(:@@foo, :foo)
    assert_equal(:foo, c.class_eval { @@foo })
    assert_raise(NameError) { c.class_variable_set(:foo, 1) }
  end

  def test_class_variable_defined
    c = Class.new
    c.class_eval { @@foo = :foo }
    assert_equal(true, c.class_variable_defined?(:@@foo))
    assert_equal(false, c.class_variable_defined?(:@@bar))
    assert_raise(NameError) { c.class_variable_defined?(:foo) }
  end

  def test_remove_class_variable
    c = Class.new
    c.class_eval { @@foo = :foo }
    c.class_eval { remove_class_variable(:@@foo) }
    assert_equal(false, c.class_variable_defined?(:@@foo))
  end

  def test_export_method
    m = Module.new
    assert_raise(NameError) do
      m.instance_eval { public(:foo) }
    end
  end

  def test_attr
    assert_in_out_err([], <<-INPUT, %w(:ok nil), /warning: private attribute\?$/)
      $VERBOSE = true
      c = Class.new
      c.instance_eval do
        private
        attr_reader :foo
      end
      o = c.new
      o.foo rescue p(:ok)
      p(o.instance_eval { foo })
    INPUT

    c = Class.new
    assert_raise(NameError) do
      c.instance_eval { attr_reader :"$" }
    end
  end

  def test_undef
    assert_raise(SecurityError) do
      Thread.new do
        $SAFE = 4
        Class.instance_eval { undef_method(:foo) }
      end.join
    end

    c = Class.new
    assert_raise(NameError) do
      c.instance_eval { undef_method(:foo) }
    end

    m = Module.new
    assert_raise(NameError) do
      m.instance_eval { undef_method(:foo) }
    end

    o = Object.new
    assert_raise(NameError) do
      class << o; self; end.instance_eval { undef_method(:foo) }
    end

    %w(object_id __send__ initialize).each do |m|
      assert_in_out_err([], <<-INPUT, [], /warning: undefining `#{m}' may cause serious problem$/)
        $VERBOSE = false
        Class.new.instance_eval { undef_method(:#{m}) }
      INPUT
    end
  end

  def test_alias
    m = Module.new
    assert_raise(NameError) do
      m.class_eval { alias foo bar }
    end

    assert_in_out_err([], <<-INPUT, %w(2), /warning: discarding old foo$/)
      $VERBOSE = true
      c = Class.new
      c.class_eval do
        def foo; 1; end
        def bar; 2; end
      end
      c.class_eval { alias foo bar }
      p c.new.foo
    INPUT
  end

  def test_mod_constants
    m = Module.new
    m.const_set(:Foo, :foo)
    assert_equal([:Foo], m.constants(true))
    assert_equal([:Foo], m.constants(false))
    m.instance_eval { remove_const(:Foo) }
  end

  def test_frozen_class
    m = Module.new
    m.freeze
    assert_raise(RuntimeError) do
      m.instance_eval { undef_method(:foo) }
    end

    c = Class.new
    c.freeze
    assert_raise(RuntimeError) do
      c.instance_eval { undef_method(:foo) }
    end

    o = Object.new
    c = class << o; self; end
    c.freeze
    assert_raise(RuntimeError) do
      c.instance_eval { undef_method(:foo) }
    end
  end

  def test_method_defined
    c = Class.new
    c.class_eval do
      def foo; end
      def bar; end
      def baz; end
      public :foo
      protected :bar
      private :baz
    end

    assert_equal(true, c.public_method_defined?(:foo))
    assert_equal(false, c.public_method_defined?(:bar))
    assert_equal(false, c.public_method_defined?(:baz))

    assert_equal(false, c.protected_method_defined?(:foo))
    assert_equal(true, c.protected_method_defined?(:bar))
    assert_equal(false, c.protected_method_defined?(:baz))

    assert_equal(false, c.private_method_defined?(:foo))
    assert_equal(false, c.private_method_defined?(:bar))
    assert_equal(true, c.private_method_defined?(:baz))
  end

  def test_change_visibility_under_safe4
    c = Class.new
    c.class_eval do
      def foo; end
    end
    assert_raise(SecurityError) do
      Thread.new do
        $SAFE = 4
        c.class_eval { private :foo }
      end.join
    end
  end

  def test_top_public_private
    assert_in_out_err([], <<-INPUT, %w([:foo] [:bar]), [])
      private
      def foo; :foo; end
      public
      def bar; :bar; end
      p self.private_methods.grep(/^foo$|^bar$/)
      p self.methods.grep(/^foo$|^bar$/)
    INPUT
  end

  def test_append_features
    t = nil
    m = Module.new
    m.module_eval do
      def foo; :foo; end
    end
    class << m; self; end.class_eval do
      define_method(:append_features) do |mod|
        t = mod
        super(mod)
      end
    end

    m2 = Module.new
    m2.module_eval { include(m) }
    assert_equal(m2, t)

    o = Object.new
    o.extend(m2)
    assert_equal(true, o.respond_to?(:foo))
  end

  def test_append_features_raise
    m = Module.new
    m.module_eval do
      def foo; :foo; end
    end
    class << m; self; end.class_eval do
      define_method(:append_features) {|mod| raise }
    end

    m2 = Module.new
    assert_raise(RuntimeError) do
      m2.module_eval { include(m) }
    end

    o = Object.new
    o.extend(m2)
    assert_equal(false, o.respond_to?(:foo))
  end

  def test_append_features_type_error
    assert_raise(TypeError) do
      Module.new.instance_eval { append_features(1) }
    end
  end

  def test_included
    m = Module.new
    m.module_eval do
      def foo; :foo; end
    end
    class << m; self; end.class_eval do
      define_method(:included) {|mod| raise }
    end

    m2 = Module.new
    assert_raise(RuntimeError) do
      m2.module_eval { include(m) }
    end

    o = Object.new
    o.extend(m2)
    assert_equal(true, o.respond_to?(:foo))
  end

  def test_cyclic_include
    m1 = Module.new
    m2 = Module.new
    m1.instance_eval { include(m2) }
    assert_raise(ArgumentError) do
      m2.instance_eval { include(m1) }
    end
  end

  def test_include_p
    m = Module.new
    c1 = Class.new
    c1.instance_eval { include(m) }
    c2 = Class.new(c1)
    assert_equal(true, c1.include?(m))
    assert_equal(true, c2.include?(m))
    assert_equal(false, m.include?(m))
  end

  def test_include_under_safe4
    m = Module.new
    c1 = Class.new
    assert_raise(SecurityError) do
      lambda {
        $SAFE = 4
        c1.instance_eval { include(m) }
      }.call
    end
    assert_nothing_raised do
      lambda {
        $SAFE = 4
        c2 = Class.new
        c2.instance_eval { include(m) }
      }.call
    end
  end
end
