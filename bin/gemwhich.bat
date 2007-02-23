@echo off
rem ---------------------------------------------------------------------------

call %~dp0_jrubyvars

%_STARTJAVA% -Xmx256m -ea -cp "%CLASSPATH%" -Djruby.base="%JRUBY_BASE%" -Djruby.home="%JRUBY_HOME%" -Djruby.lib="%JRUBY_HOME%\lib" -Djruby.shell="cmd.exe" -Djruby.script=jruby.bat org.jruby.Main %JRUBY_OPTS% "%JRUBY_HOME%\bin\gemwhich" %*
set E=%ERRORLEVEL%

call %~dp0_jrubycleanup
