require 'test/minirunit'

test_check "Test File"

# dry tests which don't access the file system

test_ok(File::SEPARATOR)
test_ok(File::PATH_SEPARATOR)

# basename
test_equal("", File.basename(""))
test_equal("a", File.basename("a"))
test_equal("b", File.basename("a/b"))
test_equal("c", File.basename("a/b/c"))

test_equal("b", File.basename("a/b", ""))
test_equal("b", File.basename("a/bc", "c"))
test_equal("b", File.basename("a/b.c", ".c"))
test_equal("b", File.basename("a/b.c", ".*"))
test_equal(".c", File.basename("a/.c", ".*"))


test_equal("a", File.basename("a/"))
test_equal("b", File.basename("a/b/"))
test_equal("/", File.basename("/"))

test_equal("/bin", File.expand_path("../../bin", "/foo/bar"))
test_equal("/foo/bin", File.expand_path("../bin", "/foo/bar"))
test_equal("//abc/def/jkl/mno", File.expand_path("//abc//def/jkl//mno"))
test_equal("//abc/def/jkl/mno/foo", File.expand_path("foo", "//abc//def/jkl//mno"))
begin
    File.expand_path("~nonexistent")
    test_ok(false)
rescue ArgumentError => e
    test_ok(true)
rescue Exception => e
    test_ok(false)
end

# Until windows and macos have code to get this info correctly we will 
# not include
#username = ENV['USER']
#home = ENV['HOME']
#if (username && home) 
#  test_equal(home, File.expand_path("~#{username}"))
#  test_equal(home, File.expand_path("~"))
#  test_equal(home, File.expand_path("~/"))
#end

# dirname
test_equal(".", File.dirname(""))
test_equal(".", File.dirname("."))
test_equal(".", File.dirname(".."))
test_equal(".", File.dirname("a"))
test_equal(".", File.dirname("./a"))
test_equal("./a", File.dirname("./a/b"))
test_equal("/", File.dirname("/"))
test_equal("/", File.dirname("/a"))
test_equal("/a", File.dirname("/a/b"))
test_equal("/a", File.dirname("/a/b/"))
test_equal("/", File.dirname("/"))

test_equal("", File.extname(""))
test_equal("", File.extname("abc"))
test_equal(".foo", File.extname("abc.foo"))
test_equal(".foo", File.extname("abc.bar.foo"))
test_equal("", File.extname("abc.bar/foo"))

# expand_path

##### fnmatch #####
test_equal(true, File.fnmatch('cat', 'cat'))
test_equal(false, File.fnmatch('cat', 'category'))
test_equal(false, File.fnmatch('c{at,ub}s', 'cats'))
test_equal(false, File.fnmatch('c{at,ub}s', 'cubs'))
test_equal(false, File.fnmatch('c{at,ub}s', 'cat'))

test_equal(true, File.fnmatch('c?t', 'cat'))
test_equal(false, File.fnmatch('c\?t', 'cat'))
test_equal(true, File.fnmatch('c\?t', 'c?t'))
test_equal(false, File.fnmatch('c??t', 'cat'))
test_equal(true, File.fnmatch('c*', 'cats'));
test_equal(true, File.fnmatch('c*t', 'cat'))
#test_equal(true, File.fnmatch('c\at', 'cat')) # Doesn't work correctly on both Unix and Win32
test_equal(false, File.fnmatch('c\at', 'cat', File::FNM_NOESCAPE))
test_equal(true, File.fnmatch('a?b', 'a/b'))
test_equal(false, File.fnmatch('a?b', 'a/b', File::FNM_PATHNAME))
test_equal(false, File.fnmatch('a?b', 'a/b', File::FNM_PATHNAME))

test_equal(false, File.fnmatch('*', '.profile'))
test_equal(true, File.fnmatch('*', '.profile', File::FNM_DOTMATCH))
test_equal(true, File.fnmatch('*', 'dave/.profile'))
test_equal(true, File.fnmatch('*', 'dave/.profile', File::FNM_DOTMATCH))
test_equal(false, File.fnmatch('*', 'dave/.profile', File::FNM_PATHNAME))

