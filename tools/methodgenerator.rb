
require 'rexml/sax2parser'


INCLUDES = %w(
org.jruby.Ruby
org.jruby.RubyClass
org.jruby.RubyModule
org.jruby.runtime.builtin.IRubyObject
org.jruby.runtime.builtin.definitions.MethodContext
org.jruby.runtime.builtin.definitions.SingletonMethodContext
org.jruby.runtime.builtin.definitions.ModuleDefinition
org.jruby.runtime.builtin.definitions.ClassDefinition
org.jruby.runtime.builtin.definitions.ModuleFunctionsContext
org.jruby.util.Asserts
)


class AbstractMethodDescription

  def generate_constant(output)
    # no-op
  end

  def generate_creation(output)
    # no-op
  end

  def generate_switch_case(output)
    # no-op
  end
end


class MethodDescription < AbstractMethodDescription

  attr :arity, true
  attr :java_name, true
  attr :name

  def initialize(generator, name, count)
    @generator, @name, @count = generator, name, count
    @arity = 0
    @is_optional = false
    @java_name = name
  end

  def optional=(optional)
    @is_optional = optional
  end

  def optional?
    @is_optional
  end

  def generate_constant(output)
    output.write('public static final int ')
    output.write("#{constant_name} = ")
    output.write("#{@generator.constant_name} | #{@count.to_s};\n")
  end

  def generate_creation(output)
    output.write("context.")
    if @is_optional
      output.write("createOptional")
    else
      output.write("create")
    end
    output.write("(\"#{@name}\", #{constant_name}, #{arity});\n")
  end

  def constant_name
    @java_name.upcase
  end
end


class StaticMethodDescription < MethodDescription

  def generate_constant(output)
    output.write('public static final int ')
    output.write("#{constant_name} = ")
    output.write("STATIC | #{@count.to_s};\n")
  end

  def generate_switch_case(output)
    output.write("case #{constant_name} :\n")
    output.write("return #{@generator.implementation}.#{java_name}(")
    output.write("receiver")
    if optional?
      output.write(", args")
    else
      (0...arity).each {|i|
        output.write(", args[#{i}]")
      }
    end
    output.write(");\n")
  end
end


class Alias < AbstractMethodDescription

  attr :name

  def initialize(name, original)
    @name, @original = name, original
  end

  def generate_creation(output)
    output.write('context.')
    if @original.optional?
      output.write("createOptional")
    else
      output.write("create")
    end
    output.write("(\"#{@name}\", #{constant_name}, #{arity});\n")
  end

  def constant_name
    @original.constant_name
  end

  def arity
    @original.arity
  end
end


class UndefineMethod < AbstractMethodDescription

  def initialize(name)
    @name = name
  end

  def generate_creation(output)
    output.write('context.undefineMethod("')
    output.write(@name)
    output.write('");' + "\n")
  end
end



