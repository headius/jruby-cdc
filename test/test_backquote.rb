require 'test/unit'
require 'rbconfig'

class TestBackquote < Test::Unit::TestCase
  WINDOWS = Config::CONFIG['host_os'] =~ /Windows|mswin/
  def test_backquote_special_commands
    if File.exists?("/bin/echo")
      output = `/bin/echo hello`
      assert_equal("hello\n", output)
    end
  end

  def test_system_special_commands
    if File.exists?("/bin/true")
      assert(system("/bin/true"))
      assert_equal(0, $?.exitstatus)
    end

    if File.exists?("/bin/false")
      assert(! system("/bin/false"))
      assert($?.exitstatus > 0)
    end
  end

  def test_backquote_ruby
    assert_equal "true\n", `ruby -e "puts true"`
  end

  #JRUBY-2251
  def test_empty_backquotes
    if (!WINDOWS)
      assert_equal("", ``)    # empty
      assert_equal("", `   `) # spaces
      assert_equal("", `\n`)
    else # we just check that empty backquotes won't blow up JRuby
      ``    rescue nil
      `   ` rescue nil
      `\n`  rescue nil
    end
  end

  # http://jira.codehaus.org/browse/JRUBY-1557
  def test_backquotes_with_redirects_pass_through_shell
    if File.exists?("/dev/null")
      File.open("arguments", "w") do |f|
        f << %q{#!/bin/sh} << "\n"
        f << %q{echo "arguments: $@"}
      end
      File.chmod 0755, "arguments"

      assert_equal "arguments: one two\n", `./arguments one two 2> /dev/null`
      assert_equal "", `./arguments three four > /dev/null`
      ruby = File.join(Config::CONFIG['bindir'], Config::CONFIG['ruby_install_name'])
      assert_equal "arguments: five six\n", `#{ruby} -e 'puts "arguments: five six"' 2> /dev/null`
    end
  ensure
    File.delete("arguments") rescue nil
  end
end
