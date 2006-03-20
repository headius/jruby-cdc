###### BEGIN LICENSE BLOCK ######
# Version: CPL 1.0/GPL 2.0/LGPL 2.1
#
# The contents of this file are subject to the Common Public
# License Version 1.0 (the "License"); you may not use this file
# except in compliance with the License. You may obtain a copy of
# the License at http://www.eclipse.org/legal/cpl-v10.html
#
# Software distributed under the License is distributed on an "AS
# IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
# implied. See the License for the specific language governing
# rights and limitations under the License.
#
# Copyright (C) 2002 Anders Bengtsson <ndrsbngtssn@yahoo.se>
# Copyright (C) 2002 Jan Arne Petersen <jpetersen@uni-bonn.de>
# Copyright (C) 2004-2005 Thomas E Enebo <enebo@acm.org>
# Copyright (C) 2004 David Corbin <dcorbin@users.sourceforge.net>
# 
# Alternatively, the contents of this file may be used under the terms of
# either of the GNU General Public License Version 2 or later (the "GPL"),
# or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
# in which case the provisions of the GPL or the LGPL are applicable instead
# of those above. If you wish to allow use of your version of this file only
# under the terms of either the GPL or the LGPL, and not to allow others to
# use your version of this file under the terms of the CPL, indicate your
# decision by deleting the provisions above and replace them with the notice
# and other provisions required by the GPL or the LGPL. If you do not delete
# the provisions above, a recipient may use your version of this file under
# the terms of any one of the CPL, the GPL or the LGPL.
###### END LICENSE BLOCK ######

# JavaProxy is a base class for all Java Proxies.  A Java proxy is a high-level abstraction
# that wraps a low-level JavaObject with ruby methods capable of dispatching to the JavaObjects
# native java methods.  
class JavaProxy
  class << self
    attr :java_class, true

    # Allocate a new instance for the provided java_object.  This is like a second 'new' to
    # by-pass any 'initialize' methods we may have created for the proxy class (we already
    # have the instance for the proxy...We don't want to re-create it).
    def new_instance_for(java_object)
      new_instance = allocate
      new_instance.java_object = java_object
      new_instance
    end
    
    # Carry the Java class as a class variable on the derived classes too, otherwise 
    # JavaProxy.java_class won't work.
    def inherited(subclass)
      subclass.java_class = self.java_class unless subclass.java_class
      super
    end
    
    def singleton_class
      class << self; self; end 
    end

    # If the proxy class itself is passed as a parameter this will be called by Java#ruby_to_java    
    def to_java_object
      self.java_class
    end

    def []
      JavaUtilities.get_proxy_class(java_class.array_class)
    end
    
    def setup
      unless java_class.array?
        setup_attributes
        setup_class_methods
        setup_constants
        setup_inner_classes
        setup_instance_methods
      end
    end
    
    def setup_attributes
      instance_methods = java_class.java_instance_methods.collect! {|m| m.name}
      java_class.fields.select {|field| field.public? && !field.static? }.each do |attr|
        name = attr.name
      
        # Do not add any constants that have the same name as an existing method
        next if instance_methods.detect {|m| m == name }

        class_eval do
          define_method(name) do |*args| Java.java_to_ruby(attr.value(@java_object)); end
        end

        next if attr.final?

        class_eval do
          define_method("#{name}=") do |*args|
            Java.java_to_ruby(attr.set_value(@java_object, Java.ruby_to_java(args.first)))
          end
        end
      end
    end

    def setup_class_methods
      java_class.java_class_methods.select { |m| m.public? }.group_by { |m| m.name 
      }.each do |name, methods|
        if methods.length == 1
          method = methods.first
          singleton_class.send(:define_method, name) do |*args|
            args.collect! { |v| Java.ruby_to_java(v) }
            Java.java_to_ruby(method.invoke_static(*args))
          end
        else
          singleton_class.send(:define_method, name) do |*args|
            args.collect! { |v| Java.ruby_to_java(v) }
            Java.java_to_ruby(JavaUtilities.matching_method(methods, args).invoke_static(*args))
          end
        end
      end
    end
    
    def setup_constants
      fields = java_class.fields
      constants = fields.select {|field|
        field.static? and field.final? and JavaUtilities.valid_constant_name?(field.name)
      }.each {|constant|
        const_set(constant.name, Java.java_to_ruby(constant.static_value))
      }
      naughty_constants = fields.select {|field|
        field.static? and field.final? and !JavaUtilities.valid_constant_name?(field.name)
      }

      class_methods = java_class.java_class_methods.collect! { |m| m.name }

      naughty_constants.each do |constant|
        name = constant.name
        # Do not add any constants that has same-name class method
        next if class_methods.detect {|m| m == name }

        class_eval do
          singleton_class.send(:define_method, name) do |*args|
            Java.java_to_ruby(java_class.field(name).static_value)
          end
        end
      end
    end

    def setup_inner_classes
      def const_missing(constant)
        inner_class = nil
        begin
          inner_class = Java::JavaClass.for_name(java_class.name + '$' + constant.to_s)
        rescue NameError
          return super
        end
        JavaUtilities.create_proxy_class(constant, inner_class, self)
      end
    end
    
    def setup_instance_methods
      java_class.define_instance_methods_for_proxy(self)
    end
  end
  
  attr :java_object, true

  def java_class
    self.class.java_class
  end

  def ==(rhs)
    java_object == rhs
  end
  
  def to_s
    java_object.to_s
  end

  def eql?(rhs)
    self == rhs
  end
  
  def equal?(rhs)
    java_object.equal?(rhs)
  end
  
  def hash()
    java_object.hash()
  end
  
  def to_java_object
    java_object
  end
