package java.nio.channels;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class SocketChannel extends SelectableChannel
  implements ReadableByteChannel, WritableByteChannel
{
  public static final int InvalidSocket = -1;

  protected int socket = InvalidSocket;
  protected boolean open = true;
  protected boolean connected = false;

  public static SocketChannel open() {
    return new SocketChannel();
  }

  public void configureBlocking(boolean v) {
    if (v) throw new IllegalArgumentException();
  }

  public void connect(InetSocketAddress address) throws Exception {
    socket = doConnect(address.getHostName(), address.getPort());
  }

  public void close() throws IOException {
    super.close();
    if (! open) return;
    closeSocket();
    open = false;
  }

  public boolean isOpen() {
    return open;
  }

  private int doConnect(String host, int port) throws Exception {
    boolean b[] = new boolean[1];
    int s = natDoConnect(host, port, b);
    connected = b[0];
    return s;
  }

  public int read(ByteBuffer b) throws IOException {
    if (! open) return -1;
    if (b.remaining() == 0) return 0;
    int r = natRead(socket, b.array(), b.arrayOffset() + b.position(), b.remaining());
    if (r > 0) {
      b.position(b.position() + r);
    }
    return r;
  }

  public int write(ByteBuffer b) throws IOException {
    if (! connected) {
      natThrowWriteError(socket);
    }
    int w = natWrite(socket, b.array(), b.arrayOffset() + b.position(), b.remaining());
    if (w > 0) {
      b.position(b.position() + w);
    }
    return w;
  }

  private void closeSocket() {
    natCloseSocket(socket);
  }

  int socketFD() {
    return socket;
  }

  private static native int natDoConnect(String host, int port, boolean[] connected)
    throws Exception;
  private static native int natRead(int socket, byte[] buffer, int offset, int length)
    throws IOException;
  private static native int natWrite(int socket, byte[] buffer, int offset, int length)
    throws IOException;
  private static native void natThrowWriteError(int socket) throws IOException;
  private static native void natCloseSocket(int socket);
}
