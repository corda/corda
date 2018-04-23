/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.io;

public abstract class Writer implements Closeable, Flushable, Appendable {
  public void write(int c) throws IOException {
    char[] buffer = new char[] { (char) c };
    write(buffer);
  }

  public void write(char[] buffer) throws IOException {
    write(buffer, 0, buffer.length);
  }

  public void write(String s) throws IOException {
    write(s.toCharArray());
  }

  public void write(String s, int offset, int length) throws IOException {
    char[] b = new char[length];
    s.getChars(offset, offset + length, b, 0);
    write(b);
  }

  public abstract void write(char[] buffer, int offset, int length)
    throws IOException;

  public Appendable append(final char c) throws IOException {
    write((int)c);
    return this;
  }

  public Appendable append(final CharSequence sequence) throws IOException {
    return append(sequence, 0, sequence.length());
  }

  public Appendable append(CharSequence sequence, int start, int end) 
      throws IOException {
    final int length = end - start;
    if (sequence instanceof String) {
      write((String)sequence, start, length);
    } else {
      final char[] charArray = new char[length];
      for (int i = start; i < end; i++) { 
        charArray[i] = sequence.charAt(i);
      }
      write(charArray, 0, length);
    }
    return this;
  }

  public abstract void flush() throws IOException;

  public abstract void close() throws IOException;
}
