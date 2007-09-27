/*
 * JRubyC.java
 *
 * Created on January 11, 2007, 11:24 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.jruby;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;

import org.jruby.ast.Node;
import org.jruby.compiler.ASTInspector;
import org.jruby.compiler.NodeCompilerFactory;
import org.jruby.compiler.NotCompilableException;
import org.jruby.compiler.impl.StandardASMCompiler;

/**
 *
 * @author headius
 */
public class JRubyC {
    
    public static void main(String args[]) {
        Ruby runtime = Ruby.getDefaultInstance();
        
        try {
            if (args.length < 1) {
                System.out.println("Usage: jrubyc <filename> [<filename> ...]");
                return;
            }
            for (int i = 0; i < args.length; i++) {
                String filename = args[i];
                if (filename.startsWith("./")) filename = filename.substring(2);
                File srcfile = new File(filename);
                if (!srcfile.exists()) {
                    System.out.println("Error -- file not found: " + filename);
                    return;
                }
                File destfile = new File(System.getProperty("user.dir"));

                int size = (int)srcfile.length();
                byte[] chars = new byte[size];
                new FileInputStream(srcfile).read(chars);
                // FIXME: encoding?
                String content = new String(chars);
                Node scriptNode = runtime.parseFile(new StringReader(content), filename, null);

                ASTInspector inspector = new ASTInspector();
                inspector.inspect(scriptNode);

                // do the compile
                String classPath = filename.substring(0, filename.lastIndexOf(".")).replace('-', '_').replace('.', '_');
                int lastSlashIndex = classPath.lastIndexOf('/');
                if (!Character.isJavaIdentifierStart(classPath.charAt(lastSlashIndex + 1))) {
                    if (lastSlashIndex == -1) {
                        classPath = "_" + classPath;
                    } else {
                        classPath = classPath.substring(0, lastSlashIndex + 1) + "_" + classPath.substring(lastSlashIndex + 1);
                    }
                }
                String classDotted = classPath.replace('/', '.').replace('\\', '.');
                StandardASMCompiler compiler = new StandardASMCompiler(classPath, filename);
                System.out.println("Compiling file \"" + filename + "\" as class \"" + classDotted + "\"");
                NodeCompilerFactory.compileRoot(scriptNode, compiler, inspector);

                compiler.writeClass(destfile);
            }
        } catch (IOException ioe) {
            System.err.println("Error -- IO exception during compile: " + ioe.getMessage());
        } catch (NotCompilableException nce) {
            System.err.println("Error -- Not compilable: " + nce.getMessage());
        }
    }
    
}
