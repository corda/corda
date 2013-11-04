/* Copyright (c) 2008-2013, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.net;

public class InetSocketAddress extends SocketAddress {
  private final InetAddress address;
  private final int port;

  public InetSocketAddress(String host, int port) throws UnknownHostException {
	  this.address = InetAddress.getByName(host);
	  this.port = port;
  }
  
  public InetSocketAddress(InetAddress address, int port) {
    this.address = address;
    this.port = port;
  }

  public InetAddress getAddress() {
    return address;
  }
  
  public String getHostName() {
	return address.getHostName();
  }

  public int getPort() {
    return port;
  }
}
