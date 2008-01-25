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
 * Copyright (C) 2007 Ola Bini <ola@ologix.com>
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
package org.jruby.ext.socket;

import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.DatagramChannel;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import org.jruby.Ruby;
import org.jruby.RubyClass;
import org.jruby.RubyNumeric;
import org.jruby.RubyIO;
import org.jruby.RubyString;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.Stream;
import org.jruby.util.ChannelStream;

/**
 * @author <a href="mailto:ola.bini@ki.se">Ola Bini</a>
 */
public class RubyBasicSocket extends RubyIO {
    private static ObjectAllocator BASICSOCKET_ALLOCATOR = new ObjectAllocator() {
        public IRubyObject allocate(Ruby runtime, RubyClass klass) {
            return new RubyBasicSocket(runtime, klass);
        }
    };

    static void createBasicSocket(Ruby runtime) {
        RubyClass rb_cBasicSocket = runtime.defineClass("BasicSocket", runtime.getIO(), BASICSOCKET_ALLOCATOR);

        // These are empty implementations and shouldn't be used
        RubyClass rb_cUnixSocket = runtime.defineClass("UNIXSocket", rb_cBasicSocket, BASICSOCKET_ALLOCATOR);
        RubyClass rb_cUnixServer = runtime.defineClass("UNIXServer", rb_cUnixSocket, BASICSOCKET_ALLOCATOR);

        CallbackFactory cfact = runtime.callbackFactory(RubyBasicSocket.class);
        rb_cBasicSocket.defineFastMethod("send", cfact.getFastOptMethod("write_send"));
        rb_cBasicSocket.defineFastMethod("recv", cfact.getFastOptMethod("recv"));
        rb_cBasicSocket.defineFastMethod("shutdown", cfact.getFastOptMethod("shutdown"));
        rb_cBasicSocket.defineFastMethod("__getsockname", cfact.getFastMethod("getsockname"));
        rb_cBasicSocket.defineFastMethod("__getpeername", cfact.getFastMethod("getpeername"));
        rb_cBasicSocket.defineFastMethod("getsockname", cfact.getFastMethod("getsockname"));
        rb_cBasicSocket.defineFastMethod("getpeername", cfact.getFastMethod("getpeername"));
        rb_cBasicSocket.defineFastMethod("getsockopt", cfact.getFastMethod("getsockopt", IRubyObject.class, IRubyObject.class));
        rb_cBasicSocket.defineFastMethod("setsockopt", cfact.getFastMethod("setsockopt", IRubyObject.class, IRubyObject.class, IRubyObject.class));
        rb_cBasicSocket.getMetaClass().defineFastMethod("do_not_reverse_lookup", cfact.getFastSingletonMethod("do_not_reverse_lookup"));
        rb_cBasicSocket.getMetaClass().defineFastMethod("do_not_reverse_lookup=", cfact.getFastSingletonMethod("set_do_not_reverse_lookup", IRubyObject.class));
    }

    protected Channel socketChannel;

    public RubyBasicSocket(Ruby runtime, RubyClass type) {
        super(runtime, type);
    }

    protected void setChannel(Channel c) {
        this.socketChannel = c;
        try {
            openFile.setMainStream(new ChannelStream(getRuntime(), new ChannelDescriptor(socketChannel, getNewFileno())));
            openFile.getMainStream().setSync(true);
    	} catch (IOException e) {
            throw getRuntime().newIOError(e.getMessage());
        }
        registerIOHandler(openFile.getMainStream());
        openFile.setModes(openFile.getMainStream().getModes());
    }

    @Override
    public IRubyObject close_write() {
        try {
            ((SocketChannel)this.socketChannel).socket().shutdownOutput();
            openFile.getMainStream().closeWrite();
        } catch (Stream.BadDescriptorException bde) {
            throw getRuntime().newErrnoEBADFError();
    	} catch (IOException e) {
            throw getRuntime().newIOError(e.getMessage());
        }
        return getRuntime().getNil();
    }

    public IRubyObject write_send(IRubyObject[] args) {
        return syswrite(args[0]);
    }
    
    public IRubyObject recv(IRubyObject[] args) {
        try {
            return RubyString.newString(getRuntime(), openFile.getMainStream().read(RubyNumeric.fix2int(args[0])));
        } catch (Stream.BadDescriptorException e) {
            throw getRuntime().newErrnoEBADFError();
        } catch (EOFException e) {
            // recv returns nil on EOF
            return getRuntime().getNil();
    	} catch (IOException e) {
            // All errors to sysread should be SystemCallErrors, but on a closed stream
            // Ruby returns an IOError.  Java throws same exception for all errors so
            // we resort to this hack...
            if ("Socket not open".equals(e.getMessage())) {
	            throw getRuntime().newIOError(e.getMessage());
            }
    	    throw getRuntime().newSystemCallError(e.getMessage());
    	}
    }

