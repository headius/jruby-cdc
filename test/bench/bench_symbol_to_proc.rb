require 'benchmark'

class Symbol
    def to_proc
        lambda {|x, *args| x.send(self, *args)}
    end
end

def bench_symbol_to_proc(bm)
  list = %w[zxcv asdf qwer wert sdfg xcvb cvbn dfgh erty rtyu fghj vbnm]
  bm.report("str_list.map {|x| x.upcase}") {
    100_000.times { list.map {|x| x.upcase} }
  }

  bm.report("str_list.map(&:upcase)") {
    100_000.times { list.map(&:upcase) }
  }
end

if $0 == __FILE__
  Benchmark.bmbm(40) {|bm| bench_symbol_to_proc(bm)}
end
