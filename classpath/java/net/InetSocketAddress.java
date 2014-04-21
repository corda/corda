/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.net;

public class InetSocketAddress extends SocketAddress {
  private final String host;
  private final InetAddress address;
  private final int port;

  public InetSocketAddress(String host, int port) {
    InetAddress address;
    try {
      address = InetAddress.getByName(host);
      host = address.getHostName();
    } catch (UnknownHostException e) {
      address = null;
    }
    this.host = host;
    this.address = address;
    this.port = port;
  }
  
  public InetSocketAddress(InetAddress address, int port) {
    this.host = address.getHostName();
    this.address = address;
    this.port = port;
  }

  public InetAddress getAddress() {
    return address;
  }
  
  public String getHostName() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public boolean equals(Object o) {
    if (o instanceof InetSocketAddress) {
      InetSocketAddress a = (InetSocketAddress) o;
      return a.address.equals(address) && a.port == port;
    } else {
      return false;
    }
  }

  public int hashCode() {
    return port ^ address.hashCode();
  }

  public String toString() {
    return getHostName() + ":" + port;
  }
}