class MethodGenerator

  def initialize(input)
    @input = input
    @is_module = false
    @name = nil
    @methods = []
    @class_methods = []
    @implementation = nil
    @superclass = "Object"
    @package = nil
  end

  attr :implementation

  def package=(package)
    @package = package
  end

  def constant_name
    @name.upcase
  end

  def generate(output)
    read_input

    output.write("/* Generated code - do not edit! */\n")
    output.write("\n")

    if @package
      output.write("package #{@package};\n")
      output.write("\n")
    end

    write_includes(output)
    output.write("\n")
    output.write("public class #{@name}Definition")
    output.write(" extends ")
    if @is_module
      output.write("Module")
    else
      output.write("Class")
    end
    output.write("Definition {\n")
    output.write("private static final int #{constant_name} = 0xf000;\n")
    output.write("private static final int STATIC = #{constant_name} | 0x100;\n")
    @methods.each {|m|
      m.generate_constant(output)
    }
    @class_methods.each {|m|
      m.generate_constant(output)
    }
    output.write("\n")

    output.write("public #{@name}Definition(Ruby runtime) {\n")
    output.write("super(runtime);\n")
    output.write("}\n")
    output.write("\n")

    if @is_module
      output.write("protected RubyModule createModule(Ruby runtime) {\n")
      output.write('return runtime.defineModule("')
      output.write(@name)
      output.write('");' + "\n")
      output.write("}\n")
    else
      output.write("protected RubyClass createType(Ruby runtime) {\n")
      output.write('return runtime.defineClass("')
      output.write(@name)
      output.write('", ')
      if @superclass == :none
        output.write('(RubyClass) null')
      else
        output.write('(RubyClass) runtime.getClasses().getClass("')
        output.write(@superclass)
        output.write('")')
      end
      output.write(');' + "\n")
      output.write("}\n")
    end
    output.write("\n")

    output.write("protected void defineMethods(MethodContext context) {\n")
    @methods.each {|m|
      m.generate_creation(output)
    }
    output.write("}\n")
    output.write("\n")

    if @is_module
      output.write("protected void defineModuleFunctions(ModuleFunctionsContext context) {\n")
    else
      output.write("protected void defineSingletonMethods(SingletonMethodContext context) {\n")
    end
    @class_methods.each {|m|
      m.generate_creation(output)
    }
    output.write("}\n")

    output.write("public IRubyObject callIndexed(int index, IRubyObject receiver, IRubyObject[] args) {\n")
    output.write("switch (index) {\n")
    @class_methods.each {|m|
      m.generate_switch_case(output)
    }
    output.write("default :\n")
    output.write("Asserts.notReached();\n")
    output.write("return null;\n")
    output.write("}\n")
    output.write("}\n")

    output.write("}\n")
  end

  def write_includes(output)
    INCLUDES.each {|include|
      output.write("import #{include};\n")
    }
  end

  def read_input
    method_count = nil
    methods = nil
    method_description_class = nil

    parser = Parser.new(@input)
    parser.on_tag_start("module") {|name, attributes|
      if attributes['type'] == "module"
        @is_module = true
      end
    }
    parser.on_tag_content("name") {|text|
      @name = text
    }
    parser.on_tag_content("superclass") {|text|
      if text == 'none'
        @superclass = :none
      else
        @superclass = text
      end
    }
    parser.on_tag_content("implementation") {|text|
      @implementation = text
    }
    parser.on_tag_start("instance-methods") {|name, attributes|
      methods = @methods
      method_count = 0
      method_description_class = MethodDescription
    }
    parser.on_tag_start("class-methods") {|name, attributes|
      methods = @class_methods
      method_count = 0
      method_description_class = StaticMethodDescription
    }
    parser.on_tag_start("method") {|name, attributes|
      method_count += 1
      methods << method_description_class.new(self,
                                              attributes['name'],
                                              method_count)
    }
    parser.on_tag_start("arity") {|name, attributes|
      if attributes.has_key?('optional')
        methods.last.optional = (attributes['optional'] == 'true')
      end
    }
    parser.on_tag_content("arity") {|text|
      methods.last.arity = text.to_i
    }
    parser.on_tag_content("java") {|text|
      methods.last.java_name = text
    }
    parser.on_tag_start("method-alias") {|name, attributes|
      original_name = attributes['original']
      original = methods.detect {|m| m.name == original_name }
      name = attributes['name']
      methods << Alias.new(name, original)
    }
    parser.on_tag_start("undefine-method") {|name, attributes|
      name = attributes['name']
      methods << UndefineMethod.new(name)
    }
    parser.parse
  end
end

class Parser

  def initialize(input)
    @saxparser = REXML::SAX2Parser.new(input)
  end

  def on_tag_start(name, &block)
    name.gsub!(/\-/, '\-')
    @saxparser.listen(:start_element, ['^' + name + '$']) {
      |uri, localname, qname, attributes|
      block.call(localname, attributes)
    }
  end

  def on_tag_content(name, &block)
    @saxparser.listen(:characters, ['^' + name + '$']) {
      |text|
      block.call(text)
    }
  end

  def parse
    @saxparser.parse
  end
end




if $0 == __FILE__
  generator = MethodGenerator.new(STDIN)
  unless ARGV.empty?
    generator.package = ARGV[0]
  end
  generator.generate(STDOUT)
end
