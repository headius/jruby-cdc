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

import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.JRubyFile;

public class RubyFileTest {
    public static RubyModule createFileTestModule(IRuby runtime) {
        RubyModule fileTestModule = runtime.defineModule("FileTest");
        CallbackFactory callbackFactory = runtime.callbackFactory(RubyFileTest.class);

        fileTestModule.defineFastModuleFunction("file?", callbackFactory.getFastSingletonMethod("file_p", IRubyObject.class));
        fileTestModule.defineFastModuleFunction("directory?", callbackFactory.getFastSingletonMethod("directory_p", IRubyObject.class));
        fileTestModule.defineFastModuleFunction("exist?", callbackFactory.getFastSingletonMethod("exist_p", IRubyObject.class));
        fileTestModule.defineFastModuleFunction("exists?", callbackFactory.getFastSingletonMethod("exist_p", IRubyObject.class));
        fileTestModule.defineFastModuleFunction("readable?", callbackFactory.getFastSingletonMethod("readable_p", IRubyObject.class));
        fileTestModule.defineFastModuleFunction("readable_real?", callbackFactory.getFastSingletonMethod("readable_p", IRubyObject.class));
        fileTestModule.defineFastModuleFunction("size", callbackFactory.getFastSingletonMethod("size", IRubyObject.class));
        fileTestModule.defineFastModuleFunction("writable?", callbackFactory.getFastSingletonMethod("writable_p", IRubyObject.class));
        fileTestModule.defineFastModuleFunction("writable_real?", callbackFactory.getFastSingletonMethod("writable_p", IRubyObject.class));
        fileTestModule.defineFastModuleFunction("zero?", callbackFactory.getFastSingletonMethod("zero_p", IRubyObject.class));
        
        fileTestModule.defineFastMethod("file?", callbackFactory.getFastSingletonMethod("file_p", IRubyObject.class));
        fileTestModule.defineFastMethod("directory?", callbackFactory.getFastSingletonMethod("directory_p", IRubyObject.class));
        fileTestModule.defineFastMethod("exist?", callbackFactory.getFastSingletonMethod("exist_p", IRubyObject.class));
        fileTestModule.defineFastMethod("exists?", callbackFactory.getFastSingletonMethod("exist_p", IRubyObject.class));
        fileTestModule.defineFastMethod("readable?", callbackFactory.getFastSingletonMethod("readable_p", IRubyObject.class));
        fileTestModule.defineFastMethod("readable_real?", callbackFactory.getFastSingletonMethod("readable_p", IRubyObject.class));
        fileTestModule.defineFastMethod("size", callbackFactory.getFastSingletonMethod("size", IRubyObject.class));
        fileTestModule.defineFastMethod("writable?", callbackFactory.getFastSingletonMethod("writable_p", IRubyObject.class));
        fileTestModule.defineFastMethod("writable_real?", callbackFactory.getFastSingletonMethod("writable_p", IRubyObject.class));
        fileTestModule.defineFastMethod("zero?", callbackFactory.getFastSingletonMethod("zero_p", IRubyObject.class));
        
        return fileTestModule;
    }
    
    public static RubyBoolean directory_p(IRubyObject recv, IRubyObject filename) {
        return recv.getRuntime().newBoolean(newFile(filename).isDirectory());
    }
    
    public static IRubyObject exist_p(IRubyObject recv, IRubyObject filename) {
        return recv.getRuntime().newBoolean(newFile(filename).exists());
    }

    // We do both readable and readable_real through the same method because
    // in our java process effective and real userid will always be the same.
    public static RubyBoolean readable_p(IRubyObject recv, IRubyObject filename) {
        return filename.getRuntime().newBoolean(newFile(filename).canRead());
    }
    
    public static IRubyObject size(IRubyObject recv, IRubyObject filename) {
        JRubyFile file = newFile(filename);
        
        if (!file.exists()) {
            throw recv.getRuntime().newErrnoENOENTError("No such file: " + filename);
        }
        return filename.getRuntime().newFixnum(file.length());
    }
    
    // We do both writable and writable_real through the same method because
    // in our java process effective and real userid will always be the same.
    public static RubyBoolean writable_p(IRubyObject recv, IRubyObject filename) {
        return filename.getRuntime().newBoolean(newFile(filename).canWrite());
    }
    
    public static RubyBoolean zero_p(IRubyObject recv, IRubyObject filename) {
        JRubyFile file = newFile(filename);
        
        return filename.getRuntime().newBoolean(file.exists() && file.length() == 0L);
    }

    public static RubyBoolean file_p(IRubyObject recv, IRubyObject filename) {
        JRubyFile file = newFile(filename);
        
        return filename.getRuntime().newBoolean(file.isFile());
    }
    
    private static JRubyFile newFile(IRubyObject path) {
        return JRubyFile.create(path.getRuntime().getCurrentDirectory(), path.convertToString().toString());
    }
}
