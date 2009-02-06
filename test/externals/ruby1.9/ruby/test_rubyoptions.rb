require 'test/unit'

require 'tmpdir'
require 'tempfile'
require_relative 'envutil'

class TestRubyOptions < Test::Unit::TestCase
  def test_source_file
    assert_in_out_err([], "", [], [])
  end

  def test_usage
    assert_in_out_err(%w(-h)) do |r, e|
      assert_operator(r.size, :<=, 24)
      assert_equal([], e)
    end

    assert_in_out_err(%w(--help)) do |r, e|
      assert_operator(r.size, :<=, 24)
      assert_equal([], e)
    end
  end

  def test_option_variables
    assert_in_out_err(["-e", 'p [$-p, $-l, $-a]']) do |r, e|
      assert_equal(["[false, false, false]"], r)
      assert_equal([], e)
    end

    assert_in_out_err(%w(-p -l -a -e) + ['p [$-p, $-l, $-a]'],
                      "foo\nbar\nbaz\n") do |r, e|
      assert_equal(
        [ '[true, true, true]', 'foo',
          '[true, true, true]', 'bar',
          '[true, true, true]', 'baz' ], r)
      assert_equal([], e)
    end
  end

  def test_warning
    assert_in_out_err(%w(-W0 -e) + ['p $-W'], "", %w(0), [])
    assert_in_out_err(%w(-W1 -e) + ['p $-W'], "", %w(1), [])
    assert_in_out_err(%w(-Wx -e) + ['p $-W'], "", %w(1), [])
    assert_in_out_err(%w(-W -e) + ['p $-W'], "", %w(2), [])
  end

  def test_safe_level
    assert_in_out_err(%w(-T -e) + [""], "", [],
                      /no -e allowed in tainted mode \(SecurityError\)/)

    assert_in_out_err(%w(-T4 -S foo.rb), "", [],
                      /no -S allowed in tainted mode \(SecurityError\)/)
  end

  def test_debug
    assert_in_out_err(%w(-de) + ["p $DEBUG"], "", %w(true), [])

    assert_in_out_err(%w(--debug -e) + ["p $DEBUG"], "", %w(true), [])
  end

  def test_verbose
    assert_in_out_err(%w(-vve) + [""]) do |r, e|
      assert_match(/^ruby #{RUBY_VERSION}[p ].*? \[#{RUBY_PLATFORM}\]$/, r.join)
      assert_equal RUBY_DESCRIPTION, r.join.chomp
      assert_equal([], e)
    end

    assert_in_out_err(%w(--verbose -e) + ["p $VERBOSE"], "", %w(true), [])

    assert_in_out_err(%w(--verbose), "", [], [])
  end

  def test_copyright
    assert_in_out_err(%w(--copyright), "",
                      /^ruby - Copyright \(C\) 1993-\d+ Yukihiro Matsumoto$/, [])

    assert_in_out_err(%w(--verbose -e) + ["p $VERBOSE"], "", %w(true), [])
  end

  def test_enable
    assert_in_out_err(%w(--enable all -e) + [""], "", [], [])
    assert_in_out_err(%w(--enable-all -e) + [""], "", [], [])
    assert_in_out_err(%w(--enable=all -e) + [""], "", [], [])
    assert_in_out_err(%w(--enable foobarbazqux -e) + [""], "", [],
                      /unknown argument for --enable: `foobarbazqux'/)
    assert_in_out_err(%w(--enable), "", [], /missing argument for --enable/)
  end

  def test_disable
    assert_in_out_err(%w(--disable all -e) + [""], "", [], [])
    assert_in_out_err(%w(--disable-all -e) + [""], "", [], [])
    assert_in_out_err(%w(--disable=all -e) + [""], "", [], [])
    assert_in_out_err(%w(--disable foobarbazqux -e) + [""], "", [],
                      /unknown argument for --disable: `foobarbazqux'/)
    assert_in_out_err(%w(--disable), "", [], /missing argument for --disable/)
  end

  def test_kanji
    assert_in_out_err(%w(-KU), "p '\u3042'") do |r, e|
      assert_equal("\"\u3042\"", r.join.force_encoding(Encoding::UTF_8))
    end
    assert_in_out_err(%w(-KE -e) + [""], "", [], [])
    assert_in_out_err(%w(-KS -e) + [""], "", [], [])
    assert_in_out_err(%w(-KN -e) + [""], "", [], [])
  end

  def test_version
    assert_in_out_err(%w(--version)) do |r, e|
      assert_match(/^ruby #{RUBY_VERSION}[p ].*? \[#{RUBY_PLATFORM}\]$/, r.join)
      assert_equal RUBY_DESCRIPTION, r.join.chomp
      assert_equal([], e)
    end
  end

  def test_eval
    assert_in_out_err(%w(-e), "", [], /no code specified for -e \(RuntimeError\)/)
  end

  def test_require
    require "pp"
    assert_in_out_err(%w(-r pp -e) + ["pp 1"], "", %w(1), [])
    assert_in_out_err(%w(-rpp -e) + ["pp 1"], "", %w(1), [])
  rescue LoadError
  end

  def test_include
    d = Dir.tmpdir
    assert_in_out_err(["-I" + d, "-e", ""], "", [], [])
    assert_in_out_err(["-I", d, "-e", ""], "", [], [])
  end

  def test_separator
    assert_in_out_err(%w(-000 -e) + ["print gets"], "foo\nbar\0baz", %W(foo bar\0baz), [])

    assert_in_out_err(%w(-0141 -e) + ["print gets"], "foo\nbar\0baz", %w(foo ba), [])

    assert_in_out_err(%w(-0e) + ["print gets"], "foo\nbar\0baz", %W(foo bar\0), [])
  end

  def test_autosplit
    assert_in_out_err(%w(-an -F: -e) + ["p $F"], "foo:bar:baz\nqux:quux:quuux\n",
                      ['["foo", "bar", "baz\n"]', '["qux", "quux", "quuux\n"]'], [])
  end

  def test_chdir
    assert_in_out_err(%w(-C), "", [], /Can't chdir/)

    assert_in_out_err(%w(-C test_ruby_test_rubyoptions_foobarbazqux), "", [], /Can't chdir/)

    d = Dir.tmpdir
    assert_in_out_err(["-C", d, "-e", "puts Dir.pwd"]) do |r, e|
      assert(File.identical?(r.join, d))
      assert_equal([], e)
    end
  end

  def test_yydebug
    assert_in_out_err(["-ye", ""]) do |r, e|
      assert_equal([], r)
      assert_not_equal([], e)
    end

    assert_in_out_err(%w(--yydebug -e) + [""]) do |r, e|
      assert_equal([], r)
      assert_not_equal([], e)
    end
  end

  def test_encoding
    assert_in_out_err(%w(-Eutf-8), "p '\u3042'", [], /invalid multibyte char/)

    assert_in_out_err(%w(--encoding), "", [], /missing argument for --encoding/)

    assert_in_out_err(%w(--encoding test_ruby_test_rubyoptions_foobarbazqux), "", [],
                      /unknown encoding name - test_ruby_test_rubyoptions_foobarbazqux \(RuntimeError\)/)

    assert_in_out_err(%w(--encoding utf-8), "p '\u3042'", [], /invalid multibyte char/)
  end

  def test_syntax_check
    assert_in_out_err(%w(-c -e 1+1), "", ["Syntax OK"], [])
  end

  def test_invalid_option
    assert_in_out_err(%w(--foobarbazqux), "", [], /invalid option --foobarbazqux/)

    assert_in_out_err(%W(-\r -e) + [""], "", [], [])

    assert_in_out_err(%W(-\rx), "", [], /invalid option -\\x0D  \(-h will show valid options\) \(RuntimeError\)/)

    assert_in_out_err(%W(-\x01), "", [], /invalid option -\\x01  \(-h will show valid options\) \(RuntimeError\)/)

    assert_in_out_err(%w(-Z), "", [], /invalid option -Z  \(-h will show valid options\) \(RuntimeError\)/)
  end

  def test_rubyopt
    rubyopt_orig = ENV['RUBYOPT']

    ENV['RUBYOPT'] = ' - -'
    assert_in_out_err([], "", [], [])

    assert_in_out_err(['-e', 'p $:.include?(".")'], "", ["true"], [])

    ENV['RUBYOPT'] = '-e "p 1"'
    assert_in_out_err([], "", [], /invalid switch in RUBYOPT: -e \(RuntimeError\)/)

    ENV['RUBYOPT'] = '-T1'
    assert_in_out_err([], "", [], /no program input from stdin allowed in tainted mode \(SecurityError\)/)

    assert_in_out_err(['-e', 'p $:.include?(".")'], "", ["false"], [])

    ENV['RUBYOPT'] = '-T4'
    assert_in_out_err([], "", [], /no program input from stdin allowed in tainted mode \(SecurityError\)/)

    ENV['RUBYOPT'] = '-Eus-ascii -KN'
    assert_in_out_err(%w(-Eutf-8 -KU), "p '\u3042'") do |r, e|
      assert_equal("\"\u3042\"", r.join.force_encoding(Encoding::UTF_8))
      assert_equal([], e)
    end

  ensure
    if rubyopt_orig
      ENV['RUBYOPT'] = rubyopt_orig
    else
      ENV.delete('RUBYOPT')
    end
  end

  def test_search
    rubypath_orig = ENV['RUBYPATH']
    path_orig = ENV['PATH']

    t = Tempfile.new(["test_ruby_test_rubyoption", ".rb"])
    t.puts "p 1"
    t.close

    @verbose = $VERBOSE
    $VERBOSE = nil

    ENV['PATH'] = File.dirname(t.path)

    assert_in_out_err(%w(-S) + [File.basename(t.path)], "", %w(1), [])

    ENV['RUBYPATH'] = File.dirname(t.path)

    assert_in_out_err(%w(-S) + [File.basename(t.path)], "", %w(1), [])

  ensure
    if rubypath_orig
      ENV['RUBYPATH'] = rubypath_orig
    else
      ENV.delete('RUBYPATH')
    end
    if path_orig
      ENV['PATH'] = path_orig
    else
      ENV.delete('PATH')
    end
    t.close(true) if t
    $VERBOSE = @verbose
  end

  def test_shebang
    assert_in_out_err([], "#! /test_r_u_b_y_test_r_u_b_y_options_foobarbazqux\r\np 1\r\n",
                      [], /Can't exec [\/\\]test_r_u_b_y_test_r_u_b_y_options_foobarbazqux \(fatal\)/)

    assert_in_out_err([], "#! /test_r_u_b_y_test_r_u_b_y_options_foobarbazqux -foo -bar\r\np 1\r\n",
                      [], /Can't exec [\/\\]test_r_u_b_y_test_r_u_b_y_options_foobarbazqux \(fatal\)/)

    assert_in_out_err([], "#!ruby -KU -Eutf-8\r\np \"\u3042\"\r\n") do |r, e|
      assert_equal("\"\u3042\"", r.join.force_encoding(Encoding::UTF_8))
      assert_equal([], e)
    end
  end

  def test_sflag
    assert_in_out_err(%w(- -abc -def=foo -ghi-jkl -- -xyz),
                      "#!ruby -s\np [$abc, $def, $ghi_jkl, $xyz]\n",
                      ['[true, "foo", true, nil]'], [])

    assert_in_out_err(%w(- -#), "#!ruby -s\n", [],
                      /invalid name for global variable - -# \(NameError\)/)

    assert_in_out_err(%w(- -#=foo), "#!ruby -s\n", [],
                      /invalid name for global variable - -# \(NameError\)/)
  end
end
