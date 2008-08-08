require File.dirname(__FILE__) + "/../spec_helper"

import "java_integration.fixtures.SingleMethodInterface"
import "java_integration.fixtures.UsesSingleMethodInterface"
import "java_integration.fixtures.DescendantOfSingleMethodInterface"
import "java_integration.fixtures.UsesDescendantOfSingleMethodInterface"
import "java_integration.fixtures.BeanLikeInterface"
import "java_integration.fixtures.BeanLikeInterfaceHandler"

describe "Single-method Java interfaces implemented in Ruby" do
  before :all do
    @value_holder1 = Class.new do
      include SingleMethodInterface
      def initialize(val)
        @value = val
      end
      def callIt
        @value
      end
    end

    @value_holder2 = Class.new do
      include SingleMethodInterface
      def initialize(val)
        @value = val
      end
      def call_it
        @value
      end
    end
  end
  
  it "should be kind_of? the interface" do
    @value_holder1.new(1).should be_kind_of(SingleMethodInterface)
    SingleMethodInterface.should === @value_holder1.new(1)
  end

  it "should be implemented with 'include InterfaceClass'" do
    UsesSingleMethodInterface.callIt(@value_holder1.new(1)).should == 1
    UsesSingleMethodInterface.callIt(@value_holder2.new(1)).should == 1
  end

  it "should be cast-able to the interface on the Java side" do
    UsesSingleMethodInterface.castAndCallIt(@value_holder1.new(2)).should == 2
    UsesSingleMethodInterface.castAndCallIt(@value_holder2.new(2)).should == 2
  end
  
  it "should allow implementation using the underscored version" do
    UsesSingleMethodInterface.callIt(@value_holder2.new(3)).should == 3
  end
  
  it "should allow reopening implementations" do
    @value_holder3 = Class.new do
      include SingleMethodInterface
      def initialize(val)
        @value = val
      end
      def callIt
        @value
      end
    end
    obj = @value_holder3.new(4);
    UsesSingleMethodInterface.callIt(obj).should == 4
    @value_holder3.class_eval do
      def callIt
        @value + @value
      end
    end
    UsesSingleMethodInterface.callIt(obj).should == 8
    
    @value_holder3 = Class.new do
      include SingleMethodInterface
      def initialize(val)
        @value = val
      end
      def call_it
        @value
      end
    end
    obj = @value_holder3.new(4);
    UsesSingleMethodInterface.callIt(obj).should == 4
    @value_holder3.class_eval do
      def call_it
        @value + @value
      end
    end
    UsesSingleMethodInterface.callIt(obj).should == 8
  end
end

describe "Single-method Java interfaces" do
  it "can be coerced from a block passed to a constructor" do
    UsesSingleMethodInterface.new { 1 }.result.should == 1
    UsesSingleMethodInterface.new(nil) { 1 }.result.should == 1
    UsesSingleMethodInterface.new(nil, nil) { 1 }.result.should == 1
    UsesSingleMethodInterface.new(nil, nil, nil) { 1 }.result.should == 1
    # 3 normal args is our cutoff for specific-arity optz, so test four
    UsesSingleMethodInterface.new(nil, nil, nil, nil) { 1 }.result.should == 1
  end
  
  it "can be coerced from a block passed to a static method" do
    UsesSingleMethodInterface.callIt { 1 }.should == 1
    UsesSingleMethodInterface.callIt(nil) { 1 }.should == 1
    UsesSingleMethodInterface.callIt(nil, nil) { 1 }.should == 1
    UsesSingleMethodInterface.callIt(nil, nil, nil) { 1 }.should == 1
    # 3 normal args is our cutoff for specific-arity optz, so test four
    UsesSingleMethodInterface.callIt(nil, nil, nil, nil) { 1 }.should == 1
  end
  
  it "can be coerced from a block passed to a instance method" do
    UsesSingleMethodInterface.new.callIt2 do 1 end.should == 1
    UsesSingleMethodInterface.new.callIt2(nil) do 1 end.should == 1
    UsesSingleMethodInterface.new.callIt2(nil, nil) do 1 end.should == 1
    UsesSingleMethodInterface.new.callIt2(nil, nil, nil) do 1 end.should == 1
    # 3 normal args is our cutoff for specific-arity optz, so test four
    UsesSingleMethodInterface.new.callIt2(nil, nil, nil, nil) do 1 end.should == 1
  end
  
  it "should be implementable with .impl" do
    impl = SingleMethodInterface.impl {|name| name}
    impl.should be_kind_of(SingleMethodInterface)
    SingleMethodInterface.should === impl
    
    UsesSingleMethodInterface.callIt(impl).should == :callIt
  end
end

describe "A bean-like Java interface" do
  it "allows implementation with attr* methods" do
    myimpl1 = Class.new do
      include BeanLikeInterface
      attr_accessor :value, :my_value, :foo, :my_foo
    end
    myimpl2 = Class.new do
      include BeanLikeInterface
      attr_accessor :value, :myValue, :foo, :myFoo
    end
    [myimpl1, myimpl2].each do |impl|
      bli = impl.new
      blih = BeanLikeInterfaceHandler.new(bli)
      lambda do
        blih.setValue(1)
        blih.setMyValue(2)
        blih.setFoo(true)
        blih.setMyFoo(true)
        blih.getValue().should == 1
        blih.getMyValue().should == 2
        blih.isFoo().should == true
        blih.isMyFoo().should == true
      end.should_not raise_error
    end
  end
  
  it "allows implementing boolean methods with ? names" do
    myimpl1 = Class.new do
      include BeanLikeInterface
      def foo?
        true
      end
      def my_foo?
        true
      end
      def friendly?
        true
      end
      def supah_friendly?
        true
      end
    end
    myimpl2 = Class.new do
      include BeanLikeInterface
      def foo?
        true
      end
      def myFoo?
        true
      end
      def friendly?
        true
      end
      def supahFriendly?
        true
      end
    end
    [myimpl1, myimpl2].each do |impl|
      bli = impl.new
      blih = BeanLikeInterfaceHandler.new(bli)
      lambda do
        blih.isFoo().should == true
        blih.isMyFoo().should == true
        blih.friendly().should == true
        blih.supahFriendly().should == true
      end.should_not raise_error
    end
  end
  
  it "does not honor beanified implementations of methods that don't match javabean spec" do
    myimpl1 = Class.new do
      include BeanLikeInterface
      def something_foo(x)
        x
      end
      def something_foo=(x,y)
        y
      end
    end
    myimpl2 = Class.new do
      include BeanLikeInterface
      def somethingFoo(x)
        x
      end
      def somethingFoo=(x,y)
        y
      end
    end
    [myimpl1, myimpl2].each do |impl|
      bli = impl.new
      blih = BeanLikeInterfaceHandler.new(bli)
      lambda { blih.getSomethingFoo(1) }.should raise_error(NameError)
      lambda { blih.setSomethingFoo(1,2) }.should raise_error(NameError)
    end
  end
end

describe "A Ruby class including a descendant interface" do
  it "implements all methods from that interface and parents" do
    impl = Class.new do
      include DescendantOfSingleMethodInterface
      
      def callIt; "foo"; end
      def callThat; "bar"; end
    end
    
    dosmi = impl.new
    
    UsesSingleMethodInterface.callIt(dosmi).should == "foo"
    UsesDescendantOfSingleMethodInterface.callThat(dosmi).should == "bar"
  end
end