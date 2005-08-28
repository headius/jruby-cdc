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
 * Copyright (C) 2002-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2004 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Charles O Nutter <headius@headius.com>
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jruby.exceptions.ErrnoError;
import org.jruby.exceptions.IOError;
import org.jruby.exceptions.NotImplementedError;
import org.jruby.exceptions.SystemCallError;
import org.jruby.javasupport.JavaUtil;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.Glob;

/**
 * .The Ruby built-in class Dir.
 *
 * @author  jvoegele
 * @version $Revision$
 */
public class RubyDir extends RubyObject {
    protected String    path;
    protected File      dir;
    private   String[]  snapshot;   // snapshot of contents of directory
    private   int       pos;        // current position in directory
    private boolean isOpen = true;

    public RubyDir(Ruby runtime, RubyClass type) {
        super(runtime, type);
    }

    public static RubyClass createDirClass(Ruby runtime) {
        RubyClass dirClass = runtime.defineClass("Dir", runtime.getObject());

        dirClass.includeModule(runtime.getModule("Enumerable"));

        CallbackFactory callbackFactory = runtime.callbackFactory(RubyDir.class);

		dirClass.defineSingletonMethod("new", callbackFactory.getOptSingletonMethod("newInstance"));
        dirClass.defineSingletonMethod("glob", callbackFactory.getSingletonMethod("glob", RubyString.class));
        dirClass.defineSingletonMethod("entries", callbackFactory.getSingletonMethod("entries", RubyString.class));
        dirClass.defineSingletonMethod("[]", callbackFactory.getSingletonMethod("glob", RubyString.class));
        // dirClass.defineAlias("[]", "glob");
        dirClass.defineSingletonMethod("chdir", callbackFactory.getOptSingletonMethod("chdir"));
        dirClass.defineSingletonMethod("chroot", callbackFactory.getSingletonMethod("chroot", RubyString.class));
        //dirClass.defineSingletonMethod("delete", callbackFactory.getSingletonMethod(RubyDir.class, "delete", RubyString.class));
        dirClass.defineSingletonMethod("foreach", callbackFactory.getSingletonMethod("foreach", RubyString.class));
        dirClass.defineSingletonMethod("getwd", callbackFactory.getSingletonMethod("getwd"));
        dirClass.defineSingletonMethod("pwd", callbackFactory.getSingletonMethod("getwd"));
        // dirClass.defineAlias("pwd", "getwd");
        dirClass.defineSingletonMethod("mkdir", callbackFactory.getOptSingletonMethod("mkdir"));
        dirClass.defineSingletonMethod("open", callbackFactory.getSingletonMethod("open", RubyString.class));
        dirClass.defineSingletonMethod("rmdir", callbackFactory.getSingletonMethod("rmdir", RubyString.class));
        dirClass.defineSingletonMethod("unlink", callbackFactory.getSingletonMethod("rmdir", RubyString.class));
        dirClass.defineSingletonMethod("delete", callbackFactory.getSingletonMethod("rmdir", RubyString.class));
        // dirClass.defineAlias("unlink", "rmdir");
        // dirClass.defineAlias("delete", "rmdir");

        dirClass.defineMethod("close", callbackFactory.getMethod("close"));
        dirClass.defineMethod("each", callbackFactory.getMethod("each"));
        dirClass.defineMethod("entries", callbackFactory.getMethod("entries"));
        dirClass.defineMethod("tell", callbackFactory.getMethod("tell"));
        dirClass.defineAlias("pos", "tell");
        dirClass.defineMethod("seek", callbackFactory.getMethod("seek", RubyFixnum.class));
        dirClass.defineAlias("pos=", "seek");
        dirClass.defineMethod("read", callbackFactory.getMethod("read"));
        dirClass.defineMethod("rewind", callbackFactory.getMethod("rewind"));
		dirClass.defineMethod("initialize", callbackFactory.getMethod("initialize", RubyString.class));

        return dirClass;
    }

    public static IRubyObject newInstance(IRubyObject recv, IRubyObject[] args) {
        RubyDir result = new RubyDir(recv.getRuntime(), (RubyClass)recv);
        result.callInit(args);
        return result;
    }

    /**
     * Creates a new <code>Dir</code>.  This method takes a snapshot of the
     * contents of the directory at creation time, so changes to the contents
     * of the directory will not be reflected during the lifetime of the
     * <code>Dir</code> object returned, so a new <code>Dir</code> instance
     * must be created to reflect changes to the underlying file system.
     */
    public IRubyObject initialize(RubyString newPath) {
        newPath.checkSafeString();

// TODO: Consolidate this absolute file nonsense
        dir = new File(newPath.getValue()).getAbsoluteFile();
        if (!dir.isDirectory()) {
            dir = null;
            throw ErrnoError.getErrnoError(getRuntime(), "ENOENT", newPath.getValue() + " is not a directory");
        }
		List snapshotList = new ArrayList();
		snapshotList.add(".");
		snapshotList.add("..");
		snapshotList.addAll(getContents(dir));
		snapshot = (String[]) snapshotList.toArray(new String[snapshotList.size()]);
		pos = 0;

        return this;
    }

// ----- Ruby Class Methods ----------------------------------------------------

