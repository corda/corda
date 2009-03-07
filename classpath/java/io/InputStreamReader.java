/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.io;

public class InputStreamReader extends Reader {
  private final InputStream in;

  public InputStreamReader(InputStream in) {
    this.in = in;
  }

  public InputStreamReader(InputStream in, String encoding)
    throws UnsupportedEncodingException
  {
    this(in);

    if (! encoding.equals("UTF-8")) {
      throw new UnsupportedEncodingException(encoding);
    }    
  }

  
  public int read(char[] b, int offset, int length) throws IOException {
    byte[] buffer = new byte[length];
    int c = in.read(buffer);
    for (int i = 0; i < c; ++i) {
      b[i + offset] = (char) buffer[i];
    }
    return c;
  }

  public void close() throws IOException {
    in.close();
  }
}
