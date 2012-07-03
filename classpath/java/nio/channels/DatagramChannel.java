/* Copyright (c) 2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.nio.channels;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.Socket;
import java.net.StandardProtocolFamily;

public class DatagramChannel extends SelectableChannel
  implements ReadableByteChannel, WritableByteChannel
{
  public static final int InvalidSocket = -1;

  private int socket = InvalidSocket;
  private boolean blocking = true;

  public DatagramChannel configureBlocking(boolean v) throws IOException {
    blocking = v;
    if (socket != InvalidSocket) {
      configureBlocking(socket, v);
    }
    return this;
  }

  int socketFD() {
    return socket;
  }

  void handleReadyOps(int ops) {
    // ignore
  }

  public static DatagramChannel open(ProtocolFamily family)
    throws IOException
  {
    if (family.equals(StandardProtocolFamily.INET)) {
      Socket.init();
    
      return new DatagramChannel();
    } else {
      throw new UnsupportedOperationException();
    }
  }

  public DatagramChannel bind(SocketAddress address) throws IOException {
    InetSocketAddress inetAddress;
    try {
      inetAddress = (InetSocketAddress) address;
    } catch (ClassCastException e) {
      throw new UnsupportedAddressTypeException();
    }

    socket = bind(inetAddress.getHostName(), inetAddress.getPort());

    return this;
  }

  public DatagramChannel connect(SocketAddress address) throws IOException {
    InetSocketAddress inetAddress;
    try {
      inetAddress = (InetSocketAddress) address;
    } catch (ClassCastException e) {
      throw new UnsupportedAddressTypeException();
    }

    socket = connect(inetAddress.getHostName(), inetAddress.getPort());

    return this;
  }

  public int write(ByteBuffer b) throws IOException {
    if (b.remaining() == 0) return 0;

    byte[] array = b.array();
    if (array == null) throw new NullPointerException();

    int c = write
      (socket, array, b.arrayOffset() + b.position(), b.remaining(), blocking);

    if (c > 0) {
      b.position(b.position() + c);
    }

    return c;
  }

  public int read(ByteBuffer b) throws IOException {
    if (b.remaining() == 0) return 0;

    byte[] array = b.array();
    if (array == null) throw new NullPointerException();

    int c = read
      (socket, array, b.arrayOffset() + b.position(), b.remaining(), blocking);

    if (c > 0) {
      b.position(b.position() + c);
    }

    return c;
  }

  private static native void configureBlocking(int socket, boolean blocking)
    throws IOException;
  private static native int bind(String hostname, int port)
    throws IOException;
  private static native int connect(String hostname, int port)
    throws IOException;
  private static native int write(int socket, byte[] array, int offset,
                                  int length, boolean blocking)
    throws IOException;
  private static native int read(int socket, byte[] array, int offset,
                                 int length, boolean blocking)
    throws IOException;
}
