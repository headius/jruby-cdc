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
 * Copyright (C) 2008 MenTaLguY <mental@rydia.net>
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

import java.awt.BorderLayout;
import java.awt.Graphics;
import java.io.InputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;

import java.lang.reflect.InvocationTargetException;

import org.jruby.Ruby;
import org.jruby.RubyInstanceConfig;
import org.jruby.RubyModule;
import org.jruby.RubyProc;
import org.jruby.javasupport.JavaObject;
import org.jruby.runtime.Block;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

import javax.swing.JApplet;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * @author <a href="mailto:mental@rydia.net">MenTaLguY</a>
 *
 * The JRubyApplet class provides a simple way to write Java applets using
 * JRuby without needing to create a custom Java applet class.  At applet
 * initialization time, JRubyApplet starts up a JRuby runtime, then evaluates
 * the scriptlet given as the "eval" applet parameter.
 *
 * The Java applet instance is available to the Ruby script as
 * JRubyApplet.applet; the script can also define callbacks for applet
 * start, stop, and destroy by passing blocks to JRubyApplet.on_start,
 * JRubyApplet.on_stop, and JRubyApplet.on_destroy, respectively.
 *
 */
public class JRubyApplet extends JApplet {
    private Ruby runtime;
    private IRubyObject rubyObject;
    private RubyProc startProc;
    private RubyProc stopProc;
    private RubyProc destroyProc;
    private RubyProc paintProc;
    private Graphics priorGraphics;
    private IRubyObject wrappedGraphics;

    public static class AppletModule {
        public static void setup(Ruby runtime, IRubyObject applet) {
            RubyModule module = runtime.defineModule("JRubyApplet");
            module.dataWrapStruct(applet);
            CallbackFactory cb = runtime.callbackFactory(AppletModule.class);
            module.getMetaClass().defineMethod("applet", cb.getSingletonMethod("applet"));
            module.getMetaClass().defineMethod("on_start", cb.getSingletonMethod("on_start"));
            module.getMetaClass().defineMethod("on_stop", cb.getSingletonMethod("on_stop"));
            module.getMetaClass().defineMethod("on_destroy", cb.getSingletonMethod("on_destroy"));
            module.getMetaClass().defineMethod("on_paint", cb.getSingletonMethod("on_paint"));
        }

        private static RubyProc blockToProc(Ruby runtime, Block block) {
            if (block.isGiven()) {
                RubyProc proc = block.getProcObject();
                if (proc == null) {
                    proc = RubyProc.newProc(runtime, block, block.type);
                }
                return proc;
            } else {
                return null;
            }
        }

        private static JRubyApplet appletUnwrapped(IRubyObject recv) {
            return (JRubyApplet)((JavaObject)recv.dataGetStruct()).getValue();
        }

        public static IRubyObject applet(IRubyObject recv, Block block) {
            return (IRubyObject)recv.dataGetStruct();
        }

        public static IRubyObject on_start(IRubyObject recv, Block block) {
            JRubyApplet applet = appletUnwrapped(recv);
            applet.setStartProc(blockToProc(recv.getRuntime(), block));
            return recv;
        }

        public static IRubyObject on_stop(IRubyObject recv, Block block) {
            JRubyApplet applet = appletUnwrapped(recv);
            applet.setStopProc(blockToProc(recv.getRuntime(), block));
            return recv;
        }

        public static IRubyObject on_destroy(IRubyObject recv, Block block) {
            JRubyApplet applet = appletUnwrapped(recv);
            applet.setDestroyProc(blockToProc(recv.getRuntime(), block));
            return recv;
        }

        public static IRubyObject on_paint(IRubyObject recv, Block block) {
            JRubyApplet applet = appletUnwrapped(recv);
            applet.setPaintProc(blockToProc(recv.getRuntime(), block));
            return recv;
        }
    }

    private boolean getBooleanParameter(String name, boolean default_value) {
        String value = getParameter(name);
        if ( value != null ) {
            return value.equals("true");
        } else {
            return default_value;
        }
    }

