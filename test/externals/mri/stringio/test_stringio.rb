require 'test/unit'
require 'stringio'
dir = File.expand_path(__FILE__)
2.times {dir = File.dirname(dir)}
$:.replace([File.join(dir, "ruby")] | $:)
require 'ut_eof'

class TestStringIO < Test::Unit::TestCase
  include TestEOF
  def open_file(content)
    f = StringIO.new(content)
    yield f
  end
  alias open_file_rw open_file

  include TestEOF::Seek

  def test_truncate
    io = StringIO.new("")
    io.puts "abc"
    io.truncate(0)
    io.puts "def"
    assert_equal("\0\0\0\0def\n", io.string, "[ruby-dev:24190]")
    assert_raise(Errno::EINVAL) { io.truncate(-1) }
    io.truncate(10)
    assert_equal("\0\0\0\0def\n\0\0", io.string)
  end

  def test_seek_beyond_eof
    io = StringIO.new
    n = 100
    io.seek(n)
    io.print "last"
    assert_equal("\0" * n + "last", io.string, "[ruby-dev:24194]")
  end

  def test_overwrite
    stringio = StringIO.new
    responses = ['', 'just another ruby', 'hacker']
    responses.each do |resp|
      stringio.puts(resp)
      stringio.rewind
    end
    assert_equal("hacker\nother ruby\n", stringio.string, "[ruby-core:3836]")
  end

  def test_gets
    assert_equal(nil, StringIO.new("").gets)
    assert_equal("\n", StringIO.new("\n").gets)
    assert_equal("a\n", StringIO.new("a\n").gets)
    assert_equal("a\n", StringIO.new("a\nb\n").gets)
    assert_equal("a", StringIO.new("a").gets)
    assert_equal("a\n", StringIO.new("a\nb").gets)
    assert_equal("abc\n", StringIO.new("abc\n\ndef\n").gets)
    assert_equal("abc\n\ndef\n", StringIO.new("abc\n\ndef\n").gets(nil))
    # MRI 1.9 behavior:
    # assert_equal("abc\n\n", StringIO.new("abc\n\ndef\n").gets(""))
  end

  def test_readlines
    assert_equal([], StringIO.new("").readlines)
    assert_equal(["\n"], StringIO.new("\n").readlines)
    assert_equal(["a\n"], StringIO.new("a\n").readlines)
    assert_equal(["a\n", "b\n"], StringIO.new("a\nb\n").readlines)
    assert_equal(["a"], StringIO.new("a").readlines)
    assert_equal(["a\n", "b"], StringIO.new("a\nb").readlines)
    assert_equal(["abc\n", "\n", "def\n"], StringIO.new("abc\n\ndef\n").readlines)
    assert_equal(["abc\n\ndef\n"], StringIO.new("abc\n\ndef\n").readlines(nil), "[ruby-dev:34591]")
    # MRI 1.9 behavior:
    # assert_equal(["abc\n\n", "def\n"], StringIO.new("abc\n\ndef\n").readlines(""))
  end

  def test_write
    s = ""
    f = StringIO.new(s, "w")
    f.print("foo")
    f.close
    assert_equal("foo", s)

    f = StringIO.new(s, File::WRONLY)
    f.print("bar")
    f.close
    assert_equal("bar", s)

    f = StringIO.new(s, "a")
    o = Object.new
    def o.to_s; "baz"; end
    f.print(o)
    f.close
    assert_equal("barbaz", s)
  ensure
    f.close unless f.closed?
  end

  def test_mode_error
    f = StringIO.new("", "r")
    assert_raise(IOError) { f.write("foo") }

    f = StringIO.new("", "w")
    assert_raise(IOError) { f.read }

    assert_raise(Errno::EACCES) { StringIO.new("".freeze, "w") }
    s = ""
    f = StringIO.new(s, "w")
    s.freeze
    assert_raise(IOError) { f.write("foo") }

    assert_raise(IOError) { StringIO.allocate.read }
  ensure
    f.close unless f.closed?
  end

  def test_open
    s = ""
    StringIO.open("foo") {|f| s = f.read }
    assert_equal("foo", s)
  end

  def test_isatty
    assert_equal(false, StringIO.new("").isatty)
  end
  
  def test_path
    assert_equal(nil, StringIO.new("").path)
  end

  def test_fsync
    assert_equal(0, StringIO.new("").fsync)
  end

  def test_sync
    assert_equal(true, StringIO.new("").sync)
    assert_equal(false, StringIO.new("").sync = false)
  end

  def test_set_fcntl
    assert_raise(NotImplementedError) { StringIO.new("").fcntl }
  end
  
  def test_close
    f = StringIO.new("")
    f.close
    assert_raise(IOError) { f.close }

    f = StringIO.new("")
    f.close_read
    f.close_write
    assert_raise(IOError) { f.close }
  ensure
    f.close unless f.closed?
  end
  
  def test_close_read
    f = StringIO.new("")
    f.close_read
    assert_raise(IOError) { f.read }
    assert_raise(IOError) { f.close_read }
    f.close

    f = StringIO.new("", "w")
    assert_raise(IOError) { f.close_read }
    f.close
  ensure
    f.close unless f.closed?
  end
  
  def test_close_write
    f = StringIO.new("")
    f.close_write
    assert_raise(IOError) { f.write("foo") }
    assert_raise(IOError) { f.close_write }
    f.close

    f = StringIO.new("", "r")
    assert_raise(IOError) { f.close_write }
    f.close
  ensure
    f.close unless f.closed?
  end

  def test_closed
    f = StringIO.new("")
    assert_equal(false, f.closed?)
    f.close
    assert_equal(true, f.closed?)
  ensure
    f.close unless f.closed?
  end

  def test_closed_read
    f = StringIO.new("")
    assert_equal(false, f.closed_read?)
    f.close_write
    assert_equal(false, f.closed_read?)
    f.close_read
    assert_equal(true, f.closed_read?)
  ensure
    f.close unless f.closed?
  end

  def test_closed_write
    f = StringIO.new("")
    assert_equal(false, f.closed_write?)
    f.close_read
    assert_equal(false, f.closed_write?)
    f.close_write
    assert_equal(true, f.closed_write?)
  ensure
    f.close unless f.closed?
  end

  # Ruby 1.9 specific
  def XXXtest_dup
    f1 = StringIO.new("1234")
    assert_equal("1", f1.getc)
    f2 = f1.dup
    assert_equal("2", f2.getc)
    assert_equal("3", f1.getc)
    assert_equal("4", f2.getc)
    assert_equal(nil, f1.getc)
    assert_equal(true, f2.eof?)
    f1.close
    assert_equal(true, f2.closed?)
  ensure
    f1.close unless f1.closed?
    f2.close unless f2.closed?
  end

  def test_lineno
    f = StringIO.new("foo\nbar\nbaz\n")
    assert_equal([0, "foo\n"], [f.lineno, f.gets])
    assert_equal([1, "bar\n"], [f.lineno, f.gets])
    f.lineno = 1000
    assert_equal([1000, "baz\n"], [f.lineno, f.gets])
    assert_equal([1001, nil], [f.lineno, f.gets])
  ensure
    f.close unless f.closed?
  end

  def test_pos
    f = StringIO.new("foo\nbar\nbaz\n")
    assert_equal([0, "foo\n"], [f.pos, f.gets])
    assert_equal([4, "bar\n"], [f.pos, f.gets])
    assert_raise(Errno::EINVAL) { f.pos = -1 }
    f.pos = 1
    assert_equal([1, "oo\n"], [f.pos, f.gets])
    assert_equal([4, "bar\n"], [f.pos, f.gets])
    assert_equal([8, "baz\n"], [f.pos, f.gets])
    assert_equal([12, nil], [f.pos, f.gets])
  ensure
    f.close unless f.closed?
  end

  def test_reopen
    f = StringIO.new("foo\nbar\nbaz\n")
    assert_equal("foo\n", f.gets)
    f.reopen("qux\nquux\nquuux\n")
    assert_equal("quux\n", f.gets)

    f2 = StringIO.new("")
    f2.reopen(f)
    assert_equal("quuux\n", f2.gets)
  ensure
    f.close unless f.closed?
  end

  def test_seek
    f = StringIO.new("1234")
    assert_raise(Errno::EINVAL) { f.seek(-1) }
    f.seek(-1, 2)
    # Ruby 1.9 behavior:
    # assert_equal("4", f.getc)
    # Ruby 1.8.7+ behovior:
    # assert_raise(Errno::EINVAL) { f.seek(1, 3) }
    f.close
    # Ruby 1.8.7+ behovior:
    # assert_raise(IOError) { f.seek(0) }
  ensure
    f.close unless f.closed?
  end

  # Ruby 1.9 specific
  def XXXtest_each_byte
    f = StringIO.new("1234")
    a = []
    f.each_byte {|c| a << c }
    assert_equal(%w(1 2 3 4).map {|c| c.ord }, a)
  ensure
    f.close unless f.closed?
  end

  # Ruby 1.9 specific
  def XXXtest_getbyte
    f = StringIO.new("1234")
    assert_equal("1".ord, f.getbyte)
    assert_equal("2".ord, f.getbyte)
    assert_equal("3".ord, f.getbyte)
    assert_equal("4".ord, f.getbyte)
    assert_equal(nil, f.getbyte)
  ensure
    f.close unless f.closed?
  end

  # Ruby 1.9 behavior:
  def XXXtest_ungetc
    s = "1234"
    f = StringIO.new(s, "r")
    assert_nothing_raised { f.ungetc("x") }
    assert_equal("x", f.getc) # bug?
    assert_equal("1", f.getc)

    s = "1234"
    f = StringIO.new(s, "r")
    assert_equal("1", f.getc)
    f.ungetc("y".ord)
    assert_equal("y", f.getc)
    assert_equal("2", f.getc)
  ensure
    f.close unless f.closed?
  end

  def test_readchar
    f = StringIO.new("1234")
    a = ""
    assert_raise(EOFError) { loop { a << f.readchar } }
    assert_equal("1234", a)
  end

  # Ruby 1.8.7+ behavior
  def XXXtest_readbyte
    f = StringIO.new("1234")
    a = []
    assert_raise(EOFError) { loop { a << f.readbyte } }
    assert_equal("1234".unpack("C*"), a)
  end

  # Ruby 1.9 specific
  def XXXtest_each_char
    f = StringIO.new("1234")
    assert_equal(%w(1 2 3 4), f.each_char.to_a)
  end

  # Ruby 1.9 behavior
  def XXXtest_gets2
    f = StringIO.new("foo\nbar\nbaz\n")
    assert_equal("fo", f.gets(2))

    o = Object.new
    def o.to_str; "z"; end
    assert_equal("o\nbar\nbaz", f.gets(o))

    f = StringIO.new("foo\nbar\nbaz\n")
    assert_equal("foo\nbar\nbaz", f.gets("az"))
    f = StringIO.new("a" * 10000 + "zz!")
    assert_equal("a" * 10000 + "zz", f.gets("zz"))
    f = StringIO.new("a" * 10000 + "zz!")
    assert_equal("a" * 10000 + "zz!", f.gets("zzz"))
  end

  # Ruby 1.8.7 specific
  def XXXtest_each
    f = StringIO.new("foo\nbar\nbaz\n")
    assert_equal(["foo\n", "bar\n", "baz\n"], f.each.to_a)
  end

  def test_putc
    s = ""
    f = StringIO.new(s, "w")
    f.putc("1")
    f.putc("2")
    f.putc("3")
    f.close
    assert_equal("123", s)

    s = "foo"
    f = StringIO.new(s, "a")
    f.putc("1")
    f.putc("2")
    f.putc("3")
    f.close
    assert_equal("foo123", s)
  end

  def test_read
    f = StringIO.new("1234")
    assert_raise(ArgumentError) { f.read(-1) }
    assert_raise(ArgumentError) { f.read(1, 2, 3) }
  end

  def test_size
    f = StringIO.new("1234")
    assert_equal(4, f.size)
  end

end
