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

module JRuby
  module Compiler
    module Bytecode

      SELF_INDEX = 1
      RUNTIME_INDEX = 0

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

        def emit_jvm_bytecode(list, factory)
          list.append(BCEL::ALOAD.new(RUNTIME_INDEX))

          list.append(BCEL::ICONST.new(@value))
          list.append(BCEL::I2L.new())

          arg_types = BCEL::Type[].new(2)
          arg_types[0] = BCEL::ObjectType.new("org.jruby.Ruby")
          arg_types[1] = BCEL::Type::LONG
          list.append(factory.createInvoke("org.jruby.RubyFixnum",
                                           "newFixnum",
                                           BCEL::ObjectType.new("org.jruby.RubyFixnum"),
                                           arg_types,
                                           BCEL::Constants::INVOKESTATIC))
        end
      end

      class PushSelf

        def emit_jvm_bytecode(list, factory)
          list.append(BCEL::ALOAD.new(SELF_INDEX))
        end
      end

      class Call
        attr_reader :name
        attr_reader :arity

        def initialize(name, arity, type)
          @name, @arity, @type = name, arity, type
        end

        def emit_jvm_bytecode(list, factory)
          # ..., receiver, arg1, arg2

          list.append(BCEL::ICONST.new(@arity))
          list.append(factory.createNewArray(BCEL::ObjectType.new("org.jruby.runtime.builtin.IRubyObject"), @arity)) # is this argument really arity?

          # ..., receiver, arg1, arg2, args_array

          # WARNING: the following line destroys the 'self' variable!!!!...

          list.append(BCEL::InstructionFactory.createStore(BCEL::ArrayType.new("org.jruby.runtime.builtin.IRubyObject", 1), SELF_INDEX))

          for i in 0...arity
            list.append(BCEL::InstructionFactory.createLoad(BCEL::ArrayType.new("org.jruby.runtime.builtin.IRubyObject", 1), SELF_INDEX))
            # ..., receiver, arg1, ..., argN, args_array
            list.append(BCEL::SWAP.new)
            # ..., receiver, arg1, ..., args_array, argN
            list.append(BCEL::ICONST.new(i))
            # ..., receiver, arg1, ..., args_array, argN, index
            list.append(BCEL::SWAP.new)
            # ..., receiver, arg1, ..., args_array, index, argN
            list.append(BCEL::AASTORE.new())
            # ..., receiver, arg1, ... argN-1
          end

          # ..., receiver
          list.append(BCEL::InstructionFactory.createLoad(BCEL::ArrayType.new("org.jruby.runtime.builtin.IRubyObject", 1), SELF_INDEX))
          # ..., receiver, args_array

          list.append(BCEL::PUSH.new(factory.getConstantPool, @name))

          # ..., receiver, args_array, name

          list.append(BCEL::SWAP.new)

          # ..., receiver, name, args_array

          arg_types = BCEL::Type[].new(2)
          arg_types[0] = BCEL::Type::STRING
          arg_types[1] = BCEL::ArrayType.new("org.jruby.runtime.builtin.IRubyObject", 1)
          list.append(factory.createInvoke("org.jruby.runtime.builtin.IRubyObject",
                                           "callMethod",
                                           BCEL::ObjectType.new("org.jruby.runtime.builtin.IRubyObject"),
                                           arg_types,
                                           BCEL::Constants::INVOKEINTERFACE))
        end
      end

      class PushString
        attr_reader :value

        def initialize(value)
          @value = value
        end

        def emit_jvm_bytecode(list, factory)
          list.append(BCEL::ALOAD.new(RUNTIME_INDEX))
          list.append(BCEL::PUSH.new(factory.getConstantPool, @value))

          arg_types = BCEL::Type[].new(2)
          arg_types[0] = BCEL::ObjectType.new("org.jruby.Ruby")
          arg_types[1] = BCEL::Type::STRING
          list.append(factory.createInvoke("org.jruby.RubyString",
                                           "newString",
                                           BCEL::ObjectType.new("org.jruby.RubyString"),
                                           arg_types,
                                           BCEL::Constants::INVOKESTATIC))
        end
      end
    end
  end
end