    protected InetSocketAddress getLocalSocket() {
        if (socketChannel instanceof SocketChannel) {
            return (InetSocketAddress)((SocketChannel)socketChannel).socket().getLocalSocketAddress();
        } else if (socketChannel instanceof ServerSocketChannel) {
            return (InetSocketAddress)((ServerSocketChannel) socketChannel).socket().getLocalSocketAddress();
        } else {
            return null;
        }
    }

    protected InetSocketAddress getRemoteSocket() {
        if(socketChannel instanceof SocketChannel) {
            return (InetSocketAddress)((SocketChannel)socketChannel).socket().getRemoteSocketAddress();
        } else {
            return null;
        }
    }

    private Socket asSocket() {
        if(!(socketChannel instanceof SocketChannel)) {
            throw getRuntime().newErrnoENOPROTOOPTError();
        }

        return ((SocketChannel)socketChannel).socket();
    }

    private ServerSocket asServerSocket() {
        if(!(socketChannel instanceof ServerSocketChannel)) {
            throw getRuntime().newErrnoENOPROTOOPTError();
        }

        return ((ServerSocketChannel)socketChannel).socket();
    }

    private DatagramSocket asDatagramSocket() {
        if(!(socketChannel instanceof DatagramChannel)) {
            throw getRuntime().newErrnoENOPROTOOPTError();
        }

        return ((DatagramChannel)socketChannel).socket();
    }

    private IRubyObject getBroadcast() throws IOException {
        return trueFalse((socketChannel instanceof DatagramChannel) ? asDatagramSocket().getBroadcast() : false);
    }

    private IRubyObject getKeepAlive() throws IOException {
        return trueFalse(
                         (socketChannel instanceof SocketChannel) ? asSocket().getKeepAlive() : false
                         );
    }

    private IRubyObject getLinger() throws IOException {
        return number(
                      (socketChannel instanceof SocketChannel) ? asSocket().getSoLinger() : 0
                      );
    }

    private IRubyObject getOOBInline() throws IOException {
        return trueFalse(
                         (socketChannel instanceof SocketChannel) ? asSocket().getOOBInline() : false
                         );
    }

    private IRubyObject getRcvBuf() throws IOException {
        return number(
                      (socketChannel instanceof SocketChannel) ? asSocket().getReceiveBufferSize() : 
                      ((socketChannel instanceof ServerSocketChannel) ? asServerSocket().getReceiveBufferSize() : 
                       asDatagramSocket().getReceiveBufferSize())
                      );
    }

    private IRubyObject getSndBuf() throws IOException {
        return number(
                      (socketChannel instanceof SocketChannel) ? asSocket().getSendBufferSize() : 
                      ((socketChannel instanceof DatagramChannel) ? asDatagramSocket().getSendBufferSize() : 0)
                      );
    }

    private IRubyObject getReuseAddr() throws IOException {
        return trueFalse(
                         (socketChannel instanceof ServerSocketChannel) ? asServerSocket().getReuseAddress() : false
                         );
    }

    private IRubyObject getTimeout() throws IOException {
        return number(
                      (socketChannel instanceof SocketChannel) ? asSocket().getSoTimeout() : 
                      ((socketChannel instanceof ServerSocketChannel) ? asServerSocket().getSoTimeout() : 
                       ((socketChannel instanceof DatagramChannel) ? asDatagramSocket().getSoTimeout() : 0))
                      );
    }

    protected int getSoTypeDefault() {
        return 0;
    }

    private IRubyObject getSoType() throws IOException {
        return number(
                      (socketChannel instanceof SocketChannel) ? RubySocket.SOCK_STREAM : 
                      ((socketChannel instanceof ServerSocketChannel) ? RubySocket.SOCK_STREAM : 
                       ((socketChannel instanceof DatagramChannel) ? RubySocket.SOCK_DGRAM : getSoTypeDefault()))
                      );
    }

    private IRubyObject trueFalse(boolean val) {
        return getRuntime().newString( val ? " \u0000\u0000\u0000" : "\u0000\u0000\u0000\u0000" );
    }

    private IRubyObject number(long s) {
        StringBuffer result = new StringBuffer();
        result.append((char) ((s>>24) &0xff)).append((char) ((s>>16) &0xff));
        result.append((char) ((s >> 8) & 0xff)).append((char) (s & 0xff));
        return getRuntime().newString(result.toString());
    }

