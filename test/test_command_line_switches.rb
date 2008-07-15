require 'test/unit'
require 'test/test_helper'

class TestCommandLineSwitches < Test::Unit::TestCase
  include TestHelper

  def test_dash_0_splits_records
    output = `echo '1,2,3' | #{RUBY} -054 -n -e 'puts $_ + " "'`
    
    assert_equal "1, ,2, ,3\n ,", output
  end

  def test_dash_little_a_splits_input
    output = `echo '1 2 3' | #{RUBY} -a -n -e 'puts $F.join(",")'`
    assert_equal "1,2,3\n", output
  end
  
  def test_dash_little_b_benchmarks
    assert_match /Runtime: \d+ ms/, `#{RUBY} -b -e 'puts nil'`
  end

  def test_dash_little_c_checks_syntax
    with_jruby_shell_spawning do
      with_temp_script(%q{ bad : code }) do |s|
        assert_match /SyntaxError/, `#{RUBY} -c #{s.path} 2>&1`
      end
    end
  end

  def test_dash_little_c_checks_syntax_only
    with_jruby_shell_spawning do
      with_temp_script(%q{ puts "a" }) do |s|
        assert_equal "Syntax OK\n", `#{RUBY} -c #{s.path}`
      end
    end
  end

  def test_dash_big_c_changes_directory
    parent_dir = Dir.chdir('..') { Dir.pwd }
    assert_equal "#{parent_dir}\n", `#{RUBY} -C .. -e 'puts Dir.pwd'`
  end

  def test_dash_little_d_sets_debug_flag
    assert_equal "true\n", `#{RUBY} -d -e 'puts $DEBUG'`
  end

  def test_dash_little_e_executes_code
    assert_equal "nil\n", `#{RUBY} -e 'puts nil'`
  end

  def test_dash_big_f_changes_autosplit_pattern
    assert_equal "1;2;3\n", `echo '1,2,3' | #{RUBY} -a -F, -n -e 'puts $F.join(";")'`
  end

  def test_dash_big_i_puts_argument_on_load_path
    assert_match /^hello/, `#{RUBY} -Ihello -e 'puts $LOAD_PATH'`
  end

  def test_dash_big_j_sets_java_properties
    with_jruby_shell_spawning do
      assert_match /ruby 1\.9/, `#{RUBY} -J-Djruby.compat.version=RUBY1_9 --version`
    end
  end
  
  def test_dash_big_k_sets_kcode
    assert_equal "UTF8\n", `#{RUBY} -Ku -e 'puts $KCODE'`
  end

  # TODO -l: no idea what line ending processing is
  def test_dash_little_n_wraps_script_with_while_gets
    with_temp_script(%q{ puts "#{$_}#{$_}" }) do |s|
      output = IO.popen("echo \"a\nb\" | #{RUBY} -n #{s.path}", "r") { |p| p.read }
      assert_equal "a\na\nb\nb\n", output
    end
  end
  
  def test_dash_little_p_wraps_script_with_while_gets_and_prints
    with_temp_script(%q{ puts "#{$_}#{$_}" }) do |s|
      output = IO.popen("echo \"a\nb\" | #{RUBY} -p #{s.path}", "r") { |p| p.read }
      assert_equal "a\na\na\nb\nb\nb\n", output
    end
  end

  def test_little_r_requires_library
    assert_equal "constant\n", `#{RUBY} -rrubygems -e 'puts defined?(Gem)'`
  end

  def test_dash_little_s_one_keyval
    with_temp_script(%q{puts $v}) do |s|
      assert_equal "123", `#{RUBY} -s #{s.path} -v=123`.chomp
    end
  end

  def test_dash_little_s_two_keyvals
    with_temp_script(%q{puts $v, $foo}) do |s|
      assert_equal "123\nbar", `#{RUBY} -s #{s.path} -v=123 -foo=bar`.chomp
    end
  end

  def test_dash_little_s_removes_options_from_argv
    with_temp_script(%q{puts $v, *ARGV}) do |s|
      assert_equal "123\n4\n5\n6", `#{RUBY} -s #{s.path} -v=123 4 5 6`.chomp
    end
  end

  def test_dash_little_s_options_must_come_after_script
    with_temp_script(%q{puts $v, *ARGV}) do |s|
      assert_equal "nil\na\n-v=123\nb\nc", `#{RUBY} -s #{s.path} a -v=123 b c`.chomp
    end
  end

  def test_dash_little_s_options_ignores_invalid_global_var_names
    with_temp_script(%q{puts $v}) do |s|
      assert_equal "nil", `#{RUBY} -s #{s.path} -v-a=123`.chomp
    end
  end

  # JRUBY-2693
  def test_dash_little_r_provides_prorgam_name_to_loaded_library
    with_temp_script(%q{puts $0; puts $PROGRAM_NAME}) do |s|
      assert_equal(
        "#{s.path}\n#{s.path}\n#{s.path}\n#{s.path}\n",
        jruby("-r#{s.path} #{s.path}")
      )
    end
  end

  # This test is difficult to indicate meaning with. I am calling 
  # jgem, as it should not exist outside the jruby.bin directory.
  def test_dash_big_S_executes_script_in_jruby_bin_dir
    assert_match /^\d+\.\d+\.\d+/, `#{RUBY} -S jgem --version`
  end

  def test_dash_big_T_sets_taint_level
    assert_equal "3\n", `#{RUBY} -T3 -e 'puts $SAFE'`
  end

  def test_dash_little_v_prints_version
    assert_match /ruby \d+\.\d+\.\d+/, `ruby -v -e 'a = 1'`
  end
  
  def test_dash_little_v_turns_on_verbose_mode
    assert_match /true\n$/, `#{RUBY} -v -e 'puts $VERBOSE'`
  end
  
  def test_dash_little_w_turns_warnings_on
    with_jruby_shell_spawning do
      assert_match /warning/, `#{RUBY} -v -e 'defined? true' 2>&1`
    end
  end

  def test_dash_big_w_sets_warning_level
    with_jruby_shell_spawning do
      assert_equal "", `#{RUBY} -W1 -e 'defined? true' 2>&1`
      assert_match /warning/, `#{RUBY} -W2 -e 'defined? true' 2>&1`
    end    
  end

  def test_dash_big_x_sets_extended_options
    # turn on ObjectSpace
    assert_no_match /ObjectSpace is disabled/,
      `#{RUBY} -X+O -e 'ObjectSpace.each_object(Fixnum) {|o| puts o.inspect}'`
  end

  def test_dash_dash_copyright_displays_copyright
    assert_equal "JRuby - Copyright (C) 2001-2008 The JRuby Community (and contribs)\n",
      `#{RUBY} --copyright`
  end

  # TODO --debug: cannot figure out how to test

  # TODO --jdb: cannot figure out how to test

  def test_dash_dash_properties_shows_list_of_properties
    assert_match /^These properties can be used/, `#{RUBY} --properties`
  end

  def test_dash_dash_version_shows_version
    version_string = `#{RUBY} --version`
    assert_match /ruby \d+\.\d+\.\d+/, version_string
    assert_match /jruby \d+\.\d+\.\d+/, version_string
  end

  # JRUBY-2805
  def test_args_with_equals_sign
    result = jruby(%q{ -rjava -J-Dfoo=bar -e "print java.lang.System.getProperty('foo')"})
    assert_equal("bar", result)
  end

  # JRUBY-2648
  def test_server_vm_option
    # server VM when explicitly set --server
    result = jruby("--server -rjava \
      -e 'print java.lang.management.ManagementFactory.getCompilationMXBean.name'")
    assert_match /tiered/, result.downcase

    # server VM when explicitly set via -J-server
    result = jruby("-J-server -rjava \
      -e 'print java.lang.management.ManagementFactory.getCompilationMXBean.name'")
    assert_match /tiered/, result.downcase
  end

  # JRUBY-2648
  def test_client_vm_option
    # client VM by default:
    result = jruby("-rjava \
      -e 'print java.lang.management.ManagementFactory.getCompilationMXBean.name'")
    assert_match /client/, result.downcase

    # client VM when explicitly set via --client
    result = jruby("--client -rjava \
      -e 'print java.lang.management.ManagementFactory.getCompilationMXBean.name'")
    assert_match /client/, result.downcase

    # client VM when explicitly set via -J-client
    result = jruby("-J-client -rjava \
      -e 'print java.lang.management.ManagementFactory.getCompilationMXBean.name'")
    assert_match /client/, result.downcase
  end
end
