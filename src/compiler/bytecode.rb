#
# Copyright (C) 2002 Anders Bengtsson <ndrsbngtssn@yahoo.se>
#
# JRuby - http://jruby.sourceforge.net
#
# This file is part of JRuby
#
# JRuby is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License as
# published by the Free Software Foundation; either version 2 of the
# License, or (at your option) any later version.
#
# JRuby is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public
# License along with JRuby; if not, write to
# the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
# Boston, MA  02111-1307 USA

require 'bcel.rb'

module JRuby
  module Compiler
    module Bytecode

      SELF_INDEX = 1
      RUNTIME_INDEX = 0

      IRUBYOBJECT_TYPE =
        BCEL::ObjectType.new("org.jruby.runtime.builtin.IRubyObject")
      RUBY_TYPE =
        BCEL::ObjectType.new("org.jruby.Ruby")


      class CreateMethod
        def initialize(name, arity)
          @name, @arity = name, arity
        end

        def emit_jvm_bytecode(generator)
          factory = generator.factory

          # klass
          generator.append(BCEL::PUSH.new(generator.getConstantPool,
                                          generator.java_class_name))
          arg_types = BCEL::Type[].new(1)
          arg_types[0] = BCEL::Type::STRING
          generator.appendInvoke("java.lang.Class",
                                 "forName",
                                 BCEL::ObjectType.new("java.lang.Class"),
                                 arg_types,
                                 BCEL::Constants::INVOKESTATIC)

          klass =
            generator.addLocalVariable("args_array",
                                       BCEL::ObjectType.new("java.lang.Class"),
                                       nil,
                                       nil)
          klass.setStart(generator.getEnd)
          generator.append(BCEL::ASTORE.new(klass.getIndex))

          # methodName
          generator.append(BCEL::PUSH.new(generator.getConstantPool,
                                          @name))
          methodName =
            generator.addLocalVariable("methodName",
                                       BCEL::Type::STRING,
                                       nil,
                                       nil)
          methodName.setStart(generator.getEnd)
          generator.append(BCEL::ASTORE.new(methodName.getIndex))

          # args
          generator.append(BCEL::PUSH.new(generator.getConstantPool,
                                          @arity))
          generator.append(factory.createNewArray(BCEL::ObjectType.new("java.lang.Class"),
                                                  1))
          for i in 0...@arity
            generator.append(BCEL::DUP.new)
            generator.append(BCEL::PUSH.new(generator.getConstantPool,
                                            i))
            generator.append(BCEL::PUSH.new(generator.getConstantPool,
                                            IRUBYOBJECT_TYPE.getClassName))
            generator.appendInvoke("java.lang.Class",
                                   "forName",
                                   BCEL::ObjectType.new("java.lang.Class"),
                                   arg_types,
                                   BCEL::Constants::INVOKESTATIC)
            generator.append(BCEL::AASTORE.new)
          end
          args = generator.addLocalVariable("args",
                                            BCEL::ArrayType.new("java.lang.Class", 1),
                                            nil,
                                            nil)
          args.setStart(generator.getEnd)
          generator.append(BCEL::ASTORE.new(args.getIndex))

          # isRestArgs
          generator.append(BCEL::PUSH.new(generator.getConstantPool,
                                          false))
          isRestArgs = generator.addLocalVariable("isRestArgs",
                                                  BCEL::Type::BOOLEAN,
                                                  nil,
                                                  nil)
          isRestArgs.setStart(generator.getEnd)
          generator.append(BCEL::ISTORE.new(isRestArgs.getIndex))

          # isStaticMethod
          generator.append(BCEL::PUSH.new(generator.getConstantPool,
                                          true))
          isStaticMethod = generator.addLocalVariable("isStaticMethod",
                                                      BCEL::Type::BOOLEAN,
                                                      nil,
                                                      nil)
          isStaticMethod.setStart(generator.getEnd)
          generator.append(BCEL::ISTORE.new(isStaticMethod.getIndex))

          # arity
          generator.append(BCEL::PUSH.new(generator.getConstantPool,
                                          @arity))
          arg_types = BCEL::Type[].new(1)
          arg_types[0] = BCEL::Type::INT
          generator.appendInvoke("org.jruby.runtime.Arity",
                                 "fixed",
                                 BCEL::ObjectType.new("org.jruby.runtime.Arity"),
                                 arg_types,
                                 BCEL::Constants::INVOKESTATIC)
          arity = generator.addLocalVariable("arity",
                                             BCEL::ObjectType.new("org.jruby.runtime.Arity"),
                                             nil,
                                             nil)
          arity.setStart(generator.getEnd)
          generator.append(BCEL::ASTORE.new(arity.getIndex))

          # Create a callback
          generator.append(factory.createNew("org.jruby.runtime.ReflectionCallbackMethod"))
          generator.append(BCEL::DUP.new)

          generator.append(BCEL::ALOAD.new(klass.getIndex))
          generator.append(BCEL::ALOAD.new(methodName.getIndex))
          generator.append(BCEL::ALOAD.new(args.getIndex))
          generator.append(BCEL::ILOAD.new(isRestArgs.getIndex))
          generator.append(BCEL::ILOAD.new(isStaticMethod.getIndex))
          generator.append(BCEL::ALOAD.new(arity.getIndex))

          # Fixme: destroy local variables

          arg_types = BCEL::Type[].new(6)
          arg_types[0] = BCEL::ObjectType.new("java.lang.Class")
          arg_types[1] = BCEL::Type::STRING
          arg_types[2] = BCEL::ArrayType.new("java.lang.Class", 1)
          arg_types[3] = BCEL::Type::BOOLEAN
          arg_types[4] = BCEL::Type::BOOLEAN
          arg_types[5] = BCEL::ObjectType.new("org.jruby.runtime.Arity")

          # Call constructor
          generator.appendInvoke("org.jruby.runtime.ReflectionCallbackMethod",
                                 "<init>",
                                 BCEL::Type::VOID,
                                 arg_types,
                                 BCEL::Constants::INVOKESPECIAL)

          # Register it to the runtime

          # Get the current class
          generator.append(BCEL::ALOAD.new(RUNTIME_INDEX))
          generator.appendInvoke("org.jruby.Ruby",
                                 "getRubyClass",
                                 BCEL::ObjectType.new("org.jruby.RubyModule"),
                                 BCEL::Type[].new(0),
                                 BCEL::Constants::INVOKEVIRTUAL)

          # Stack: ..., callback, current_class
          generator.append(BCEL::SWAP.new)
          # Stack: ..., current_class, callback
          generator.append(BCEL::PUSH.new(generator.getConstantPool,
                                          @name))
          # Stack: ..., current_class, callback, name
          generator.append(BCEL::SWAP.new)
          # Stack: ..., current_class, name, callback

          # addMethod(name, callback)
          arg_types = BCEL::Type[].new(2)
          arg_types[0] = BCEL::Type::STRING
          arg_types[1] = BCEL::ObjectType.new("org.jruby.runtime.ICallable")
          generator.appendInvoke("org.jruby.RubyModule",
                                 "addMethod",
                                 BCEL::Type::VOID,
                                 arg_types,
                                 BCEL::Constants::INVOKEVIRTUAL)
        end
      end

      class AssignLocal
        attr_reader :index

        def initialize(index)
          @index = index
        end
      end

      class PushFixnum
        attr_reader :value

        def initialize(value)
          @value = value
        end

        def emit_jvm_bytecode(generator)
          factory = generator.factory

          push_runtime(generator)

          generator.append(BCEL::PUSH.new(generator.getConstantPool, @value))
          generator.append(BCEL::I2L.new())

          arg_types = BCEL::Type[].new(2)
          arg_types[0] = RUBY_TYPE
          arg_types[1] = BCEL::Type::LONG
          generator.appendInvoke("org.jruby.RubyFixnum",
                                 "newFixnum",
                                 BCEL::ObjectType.new("org.jruby.RubyFixnum"),
                                 arg_types,
                                 BCEL::Constants::INVOKESTATIC)
        end
      end

      class PushSelf

        def emit_jvm_bytecode(generator)
          generator.append(BCEL::ALOAD.new(SELF_INDEX))
        end
      end

      class PushLocal
        def initialize(index)
          @index = index
        end

        def emit_jvm_bytecode(generator)
          factory = generator.factory
          push_scope_stack(generator) # ... why do we do this?
          generator.append(BCEL::PUSH.new(generator.getConstantPool,
                                     @index))
          arg_types = BCEL::Type[].new(1)
          arg_types[0] = BCEL::Type::INT
          generator.appendInvoke("org.jruby.runtime.ScopeStack",
                                 "getValue",
                                 IRUBYOBJECT_TYPE,
                                 arg_types,
                                 BCEL::Constants::INVOKEVIRTUAL)
        end
      end

      def push_runtime(generator)
        generator.append(BCEL::ALOAD.new(RUNTIME_INDEX))
      end

      def push_scope_stack(generator)
        factory = generator.factory
        push_runtime(generator)
        generator.appendInvoke(RUBY_TYPE.getClassName,
                               "getScope",
                               BCEL::ObjectType.new("org.jruby.runtime.ScopeStack"),
                               BCEL::Type[].new(0),
                               BCEL::Constants::INVOKEVIRTUAL)
      end

      def push_frame_stack(generator)
        factory = generator.factory
        push_runtime(generator)
        generator.appendInvoke(RUBY_TYPE.getClassName,
                               "getFrameStack",
                               BCEL::ObjectType.new("org.jruby.runtime.FrameStack"),
                               BCEL::Type[].new(0),
                               BCEL::Constants::INVOKEVIRTUAL)
      end

      class Call
        attr_reader :name
        attr_reader :arity

        def initialize(name, arity, type)
          @name, @arity, @type = name, arity, type
        end

        def emit_jvm_bytecode(generator)
          args_array = generator.getLocalVariables.detect {|lv|
            lv.getName == "args_array"
          }
          if args_array.nil?
            args_array =
              generator.addLocalVariable("args_array",
                                         BCEL::ArrayType.new(IRUBYOBJECT_TYPE, 1),
                                         nil,
                                         nil)
          end

          factory = generator.factory

          generator.append(BCEL::PUSH.new(generator.getConstantPool,
                                          @arity))
          generator.append(factory.createNewArray(IRUBYOBJECT_TYPE, 1))
          args_array.setStart(generator.getEnd())

          generator.append(BCEL::InstructionFactory.createStore(args_array.getType,
                                                                args_array.getIndex))

          # Take the method arguments from the stack
          # and put them in the array.
          for i in 0...arity
            generator.append(BCEL::InstructionFactory.createLoad(args_array.getType,
                                                                 args_array.getIndex))
            generator.append(BCEL::SWAP.new)
            generator.append(BCEL::PUSH.new(generator.getConstantPool, i))
            generator.append(BCEL::SWAP.new)
            generator.append(BCEL::AASTORE.new())
          end

          generator.append(BCEL::InstructionFactory.createLoad(args_array.getType,
                                                          args_array.getIndex))
          generator.append(BCEL::PUSH.new(factory.getConstantPool, @name))
          generator.append(BCEL::SWAP.new)

          arg_types = BCEL::Type[].new(2)
          arg_types[0] = BCEL::Type::STRING
          arg_types[1] = BCEL::ArrayType.new(IRUBYOBJECT_TYPE, 1)
          generator.appendInvoke(IRUBYOBJECT_TYPE.getClassName,
                                 "callMethod",
                                 IRUBYOBJECT_TYPE,
                                 arg_types,
                                 BCEL::Constants::INVOKEINTERFACE)
        end
      end

      class PushString
        attr_reader :value

        def initialize(value)
          @value = value
        end

        def emit_jvm_bytecode(generator)
          factory = generator.factory
          push_runtime(generator)
          generator.append(BCEL::PUSH.new(factory.getConstantPool, @value))

          arg_types = BCEL::Type[].new(2)
          arg_types[0] = RUBY_TYPE
          arg_types[1] = BCEL::Type::STRING
          generator.appendInvoke("org.jruby.RubyString",
                                 "newString",
                                 BCEL::ObjectType.new("org.jruby.RubyString"),
                                 arg_types,
                                 BCEL::Constants::INVOKESTATIC)
        end
      end

      class PushSymbol
        def initialize(name)
          @name = name
        end

        def emit_jvm_bytecode(generator)
          factory = generator.factory
          push_runtime(generator)
          generator.append(BCEL::PUSH.new(factory.getConstantPool, @name))

          arg_types = BCEL::Type[].new(2)
          arg_types[0] = RUBY_TYPE
          arg_types[1] = BCEL::Type::STRING
          generator.appendInvoke("org.jruby.RubySymbol",
                                 "newSymbol",
                                 BCEL::ObjectType.new("org.jruby.RubySymbol"),
                                 arg_types,
                                 BCEL::Constants::INVOKESTATIC)
        end
      end

      class Negate

      end

      class PushNil
        def emit_jvm_bytecode(generator)
          factory = generator.factory
          push_runtime(generator)
          generator.appendInvoke(RUBY_TYPE.getClassName,
                                 "getNil",
                                 IRUBYOBJECT_TYPE,
                                 BCEL::Type[].new(0),
                                 BCEL::Constants::INVOKEVIRTUAL)
        end
      end

      class PushBoolean
        def initialize(value)
          @value = value
        end

        def emit_jvm_bytecode(generator)
          factory = generator.factory

          push_runtime(generator)
          if @value
            methodname = "getTrue"
          else
            methodname = "getFalse"
          end
          generator.appendInvoke(RUBY_TYPE.getClassName,
                                 methodname,
                                 BCEL::ObjectType.new("org.jruby.RubyBoolean"),
                                 BCEL::Type[].new(0),
                                 BCEL::Constants::INVOKEVIRTUAL)
        end
      end

      class PushArray
        def initialize(initial_size)
          @size = initial_size
        end

        def emit_jvm_bytecode(generator)
          factory = generator.factory
          push_runtime(generator)
          generator.append(BCEL::PUSH.new(generator.getConstantPool, @size))
          generator.append(BCEL::I2L.new)

          args_array = BCEL::Type[].new(2)
          args_array[0] = RUBY_TYPE
          args_array[1] = BCEL::Type::LONG
          generator.appendInvoke("org.jruby.RubyArray",
                                 "newArray",
                                 BCEL::ObjectType.new("org.jruby.RubyArray"),
                                 args_array,
                                 BCEL::Constants::INVOKESTATIC)
        end
      end

      class PushConstant
        def initialize(name)
          @name = name
        end
      end

      class IfFalse
        attr_writer :target

        def initialize(target)
          @target = target
        end

        def emit_jvm_bytecode(generator)
          factory = generator.factory

          generator.appendInvoke(IRUBYOBJECT_TYPE.getClassName,
                                 "isTrue",
                                 BCEL::Type::BOOLEAN,
                                 BCEL::Type[].new(0),
                                 BCEL::Constants::INVOKEINTERFACE)
          # If value on stack was false we should have a 0 now
          branch = BCEL::IFEQ.new(nil)
          @target.add_listener(branch)
          generator.append(branch)
        end
      end

      class NewScope
        def initialize(local_names)
          @local_names = local_names
        end

        def emit_jvm_bytecode(generator)
          factory = generator.factory

          # runtime.getFrameStack().pushCopy()
          push_frame_stack(generator)
          generator.appendInvoke("org.jruby.runtime.FrameStack",
                                 "pushCopy",
                                 BCEL::Type::VOID,
                                 BCEL::Type[].new(0),
                                 BCEL::Constants::INVOKEVIRTUAL)

          # runtime.getScopeStack().push(localnames)
          push_scope_stack(generator)
          # FIXME: store this array instead of creating it on the fly!
          generator.append(BCEL::PUSH.new(generator.getConstantPool,
                                          @local_names.size))
          generator.append(factory.createNewArray(BCEL::Type::STRING, 1))

          iter = @local_names.iterator
          index = 0
          while iter.hasNext
            generator.append(BCEL::DUP.new)
            name = iter.next
            generator.append(BCEL::PUSH.new(generator.getConstantPool,
                                            index))
            index += 1
            generator.append(BCEL::PUSH.new(generator.getConstantPool,
                                            name))
            generator.append(BCEL::AASTORE.new)
          end
          # Stack: ..., scopestack, namesarray
          arg_types = BCEL::Type[].new(1)
          arg_types[0] = BCEL::ArrayType.new(BCEL::Type::STRING, 1)
          generator.appendInvoke("org.jruby.runtime.ScopeStack",
                                 "push",
                                 BCEL::Type::VOID,
                                 arg_types,
                                 BCEL::Constants::INVOKEVIRTUAL)
        end
      end

      class RestoreScope
        def emit_jvm_bytecode(generator)
          factory = generator.factory

          # getScopeStack.pop()
          push_scope_stack(generator)
          generator.appendInvoke("org.jruby.runtime.ScopeStack",
                                 "pop",
                                 BCEL::ObjectType.new("org.jruby.util.collections.StackElement"),
                                 BCEL::Type[].new(0),
                                 BCEL::Constants::INVOKEVIRTUAL)
          # getFrameStack.pop()
          push_frame_stack(generator)
          generator.appendInvoke("org.jruby.runtime.FrameStack",
                                 "pop",
                                 BCEL::ObjectType.new("java.lang.Object"),
                                 BCEL::Type[].new(0),
                                 BCEL::Constants::INVOKEVIRTUAL)
        end
      end

      class CreateRange
        def initialize(exclusive)
          @exclusive = exclusive
        end

        def emit_jvm_bytecode(generator)
          factory = generator.factory

          # Inserting 'runtime' before the two range arguments

          # Stack: ..., begin, end
          push_runtime(generator)
          # Stack: ..., begin, end, runtime
          generator.append(BCEL::DUP_X2.new)
          # Stack: ..., runtime, begin, end, runtime
          generator.append(BCEL::POP.new)
          # Stack: ..., runtime, begin, end
          generator.append(BCEL::PUSH.new(generator.getConstantPool,
                                          @exclusive))
          # Stack: ..., runtime, begin, end, isexclusive

          arg_types = BCEL::Type[].new(4)
          arg_types[0] = RUBY_TYPE
          arg_types[1] = IRUBYOBJECT_TYPE
          arg_types[2] = IRUBYOBJECT_TYPE
          arg_types[3] = BCEL::Type::BOOLEAN
          generator.appendInvoke("org.jruby.RubyRange",
                                 "newRange",
                                 BCEL::ObjectType.new("org.jruby.RubyRange"),
                                 arg_types,
                                 BCEL::Constants::INVOKESTATIC)
        end
      end

      class Goto
        attr_writer :target

        def initialize(target)
          @target = target
        end

        def emit_jvm_bytecode(generator)
          list = generator.getInstructionList
          goto = BCEL::GOTO.new(nil)
          @target.add_listener(goto)
          generator.append(goto)
        end
      end

      class Label
        def initialize
          @handle = nil
          @listeners = []
        end

        def add_listener(listener)
          @listeners << listener
          unless @handle.nil?
            listener.setTarget(@handle)
          end
        end

        def emit_jvm_bytecode(generator)
          @handle = generator.append(BCEL::NOP.new)
          @listeners.each {|l| l.setTarget(@handle) }
        end
      end

    end
  end
end
