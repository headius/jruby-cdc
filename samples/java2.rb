Java::import "java.io"

filename = "./samples/java2.rb"

file = File.new filename

fr = FileReader.new file
br = BufferedReader.new fr

s = br.readLine

print "------ ", filename, "------\n"

while (s.nil? ^ true)
  puts s.to_s
  s = br.readLine
end

print "------ ", filename, " end ------\n";

br.close