end

class ArrayJavaProxy < JavaProxy
  include Enumerable

  def initialize(size)
    self.java_object = java_class.component_type.new_array(size)
  end

  def length()
    java_object.length
  end
  
  def [](index)
    Java.java_to_ruby(java_object[index])
  end
  
  def []=(index, value)
    java_object[index] = Java.ruby_to_java(value)
  end
  
  def each()
    block = Proc.new
    for index in 0...java_object.length
      block.call(self[index])
    end
  end
end

class InterfaceJavaProxy < JavaProxy
  def initialize
    self.java_object = Java.new_proxy_instance(self.class.java_class) { |proxy, method, *args|
      args.collect! { |arg| Java.java_to_ruby(arg) }
      Java.ruby_to_java(send(method.name, *args))
    }
  end
end

class ConcreteJavaProxy < JavaProxy
  def initialize(*args)
    constructors = java_class.constructors.select {|c| c.arity == args.length }
    raise NameError.new("wrong # of arguments for constructor") if constructors.empty?
    args.collect! { |v| Java.ruby_to_java(v) }
    self.java_object = JavaUtilities.matching_method(constructors, args).new_instance(*args)
  end
end

module JavaUtilities
  @proxy_classes = {}
  @proxy_extenders = []
  
  def JavaUtilities.add_proxy_extender(extender)
    @proxy_extenders << extender
    # Already loaded proxies should be extended if they qualify
    @proxy_classes.values.each {|proxy_class| extender.extend_proxy(proxy_class) }
  end
  
  def JavaUtilities.extend_proxy(java_class_name, &block)
	add_proxy_extender JavaInterfaceExtender.new(java_class_name, &block)
  end

  def JavaUtilities.valid_constant_name?(name)
    return false if name.empty?
    first_char = name[0..0]
    first_char == first_char.upcase && first_char != first_char.downcase
  end

  def JavaUtilities.get_proxy_class(java_class)
    java_class = Java::JavaClass.for_name(java_class) if java_class.kind_of?(String)
    class_id = java_class.id

    unless @proxy_classes[class_id]
      if java_class.interface?
        base_type = InterfaceJavaProxy
      elsif java_class.array?
        base_type = ArrayJavaProxy
      else
        base_type = ConcreteJavaProxy
      end
      
      proxy_class = Class.new(base_type) { self.java_class = java_class }
      @proxy_classes[class_id] = proxy_class
      # We do not setup the proxy before we register it so that same-typed constants do
      # not try and create a fresh proxy class and go into an infinite loop
      proxy_class.setup
      @proxy_extenders.each {|e| e.extend_proxy(proxy_class)}
    end

    @proxy_classes[class_id]
  end

  def JavaUtilities.get_java_class(name)
    begin
      return Java::JavaClass.for_name(name)
    rescue NameError
      return nil
    end
  end
  
  def JavaUtilities.create_proxy_class(constant, java_class, mod)
    mod.const_set(constant.to_s, get_proxy_class(java_class))
  end
  
  def JavaUtilities.print_class(java_type, indent="")
     while (!java_type.nil? && java_type.name != "java.lang.Class")
        puts "#{indent}Name:  #{java_type.name}, access: #{ JavaUtilities.access(java_type) }  Interfaces: "
        java_type.interfaces.each { |i| print_class(i, "  #{indent}") }
        puts "#{indent}SuperClass: "
        print_class(java_type.superclass, "  #{indent}")
        java_type = java_type.superclass
     end
  end

  def JavaUtilities.access(java_type)
    java_type.public? ? "public" : (java_type.protected? ? "protected" : "private")
  end

  # Wrap a low-level java_object with a high-level java proxy  
  def JavaUtilities.wrap(java_object)
    get_proxy_class(java_object.java_class).new_instance_for(java_object)
  end

  def JavaUtilities.matching_method(methods, args)
    arg_types = args.collect {|a| a.java_class }
    methods.each do |method|
      types = method.argument_types

      # Exact match
      return method if types == arg_types
      
      # Compatible (by inheritance)
      if (types.length == arg_types.length)
        match = true
        0.upto(types.length - 1) do |i|
          match = false unless types[i].assignable_from?(arg_types[i])
        end
        return method if match
      end
    end

    name = methods.first.kind_of?(Java::JavaConstructor) ? 
      "constructor" : "method '" + methods.first.name + "'"
    raise NameError.new("no " + name + " with arguments matching " + arg_types.inspect)
  end