    /**
     * Returns an array of filenames matching the specified wildcard pattern
     * <code>pat</code>.
     */
    public static RubyArray glob(IRubyObject recv, RubyString pat) {
        String pattern = pat.toString();
        String[] files = new Glob(System.getProperty("user.dir"), pattern).getNames();
        return recv.getRuntime().newArray(JavaUtil.convertJavaArrayToRuby(recv.getRuntime(), files));
    }

    /**
     * @return all entries for this Dir
     */
    public RubyArray entries() {
        return getRuntime().newArray(JavaUtil.convertJavaArrayToRuby(getRuntime(), snapshot));
    }
    
    /**
     * Returns an array containing all of the filenames in the given directory.
     */
    public static RubyArray entries(IRubyObject recv, RubyString path) {
        if (".".equals(path.toString().trim())) {
            path = recv.getRuntime().newString(System.getProperty("user.dir"));
        }
        
        File directory = new File(path.toString());
        
        if (!directory.isDirectory()) {
            throw ErrnoError.getErrnoError(recv.getRuntime(), "ENOENT", 
            	"No such directory");
        }
        List fileList = getContents(directory);
		fileList.add(0,".");
		fileList.add(1,"..");
        Object[] files = fileList.toArray();
        return recv.getRuntime().newArray(JavaUtil.convertJavaArrayToRuby(recv.getRuntime(), files));
    }

    /** Changes the current directory to <code>path</code> */
    public static IRubyObject chdir(IRubyObject recv, IRubyObject[] args) {
        recv.checkArgumentCount(args, 0, 1);
        RubyString path = args.length == 1 ? 
            (RubyString) args[0].convertToString() : getHomeDirectoryPath(recv); 
        File dir = getDir(recv.getRuntime(), path.toString(), true);
        String realPath = null;
        String oldCwd = System.getProperty("user.dir");
    
        // We get canonical path to try and flatten the path out.
        // a dir '/subdir/..' should return as '/'
        // cnutter: Do we want to flatten path out?
        try {
            realPath = dir.getCanonicalPath();
        } catch (IOException e) {
            realPath = dir.getAbsolutePath();
        }
        
        IRubyObject result = null;
        if (recv.getRuntime().isBlockGiven()) {
        	// FIXME: Don't use user.dir for cwd
        	System.setProperty("user.dir", realPath);
        	result = recv.getRuntime().yield(path);
        	System.setProperty("user.dir", oldCwd); 
        } else {
        	System.setProperty("user.dir", realPath);
        	result = recv.getRuntime().newFixnum(0);
        }
        
        return result;
    }

    /**
     * Changes the root directory (only allowed by super user).  Not available
     * on all platforms.
     */
    public static IRubyObject chroot(IRubyObject recv, RubyString path) {
        throw new NotImplementedError(recv.getRuntime(), "chroot not implemented: chroot is non-portable and is not supported.");
    }

    /**
     * Deletes the directory specified by <code>path</code>.  The directory must
     * be empty.
     */
    public static IRubyObject rmdir(IRubyObject recv, RubyString path) {
        File directory = getDir(recv.getRuntime(), path.toString(), true);
        
        if (!directory.delete()) {
            throw new SystemCallError(recv.getRuntime(), "No such directory");
        }
        
        return recv.getRuntime().newFixnum(0);
    }

    /**
     * Executes the block once for each file in the directory specified by
     * <code>path</code>.
     */
    public static IRubyObject foreach(IRubyObject recv, RubyString path) {
        path.checkSafeString();

        RubyDir dir = (RubyDir) newInstance(recv.getRuntime().getClass("Dir"),
                                            new IRubyObject[] { path });
        
        dir.each();
        return recv.getRuntime().getNil();
    }

    /** Returns the current directory. */
    public static RubyString getwd(IRubyObject recv) {
        return recv.getRuntime().newString(System.getProperty("user.dir"));
    }

    /**
     * Creates the directory specified by <code>path</code>.  Note that the
     * <code>mode</code> parameter is provided only to support existing Ruby
     * code, and is ignored.
     */
    public static IRubyObject mkdir(IRubyObject recv, IRubyObject[] args) {
        if (args.length < 1) {
            throw recv.getRuntime().newArgumentError(args.length, 1);
        }
        if (args.length > 2) {
            throw recv.getRuntime().newArgumentError(args.length, 2);
        }

        args[0].checkSafeString();
        String path = args[0].toString();

        File newDir = getDir(recv.getRuntime(), path, false);

        return newDir.mkdir() ? RubyFixnum.zero(recv.getRuntime()) :
            RubyFixnum.one(recv.getRuntime());
    }

