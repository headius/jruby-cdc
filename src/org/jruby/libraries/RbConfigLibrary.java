/***** BEGIN LICENSE BLOCK *****
 * Version: CPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Common Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/cpl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2005 Charles O Nutter
 * Copyright (C) 2006 Nick Sieger
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the CPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the CPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jruby.libraries;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jruby.Ruby;
import org.jruby.RubyHash;
import org.jruby.RubyModule;
import org.jruby.ext.posix.util.Platform;
import org.jruby.runtime.Constants;
import org.jruby.runtime.load.Library;
import org.jruby.util.NormalizedFile;
import org.jruby.anno.JRubyModule;

@JRubyModule(name="Config")
public class RbConfigLibrary implements Library {
    // Ruby's designation for some platforms, minus version numbers in some cases
    private static final String RUBY_DARWIN = "darwin";
    private static final String RUBY_LINUX = "linux";
    private static final String RUBY_WIN32 = "mswin32";
    private static final String RUBY_SOLARIS = "solaris";
    private static final String RUBY_FREEBSD = "freebsd";
    private static final String RUBY_AIX = "aix";
    
    /** This is a map from Java's "friendly" OS names to those used by Ruby */
    public static final Map<String, String> RUBY_OS_NAMES = new HashMap<String, String>();
    static {
        RUBY_OS_NAMES.put("Mac OS X", RUBY_DARWIN);
        RUBY_OS_NAMES.put("Darwin", RUBY_DARWIN);
        RUBY_OS_NAMES.put("Linux", RUBY_LINUX);
        RUBY_OS_NAMES.put("Windows 95", RUBY_WIN32);
        RUBY_OS_NAMES.put("Windows 98", RUBY_WIN32);
        RUBY_OS_NAMES.put("Windows Me", RUBY_WIN32);
        RUBY_OS_NAMES.put("Windows NT", RUBY_WIN32);
        // that's what JDK5 produces on Windows Vista
        RUBY_OS_NAMES.put("Windows NT (unknown)", RUBY_WIN32);
        RUBY_OS_NAMES.put("Windows XP", RUBY_WIN32);
        RUBY_OS_NAMES.put("Windows 2003", RUBY_WIN32);
        RUBY_OS_NAMES.put("Windows Vista", RUBY_WIN32);
        RUBY_OS_NAMES.put("Solaris", RUBY_SOLARIS);
        RUBY_OS_NAMES.put("FreeBSD", RUBY_FREEBSD);
        RUBY_OS_NAMES.put("AIX", RUBY_AIX);
    }
    
    public static String getOSName() {
        String OSName = Platform.getOSName();
        String theOSName = RUBY_OS_NAMES.get(OSName);
        
        return theOSName == null ? OSName : theOSName;
    }
    /**
     * Just enough configuration settings (most don't make sense in Java) to run the rubytests
     * unit tests. The tests use <code>bindir</code>, <code>RUBY_INSTALL_NAME</code> and
     * <code>EXEEXT</code>.
     */
    public void load(Ruby runtime, boolean wrap) {
        RubyModule configModule = runtime.defineModule("Config");
        RubyHash configHash = RubyHash.newHash(runtime);
        configModule.defineConstant("CONFIG", configHash);
        runtime.getObject().defineConstant("RbConfig", configModule);

        String[] versionParts = Constants.RUBY_VERSION.split("\\.");
        setConfig(configHash, "MAJOR", versionParts[0]);
        setConfig(configHash, "MINOR", versionParts[1]);
        setConfig(configHash, "TEENY", versionParts[2]);
        setConfig(configHash, "ruby_version", versionParts[0] + '.' + versionParts[1]);
        // Rubygems is too specific on host cpu so until we have real need lets default to universal
        //setConfig(configHash, "arch", System.getProperty("os.arch") + "-java" + System.getProperty("java.specification.version"));
        setConfig(configHash, "arch", "universal-java" + System.getProperty("java.specification.version"));

        String normalizedHome;
        if (Ruby.isSecurityRestricted()) {
            normalizedHome = "SECURITY RESTRICTED";
        } else {
            normalizedHome = runtime.getJRubyHome();
        }
        setConfig(configHash, "bindir", new File(normalizedHome, "bin").getPath());
        setConfig(configHash, "RUBY_INSTALL_NAME", jrubyScript());
        setConfig(configHash, "ruby_install_name", jrubyScript());
        setConfig(configHash, "SHELL", jrubyShell());
        setConfig(configHash, "prefix", normalizedHome);
        setConfig(configHash, "exec_prefix", normalizedHome);

        setConfig(configHash, "host_os", getOSName());
        setConfig(configHash, "host_vendor", System.getProperty("java.vendor"));
        setConfig(configHash, "host_cpu", Platform.ARCH);
        
        setConfig(configHash, "target_os", getOSName());
        
        setConfig(configHash, "target_cpu", Platform.ARCH);
        
        String jrubyJarFile = "jruby.jar";
        URL jrubyPropertiesUrl = Ruby.getClassLoader().getResource(Constants.JRUBY_PROPERTIES);
        if (jrubyPropertiesUrl != null) {
            Pattern jarFile = Pattern.compile("jar:file:.*?([a-zA-Z0-9.\\-]+\\.jar)!" + Constants.JRUBY_PROPERTIES);
            Matcher jarMatcher = jarFile.matcher(jrubyPropertiesUrl.toString());
            jarMatcher.find();
            if (jarMatcher.matches()) {
                jrubyJarFile = jarMatcher.group(1);
            }
        }
        setConfig(configHash, "LIBRUBY", jrubyJarFile);
        setConfig(configHash, "LIBRUBY_SO", jrubyJarFile);
        
        setConfig(configHash, "build", Constants.BUILD);
        setConfig(configHash, "target", Constants.TARGET);
        
        String libdir = System.getProperty("jruby.lib");
        if (libdir == null) {
            libdir = new File(normalizedHome, "lib").getPath();
        } else {
            try {
            // Our shell scripts pass in non-canonicalized paths, but even if we didn't
            // anyone who did would become unhappy because Ruby apps expect no relative
            // operators in the pathname (rubygems, for example).
                libdir = new NormalizedFile(libdir).getCanonicalPath();
            } catch (IOException e) {
                libdir = new NormalizedFile(libdir).getAbsolutePath();
            }
        }

        setConfig(configHash, "libdir", libdir);
        setConfig(configHash, "rubylibdir",     new File(libdir, "ruby/1.8").getPath());
        setConfig(configHash, "sitedir",        new File(libdir, "ruby/site_ruby").getPath());
        setConfig(configHash, "sitelibdir",     new File(libdir, "ruby/site_ruby/1.8").getPath());
        setConfig(configHash, "sitearchdir",    new File(libdir, "ruby/site_ruby/1.8/java").getPath());
        setConfig(configHash, "archdir",    new File(libdir, "ruby/site_ruby/1.8/java").getPath());
        setConfig(configHash, "configure_args", "");
        setConfig(configHash, "datadir", new File(normalizedHome, "share").getPath());
        setConfig(configHash, "mandir", new File(normalizedHome, "man").getPath());
        setConfig(configHash, "sysconfdir", new File(normalizedHome, "etc").getPath());
        setConfig(configHash, "localstatedir", new File(normalizedHome, "var").getPath());
        setConfig(configHash, "DLEXT", "jar");

        if (Platform.IS_WINDOWS) {
            setConfig(configHash, "EXEEXT", ".exe");
        } else {
            setConfig(configHash, "EXEEXT", "");
        }
    }

    private static void setConfig(RubyHash configHash, String key, String value) {
        Ruby runtime = configHash.getRuntime();
        configHash.op_aset(runtime.getCurrentContext(), runtime.newString(key), runtime.newString(value));
    }

    public static String jrubyScript() {
        return System.getProperty("jruby.script", Platform.IS_WINDOWS ? "jruby.bat" : "jruby").replace('\\', '/');
    }

    // TODO: note lack of command.com support for Win 9x...
    public static String jrubyShell() {
        return System.getProperty("jruby.shell", Platform.IS_WINDOWS ? "cmd.exe" : "/bin/sh").replace('\\', '/');
    }

}
