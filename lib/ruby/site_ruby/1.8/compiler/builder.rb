require 'compiler/bytecode'
require 'compiler/signature'

module Compiler
  class ClassBuilder
    import "jruby.objectweb.asm.Opcodes"
    import "jruby.objectweb.asm.ClassWriter"
    include Opcodes
    import java.lang.Object
    import java.lang.Void
    include Signature

    def initialize(class_name, file_name, superclass, *interfaces)
      @class_name = class_name
      @superclass = superclass
      
      @class_writer = ClassWriter.new(ClassWriter::COMPUTE_MAXS)
      
      interface_paths = []
      interfaces.each {|interface| interface_paths << path(interface)}
      @class_writer.visit(1, 1, class_name, nil, path(superclass), interface_paths.to_java(:string))
      @class_writer.visit_source(file_name, nil)
    end
    
    def self.build(class_name, file_name, superclass = java.lang.Object, *interfaces, &block)
      cb = ClassBuilder.new(class_name, file_name, superclass, *interfaces)
      cb.instance_eval &block
      cb
    end
    
    def write(filename)
      bytes = @class_writer.to_byte_array
      File.open(filename, "w") {|file| file.write(String.from_java_bytes(bytes))}
    end
    
    def constructor(*signature, &block)
      signature.unshift Void::TYPE
      
      MethodBuilder.build(self, ACC_PUBLIC, "<init>", signature, &block)
    end
    
    def method(name, *signature, &block)
      MethodBuilder.build(self, ACC_PUBLIC, name.to_s, signature, &block)
    end
    
    def static_method(name, *signature, &block)
      MethodBuilder.build(self, ACC_PUBLIC | ACC_STATIC, name.to_s, signature, &block)
    end
    
    def new_method(modifiers, name, signature)
      @class_writer.visit_method(modifiers, name, sig(*signature), nil, nil)
    end
  end
  
  class MethodBuilder
    include Compiler::Bytecode
    
    attr_reader :method_visitor
    
    def initialize(class_builder, modifiers, name, signature)
      @class_builder = class_builder
      @modifiers = modifiers
      @name = name
      @signature = signature
      
      @method_visitor = class_builder.new_method(modifiers, name, signature)
    end
    
    def self.build(class_builder, modifiers, name, signature, &block)
      mb = MethodBuilder.new(class_builder, modifiers, name, signature)
      mb.start
      mb.instance_eval &block
      mb.stop
    end
  end
end