end

# Extensions to the standard Module package.

class Module
  private

  ##
  # Includes a Java package into this class/module. The Java classes in the
  # package will become available in this class/module, unless a constant
  # with the same name as a Java class is already defined.
  #
  def include_package(package_name)
    if defined? @included_packages
      @included_packages << package_name      
      return
    end
    @included_packages = [package_name]
    @java_aliases = {} unless @java_aliases

    def self.const_missing(constant)
      real_name = @java_aliases[constant]
      real_name = constant unless real_name

      java_class = nil
      return super unless @included_packages.detect {|package|
          java_class = JavaUtilities.get_java_class(package + '.' + real_name.to_s)
      }
      
      JavaUtilities.create_proxy_class(constant, java_class, self)
    end
  end

  def java_alias(new_id, old_id)
    @java_aliases[new_id] = old_id
  end
end

class ConstantAlreadyExistsError < RuntimeError
end

class Object
  def include_class(include_class)
    class_names = include_class.to_a

    class_names.each do |full_class_name|
      package_name, class_name = full_class_name.match(/((.*)\.)?([^\.]*)/)[2,3]

      if block_given?
        constant = yield(package_name, class_name)
      else
        constant = class_name
      end
      
      cls = self.kind_of?(Module) ? self : self.class

	  # Constant already exists...do not let proxy get created unless the collision is the proxy
	  # you are trying to include.
      if (cls.const_defined?(constant) )
        proxy = JavaUtilities.get_proxy_class(full_class_name)
        existing_constant = cls.const_get(constant)
      	raise ConstantAlreadyExistsError.new, "Class #{constant} already exists" unless existing_constant == proxy
	  end

      # FIXME: When I changed this user const_set instead of eval below Comparator got lost
      # which means I am missing something.
      if (respond_to?(:class_eval, true))
        class_eval("#{constant} = JavaUtilities.get_proxy_class(\"#{full_class_name}\")")
      else
        eval("#{constant} = JavaUtilities.get_proxy_class(\"#{full_class_name}\")")
      end
    end
  end
end


class JavaInterfaceExtender
  def initialize(java_class_name, &block)
    @java_class = Java::JavaClass.for_name(java_class_name)
    @block = block
  end
  
  def extend_proxy(proxy_class)
    proxy_class.class_eval &@block if @java_class.assignable_from? proxy_class.java_class
  end
end

require 'builtin/java/exceptions'
require 'builtin/java/collections'