    public IRubyObject getsockopt(IRubyObject lev, IRubyObject optname) {
        int level = RubyNumeric.fix2int(lev);
        int opt = RubyNumeric.fix2int(optname);

        try {
            switch(level) {
            case RubySocket.SOL_IP:
            case RubySocket.SOL_SOCKET:
            case RubySocket.SOL_TCP:
            case RubySocket.SOL_UDP:
                switch(opt) {
                case RubySocket.SO_BROADCAST:
                    return getBroadcast();
                case RubySocket.SO_KEEPALIVE:
                    return getKeepAlive();
                case RubySocket.SO_LINGER:
                    return getLinger();
                case RubySocket.SO_OOBINLINE:
                    return getOOBInline();
                case RubySocket.SO_RCVBUF:
                    return getRcvBuf();
                case RubySocket.SO_REUSEADDR:
                    return getReuseAddr();
                case RubySocket.SO_SNDBUF:
                    return getSndBuf();
                case RubySocket.SO_RCVTIMEO:
                case RubySocket.SO_SNDTIMEO:
                    return getTimeout();
                case RubySocket.SO_TYPE:
                    return getSoType();

                    // Can't support the rest with Java
                case RubySocket.SO_RCVLOWAT:
                    return number(1);
                case RubySocket.SO_SNDLOWAT:
                    return number(2048);
                case RubySocket.SO_DEBUG:
                case RubySocket.SO_ERROR:
                case RubySocket.SO_DONTROUTE:
                case RubySocket.SO_TIMESTAMP:
                    return trueFalse(false);
                default:
                    throw getRuntime().newErrnoENOPROTOOPTError();
                }
            default:
                throw getRuntime().newErrnoENOPROTOOPTError();
            }
        } catch(IOException e) {
            throw getRuntime().newErrnoENOPROTOOPTError();
        }
    }

    public IRubyObject setsockopt(IRubyObject lev, IRubyObject optname, IRubyObject val) {
        int level = RubyNumeric.fix2int(lev);
        int opt = RubyNumeric.fix2int(optname);
        switch(level) {
        case RubySocket.SOL_IP:
        case RubySocket.SOL_SOCKET:
        case RubySocket.SOL_TCP:
        case RubySocket.SOL_UDP:
            switch(opt) {
            case RubySocket.SO_BROADCAST:
                break;
            case RubySocket.SO_DEBUG:
                break;
            case RubySocket.SO_DONTROUTE:
                break;
            case RubySocket.SO_ERROR:
                break;
            case RubySocket.SO_KEEPALIVE:
                break;
            case RubySocket.SO_LINGER:
                break;
            case RubySocket.SO_OOBINLINE:
                break;
            case RubySocket.SO_RCVBUF:
                break;
            case RubySocket.SO_RCVLOWAT:
                break;
            case RubySocket.SO_RCVTIMEO:
                break;
            case RubySocket.SO_REUSEADDR:
                break;
            case RubySocket.SO_SNDBUF:
                break;
            case RubySocket.SO_SNDLOWAT:
                break;
            case RubySocket.SO_SNDTIMEO:
                break;
            case RubySocket.SO_TIMESTAMP:
                break;
            case RubySocket.SO_TYPE:
                break;
            default:
                throw getRuntime().newErrnoENOPROTOOPTError();
            }
            break;
        default:
            throw getRuntime().newErrnoENOPROTOOPTError();
        }

        return getRuntime().getNil();
    }

    public IRubyObject getsockname() {
        SocketAddress sock = getLocalSocket();
        if(null == sock) {
            throw getRuntime().newIOError("Not Supported");
        }
        return getRuntime().newString(sock.toString());
    }

    public IRubyObject getpeername() {
        SocketAddress sock = getRemoteSocket();
        if(null == sock) {
            throw getRuntime().newIOError("Not Supported");
        }
        return getRuntime().newString(sock.toString());
    }

    public IRubyObject shutdown(IRubyObject[] args) {
        if (getRuntime().getSafeLevel() >= 4 && tainted_p().isFalse()) {
            throw getRuntime().newSecurityError("Insecure: can't shutdown socket");
        }
        
        int how = 2;
        if (args.length > 0) {
            how = RubyNumeric.fix2int(args[0]);
        }
        if (how < 0 || 2 < how) {
            throw getRuntime().newArgumentError("`how' should be either 0, 1, 2");
        }
        if (how != 2) {
            throw getRuntime().newNotImplementedError("Shutdown currently only works with how=2");
        }
        return close();
    }

    public static IRubyObject do_not_reverse_lookup(IRubyObject recv) {
        return recv.getRuntime().isDoNotReverseLookupEnabled() ? recv.getRuntime().getTrue() : recv.getRuntime().getFalse();
    }
    
    public static IRubyObject set_do_not_reverse_lookup(IRubyObject recv, IRubyObject flag) {
        recv.getRuntime().setDoNotReverseLookupEnabled(flag.isTrue());
        return recv.getRuntime().isDoNotReverseLookupEnabled() ? recv.getRuntime().getTrue() : recv.getRuntime().getFalse();
    }
}// RubyBasicSocket
