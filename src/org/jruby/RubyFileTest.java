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
 * Copyright (C) 2004-2005 Charles O Nutter <headius@headius.com>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
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
package org.jruby;

import org.jruby.anno.JRubyMethod;

import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.JRubyFile;

public class RubyFileTest {
    public static RubyModule createFileTestModule(Ruby runtime) {
        RubyModule fileTestModule = runtime.defineModule("FileTest");
        runtime.setFileTest(fileTestModule);
        
        fileTestModule.defineAnnotatedMethods(RubyFileTest.class);
        
        return fileTestModule;
    }

    @JRubyMethod(name = "blockdev?", required = 1, module = true)
    public static IRubyObject blockdev_p(IRubyObject recv, IRubyObject filename) {
        Ruby runtime = recv.getRuntime();
        JRubyFile file = file(filename);
        
        return runtime.newBoolean(file.exists() && runtime.getPosix().stat(file.getAbsolutePath()).isBlockDev());
    }
    
    @JRubyMethod(name = "chardev?", required = 1, module = true)
    public static IRubyObject chardev_p(IRubyObject recv, IRubyObject filename) {
        Ruby runtime = recv.getRuntime();
        JRubyFile file = file(filename);
        
        return runtime.newBoolean(file.exists() && runtime.getPosix().stat(file.getAbsolutePath()).isCharDev());
    }

    @JRubyMethod(name = "directory?", required = 1, module = true)
    public static IRubyObject directory_p(IRubyObject recv, IRubyObject filename) {
        Ruby runtime = recv.getRuntime();
        JRubyFile file = file(filename);
        
        return runtime.newBoolean(file.exists() && runtime.getPosix().stat(file.getAbsolutePath()).isDirectory());
    }
    
    @JRubyMethod(name = "executable?", required = 1, module = true)
    public static IRubyObject executable_p(IRubyObject recv, IRubyObject filename) {
        Ruby runtime = recv.getRuntime();
        JRubyFile file = file(filename);
        
        return runtime.newBoolean(file.exists() && runtime.getPosix().stat(file.getAbsolutePath()).isExecutable());
    }
    
    @JRubyMethod(name = "executable_real?", required = 1, module = true)
    public static IRubyObject executable_real_p(IRubyObject recv, IRubyObject filename) {
        Ruby runtime = recv.getRuntime();
        JRubyFile file = file(filename);
        
        return runtime.newBoolean(file.exists() && runtime.getPosix().stat(file.getAbsolutePath()).isExecutableReal());
    }
    
    @JRubyMethod(name = {"exist?", "exists?"}, required = 1, module = true)
    public static IRubyObject exist_p(IRubyObject recv, IRubyObject filename) {
        if (Ruby.isSecurityRestricted()) {
            return recv.getRuntime().newBoolean(false);
        }

        if(filename.convertToString().toString().startsWith("file:")) {
            String file = filename.convertToString().toString().substring(5);
            String jar = file.substring(0,file.indexOf("!"));
            String after = file.substring(file.indexOf("!")+2);
            try {
                java.util.jar.JarFile jf = new java.util.jar.JarFile(jar);
                if(jf.getJarEntry(after) != null) {
                    return recv.getRuntime().newBoolean(true);
                } else {
                    return recv.getRuntime().newBoolean(false);
                }
            } catch(Exception e) {
                return recv.getRuntime().newBoolean(false);
            }
        }

        return recv.getRuntime().newBoolean(file(filename).exists());
    }

    @JRubyMethod(name = "file?", required = 1, module = true)
    public static RubyBoolean file_p(IRubyObject recv, IRubyObject filename) {
        JRubyFile file = file(filename);
        
        return filename.getRuntime().newBoolean(file.exists() && file.isFile());
    }

    @JRubyMethod(name = "grpowned?", required = 1, module = true)
    public static IRubyObject grpowned_p(IRubyObject recv, IRubyObject filename) {
        Ruby runtime = recv.getRuntime();
        JRubyFile file = file(filename);
        
        return runtime.newBoolean(file.exists() && runtime.getPosix().stat(file.getAbsolutePath()).isGroupOwned());
    }
    
    @JRubyMethod(name = "identical?", required = 2, module = true)
    public static IRubyObject identical_p(IRubyObject recv, IRubyObject filename1, IRubyObject filename2) {
        throw recv.getRuntime().newNotImplementedError("FileTest#identical? not yet implemented");
    }

    @JRubyMethod(name = "owned?", required = 1, module = true)
    public static IRubyObject owned_p(IRubyObject recv, IRubyObject filename) {
        Ruby runtime = recv.getRuntime();
        JRubyFile file = file(filename);
        
        return runtime.newBoolean(file.exists() && runtime.getPosix().stat(file.getAbsolutePath()).isOwned());
    }
    
