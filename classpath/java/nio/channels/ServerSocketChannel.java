/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

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
