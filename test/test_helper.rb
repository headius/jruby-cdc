require 'rbconfig'
require 'jruby' if defined?(JRUBY_VERSION)
require 'tempfile'

module TestHelper
  RUBY = File.join(Config::CONFIG['bindir'], Config::CONFIG['ruby_install_name'])
  WINDOWS = Config::CONFIG['host_os'] =~ /Windows|mswin/

  def jruby(*args)
    prev_in_process = JRuby.runtime.instance_config.run_ruby_in_process
    JRuby.runtime.instance_config.run_ruby_in_process = false
    `#{RUBY} #{args.join(' ')}`
  ensure
    JRuby.runtime.instance_config.run_ruby_in_process = prev_in_process
  end

  def with_temp_script(script)
    Tempfile.open(["test-script", ".rb"]) do |f|
      begin
        f << script
        f.close
      ensure
        begin
          # Should always yield, even in case of exceptions, otherwise
          # some tests won't even execute, and no failures will be shown
          yield f
        ensure
          f.unlink rescue nil
        end
      end
    end
  end
end