    /**
     * Returns a new directory object for <code>path</code>.  If a block is
     * provided, a new directory object is passed to the block, which closes the
     * directory object before terminating.
     */
    public static IRubyObject open(IRubyObject recv, RubyString path) {
        RubyDir directory = 
            (RubyDir) newInstance(recv.getRuntime().getClass("Dir"),
                    new IRubyObject[] { path });

        if (recv.getRuntime().isBlockGiven()) {
            try {
                recv.getRuntime().yield(directory);
            } finally {
                directory.close();
            }
            
            return recv.getRuntime().getNil();
        }
        
        return directory;
    }

// ----- Ruby Instance Methods -------------------------------------------------

    /**
     * Closes the directory stream.
     */
    public IRubyObject close() {
	// Make sure any read()s after close fail.
	isOpen = false;

        return getRuntime().getNil();
    }

    /**
     * Executes the block once for each entry in the directory.
     */
    public IRubyObject each() {
        String[] contents = snapshot;
        for (int i=0; i<contents.length; i++) {
            getRuntime().yield(getRuntime().newString(contents[i]));
        }
        return this;
    }

    /**
     * Returns the current position in the directory.
     */
    public RubyInteger tell() {
        return getRuntime().newFixnum(pos);
    }

    /**
     * Moves to a position <code>d</code>.  <code>pos</code> must be a value
     * returned by <code>tell</code> or 0.
     */
    public IRubyObject seek(RubyFixnum newPos) {
        this.pos = (int) newPos.getLongValue();
        return this;
    }

    /** Returns the next entry from this directory. */
    public IRubyObject read() {
	if (!isOpen) {
	    throw new IOError(getRuntime(), "Directory already closed");
	}

        if (pos >= snapshot.length) {
            return getRuntime().getNil();
        }
        RubyString result = new RubyString(getRuntime(), snapshot[pos]);
        pos++;
        return result;
    }

    /** Moves position in this directory to the first entry. */
    public IRubyObject rewind() {
        pos = 0;
        return getRuntime().newFixnum(pos);
    }

// ----- Helper Methods --------------------------------------------------------

    /** Returns a Java <code>File</code> object for the specified path.  If
     * <code>path</code> is not a directory, throws <code>IOError</code>.
     *
     * @param   path path for which to return the <code>File</code> object.
     * @param   mustExist is true the directory must exist.  If false it must not.
     * @throws  IOError if <code>path</code> is not a directory.
     */
    protected static File getDir(Ruby runtime, String path, boolean mustExist) {
        File result = new File(path);
		
        // For some reason Java 1.5.x will print correct absolute path on a created file, 
        // but it will still operate on an old user.dir when performing any action.
        // This could even happen with older Java runtimes?
        try {
			result = result.getCanonicalFile();
        } catch (IOException e) {
            result = result.getAbsoluteFile();
        }

		boolean isDirectory = result.isDirectory();
        if (mustExist && !isDirectory) {
            throw ErrnoError.getErrnoError(runtime, "ENOENT", path + " is not a directory");
        } else if (!mustExist && isDirectory) {
            throw ErrnoError.getErrnoError(runtime, "EEXIST", "File exists - " + path); 
        }

        return result;
    }

    /**
     * Returns the contents of the specified <code>directory</code> as an
     * <code>ArrayList</code> containing the names of the files as Java Strings.
     */
    protected static List getContents(File directory) {
        String[] contents = directory.list();
        List result = new ArrayList();

        // If an IO exception occurs (something odd, but possible)
        // A directory may return null.
        if (contents != null) {
            for (int i=0; i<contents.length; i++) {
                result.add(contents[i]);
            }
        }
        return result;
    }

    /**
     * Returns the contents of the specified <code>directory</code> as an
     * <code>ArrayList</code> containing the names of the files as Ruby Strings.
     */
    protected static List getContents(File directory, Ruby runtime) {
        List result = new ArrayList();
        String[] contents = directory.list();
        for (int i=0; i<contents.length; i++) {
            result.add(new RubyString(runtime, contents[i]));
        }
        return result;
    }
	
	/*
	 * Poor mans find home directory.  I am not sure how windows ruby behaves with '~foo', but
	 * this mostly will work on any unix/linux/cygwin system.  When someone wants to extend this
	 * to include the windows way, we should consider moving this to an external ruby file.
	 */
	public static IRubyObject getHomeDirectoryPath(IRubyObject recv, String user) {
		// TODO: Having a return where I set user inside readlines created a JumpException.  It seems that
		// evalScript should catch that and return?
		return recv.getRuntime().evalScript("File.open('/etc/passwd') do |f| f.readlines.each do" +
				"|l| f = l.split(':'); return f[5] if f[0] == '" + user + "'; end; end; nil");
	}
	
	public static RubyString getHomeDirectoryPath(IRubyObject recv) {
		RubyHash hash = (RubyHash) recv.getRuntime().getObject().getConstant("ENV");
		IRubyObject home = hash.aref(recv.getRuntime().newString("HOME"));
		
		if (home == null || home.isNil()) {
			home = hash.aref(recv.getRuntime().newString("LOGDIR"));
		}
		
		if (home == null || home.isNil()) {
			throw recv.getRuntime().newArgumentError("HOME/LOGDIR not set");
		}
		
		return (RubyString) home;
	}
}
