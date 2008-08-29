require 'ffi'

module LibC
  extend FFI::Library
  callback :qsort_cmp, [ :pointer, :pointer ], :int
  attach_function :qsort, [ :pointer, :int, :int, :qsort_cmp ], :int
end

p = MemoryPointer.new(:int, 2)
cmp = proc do |p1, p2|
  i1 = p1.get_int32(0)
  i2 = p2.get_int32(0)
  puts "In proc: Comparing #{i1} and #{i2}"
  if i1 < i2
    -1
  elsif i1 > i2
    1
  else
    0
  end
end
p.put_array_of_int32(0, [ 2, 1 ])
puts "Before qsort #{p.get_array_of_int32(0, 2).join(', ')}"
LibC.qsort(p, 2, 4, cmp)
puts "After qsort #{p.get_array_of_int32(0, 2).join(', ')}"