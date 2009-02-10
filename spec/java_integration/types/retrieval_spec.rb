require File.dirname(__FILE__) + "/../spec_helper"

import "java_integration.fixtures.ClassWithEnums"

describe "Java::JavaClass.for_name" do
  it "should return primitive classes for Java primitive type names" do
    Java::JavaClass.for_name("byte").should == Java::byte.java_class
    Java::JavaClass.for_name("boolean").should == Java::boolean.java_class
    Java::JavaClass.for_name("short").should == Java::short.java_class
    Java::JavaClass.for_name("char").should == Java::char.java_class
    Java::JavaClass.for_name("int").should == Java::int.java_class
    Java::JavaClass.for_name("long").should == Java::long.java_class
    Java::JavaClass.for_name("float").should == Java::float.java_class
    Java::JavaClass.for_name("double").should == Java::double.java_class
  end
end

describe "Java classes with nested enums" do
  it "should allow access to the values() method on the enum" do
    ClassWithEnums::Enums.values.map{|e|e.to_s}.should == ["A", "B", "C"];
  end
end