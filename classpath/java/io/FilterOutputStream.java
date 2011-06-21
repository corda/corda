/* Copyright (c) 2011, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.io;

public class FilterOutputStream extends OutputStream {
  private OutputStream os;

  public void close() throws IOException {
    os.close();
  }

  public void flush() throws IOException {
    os.flush();
  }

  public void write(byte[] b) throws IOException {
    os.write(b);
  }

  public void write(byte[] b, int off, int len) throws IOException {
    os.write(b, off, len);
  }

  public void write(int b) throws IOException {
    os.write(b);
  }

}
