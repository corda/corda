/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.io;

public abstract class FilterReader extends Reader {
  protected Reader in;

  protected FilterReader(Reader in) {
    this.in = in;
  }

  public int read() throws IOException {
    return in.read();
  }

  public int read(char[] buffer, int offset, int length) throws IOException {
    return in.read(buffer, offset, length);
  }

  public boolean ready() throws IOException {
    throw new UnsupportedOperationException();
  }

  public long skip(long n) throws IOException {
    throw new UnsupportedOperationException();
  }

  public void close() throws IOException {
    in.close();
  }

  public boolean markSupported() {
    return in.markSupported();
  }

  public void mark(int readAheadLimit) throws IOException {
    in.mark(readAheadLimit);
  }

  public void reset() throws IOException {
   in.reset();
  }
}