    @JRubyMethod(name = "pipe?", required = 1, module = true)
    public static IRubyObject pipe_p(IRubyObject recv, IRubyObject filename) {
        Ruby runtime = recv.getRuntime();
        JRubyFile file = file(filename);
        
        return runtime.newBoolean(file.exists() && runtime.getPosix().stat(file.getAbsolutePath()).isNamedPipe());
    }

    // We use file test since it is faster than a stat; also euid == uid in Java always
    @JRubyMethod(name = {"readable?", "readable_real?"}, required = 1, module = true)
    public static IRubyObject readable_p(IRubyObject recv, IRubyObject filename) {
        JRubyFile file = file(filename);

        return recv.getRuntime().newBoolean(file.exists() && file.canRead());
    }

    // Not exposed by filetest, but so similiar in nature that it is stored here
    public static IRubyObject rowned_p(IRubyObject recv, IRubyObject filename) {
        Ruby runtime = recv.getRuntime();
        JRubyFile file = file(filename);
        
        return runtime.newBoolean(file.exists() && runtime.getPosix().stat(file.getAbsolutePath()).isROwned());
    }
    
    @JRubyMethod(name = "setgid?", required = 1, module = true)
    public static IRubyObject setgid_p(IRubyObject recv, IRubyObject filename) {
        Ruby runtime = recv.getRuntime();
        JRubyFile file = file(filename);
        
        return runtime.newBoolean(file.exists() && runtime.getPosix().stat(file.getAbsolutePath()).isSetgid());
    }
    
    @JRubyMethod(name = "setuid?", required = 1, module = true)
    public static IRubyObject setuid_p(IRubyObject recv, IRubyObject filename) {
        Ruby runtime = recv.getRuntime();
        JRubyFile file = file(filename);
        
        return runtime.newBoolean(file.exists() && runtime.getPosix().stat(file.getAbsolutePath()).isSetuid());
    }
    
    @JRubyMethod(name = "size", required = 1, module = true)
    public static IRubyObject size(IRubyObject recv, IRubyObject filename) {
        JRubyFile file = file(filename);

        if (!file.exists()) noFileError(filename);
        
        return recv.getRuntime().newFixnum(file.length());
    }
    
    @JRubyMethod(name = "size?", required = 1, module = true)
    public static IRubyObject size_p(IRubyObject recv, IRubyObject filename) {
        JRubyFile file = file(filename);

        if (!file.exists()) return recv.getRuntime().getNil();
        
        return recv.getRuntime().newFixnum(file.length());
    }
    
    @JRubyMethod(name = "socket?", required = 1, module = true)
    public static IRubyObject socket_p(IRubyObject recv, IRubyObject filename) {
        Ruby runtime = recv.getRuntime();
        JRubyFile file = file(filename);
        
        return runtime.newBoolean(file.exists() && runtime.getPosix().stat(file.getAbsolutePath()).isSocket());
    }
    
    @JRubyMethod(name = "sticky?", required = 1, module = true)
    public static IRubyObject sticky_p(IRubyObject recv, IRubyObject filename) {
        Ruby runtime = recv.getRuntime();
        JRubyFile file = file(filename);
        
        return runtime.newBoolean(file.exists() && runtime.getPosix().stat(file.getAbsolutePath()).isSticky());
    }
        
    @JRubyMethod(name = "symlink?", required = 1, module = true)
    public static RubyBoolean symlink_p(IRubyObject recv, IRubyObject filename) {
        Ruby runtime = recv.getRuntime();
        JRubyFile file = file(filename);

        return runtime.newBoolean(file.exists() && runtime.getPosix().lstat(file.getAbsolutePath()).isSymlink());
    }

    // We do both writable and writable_real through the same method because
    // in our java process effective and real userid will always be the same.
    @JRubyMethod(name = {"writable?", "writable_real?"}, required = 1, module = true)
    public static RubyBoolean writable_p(IRubyObject recv, IRubyObject filename) {
        return filename.getRuntime().newBoolean(file(filename).canWrite());
    }
    
    @JRubyMethod(name = "zero?", required = 1, module = true)
    public static RubyBoolean zero_p(IRubyObject recv, IRubyObject filename) {
        JRubyFile file = file(filename);
        
        return filename.getRuntime().newBoolean(file.exists() && file.length() == 0L);
    }

    private static JRubyFile file(IRubyObject path) {
        String filename = path.convertToString().toString();
        
        return JRubyFile.create(path.getRuntime().getCurrentDirectory(), filename);
    }
    
    private static void noFileError(IRubyObject filename) {
        throw filename.getRuntime().newErrnoENOENTError("No such file or directory - " + 
                filename.convertToString());
    }
}
