require File.dirname(__FILE__) + "/../spec_helper"

import "java_integration.fixtures.CoreTypeMethods"
import "java_integration.fixtures.JavaFields"
import "java_integration.fixtures.ValueReceivingInterface"
import "java_integration.fixtures.ValueReceivingInterfaceHandler"

describe "Java String and primitive-typed methods" do
  it "should coerce to Ruby types when returned" do 
    CoreTypeMethods.getString.should be_kind_of(String)
    CoreTypeMethods.getString.should == "foo";
    
    CoreTypeMethods.getByte.should be_kind_of(Fixnum)
    CoreTypeMethods.getByte.should == 1
    CoreTypeMethods.getShort.should be_kind_of(Fixnum)
    CoreTypeMethods.getShort.should == 2
    CoreTypeMethods.getChar.should be_kind_of(Fixnum)
    CoreTypeMethods.getChar.should == 2
    CoreTypeMethods.getInt.should be_kind_of(Fixnum)
    CoreTypeMethods.getInt.should == 4
    CoreTypeMethods.getLong.should be_kind_of(Fixnum)
    CoreTypeMethods.getLong.should == 8
    
    CoreTypeMethods.getFloat.should be_kind_of(Float)
    CoreTypeMethods.getFloat.should == 4.5
    CoreTypeMethods.getDouble.should be_kind_of(Float)
    CoreTypeMethods.getDouble.should == 8.5
    
    CoreTypeMethods.getBooleanTrue.should be_kind_of(TrueClass)
    CoreTypeMethods.getBooleanTrue.should == true
    CoreTypeMethods.getBooleanFalse.should be_kind_of(FalseClass)
    CoreTypeMethods.getBooleanFalse.should == false
    
    CoreTypeMethods.getNull.should be_kind_of(NilClass)
    CoreTypeMethods.getNull.should == nil
    
    CoreTypeMethods.getVoid.should == nil
  end
  
  it "should be coerced from Ruby types when passing parameters" do
    CoreTypeMethods.setString("string").should == "string"
    
    CoreTypeMethods.setByte(1).should == "1"
    CoreTypeMethods.setShort(1).should == "1"
    CoreTypeMethods.setChar(1).should == "\001"
    CoreTypeMethods.setInt(1).should == "1"
    CoreTypeMethods.setLong(1).should == "1"
    
    CoreTypeMethods.setFloat(1.5).should == "1.5"
    CoreTypeMethods.setDouble(1.5).should == "1.5"
    
    CoreTypeMethods.setBooleanTrue(true).should == "true"
    CoreTypeMethods.setBooleanFalse(false).should == "false"
    
    CoreTypeMethods.setNull(nil).should == "null"
  end
  
  it "should raise errors when passed values can not be precisely coerced" do
    pending("precision failure does not raise error") do
      lambda { CoreTypeMethods.setByte(1 << 8) }.should raise_error(TypeError)
      lambda { CoreTypeMethods.setShort(1 << 16) }.should raise_error(TypeError)
      lambda { CoreTypeMethods.setChar(1 << 16) }.should raise_error(TypeError)
      lambda { CoreTypeMethods.setInt(1 << 32) }.should raise_error(TypeError)
      lambda { CoreTypeMethods.setLong(1 << 64) }.should raise_error(TypeError)
    end
  end
  
  it "should select the most narrow and precise overloaded method" do
    pending "selection based on precision is not supported yet" do
      CoreTypeMethods.getType(1).should == "byte"
      CoreTypeMethods.getType(1 << 8).should == "short"
      CoreTypeMethods.getType(1 << 16).should == "int"
      CoreTypeMethods.getType(1.0).should == "float"
    end
    CoreTypeMethods.getType(1 << 32).should == "long"
    
    CoreTypeMethods.getType(2.0 ** 128).should == "double"
    
    CoreTypeMethods.getType("foo").should == "String"
    pending "passing null to overloaded methods randomly selects from them" do
      CoreTypeMethods.getType(nil).should == "CharSequence"
    end
  end
end

describe "Java Object-typed methods" do
  it "coerce primitive Ruby types to their default boxed Java type" do
    CoreTypeMethods.getObjectType("foo").should == "class java.lang.String"
    CoreTypeMethods.getObjectType(1).should == "class java.lang.Long"
    CoreTypeMethods.getObjectType(1.0).should == "class java.lang.Double"
    CoreTypeMethods.getObjectType(true).should == "class java.lang.Boolean"
    CoreTypeMethods.getObjectType(1 << 128).should == "class java.math.BigInteger"
  end
