require 'minitest/unit'
require File.join(File.expand_path(File.dirname(__FILE__)), 'gemutilities')
require 'rubygems/installer'

class Gem::Installer
  attr_accessor :gem_dir

  attr_writer :format
  attr_writer :gem_home
  attr_writer :env_shebang
  attr_writer :ignore_dependencies
  attr_writer :format_executable
  attr_writer :security_policy
  attr_writer :spec
  attr_writer :wrappers
end

class GemInstallerTestCase < RubyGemTestCase

  def setup
    super

    @spec = quick_gem "a"
    @gem = File.join @tempdir, "#{@spec.full_name}.gem"

    util_build_gem @spec
    FileUtils.mv File.join(@gemhome, 'cache', "#{@spec.full_name}.gem"),
                 @tempdir

    @installer = Gem::Installer.new @gem
    @installer.gem_dir = util_gem_dir
    @installer.gem_home = @gemhome
    @installer.spec = @spec
  end

  def util_gem_bindir(version = '2')
    File.join util_gem_dir(version), "bin"
  end

  def util_gem_dir(version = '2')
    File.join @gemhome, "gems", "a-#{version}" # HACK
  end

  def util_inst_bindir
    File.join @gemhome, "bin"
  end

  def util_make_exec(version = '2', shebang = "#!/usr/bin/ruby")
    @spec.executables = ["my_exec"]

    FileUtils.mkdir_p util_gem_bindir(version)
    exec_file = @installer.formatted_program_filename "my_exec"
    exec_path = File.join util_gem_bindir(version), exec_file
    File.open exec_path, 'w' do |f|
      f.puts shebang
    end
  end

  def util_setup_gem(ui = @ui) # HACK fix use_ui to make this automatic
    @spec.files = File.join('lib', 'code.rb')
    @spec.executables << 'executable'
    @spec.extensions << File.join('ext', 'a', 'mkrf_conf.rb')

    Dir.chdir @tempdir do
      FileUtils.mkdir_p 'bin'
      FileUtils.mkdir_p 'lib'
      FileUtils.mkdir_p File.join('ext', 'a')
      File.open File.join('bin', 'executable'), 'w' do |f| f.puts '1' end
      File.open File.join('lib', 'code.rb'), 'w' do |f| f.puts '1' end
      File.open File.join('ext', 'a', 'mkrf_conf.rb'), 'w' do |f|
        f << <<-EOF
          File.open 'Rakefile', 'w' do |rf| rf.puts "task :default" end
        EOF
      end

      use_ui ui do
        FileUtils.rm @gem
        Gem::Builder.new(@spec).build
      end
    end

    @installer = Gem::Installer.new @gem
  end

end

