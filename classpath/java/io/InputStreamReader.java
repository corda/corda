/* Copyright (c) 2008-2009, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.io;

import avian.Utf8;

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

    if (c <= 0) return c;

    char[] buffer16 = Utf8.decode16(buffer, 0, c);

    for (int i = 0; i < buffer16.length; ++i) {
      b[i + offset] = buffer16[i];
    }

    return buffer16.length;
  }

  public void close() throws IOException {
    in.close();
  }
}