end

describe "Java String and primitive-typed fields" do
  it "coerce to Ruby types when retrieved" do
    # static
    JavaFields.stringStaticField.should be_kind_of(String)
    JavaFields.stringStaticField.should == "foo";
    
    JavaFields.byteStaticField.should be_kind_of(Fixnum)
    JavaFields.byteStaticField.should == 1
    JavaFields.shortStaticField.should be_kind_of(Fixnum)
    JavaFields.shortStaticField.should == 2
    JavaFields.charStaticField.should be_kind_of(Fixnum)
    JavaFields.charStaticField.should == 2
    JavaFields.intStaticField.should be_kind_of(Fixnum)
    JavaFields.intStaticField.should == 4
    JavaFields.longStaticField.should be_kind_of(Fixnum)
    JavaFields.longStaticField.should == 8
    
    JavaFields.floatStaticField.should be_kind_of(Float)
    JavaFields.floatStaticField.should == 4.5
    JavaFields.doubleStaticField.should be_kind_of(Float)
    JavaFields.doubleStaticField.should == 8.5
    
    JavaFields.trueStaticField.should be_kind_of(TrueClass)
    JavaFields.trueStaticField.should == true
    JavaFields.falseStaticField.should be_kind_of(FalseClass)
    JavaFields.falseStaticField.should == false
    
    JavaFields.nullStaticField.should be_kind_of(NilClass)
    JavaFields.nullStaticField.should == nil
    
    # instance
    jf = JavaFields.new
    jf.stringField.should be_kind_of(String)
    jf.stringField.should == "foo";
    
    jf.byteField.should be_kind_of(Fixnum)
    jf.byteField.should == 1
    jf.shortField.should be_kind_of(Fixnum)
    jf.shortField.should == 2
    jf.charField.should be_kind_of(Fixnum)
    jf.charField.should == 2
    jf.intField.should be_kind_of(Fixnum)
    jf.intField.should == 4
    jf.longField.should be_kind_of(Fixnum)
    jf.longField.should == 8
    
    jf.floatField.should be_kind_of(Float)
    jf.floatField.should == 4.5
    jf.doubleField.should be_kind_of(Float)
    jf.doubleField.should == 8.5
    
    jf.trueField.should be_kind_of(TrueClass)
    jf.trueField.should == true
    jf.falseField.should be_kind_of(FalseClass)
    jf.falseField.should == false
    
    jf.nullField.should be_kind_of(NilClass)
    jf.nullField.should == nil
  end
end

describe "Java primitive-box-typed fields" do
  it "coerce to Ruby types when retrieved" do
    # static
    JavaFields.byteObjStaticField.should be_kind_of(Fixnum)
    JavaFields.byteObjStaticField.should == 1
    JavaFields.shortObjStaticField.should be_kind_of(Fixnum)
    JavaFields.shortObjStaticField.should == 2
    JavaFields.charObjStaticField.should be_kind_of(Fixnum)
    JavaFields.charObjStaticField.should == 2
    JavaFields.intObjStaticField.should be_kind_of(Fixnum)
    JavaFields.intObjStaticField.should == 4
    JavaFields.longObjStaticField.should be_kind_of(Fixnum)
    JavaFields.longObjStaticField.should == 8
    
    JavaFields.floatObjStaticField.should be_kind_of(Float)
    JavaFields.floatObjStaticField.should == 4.5
    JavaFields.doubleObjStaticField.should be_kind_of(Float)
    JavaFields.doubleObjStaticField.should == 8.5
    
    JavaFields.trueObjStaticField.should be_kind_of(TrueClass)
    JavaFields.trueObjStaticField.should == true
    JavaFields.falseObjStaticField.should be_kind_of(FalseClass)
    JavaFields.falseObjStaticField.should == false
    
    # instance
    jf = JavaFields.new
    jf.byteObjField.should be_kind_of(Fixnum)
    jf.byteObjField.should == 1
    jf.shortObjField.should be_kind_of(Fixnum)
    jf.shortObjField.should == 2
    jf.charObjField.should be_kind_of(Fixnum)
    jf.charObjField.should == 2
    jf.intObjField.should be_kind_of(Fixnum)
    jf.intObjField.should == 4
    jf.longObjField.should be_kind_of(Fixnum)
    jf.longObjField.should == 8
    
    jf.floatObjField.should be_kind_of(Float)
    jf.floatObjField.should == 4.5
    jf.doubleObjField.should be_kind_of(Float)
    jf.doubleObjField.should == 8.5
    
    jf.trueObjField.should be_kind_of(TrueClass)
    jf.trueObjField.should == true
    jf.falseObjField.should be_kind_of(FalseClass)
    jf.falseObjField.should == false
  end
