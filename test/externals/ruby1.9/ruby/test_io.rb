require 'test/unit'
require 'tmpdir'
require "fcntl"
require 'io/nonblock'
require 'socket'
require 'stringio'
require 'timeout'
require 'tempfile'
require_relative 'envutil'

class TestIO < Test::Unit::TestCase
  def have_close_on_exec?
    begin
      $stdin.close_on_exec?
      true
    rescue NotImplementedError
      false
    end
  end

  def have_nonblock?
    IO.instance_methods.index(:"nonblock=")
  end

  def test_gets_rs
    # default_rs
    r, w = IO.pipe
    w.print "aaa\nbbb\n"
    w.close
    assert_equal "aaa\n", r.gets
    assert_equal "bbb\n", r.gets
    assert_nil r.gets
    r.close

    # nil
    r, w = IO.pipe
    w.print "a\n\nb\n\n"
    w.close
    assert_equal "a\n\nb\n\n", r.gets(nil)
    assert_nil r.gets("")
    r.close

    # "\377"
    r, w = IO.pipe('ascii-8bit')
    w.print "\377xyz"
    w.close
    r.binmode
    assert_equal("\377", r.gets("\377"), "[ruby-dev:24460]")
    r.close

    # ""
    r, w = IO.pipe
    w.print "a\n\nb\n\n"
    w.close
    assert_equal "a\n\n", r.gets(""), "[ruby-core:03771]"
    assert_equal "b\n\n", r.gets("")
    assert_nil r.gets("")
    r.close
  end

  def test_gets_limit_extra_arg
    with_pipe {|r, w|
      r, w = IO.pipe
      w << "0123456789"
      w.close
      assert_raise(TypeError) { r.gets(3,nil) }
    }
  end

  # This test cause SEGV.
  def test_ungetc
    r, w = IO.pipe
    w.close
    assert_raise(IOError, "[ruby-dev:31650]") { 20000.times { r.ungetc "a" } }
  ensure
    r.close
  end

  def test_each_byte
    r, w = IO.pipe
    w << "abc def"
    w.close
    r.each_byte {|byte| break if byte == 32 }
    assert_equal("def", r.read, "[ruby-dev:31659]")
  ensure
    r.close
  end

  def test_rubydev33072
    assert_raise(Errno::ENOENT, "[ruby-dev:33072]") do
      File.read("empty", nil, nil, {})
    end
  end

  def with_pipe
    r, w = IO.pipe
    begin
      yield r, w
    ensure
      r.close unless r.closed?
      w.close unless w.closed?
    end
  end

  def with_read_pipe(content)
    r, w = IO.pipe
    w << content
    w.close
    begin
      yield r
    ensure
      r.close
    end
  end

  def mkcdtmpdir
    Dir.mktmpdir {|d|
      Dir.chdir(d) {
        yield
      }
    }
  end

  def test_copy_stream
    mkcdtmpdir {

      content = "foobar"
      File.open("src", "w") {|f| f << content }
      ret = IO.copy_stream("src", "dst")
      assert_equal(content.bytesize, ret)
      assert_equal(content, File.read("dst"))

      # overwrite by smaller file.
      content = "baz"
      File.open("src", "w") {|f| f << content }
      ret = IO.copy_stream("src", "dst")
      assert_equal(content.bytesize, ret)
      assert_equal(content, File.read("dst"))

      ret = IO.copy_stream("src", "dst", 2)
      assert_equal(2, ret)
      assert_equal(content[0,2], File.read("dst"))

      ret = IO.copy_stream("src", "dst", 0)
      assert_equal(0, ret)
      assert_equal("", File.read("dst"))

      ret = IO.copy_stream("src", "dst", nil, 1)
      assert_equal(content.bytesize-1, ret)
      assert_equal(content[1..-1], File.read("dst"))

      assert_raise(Errno::ENOENT) {
        IO.copy_stream("nodir/foo", "dst")
      }

      assert_raise(Errno::ENOENT) {
        IO.copy_stream("src", "nodir/bar")
      }

      with_pipe {|r, w|
        ret = IO.copy_stream("src", w)
        assert_equal(content.bytesize, ret)
        w.close
        assert_equal(content, r.read)
      }

      with_pipe {|r, w|
        w.close
        assert_raise(IOError) { IO.copy_stream("src", w) }
      }

      pipe_content = "abc"
      with_read_pipe(pipe_content) {|r|
        ret = IO.copy_stream(r, "dst")
        assert_equal(pipe_content.bytesize, ret)
        assert_equal(pipe_content, File.read("dst"))
      }

      with_read_pipe("abc") {|r1|
        assert_equal("a", r1.getc)
        with_pipe {|r2, w2|
          w2.sync = false
          w2 << "def"
          ret = IO.copy_stream(r1, w2)
          assert_equal(2, ret)
          w2.close
          assert_equal("defbc", r2.read)
        }
      }

      with_read_pipe("abc") {|r1|
        assert_equal("a", r1.getc)
        with_pipe {|r2, w2|
          w2.sync = false
          w2 << "def"
          ret = IO.copy_stream(r1, w2, 1)
          assert_equal(1, ret)
          w2.close
          assert_equal("defb", r2.read)
        }
      }

      with_read_pipe("abc") {|r1|
        assert_equal("a", r1.getc)
        with_pipe {|r2, w2|
          ret = IO.copy_stream(r1, w2)
          assert_equal(2, ret)
          w2.close
          assert_equal("bc", r2.read)
        }
      }

      with_read_pipe("abc") {|r1|
        assert_equal("a", r1.getc)
        with_pipe {|r2, w2|
          ret = IO.copy_stream(r1, w2, 1)
          assert_equal(1, ret)
          w2.close
          assert_equal("b", r2.read)
        }
      }

      with_read_pipe("abc") {|r1|
        assert_equal("a", r1.getc)
        with_pipe {|r2, w2|
          ret = IO.copy_stream(r1, w2, 0)
          assert_equal(0, ret)
          w2.close
          assert_equal("", r2.read)
        }
      }

      with_pipe {|r1, w1|
        w1 << "abc"
        assert_equal("a", r1.getc)
        with_pipe {|r2, w2|
          w1 << "def"
          w1.close
          ret = IO.copy_stream(r1, w2)
          assert_equal(5, ret)
          w2.close
          assert_equal("bcdef", r2.read)
        }
      }

      with_pipe {|r, w|
        ret = IO.copy_stream("src", w, 1, 1)
        assert_equal(1, ret)
        w.close
        assert_equal(content[1,1], r.read)
      }

      if have_nonblock?
        with_read_pipe("abc") {|r1|
          assert_equal("a", r1.getc)
          with_pipe {|r2, w2|
            w2.nonblock = true
            s = w2.syswrite("a" * 100000)
            t = Thread.new { sleep 0.1; r2.read }
            ret = IO.copy_stream(r1, w2)
            w2.close
            assert_equal(2, ret)
            assert_equal("a" * s + "bc", t.value)
          }
        }
      end

      bigcontent = "abc" * 123456
      File.open("bigsrc", "w") {|f| f << bigcontent }
      ret = IO.copy_stream("bigsrc", "bigdst")
      assert_equal(bigcontent.bytesize, ret)
      assert_equal(bigcontent, File.read("bigdst"))

      File.unlink("bigdst")
      ret = IO.copy_stream("bigsrc", "bigdst", nil, 100)
      assert_equal(bigcontent.bytesize-100, ret)
      assert_equal(bigcontent[100..-1], File.read("bigdst"))

      File.unlink("bigdst")
      ret = IO.copy_stream("bigsrc", "bigdst", 30000, 100)
      assert_equal(30000, ret)
      assert_equal(bigcontent[100, 30000], File.read("bigdst"))

      File.open("bigsrc") {|f|
        begin
          assert_equal(0, f.pos)
          ret = IO.copy_stream(f, "bigdst", nil, 10)
          assert_equal(bigcontent.bytesize-10, ret)
          assert_equal(bigcontent[10..-1], File.read("bigdst"))
          assert_equal(0, f.pos)
          ret = IO.copy_stream(f, "bigdst", 40, 30)
          assert_equal(40, ret)
          assert_equal(bigcontent[30, 40], File.read("bigdst"))
          assert_equal(0, f.pos)
        rescue NotImplementedError
          #skip "pread(2) is not implemtented."
        end
      }

      with_pipe {|r, w|
        w.close
        assert_raise(IOError) { IO.copy_stream("src", w) }
      }

      megacontent = "abc" * 1234567
      File.open("megasrc", "w") {|f| f << megacontent }

      if have_nonblock?
        with_pipe {|r1, w1|
          with_pipe {|r2, w2|
            t1 = Thread.new { w1 << megacontent; w1.close }
            t2 = Thread.new { r2.read }
            r1.nonblock = true
            w2.nonblock = true
            ret = IO.copy_stream(r1, w2)
            assert_equal(megacontent.bytesize, ret)
            w2.close
            t1.join
            assert_equal(megacontent, t2.value)
          }
        }
      end

      with_pipe {|r1, w1|
        with_pipe {|r2, w2|
          t1 = Thread.new { w1 << megacontent; w1.close }
          t2 = Thread.new { r2.read }
          ret = IO.copy_stream(r1, w2)
          assert_equal(megacontent.bytesize, ret)
          w2.close
          t1.join
          assert_equal(megacontent, t2.value)
        }
      }

      with_pipe {|r, w|
        t = Thread.new { r.read }
        ret = IO.copy_stream("megasrc", w)
        assert_equal(megacontent.bytesize, ret)
        w.close
        assert_equal(megacontent, t.value)
      }
    }
  end

  def test_copy_stream_rbuf
    mkcdtmpdir {
      begin
        with_pipe {|r, w|
          File.open("foo", "w") {|f| f << "abcd" }
          File.open("foo") {|f|
            f.read(1)
            assert_equal(3, IO.copy_stream(f, w, 10, 1))
          }
          w.close
          assert_equal("bcd", r.read)
        }
      rescue NotImplementedError
        skip "pread(2) is not implemtented."
      end
    }
  end

  def with_socketpair
    s1, s2 = UNIXSocket.pair
    begin
      yield s1, s2
    ensure
      s1.close unless s1.closed?
      s2.close unless s2.closed?
    end
  end

  def test_copy_stream_socket
    return unless defined? UNIXSocket
    mkcdtmpdir {

      content = "foobar"
      File.open("src", "w") {|f| f << content }

      with_socketpair {|s1, s2|
        ret = IO.copy_stream("src", s1)
        assert_equal(content.bytesize, ret)
        s1.close
        assert_equal(content, s2.read)
      }

      bigcontent = "abc" * 123456
      File.open("bigsrc", "w") {|f| f << bigcontent }

      with_socketpair {|s1, s2|
        t = Thread.new { s2.read }
        ret = IO.copy_stream("bigsrc", s1)
        assert_equal(bigcontent.bytesize, ret)
        s1.close
        result = t.value
        assert_equal(bigcontent, result)
      }

      with_socketpair {|s1, s2|
        t = Thread.new { s2.read }
        ret = IO.copy_stream("bigsrc", s1, 10000)
        assert_equal(10000, ret)
        s1.close
        result = t.value
        assert_equal(bigcontent[0,10000], result)
      }

      File.open("bigsrc") {|f|
        assert_equal(0, f.pos)
        with_socketpair {|s1, s2|
          t = Thread.new { s2.read }
          ret = IO.copy_stream(f, s1, nil, 100)
          assert_equal(bigcontent.bytesize-100, ret)
          assert_equal(0, f.pos)
          s1.close
          result = t.value
          assert_equal(bigcontent[100..-1], result)
        }
      }

      File.open("bigsrc") {|f|
        assert_equal(bigcontent[0,100], f.read(100))
        assert_equal(100, f.pos)
        with_socketpair {|s1, s2|
          t = Thread.new { s2.read }
          ret = IO.copy_stream(f, s1)
          assert_equal(bigcontent.bytesize-100, ret)
          assert_equal(bigcontent.length, f.pos)
          s1.close
          result = t.value
          assert_equal(bigcontent[100..-1], result)
        }
      }

      megacontent = "abc" * 1234567
      File.open("megasrc", "w") {|f| f << megacontent }

      if have_nonblock?
        with_socketpair {|s1, s2|
          t = Thread.new { s2.read }
          s1.nonblock = true
          ret = IO.copy_stream("megasrc", s1)
          assert_equal(megacontent.bytesize, ret)
          s1.close
          result = t.value
          assert_equal(megacontent, result)
        }
      end
    }
  end

  def test_copy_stream_strio
    src = StringIO.new("abcd")
    dst = StringIO.new
    ret = IO.copy_stream(src, dst)
    assert_equal(4, ret)
    assert_equal("abcd", dst.string)
    assert_equal(4, src.pos)
  end

  def test_copy_stream_strio_len
    src = StringIO.new("abcd")
    dst = StringIO.new
    ret = IO.copy_stream(src, dst, 3)
    assert_equal(3, ret)
    assert_equal("abc", dst.string)
    assert_equal(3, src.pos)
  end

  def test_copy_stream_strio_off
    src = StringIO.new("abcd")
    with_pipe {|r, w|
      assert_raise(ArgumentError) {
        IO.copy_stream(src, w, 3, 1)
      }
    }
  end

  def test_copy_stream_fname_to_strio
    mkcdtmpdir {
      File.open("foo", "w") {|f| f << "abcd" }
      src = "foo"
      dst = StringIO.new
      ret = IO.copy_stream(src, dst, 3)
      assert_equal(3, ret)
      assert_equal("abc", dst.string)
    }
  end

  def test_copy_stream_strio_to_fname
    mkcdtmpdir {
      # StringIO to filename
      src = StringIO.new("abcd")
      ret = IO.copy_stream(src, "fooo", 3)
      assert_equal(3, ret)
      assert_equal("abc", File.read("fooo"))
      assert_equal(3, src.pos)
    }
  end

  def test_copy_stream_io_to_strio
    mkcdtmpdir {
      # IO to StringIO
      File.open("bar", "w") {|f| f << "abcd" }
      File.open("bar") {|src|
        dst = StringIO.new
        ret = IO.copy_stream(src, dst, 3)
        assert_equal(3, ret)
        assert_equal("abc", dst.string)
        assert_equal(3, src.pos)
      }
    }
  end

  def test_copy_stream_strio_to_io
    mkcdtmpdir {
      # StringIO to IO
      src = StringIO.new("abcd")
      ret = File.open("baz", "w") {|dst|
        IO.copy_stream(src, dst, 3)
      }
      assert_equal(3, ret)
      assert_equal("abc", File.read("baz"))
      assert_equal(3, src.pos)
    }
  end

  class Rot13IO
    def initialize(io)
      @io = io
    end

    def readpartial(*args)
      ret = @io.readpartial(*args)
      ret.tr!('a-zA-Z', 'n-za-mN-ZA-M')
      ret
    end

    def write(str)
      @io.write(str.tr('a-zA-Z', 'n-za-mN-ZA-M'))
    end

    def to_io
      @io
    end
  end

  def test_copy_stream_io_to_rot13
    mkcdtmpdir {
      File.open("bar", "w") {|f| f << "vex" }
      File.open("bar") {|src|
        File.open("baz", "w") {|dst0|
          dst = Rot13IO.new(dst0)
          ret = IO.copy_stream(src, dst, 3)
          assert_equal(3, ret)
        }
        assert_equal("irk", File.read("baz"))
      }
    }
  end

  def test_copy_stream_rot13_to_io
    mkcdtmpdir {
      File.open("bar", "w") {|f| f << "flap" }
      File.open("bar") {|src0|
        src = Rot13IO.new(src0)
        File.open("baz", "w") {|dst|
          ret = IO.copy_stream(src, dst, 4)
          assert_equal(4, ret)
        }
      }
      assert_equal("sync", File.read("baz"))
    }
  end

  def test_copy_stream_rot13_to_rot13
    mkcdtmpdir {
      File.open("bar", "w") {|f| f << "bin" }
      File.open("bar") {|src0|
        src = Rot13IO.new(src0)
        File.open("baz", "w") {|dst0|
          dst = Rot13IO.new(dst0)
          ret = IO.copy_stream(src, dst, 3)
          assert_equal(3, ret)
        }
      }
      assert_equal("bin", File.read("baz"))
    }
  end

  def test_copy_stream_strio_flush
    with_pipe {|r, w|
      w.sync = false
      w.write "zz"
      src = StringIO.new("abcd")
      IO.copy_stream(src, w)
      t = Thread.new {
        w.close
      }
      assert_equal("zzabcd", r.read)
      t.join
    }
  end

  def test_copy_stream_strio_rbuf
    with_pipe {|r, w|
      w << "abcd"
      w.close
      assert_equal("a", r.read(1))
      sio = StringIO.new
      IO.copy_stream(r, sio)
      assert_equal("bcd", sio.string)
    }
  end

  def test_copy_stream_src_wbuf
    mkcdtmpdir {
      with_pipe {|r, w|
        File.open("foe", "w+") {|f|
          f.write "abcd\n"
          f.rewind
          f.write "xy"
          IO.copy_stream(f, w)
        }
        assert_equal("xycd\n", File.read("foe"))
        w.close
        assert_equal("cd\n", r.read)
        r.close
      }
    }
  end

  def test_copy_stream_dst_rbuf
    mkcdtmpdir {
      with_pipe {|r, w|
        w << "xyz"
        w.close
        File.open("fom", "w+b") {|f|
          f.write "abcd\n"
          f.rewind
          assert_equal("abc", f.read(3))
          f.ungetc "c"
          IO.copy_stream(r, f)
        }
        assert_equal("abxyz", File.read("fom"))
      }
    }
  end

  def safe_4
    Thread.new do
      Timeout.timeout(10) do
        $SAFE = 4
        yield
      end
    end.join
  end

  def pipe(wp, rp)
    r, w = IO.pipe
    rt = Thread.new { rp.call(r) }
    wt = Thread.new { wp.call(w) }
    flunk("timeout") unless rt.join(10) && wt.join(10)
  ensure
    r.close unless !r || r.closed?
    w.close unless !w || w.closed?
    (rt.kill; rt.join) if rt
    (wt.kill; wt.join) if wt
  end

  def ruby(*args)
    args = ['-e', '$>.write($<.read)'] if args.empty?
    ruby = EnvUtil.rubybin
    f = IO.popen([ruby] + args, 'r+')
    yield(f)
  ensure
    f.close unless !f || f.closed?
  end

  def test_try_convert
    assert_equal(STDOUT, IO.try_convert(STDOUT))
    assert_equal(nil, IO.try_convert("STDOUT"))
  end

  def test_ungetc2
    f = false
    pipe(proc do |w|
      0 until f
      w.write("1" * 10000)
      w.close
    end, proc do |r|
      r.ungetc("0" * 10000)
      f = true
      assert_equal("0" * 10000 + "1" * 10000, r.read)
    end)
  end

  def test_write_non_writable
    with_pipe do |r, w|
      assert_raise(IOError) do
        r.write "foobarbaz"
      end
    end
  end

  def test_dup
    ruby do |f|
      f2 = f.dup
      f.puts "foo"
      f2.puts "bar"
      f.close_write
      f2.close_write
      assert_equal("foo\nbar\n", f.read)
      assert_equal("", f2.read)
    end
  end

  def test_dup_many
    ruby('-e', <<-'End') {|f|
      ok = 0
      a = []
      begin
        loop {a << IO.pipe}
      rescue Errno::EMFILE, Errno::ENFILE, Errno::ENOMEM
        ok += 1
      end
      print "no" if ok != 1
      begin
        loop {a << [a[-1][0].dup, a[-1][1].dup]}
      rescue Errno::EMFILE, Errno::ENFILE, Errno::ENOMEM
        ok += 1
      end
      print "no" if ok != 2
      print "ok"
    End
      assert_equal("ok", f.read)
    }
  end

  def test_inspect
    with_pipe do |r, w|
      assert(r.inspect =~ /^#<IO:0x[0-9a-f]+>$/)
      assert_raise(SecurityError) do
        safe_4 { r.inspect }
      end
    end
  end

  def test_readpartial
    pipe(proc do |w|
      w.write "foobarbaz"
      w.close
    end, proc do |r|
      assert_raise(ArgumentError) { r.readpartial(-1) }
      assert_equal("fooba", r.readpartial(5))
      r.readpartial(5, s = "")
      assert_equal("rbaz", s)
    end)
  end

  def test_readpartial_error
    with_pipe do |r, w|
      s = ""
      t = Thread.new { r.readpartial(5, s) }
      0 until s.size == 5
      s.clear
      w.write "foobarbaz"
      w.close
      assert_raise(RuntimeError) { t.join }
    end
  end

  def test_read
    pipe(proc do |w|
      w.write "foobarbaz"
      w.close
    end, proc do |r|
      assert_raise(ArgumentError) { r.read(-1) }
      assert_equal("fooba", r.read(5))
      r.read(nil, s = "")
      assert_equal("rbaz", s)
    end)
  end

  def test_read_error
    with_pipe do |r, w|
      s = ""
      t = Thread.new { r.read(5, s) }
      0 until s.size == 5
      s.clear
      w.write "foobarbaz"
      w.close
      assert_raise(RuntimeError) { t.join }
    end
  end

  def test_write_nonblock
    skip "IO#write_nonblock is not supported on file/pipe." if /mswin|bccwin|mingw/ =~ RUBY_PLATFORM
    pipe(proc do |w|
      w.write_nonblock(1)
      w.close
    end, proc do |r|
      assert_equal("1", r.read)
    end)
  end

  def test_gets
    pipe(proc do |w|
      w.write "foobarbaz"
      w.close
    end, proc do |r|
      assert_equal("", r.gets(0))
      assert_equal("foobarbaz", s = r.gets(9))
    end)
  end

  def test_close_read
    ruby do |f|
      f.close_read
      f.write "foobarbaz"
      assert_raise(IOError) { f.read }
    end
  end

  def test_close_read_pipe
    with_pipe do |r, w|
      r.close_read
      assert_raise(Errno::EPIPE) { w.write "foobarbaz" }
    end
  end

  def test_close_read_security_error
    with_pipe do |r, w|
      assert_raise(SecurityError) do
        safe_4 { r.close_read }
      end
    end
  end

  def test_close_read_non_readable
    with_pipe do |r, w|
      assert_raise(IOError) do
        w.close_read
      end
    end
  end

  def test_close_write
    ruby do |f|
      f.write "foobarbaz"
      f.close_write
      assert_equal("foobarbaz", f.read)
    end
  end

  def test_close_write_security_error
    with_pipe do |r, w|
      assert_raise(SecurityError) do
        safe_4 { r.close_write }
      end
    end
  end

  def test_close_write_non_readable
    with_pipe do |r, w|
      assert_raise(IOError) do
        r.close_write
      end
    end
  end

  def test_pid
    r, w = IO.pipe
    assert_equal(nil, r.pid)
    assert_equal(nil, w.pid)

    pipe = IO.popen(EnvUtil.rubybin, "r+")
    pid1 = pipe.pid
    pipe.puts "p $$"
    pipe.close_write
    pid2 = pipe.read.chomp.to_i
    assert_equal(pid2, pid1)
    assert_equal(pid2, pipe.pid)
    pipe.close
    assert_raise(IOError) { pipe.pid }
  end

  def make_tempfile
    t = Tempfile.new("foo")
    t.binmode
    t.puts "foo"
    t.puts "bar"
    t.puts "baz"
    t.close
    t
  end

  def test_set_lineno
    t = make_tempfile

    ruby("-e", <<-SRC, t.path) do |f|
      open(ARGV[0]) do |f|
        p $.
        f.gets; p $.
        f.gets; p $.
        f.lineno = 1000; p $.
        f.gets; p $.
        f.gets; p $.
        f.rewind; p $.
        f.gets; p $.
        f.gets; p $.
        f.gets; p $.
        f.gets; p $.
      end
    SRC
      assert_equal("0,1,2,2,1001,1001,1001,1,2,3,3", f.read.chomp.gsub("\n", ","))
    end

    pipe(proc do |w|
      w.puts "foo"
      w.puts "bar"
      w.puts "baz"
      w.close
    end, proc do |r|
      r.gets; assert_equal(1, $.)
      r.gets; assert_equal(2, $.)
      r.lineno = 1000; assert_equal(2, $.)
      r.gets; assert_equal(1001, $.)
      r.gets; assert_equal(1001, $.)
    end)
  end

  def test_readline
    pipe(proc do |w|
      w.puts "foo"
      w.puts "bar"
      w.puts "baz"
      w.close
    end, proc do |r|
      r.readline; assert_equal(1, $.)
      r.readline; assert_equal(2, $.)
      r.lineno = 1000; assert_equal(2, $.)
      r.readline; assert_equal(1001, $.)
      assert_raise(EOFError) { r.readline }
    end)
  end

  def test_each_char
    pipe(proc do |w|
      w.puts "foo"
      w.puts "bar"
      w.puts "baz"
      w.close
    end, proc do |r|
      a = []
      r.each_char {|c| a << c }
      assert_equal(%w(f o o) + ["\n"] + %w(b a r) + ["\n"] + %w(b a z) + ["\n"], a)
    end)
  end

  def test_lines
    pipe(proc do |w|
      w.puts "foo"
      w.puts "bar"
      w.puts "baz"
      w.close
    end, proc do |r|
      e = r.lines
      assert_equal("foo\n", e.next)
      assert_equal("bar\n", e.next)
      assert_equal("baz\n", e.next)
      assert_raise(StopIteration) { e.next }
    end)
  end

  def test_bytes
    pipe(proc do |w|
      w.binmode
      w.puts "foo"
      w.puts "bar"
      w.puts "baz"
      w.close
    end, proc do |r|
      e = r.bytes
      (%w(f o o) + ["\n"] + %w(b a r) + ["\n"] + %w(b a z) + ["\n"]).each do |c|
        assert_equal(c.ord, e.next)
      end
      assert_raise(StopIteration) { e.next }
    end)
  end

  def test_chars
    pipe(proc do |w|
      w.puts "foo"
      w.puts "bar"
      w.puts "baz"
      w.close
    end, proc do |r|
      e = r.chars
      (%w(f o o) + ["\n"] + %w(b a r) + ["\n"] + %w(b a z) + ["\n"]).each do |c|
        assert_equal(c, e.next)
      end
      assert_raise(StopIteration) { e.next }
    end)
  end

  def test_readbyte
    pipe(proc do |w|
      w.binmode
      w.puts "foo"
      w.puts "bar"
      w.puts "baz"
      w.close
    end, proc do |r|
      r.binmode
      (%w(f o o) + ["\n"] + %w(b a r) + ["\n"] + %w(b a z) + ["\n"]).each do |c|
        assert_equal(c.ord, r.readbyte)
      end
      assert_raise(EOFError) { r.readbyte }
    end)
  end

  def test_readchar
    pipe(proc do |w|
      w.puts "foo"
      w.puts "bar"
      w.puts "baz"
      w.close
    end, proc do |r|
      (%w(f o o) + ["\n"] + %w(b a r) + ["\n"] + %w(b a z) + ["\n"]).each do |c|
        assert_equal(c, r.readchar)
      end
      assert_raise(EOFError) { r.readchar }
    end)
  end

  def test_close_on_exec
    skip "IO\#close_on_exec is not implemented." unless have_close_on_exec?
    ruby do |f|
      assert_equal(false, f.close_on_exec?)
      f.close_on_exec = true
      assert_equal(true, f.close_on_exec?)
      f.close_on_exec = false
      assert_equal(false, f.close_on_exec?)
    end

    with_pipe do |r, w|
      assert_equal(false, r.close_on_exec?)
      r.close_on_exec = true
      assert_equal(true, r.close_on_exec?)
      r.close_on_exec = false
      assert_equal(false, r.close_on_exec?)

      assert_equal(false, w.close_on_exec?)
      w.close_on_exec = true
      assert_equal(true, w.close_on_exec?)
      w.close_on_exec = false
      assert_equal(false, w.close_on_exec?)
    end
  end

  def test_close_security_error
    with_pipe do |r, w|
      assert_raise(SecurityError) do
        safe_4 { r.close }
      end
    end
  end

  def test_sysseek
    t = make_tempfile

    open(t.path) do |f|
      f.sysseek(-4, IO::SEEK_END)
      assert_equal("baz\n", f.read)
    end

    open(t.path) do |f|
      a = [f.getc, f.getc, f.getc]
      a.reverse_each {|c| f.ungetc c }
      assert_raise(IOError) { f.sysseek(1) }
    end
  end

  def test_syswrite
    t = make_tempfile

    open(t.path, "w") do |f|
      o = Object.new
      def o.to_s; "FOO\n"; end
      f.syswrite(o)
    end
    assert_equal("FOO\n", File.read(t.path))
  end

  def test_sysread
    t = make_tempfile

    open(t.path) do |f|
      a = [f.getc, f.getc, f.getc]
      a.reverse_each {|c| f.ungetc c }
      assert_raise(IOError) { f.sysread(1) }
    end
  end

  def test_flag
    t = make_tempfile

    assert_raise(ArgumentError) do
      open(t.path, "z") { }
    end

    assert_raise(ArgumentError) do
      open(t.path, "rr") { }
    end
  end

  def test_sysopen
    t = make_tempfile
    
    fd = IO.sysopen(t.path)
    assert_kind_of(Integer, fd)
    f = IO.for_fd(fd)
    assert_equal("foo\nbar\nbaz\n", f.read)
    f.close
    
    fd = IO.sysopen(t.path, "w", 0666)
    assert_kind_of(Integer, fd)
    if defined?(Fcntl::F_GETFL)
      f = IO.for_fd(fd)
    else
      f = IO.for_fd(fd, 0666)
    end
    f.write("FOO\n")
    f.close
    
    fd = IO.sysopen(t.path, "r")
    assert_kind_of(Integer, fd)
    f = IO.for_fd(fd)
    assert_equal("FOO\n", f.read)
    f.close
  end

  def test_open_redirect
    o = Object.new
    def o.to_open; self; end
    assert_equal(o, open(o))
    o2 = nil
    open(o) do |f|
      o2 = f
    end
    assert_equal(o, o2)
  end

  def test_open_pipe
    open("|" + EnvUtil.rubybin, "r+") do |f|
      f.puts "puts 'foo'"
      f.close_write
      assert_equal("foo\n", f.read)
    end
  end

  def test_reopen
    t = make_tempfile

    with_pipe do |r, w|
      assert_raise(SecurityError) do
        safe_4 { r.reopen(t.path) }
      end
    end

    open(__FILE__) do |f|
      f.gets
      assert_nothing_raised {
        f.reopen(t.path)
        assert_equal("foo\n", f.gets)
      }
    end
  end

  def test_foreach
    a = []
    IO.foreach("|" + EnvUtil.rubybin + " -e 'puts :foo; puts :bar; puts :baz'") {|x| a << x }
    assert_equal(["foo\n", "bar\n", "baz\n"], a)

    t = make_tempfile

    a = []
    IO.foreach(t.path) {|x| a << x }
    assert_equal(["foo\n", "bar\n", "baz\n"], a)

    a = []
    IO.foreach(t.path, {:mode => "r" }) {|x| a << x }
    assert_equal(["foo\n", "bar\n", "baz\n"], a)

    a = []
    IO.foreach(t.path, {:open_args => [] }) {|x| a << x }
    assert_equal(["foo\n", "bar\n", "baz\n"], a)

    a = []
    IO.foreach(t.path, {:open_args => ["r"] }) {|x| a << x }
    assert_equal(["foo\n", "bar\n", "baz\n"], a)

    a = []
    IO.foreach(t.path, "b") {|x| a << x }
    assert_equal(["foo\nb", "ar\nb", "az\n"], a)

    a = []
    IO.foreach(t.path, 3) {|x| a << x }
    assert_equal(["foo", "\n", "bar", "\n", "baz", "\n"], a)

    a = []
    IO.foreach(t.path, "b", 3) {|x| a << x }
    assert_equal(["foo", "\nb", "ar\n", "b", "az\n"], a)

  end

  def test_s_readlines
    t = make_tempfile

    assert_equal(["foo\n", "bar\n", "baz\n"], IO.readlines(t.path))
    assert_equal(["foo\nb", "ar\nb", "az\n"], IO.readlines(t.path, "b"))
    assert_equal(["fo", "o\n", "ba", "r\n", "ba", "z\n"], IO.readlines(t.path, 2))
    assert_equal(["fo", "o\n", "b", "ar", "\nb", "az", "\n"], IO.readlines(t.path, "b", 2))
  end

  def test_printf
    pipe(proc do |w|
      printf(w, "foo %s baz\n", "bar")
      w.close_write
    end, proc do |r|
      assert_equal("foo bar baz\n", r.read)
    end)
  end

  def test_print
    t = make_tempfile

    assert_in_out_err(["-", t.path], "print while $<.gets", %w(foo bar baz), [])
  end

  def test_putc
    pipe(proc do |w|
      w.putc "A"
      w.putc "BC"
      w.putc 68
      w.close_write
    end, proc do |r|
      assert_equal("ABD", r.read)
    end)

    assert_in_out_err([], "putc 65", %w(A), [])
  end

  def test_puts_recursive_array
    a = ["foo"]
    a << a
    pipe(proc do |w|
      w.puts a
      w.close
    end, proc do |r|
      assert_equal("foo\n[...]\n", r.read)
    end)
  end

  def test_display
    pipe(proc do |w|
      "foo".display(w)
      w.close
    end, proc do |r|
      assert_equal("foo", r.read)
    end)

    assert_in_out_err([], "'foo'.display", %w(foo), [])
  end

  def test_set_stdout
    assert_raise(TypeError) { $> = Object.new }

    assert_in_out_err([], "$> = $stderr\nputs 'foo'", [], %w(foo))
  end

  def test_initialize
    t = make_tempfile
    
    fd = IO.sysopen(t.path, "w")
    assert_kind_of(Integer, fd)
    f = IO.new(fd, "w")
    f.write("FOO\n")
    f.close

    assert_equal("FOO\n", File.read(t.path))

    f = open(t.path)
    assert_raise(RuntimeError) do
      f.instance_eval { initialize }
    end
  end
  
  def test_new_with_block
    assert_in_out_err([], "r, w = IO.pipe; IO.new(r) {}", [], /^.+$/)
  end

  def test_readline2
    assert_in_out_err(["-e", <<-SRC], "foo\nbar\nbaz\n", %w(foo bar baz end), [])
      puts readline
      puts readline
      puts readline
      begin
        puts readline
      rescue EOFError
        puts "end"
      end
    SRC
  end

  def test_readlines
    assert_in_out_err(["-e", "p readlines"], "foo\nbar\nbaz\n",
                      ["[\"foo\\n\", \"bar\\n\", \"baz\\n\"]"], [])
  end

  def test_s_read
    t = make_tempfile

    assert_equal("foo\nbar\nbaz\n", File.read(t.path))
    assert_equal("foo\nba", File.read(t.path, 6))
    assert_equal("bar\n", File.read(t.path, 4, 4))
  end

  def test_uninitialized
    assert_raise(IOError) { IO.allocate.print "" }
  end

  def test_nofollow
    # O_NOFOLLOW is not standard.
    return if /freebsd|linux/ !~ RUBY_PLATFORM
    return unless defined? File::NOFOLLOW
    mkcdtmpdir {
      open("file", "w") {|f| f << "content" }
      begin
        File.symlink("file", "slnk")
      rescue NotImplementedError
        return
      end
      assert_raise(Errno::EMLINK, Errno::ELOOP) {
        open("slnk", File::RDONLY|File::NOFOLLOW) {}
      }
      assert_raise(Errno::EMLINK, Errno::ELOOP) {
        File.foreach("slnk", :open_args=>[File::RDONLY|File::NOFOLLOW]) {}
      }
    }
  end
end
