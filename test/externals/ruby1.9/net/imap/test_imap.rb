require "net/imap"
require "test/unit"

class IMAPTest < Test::Unit::TestCase
  CA_FILE = File.expand_path("cacert.pem", File.dirname(__FILE__))
  SERVER_KEY = File.expand_path("server.key", File.dirname(__FILE__))
  SERVER_CERT = File.expand_path("server.crt", File.dirname(__FILE__))

  def test_encode_utf7
    utf8 = "\357\274\241\357\274\242\357\274\243".force_encoding("UTF-8")
    s = Net::IMAP.encode_utf7(utf8)
    assert_equal("&,yH,Iv8j-".force_encoding("UTF-8"), s)
  end

  def test_decode_utf7
    s = Net::IMAP.decode_utf7("&,yH,Iv8j-")
    utf8 = "\357\274\241\357\274\242\357\274\243".force_encoding("UTF-8")
    assert_equal(utf8, s)
  end

  def test_imaps_unknown_ca
    if defined?(OpenSSL)
      assert_raise(OpenSSL::SSL::SSLError) do
        imaps_test do |port|
          Net::IMAP.new("localhost",
                        :port => port,
                        :ssl => true)
        end
      end
    end
  end

  def test_imaps_with_ca_file
    if defined?(OpenSSL)
      assert_nothing_raised do
        imaps_test do |port|
          Net::IMAP.new("localhost",
                        :port => port,
                        :ssl => { :ca_file => CA_FILE })
        end
      end
    end
  end

  def test_imaps_verify_none
    if defined?(OpenSSL)
      assert_nothing_raised do
        imaps_test do |port|
          Net::IMAP.new("localhost",
                        :port => port,
                        :ssl => { :verify_mode => OpenSSL::SSL::VERIFY_NONE })
        end
      end
    end
  end

  def test_imaps_post_connection_check
    if defined?(OpenSSL)
      assert_raise(OpenSSL::SSL::SSLError) do
        imaps_test do |port|
          Net::IMAP.new("127.0.0.1",
                        :port => port,
                        :ssl => { :ca_file => CA_FILE })
        end
      end
    end
  end

  def test_starttls
    imap = nil
    if defined?(OpenSSL)
      starttls_test do |port|
        imap = Net::IMAP.new("localhost", :port => port)
        imap.starttls(:ca_file => CA_FILE)
        imap
      end
    end
  ensure
    if imap && !imap.disconnected?
      imap.disconnect
    end
  end

  def test_unexpected_eof
    server = TCPServer.new(0)
    port = server.addr[1]
    Thread.start do
      begin
        sock = server.accept
        begin
          sock.print("* OK test server\r\n")
          sock.gets
#          sock.print("* BYE terminating connection\r\n")
#          sock.print("RUBY0001 OK LOGOUT completed\r\n")
        ensure
          sock.close
        end
      rescue
      end
    end
    begin
      begin
        imap = Net::IMAP.new("localhost", :port => port)
        assert_raise(EOFError) do
          imap.logout
        end
      ensure
        imap.disconnect if imap
      end
    ensure
      server.close
    end
  end

  private

  def imaps_test
    server = TCPServer.new(0)
    port = server.addr[1]
    ctx = OpenSSL::SSL::SSLContext.new
    ctx.ca_file = CA_FILE
    ctx.key = File.open(SERVER_KEY) { |f|
      OpenSSL::PKey::RSA.new(f)
    }
    ctx.cert = File.open(SERVER_CERT) { |f|
      OpenSSL::X509::Certificate.new(f)
    }
    ssl_server = OpenSSL::SSL::SSLServer.new(server, ctx)
    Thread.start do
      begin
        sock = ssl_server.accept
        begin
          sock.print("* OK test server\r\n")
          sock.gets
          sock.print("* BYE terminating connection\r\n")
          sock.print("RUBY0001 OK LOGOUT completed\r\n")
        ensure
          sock.close
        end
      rescue
      end
    end
    begin
      begin
        imap = yield(port)
        imap.logout
      ensure
        imap.disconnect if imap
      end
    ensure
      ssl_server.close
    end
  end

  def starttls_test
    server = TCPServer.new(0)
    port = server.addr[1]
    Thread.start do
      begin
        sock = server.accept
        sock.print("* OK test server\r\n")
        sock.gets
        sock.print("RUBY0001 OK completed\r\n")
        ctx = OpenSSL::SSL::SSLContext.new
        ctx.ca_file = CA_FILE
        ctx.key = File.open(SERVER_KEY) { |f|
          OpenSSL::PKey::RSA.new(f)
        }
        ctx.cert = File.open(SERVER_CERT) { |f|
          OpenSSL::X509::Certificate.new(f)
        }
        sock = OpenSSL::SSL::SSLSocket.new(sock, ctx)
        begin
          sock.accept
          sock.gets
          sock.print("* BYE terminating connection\r\n")
          sock.print("RUBY0002 OK LOGOUT completed\r\n")
        ensure
          sock.close
        end
      rescue
      end
    end
    begin
      begin
        imap = yield(port)
        imap.logout
      ensure
        imap.disconnect if imap
      end
    ensure
      server.close
    end
  end
end