end

describe "Java String, primitive, and object-typed interface methods" do
  it "should coerce or wrap to usable Ruby types for the implementer" do
    impl = Class.new {
      attr_accessor :result
      include ValueReceivingInterface
      
      def receiveObject(obj)
        self.result = obj
        obj
      end
      
      %w[String Byte Short Char Int Long Float Double Null True False].each do |type|
        alias_method "receive#{type}".intern, :receiveObject
      end
    }
    
    vri = impl.new
    vri_handler = ValueReceivingInterfaceHandler.new(vri);
    
    obj = java.lang.Object.new
    vri_handler.receiveObject(obj).should == obj
    vri.result.should == obj
    vri.result.class.should == java.lang.Object
    
    obj = "foo"
    vri_handler.receiveString(obj).should == obj
    vri.result.should == obj
    vri.result.class.should == String
    
    obj = 1
    
    vri_handler.receiveByte(obj).should == obj
    vri.result.should == obj
    vri.result.class.should == Fixnum
    
    vri_handler.receiveShort(obj).should == obj
    vri.result.should == obj
    vri.result.class.should == Fixnum
    
    pending "char appears to be getting signed/unsigned-garbled" do
      vri_handler.receiveChar(obj).should == obj
      vri.result.should == obj
      vri.result.class.should == Fixnum
    end
    
    vri_handler.receiveInt(obj).should == obj
    vri.result.should == obj
    vri.result.class.should == Fixnum
    
    vri_handler.receiveLong(obj).should == obj
    vri.result.should == obj
    vri.result.class.should == Fixnum
    
    vri_handler.receiveFloat(obj).should == obj
    vri.result.should == obj
    vri.result.class.should == Float
    
    vri_handler.receiveDouble(obj).should == obj
    vri.result.should == obj
    vri.result.class.should == Float
    
    vri_handler.receiveNull(nil).should == nil
    vri.result.should == nil
    vri.result.class.should == NilClass
    
    vri_handler.receiveTrue(true).should == true
    vri.result.should == true
    vri.result.class.should == TrueClass
    
    vri_handler.receiveFalse(false).should == false
    vri.result.should == false
    vri.result.class.should == FalseClass
  end
end

describe "Java primitive-box-typed interface methods" do
  it "should coerce to Ruby types for the implementer" do
    pending "We do not coerce numerics to boxed Java numerics (yet?)" do
      impl = Class.new {
        attr_accessor :result
        include ValueReceivingInterface

        def receiveByte(obj)
          self.result = obj
          obj
        end

        %w[Short Char Int Long Float Double True False].each do |type|
          alias_method "receive#{type}".intern, :receiveByte
        end
      }

      vri = impl.new
      vri_handler = ValueReceivingInterfaceHandler.new(vri);

      obj = 1

      vri_handler.receiveByteObj(obj).should == obj
      vri.result.should == obj
      vri.result.class.should == Fixnum

      vri_handler.receiveShortObj(obj).should == obj
      vri.result.should == obj
      vri.result.class.should == Fixnum

      vri_handler.receiveCharObj(obj).should == obj
      vri.result.should == obj
      vri.result.class.should == Fixnum

      vri_handler.receiveIntObj(obj).should == obj
      vri.result.should == obj
      vri.result.class.should == Fixnum

      vri_handler.receiveLongObj(obj).should == obj
      vri.result.should == obj
      vri.result.class.should == Fixnum

      vri_handler.receiveFloatObj(obj).should == obj
      vri.result.should == obj
      vri.result.class.should == Float

      vri_handler.receiveDoubleObj(obj).should == obj
      vri.result.should == obj
      vri.result.class.should == Float

      vri_handler.receiveTrueObj(true).should == true
      vri.result.should == true
      vri.result.class.should == TrueClass

      vri_handler.receiveFalseObj(false).should == false
      vri.result.should == false
      vri.result.class.should == FalseClass
    end
  end
end