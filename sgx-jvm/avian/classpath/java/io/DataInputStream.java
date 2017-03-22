/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.io;

public class DataInputStream extends InputStream implements DataInput {
  private InputStream in;

  public DataInputStream(InputStream in) {
    this.in = in;
  }

  public void close() throws IOException {
    in.close();
  }

  public int read(byte[] buffer) throws IOException {
    return in.read(buffer);
  }

  public int read(byte[] buffer, int offset, int length) throws IOException {
    return in.read(buffer, offset, length);
  }

  public void readFully(byte[] b) throws IOException {
    readFully(b, 0, b.length);
  }

  public void readFully(byte[] b, int offset, int length) throws IOException {
    while (length > 0) {
      int count = read(b, offset, length);
      if (count < 0) {
        throw new EOFException("Reached EOF " + length + " bytes too early");
      }
      offset += count;
      length -= count;
    }
  }

  public int read() throws IOException {
    return in.read();
  }

  private int read0() throws IOException {
    int b = in.read();
    if (b < 0) {
      throw new EOFException();
    }
    return b;
  }

  public boolean readBoolean() throws IOException {
    return read0() != 0;
  }

  public byte readByte() throws IOException {
    return (byte)read0();
  }

  public short readShort() throws IOException {
    return (short)((read0() << 8) | read0());
  }

  public int readInt() throws IOException {
    return ((read0() << 24) | (read0() << 16) | (read0() << 8) | read0());
  }

  public float readFloat() throws IOException {
    return Float.floatToIntBits(readInt());
  }

  public double readDouble() throws IOException {
    return Double.doubleToLongBits(readLong());
  }

  public long readLong() throws IOException {
    return ((readInt() & 0xffffffffl) << 32) | (readInt() & 0xffffffffl);
  }

  public char readChar() throws IOException {
    return (char)readShort();
  }

  public int readUnsignedByte() throws IOException {
    return readByte() & 0xff;
  }

  public int readUnsignedShort() throws IOException {
    return readShort() & 0xffff;
  }

  public String readUTF() throws IOException {
    int length = readUnsignedShort();
    byte[] bytes = new byte[length];
    readFully(bytes);
    return new String(bytes, "UTF-8");
  }

  @Deprecated
  public String readLine() throws IOException {
    int c = read();
    if (c < 0) {
      return null;
    } else if (c == '\n') {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    for (;;) {
      builder.append((char)c);
      c = read();
      if (c < 0 || c == '\n') {
        return builder.toString();
      }
    }
  }

  public int skipBytes(int n) throws IOException {
    for (int count = 0; count < n; ++count) {
      if (read() < 0) {;
        return count;
      }
    }
    return n;
  }
}
