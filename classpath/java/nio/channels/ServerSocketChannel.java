package java.nio.channels;

import java.net.InetSocketAddress;

public class ServerSocketChannel extends SocketChannel {
  public static ServerSocketChannel open() {
    return new ServerSocketChannel();
  }

  public SocketChannel accept() throws Exception {
    SocketChannel c = new SocketChannel();
    c.socket = doAccept();
    c.connected = true;
    return c;
  }

  public Handle socket() {
    return new Handle();
  }

  private int doAccept() throws Exception {
    return natDoAccept(socket);
  }

  private int doListen(String host, int port) throws Exception {
    return natDoListen(host, port);
  }

  public class Handle {
    public void bind(InetSocketAddress address)
      throws Exception
    {
      socket = doListen(address.getHostName(), address.getPort());
    }
  }

  private static native int natDoAccept(int socket) throws Exception;
  private static native int natDoListen(String host, int port) throws Exception;
}
