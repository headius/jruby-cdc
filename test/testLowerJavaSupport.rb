require 'test/minirunit'
test_check "Low-level Java Support"

if defined? Java


#    test_equal(nil, TestHelper.getNull())

    # Test casting:
    # The instance o's actual class is private, but it's returned as a public
    # interface, which should work.
#    o = TestHelper.getInterfacedInstance()
#    test_equal("stuff done", o.doStuff())

#    o = TestHelper.getLooslyCastedInstance()
#    test_equal("stuff done", o.doStuff())


#    test_exception(NameError) { Java::JavaClass.new }
#    inner_class = Java::JavaClass.for_name("org.jruby.test.TestHelper$SomeImplementation")
#    test_equal("org.jruby.test.TestHelper$SomeImplementation", inner_class.name)

  string_class = Java::JavaClass.for_name("java.lang.String")
  test_equal(string_class, Java::JavaClass.for_name("java.lang.String"))

  test_equal("java.lang.String", string_class.to_s)
  test_exception(NameError) { Java::JavaClass.for_name("not.existing.Class") }
  test_ok(string_class.public?)
  test_ok(string_class.final?)
  test_ok(! string_class.primitive?)
  runnable_class = Java::JavaClass.for_name("java.lang.Runnable")
  test_ok(! runnable_class.final?)
  test_ok(runnable_class.interface?)
  test_ok(! string_class.interface?)
  test_ok(! string_class.array?)

  inner_class = Java::JavaClass.for_name("java.lang.Character$Subset")
  test_equal("java.lang.Character$Subset", inner_class.name)

  object_class = string_class.superclass
  test_equal("java.lang.Object", object_class.name)
  test_equal(nil, object_class.superclass)
  test_ok(string_class.interfaces.include?("java.lang.Comparable"))
  test_ok(string_class.interfaces.include?("java.io.Serializable"))
  test_ok(! string_class.interfaces.include?("java.lang.Object"))

  test_ok(string_class < object_class)
  test_ok(! (string_class > object_class))
  test_ok(object_class > string_class)
  test_ok(! (object_class < string_class))
  test_ok(object_class == object_class)
  test_ok(object_class != string_class)

  string_methods = string_class.java_instance_methods
  test_ok(string_methods.detect {|m| m.name == "charAt" })
  test_ok(string_methods.detect {|m| m.name == "substring"})
  test_ok(string_methods.detect {|m| m.name == "valueOf"}.nil?)
  test_ok(string_methods.all? {|m| not m.static? })

  string_class_methods = string_class.java_class_methods
  test_ok(string_class_methods.detect {|m| m.name == "valueOf" })
  test_ok(string_class_methods.all? {|m| m.static? })

  # Instance variables
  rectangle_class = Java::JavaClass.for_name("java.awt.Rectangle")
  test_ok(rectangle_class.fields.include?("x"))
  test_ok(rectangle_class.fields.include?("y"))
  field = rectangle_class.field(:x)
  test_equal("int", field.value_type)
  test_equal("x", field.name)
  test_ok(field.public?)
  test_ok(! field.static?)
  test_ok(! field.final?)
  integer_ten = Java.primitive_to_java(10)
  integer_two = Java.primitive_to_java(2)
  constructor = rectangle_class.constructor(:int, :int, :int, :int)
  rectangle = constructor.new_instance(integer_two,
                                       integer_ten,
                                       integer_two,
                                       integer_two)
  value = field.value(rectangle)
  test_equal("java.lang.Integer", value.java_type)
  test_equal(2, Java.java_to_primitive(value))
  field.set_value(rectangle, integer_ten)
  value = field.value(rectangle)
  test_equal("java.lang.Integer", value.java_type)
  test_equal(10, Java.java_to_primitive(value))
  test_exception(TypeError) {
    field.set_value(rectangle, Java.primitive_to_java("hello"))
  }

  # Class variables
  integer_class = Java::JavaClass.for_name("java.lang.Integer")
  test_ok(integer_class.fields.include?("MAX_VALUE"))
  field = integer_class.field(:MAX_VALUE)
  test_ok(field.final?)
  test_ok(field.static?)
  test_equal(2147483647, Java.java_to_primitive(field.static_value))

  method = string_class.java_method(:toString)
  test_ok(method.kind_of?(Java::JavaMethod))
  test_equal("toString", method.name)
  test_equal(0, method.arity)
  test_ok(method.public?)
  method = string_class.java_method("equals", "java.lang.Object")
  test_equal("equals", method.name)
  test_equal(1, method.arity)
  test_ok(! method.final?)

  random_class = Java::JavaClass.for_name("java.util.Random")
  method = random_class.java_method(:nextInt)

  constructors = random_class.constructors
  test_equal(2, constructors.length)
  test_equal([0, 1], constructors.collect {|c| c.arity }.sort)
  constructor = random_class.constructor(:long)
  test_equal(1, constructor.arity)
  random = constructor.new_instance(Java.primitive_to_java(2002))
  result = method.invoke(random)
  test_equal("java.lang.Integer", result.java_type)
  result = Java.java_to_primitive(result)
  test_ok(result.kind_of?(Fixnum))

  # Converting primitives
  java_string = random_class.java_method(:toString).invoke(random)
  test_equal("java.lang.String", java_string.java_type)
  test_ok(Java.java_to_primitive(java_string).kind_of?(String))
  test_ok(Java.java_to_primitive(random).kind_of?(JavaObject))
  test_ok(Java.primitive_to_java(random) == random)
  test_ok(Java.primitive_to_java("hello").kind_of?(JavaObject))

  # Putting and getting objects back
  integer_zero = Java.primitive_to_java(0)
  arraylist_class = Java::JavaClass.for_name("java.util.ArrayList")
  list = arraylist_class.constructor().new_instance()
  add_method = arraylist_class.java_method(:add, "java.lang.Object")
  add_method.invoke(list, random)
  returned_random = arraylist_class.java_method(:get, "int").invoke(list, integer_zero)
  test_equal("java.util.Random", returned_random.java_type)
  random_class.java_method(:nextInt).invoke(returned_random)

  test_equal("java.lang.Long", Java.primitive_to_java(10).java_type)
  method = random_class.java_method(:nextInt, "int")
  test_equal(["int"], method.argument_types)
  test_exception(TypeError) { method.invoke(random, 10) }
  result = method.invoke(random, Java.primitive_to_java(10))
  test_equal("java.lang.Integer", result.java_type)

  method = string_class.java_method("valueOf", "int")
  test_ok(method.static?)
  result = method.invoke_static(Java.primitive_to_java(101))
  test_equal(string_class.to_s, result.java_type)

  # Control over return types and values
  test_equal("java.lang.String", method.return_type)
  test_equal(nil, string_class.java_method("notifyAll").return_type)
  test_equal(JavaObject,
             method.invoke_static(Java.primitive_to_java(101)).type)

  # Arrays
  array = string_class.new_array(10)
  test_equal(10, array.length)
  string_array_class = Java::JavaClass.for_name(array.java_type)
  test_ok(string_array_class.array?)
  test_equal("[Ljava.lang.String;", string_array_class.name)
  test_ok(string_array_class.constructors.empty?)
  test_ok(array[3].nil?)
  test_exception(ArgumentError) { array[10] }

  # java.lang.reflect.Proxy
  al = "java.awt.event.ActionListener"
  ae = "java.awt.event.ActionEvent"
  action_listener_class = Java::JavaClass.for_name(al)
  action_listener_instance = Java.new_proxy_instance(action_listener_class) do
    |proxy, method, event|

    test_ok(action_listener_instance.java_class == proxy.java_class)
    test_ok(method.instance_of? Java::JavaMethod)
    test_equal("actionPerformed", method.name())

    $callback_invoked = true
  end
  test_ok(action_listener_instance.instance_of? JavaObject)
  instance_class = action_listener_instance.java_class
  proxy_class = Java::JavaClass.for_name("java.lang.reflect.Proxy")
  test_ok(instance_class < action_listener_class)
  test_ok(action_listener_instance.java_class < proxy_class)
  action_performed = action_listener_class.java_method(:actionPerformed, ae)
  $callback_invoked = false
  action_performed.invoke(action_listener_instance, Java.primitive_to_java(nil))
  test_ok($callback_invoked)

  # Primitive Java types
  int_class = Java::JavaClass.for_name("int")
  test_ok(int_class.primitive?)
  boolean_class = Java::JavaClass.for_name("boolean")
  test_ok(boolean_class.primitive?)
  test_ok(Java::JavaClass.for_name("char").primitive?)

  # Assignability, non-primitives
  object_class = Java::JavaClass.for_name("java.lang.Object")
  test_ok(object_class.assignable_from?(string_class))
  test_ok(! string_class.assignable_from?(object_class))

  # Assignability, primitives
  long_class = Java::JavaClass.for_name("long")
  test_ok(int_class.assignable_from?(long_class))
  test_ok(! int_class.assignable_from?(boolean_class))
  character_class = Java::JavaClass.for_name("char")
  test_ok(int_class.assignable_from?(character_class))
  test_ok(character_class.assignable_from?(int_class))
end
