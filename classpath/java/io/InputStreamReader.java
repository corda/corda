/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.io;

import avian.Utf8;

public class InputStreamReader extends Reader {
  private static final int MultibytePadding = 4;

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
    if (length == 0) {
      return 0;
    }

    byte[] buffer = new byte[length + MultibytePadding];
    int bufferLength = length;
    int bufferOffset = 0;
    while (true) {
      int c = in.read(buffer, bufferOffset, bufferLength);

      if (c <= 0) {
        if (bufferOffset > 0) {
          // if we've reached the end of the stream while trying to
          // read a multibyte character, we still need to return any
          // competely-decoded characters, plus \ufffd to indicate an
          // unknown character
          c = 1;
          while (bufferOffset > 0) {
            char[] buffer16 = Utf8.decode16(buffer, 0, bufferOffset);

            if (buffer16 != null) {
              System.arraycopy(buffer16, 0, b, offset, buffer16.length);
              
              c = buffer16.length + 1;
              break;
            } else {
              -- bufferOffset;
            }
          }

          b[offset + c - 1] = '\ufffd';
        }

        return c;
      }

      bufferOffset += c;

      char[] buffer16 = Utf8.decode16(buffer, 0, bufferOffset);

      if (buffer16 != null) {
        bufferOffset = 0;

        System.arraycopy(buffer16, 0, b, offset, buffer16.length);

        return buffer16.length;
      } else {
        // the buffer ended in an incomplete multibyte character, so
        // we try to read a another byte at a time until it's complete
        bufferLength = 1;
      }
    }
  }

  public void close() throws IOException {
    in.close();
  }
}