    private InputStream getCodeResourceAsStream(String name) {
        try {
            final URL directURL = new URL(getCodeBase(), name);
            return directURL.openStream();
        } catch (IOException e) {
        }
        return JRubyApplet.class.getClassLoader().getResourceAsStream(name);
    }

    public void init() {
        super.init();

        synchronized (this) {
            if (runtime != null) {
                return;
            }

            final RubyInstanceConfig config = new RubyInstanceConfig() {{
                setObjectSpaceEnabled(getBooleanParameter("ObjectSpace", false));
            }};
            runtime = Ruby.newInstance(config);
            runtime.setSecurityRestricted(true);
            rubyObject = JavaObject.wrap(runtime, this);
            AppletModule.setup(runtime, rubyObject);
        }

        final String scriptName = getParameter("script");
        final InputStream scriptStream = getCodeResourceAsStream(scriptName);
        final String evalString = getParameter("eval");

        if ( scriptName == null && evalString == null ) {
            showError("No Ruby script specified.");
            return;
        }
        if ( scriptName != null && scriptStream == null ) {
            showError("Script " + scriptName + " not found.");
            return;
        }

        try {
            final Ruby runtime = this.runtime;
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    if (scriptStream != null) {
                        runtime.runFromMain(scriptStream, scriptName);
                    }
                    if (evalString != null) {
                        runtime.evalScriptlet(evalString);
                    }
                }
            });
        } catch (InterruptedException e) {
        } catch (InvocationTargetException e) {
            showException(e.getCause());
        }
    }

    private void showException(Throwable t) {
        StringWriter writer = new StringWriter();
        t.printStackTrace(new PrintWriter(writer, true));
        showError(writer.toString());
    }

    private synchronized void showError(final String message) {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    final JTextArea textArea = new JTextArea(message);
                    textArea.setEditable(false);
                    final JScrollPane pane = new JScrollPane(textArea);
                    getContentPane().removeAll();
                    getContentPane().setLayout(new BorderLayout());
                    getContentPane().add(pane);
                }
            });
        } catch (InterruptedException e) { 
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Error displaying error", e.getCause());
        }
    }

    private void invokeCallback(final RubyProc proc, final IRubyObject[] args) {
        if (proc == null) {
            return;
        }
        final Ruby runtime = this.runtime;
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    ThreadContext context = runtime.getCurrentContext();
                    proc.call(context, args, Block.NULL_BLOCK);
                }
            });
        } catch (InterruptedException e) {
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Ruby callback failed", e.getCause());
        }
    }

    private synchronized void setStartProc(RubyProc proc) {
        startProc = proc;
    }
    public synchronized void start() {
        super.start();
        invokeCallback(startProc, new IRubyObject[] {});
    }

    private synchronized void setStopProc(RubyProc proc) {
        stopProc = proc;
    }
    public synchronized void stop() {
        invokeCallback(stopProc, new IRubyObject[] {});
        super.stop();
    }

    private synchronized void setDestroyProc(RubyProc proc) {
        destroyProc = proc;
    }
    public synchronized void destroy() {
        try {
            invokeCallback(destroyProc, new IRubyObject[] {});
        } finally {
            final Ruby runtime = this.runtime;
            this.runtime = null;
            rubyObject = null;
            startProc = null;
            stopProc = null;
            destroyProc = null;
            paintProc = null;
            priorGraphics = null;
            wrappedGraphics = null;
            runtime.tearDown();
            super.destroy();
        }
    }

    private synchronized void setPaintProc(RubyProc proc) {
        paintProc = proc;
    }
    public void paint(Graphics g) {
        super.paint(g); 
        synchronized (this) {
            if (paintProc != null) {
                if (priorGraphics != g) {
                    wrappedGraphics = JavaObject.wrap(runtime, g);
                    priorGraphics = g;
                }
                ThreadContext context = runtime.getCurrentContext();
                paintProc.call(context, new IRubyObject[] {wrappedGraphics}, Block.NULL_BLOCK);
            }
        }
    }
}
