/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.net;

import java.io.IOException;

public class InetAddress {
  private final String name;
  private final int ip;

  private InetAddress(String name) throws UnknownHostException {
    this.name = name;
    this.ip = ipv4AddressForName(name);
  }

  public String getHostName() {
	return name;
  }
  
  public String getHostAddress() {
	try {
		return new InetAddress(name).toString();
	} catch (UnknownHostException e) {
		return null;	// Strange case
	}
  }

  public static InetAddress getByName(String name) throws UnknownHostException {
    try {
      Socket.init();
      return new InetAddress(name);
    } catch (IOException e) {
      UnknownHostException uhe = new UnknownHostException(name);
      uhe.initCause(e);
      throw uhe;
    }
  }

  public byte[] getAddress() {
	  byte[] res = new byte[4];
	  res[0] = (byte) ( ip >>> 24);
	  res[1] = (byte) ((ip >>> 16) & 0xFF);
	  res[2] = (byte) ((ip >>> 8 ) & 0xFF);
	  res[3] = (byte) ((ip       ) & 0xFF);
	  return res;
  }
  
  @Override
	public String toString() {
	  byte[] addr = getAddress();
	  return (int)((addr[0] + 256) % 256) + "." + 
	         (int)((addr[1] + 256) % 256) + "." + 
	         (int)((addr[2] + 256) % 256) + "." + 
	         (int)((addr[3] + 256) % 256);
	}
  
  public int getRawAddress() {
	  return ip;
  }
  
  static native int ipv4AddressForName(String name) throws UnknownHostException;

  public boolean equals(Object o) {
    return o instanceof InetAddress && ((InetAddress) o).ip == ip;
  }

  public int hashCode() {
    return ip;
  }
}
