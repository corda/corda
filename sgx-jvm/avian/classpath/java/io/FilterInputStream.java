/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.io;

public class FilterInputStream extends InputStream {
  protected InputStream in;

  public FilterInputStream(InputStream in) {
    this.in = in;
  }

  public void close() throws IOException {
    in.close();
  }

  public int read(byte[] b) throws IOException {
    return in.read(b);
  }

  public int read(byte[] b, int off, int len) throws IOException {
    return in.read(b, off, len);
  }

  public int read() throws IOException {
    return in.read();
  }
}
