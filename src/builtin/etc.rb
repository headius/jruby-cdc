Struct.new("Tms", :utime, :stime, :cutime, :cstime)

module Process
    def self.times
      Struct::Tms.new(0, 0, 0, 0)
    end
end