test_equal(false, File.fnmatch("/.ht*",""))
test_equal(false, File.fnmatch("/*~",""))
test_equal(false, File.fnmatch("/.ht*","/"))
test_equal(false, File.fnmatch("/*~","/"))
test_equal(false, File.fnmatch("/.ht*",""))
test_equal(false, File.fnmatch("/*~",""))
test_equal(false, File.fnmatch("/.ht*","/stylesheets"))
test_equal(false, File.fnmatch("/*~","/stylesheets"))
test_equal(false, File.fnmatch("/.ht*",""))
test_equal(false, File.fnmatch("/*~",""))
test_equal(false, File.fnmatch("/.ht*","/favicon.ico"))
test_equal(false, File.fnmatch("/*~","/favicon.ico"))

# join
[
  ["a", "b", "c", "d"],
  ["a"],
  [],
  ["a", "b", "..", "c"]
].each do |a|
  test_equal(a.join(File::SEPARATOR), File.join(*a))
end

test_equal("////heh////bar/heh", File.join("////heh////bar", "heh"))
test_equal("/heh/bar/heh", File.join("/heh/bar/", "heh"))
test_equal("/heh/bar/heh", File.join("/heh/bar/", "/heh"))
test_equal("/heh//bar/heh", File.join("/heh//bar/", "/heh"))
test_equal("/heh/bar/heh", File.join("/heh/", "/bar/", "/heh"))
test_equal("/HEH/BAR/FOO/HOH", File.join("/HEH", ["/BAR", "FOO"], "HOH"))
test_equal("/heh/bar", File.join("/heh//", "/bar"))
test_equal("/heh///bar", File.join("/heh", "///bar"))

# split
test_equal([".", ""], File.split(""))
test_equal([".", "."], File.split("."))
test_equal([".", ".."], File.split(".."))
test_equal(["/", "/"], File.split("/"))
test_equal([".", "a"], File.split("a"))
test_equal([".", "a"], File.split("a/"))
test_equal(["a", "b"], File.split("a/b"))
test_equal(["a/b", "c"], File.split("a/b/c"))
test_equal(["/", "a"], File.split("/a"))
test_equal(["/", "a"], File.split("/a/"))
test_equal(["/a", "b"], File.split("/a/b"))
test_equal(["/a/b", "c"], File.split("/a/b/c"))
#test_equal(["//", "a"], File.split("//a"))
test_equal(["../a/..", "b"], File.split("../a/../b/"))

# wet tests which do access the file system

# IO#readlines, IO::readlines, open, close, delete, ...

test_exception(Errno::ENOENT) { File.open("NO_SUCH_FILE_EVER") }
f = open("testFile_tmp", "w")
f.write("one\ntwo\nthree\n")
f.close

f = open("testFile_tmp")
test_equal(["one", "two", "three"],
           f.readlines.collect {|l| l.strip })
f.close

test_equal(["one", "two", "three"],
           IO.readlines("testFile_tmp").collect {|l| l.strip })

test_ok(File.delete("testFile_tmp"))

begin
  Dir.mkdir("dir_tmp")
  test_ok(File.lstat("dir_tmp").directory?)
ensure
  Dir.rmdir("dir_tmp")
end

# - file?
test_ok(File.file?('test/testFile.rb'))
test_ok(! File.file?('test'))

test_ok(File.exist?('test'))

test_ok(File.size?('build.xml'))

filename = "__test__file"
File.open(filename, "w") {|f| }
time = Time.now - 3600
File.utime(time, time, filename)
# File mtime resolution may not be sub-second on all platforms (e.g., windows)
# allow for some slop
test_ok (time.to_i - File.mtime(filename).to_i).abs < 2
File.unlink(filename)

# File::Stat tests
stat = File.stat('test');
stat2 = File.stat('build.xml');
test_ok(stat.directory?)
test_ok(stat2.file?)
test_equal("directory", stat.ftype)
test_equal("file", stat2.ftype)
test_ok(stat2.readable?)

# Test File.symlink? if possible
system("ln -s build.xml build.xml.link")
if File.exist? "build.xml.link"
  test_ok(File.symlink?("build.xml.link"))
  # JRUBY-683 -  Test that symlinks don't effect Dir and File.expand_path
  test_equal(['build.xml.link'], Dir['build.xml.link'])
  test_equal(File.expand_path('.') + '/build.xml.link', File.expand_path('build.xml.link'))
  File.delete("build.xml.link")
end

f1 = open("foo","w")
f2 = open("foo","w")
f1.flock(File::LOCK_EX | File::LOCK_NB)
test_equal(false, f2.flock(File::LOCK_EX | File::LOCK_NB))

