require 'test/minirunit'
require 'socket'

server_read = nil
client_read = nil

server_thread = Thread.new do
  serv = TCPServer.new('localhost',2202)
  sock = serv.accept
  
  server_read = sock.read(5)
  sock.write "world!"
  sock.close
end

# Makes sure we actually get the server thread started, but not wait for ever either.
rc = 0
begin
  socket = TCPSocket.new("localhost",2202) 
rescue 
  rc += 1
  if rc < 10
    sleep 1
    retry
  else
    raise
  end
end

socket.write "Hello"
client_read = socket.read(6)
socket.close
server_thread.join

test_equal("Hello", server_read)
test_equal("world!", client_read)

serv = TCPServer.new('localhost',2203)
test_no_exception { serv.listen(1024) } # fix for listen blowing up because it tried to rebind; it's a noop now
