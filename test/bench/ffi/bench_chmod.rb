require 'benchmark'
require 'ffi'

iter = 10000
file = "README"

module BasePosix
  def chmod(mode, path)
    if _chmod(path, mode) != 0
    end
  end

end
module RbxPosix
  extend FFI::Library
  extend BasePosix
  attach_function :chmod, :_chmod, [ :string, :int ], :int
end
module JPosix
  extend JFFI::Library
  extend BasePosix
  attach_function :chmod, :_chmod, [ :string, :int ], :int  
end

puts "Benchmark FFI chmod (rubinius api) performance, #{iter}x changing mode"
10.times {
  puts Benchmark.measure {
    iter.times { RbxPosix.chmod(0622, file) }
  }
}
puts "Benchmark FFI chmod (jruby api) performance, #{iter}x changing mode"
10.times {
  puts Benchmark.measure {
    iter.times { JPosix.chmod(0622, file) }
  }
}

puts "Benchmark JRuby File.chmod performance, #{iter}x changing mode"
10.times {
  puts Benchmark.measure {
    iter.times { File.chmod(0622, file) }
  }
}
