/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.io;

import java.lang.IllegalArgumentException;
import java.nio.ByteBuffer;

public class RandomAccessFile implements DataInput, Closeable {

  private long peer;
  private File file;
  private long position = 0;
  private long length;
  private boolean allowWrite;

  public RandomAccessFile(String name, String mode)
    throws FileNotFoundException
  {
    this(new File(name), mode);
  }

  public RandomAccessFile(File file, String mode)
    throws FileNotFoundException
  {
    if (file == null) throw new NullPointerException();
    if (mode.equals("rw")) allowWrite = true;
    else if (! mode.equals("r")) throw new IllegalArgumentException();
    this.file = file;
    open();
  }

  private void open() throws FileNotFoundException {
    long[] result = new long[2];
    open(file.getPath(), allowWrite, result);
    peer = result[0];
    length = result[1];
  }

  private static native void open(String name, boolean allowWrite, long[] result)
    throws FileNotFoundException;

  private void refresh() throws IOException {
    if (file.length() != length) {
      close();
      open();
    }
  }

  public long length() throws IOException {
    refresh();
    return length;
  }

  public long getFilePointer() throws IOException {
    return position;
  }

  public void seek(long position) throws IOException {
    if (position < 0 || (!allowWrite && position > length())) throw new IOException();

    this.position = position;
  }

  public int skipBytes(int count) throws IOException {
    if (position + count > length()) throw new IOException();
    this.position = position + count;
    return count;
  }
  
  public int read(byte b[], int off, int len) throws IOException {
    if(b == null)
      throw new IllegalArgumentException();
    if (peer == 0)
      throw new IOException();
    if(len == 0)
      return 0;
    if (position + len > this.length)
      throw new EOFException();
    if (off < 0 || off + len > b.length)
      throw new ArrayIndexOutOfBoundsException();
    int bytesRead = readBytes(peer, position, b, off, len);
    position += bytesRead;
    return bytesRead;
  }
  
  public int read(byte b[]) throws IOException {
    if(b == null)
      throw new IllegalArgumentException();
    if (peer == 0)
      throw new IOException();
    if(b.length == 0)
      return 0;
    if (position + b.length > this.length)
      throw new EOFException();
    int bytesRead = readBytes(peer, position, b, 0, b.length);
    position += bytesRead;
    return bytesRead;
  }

  public void readFully(byte b[], int off, int len) throws IOException {
    if (b == null)
      throw new IllegalArgumentException();
    if (peer == 0)
      throw new IOException();
    if(len == 0)
      return;
    if (position + len > this.length)
      throw new EOFException();
    if (off < 0 || off + len > b.length)
      throw new ArrayIndexOutOfBoundsException();
    int n = 0;
    do {
      int count = readBytes(peer, position, b, off + n, len - n);
      position += count;
      if (count == 0)
        throw new EOFException();
      n += count;
    } while (n < len);
  }
  
  public void readFully(byte b[]) throws IOException {
    readFully(b, 0, b.length);
  }

  private static native int readBytes(long peer, long position, byte[] buffer,
                                  int offset, int length);

  public boolean readBoolean() throws IOException {
    return readByte() != 0;
  }

  public int read() throws IOException {
    try {
      return readByte() & 0xff;
    } catch (final EOFException e) {
      return -1;
    }
  }

  public byte readByte() throws IOException {
    final byte[] buffer = new byte[1];
    readFully(buffer);
    return buffer[0];
  }

  public short readShort() throws IOException {
    final byte[] buffer = new byte[2];
    readFully(buffer);
    return (short)((buffer[0] << 8) | buffer[1]);
  }

  public int readInt() throws IOException {
    byte[] buf = new byte[4];
    readFully(buf);
    return ((buf[0] << 24) | (buf[1] << 16) | (buf[2] << 8) | buf[3]);
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

  public void write(int b) throws IOException {
    int count = writeBytes(peer, position, new byte[] { (byte)b }, 0, 1);
    if (count > 0) position += count;
  }

  private static native int writeBytes(long peer, long position, byte[] buffer,
                                  int offset, int length);

  public void close() throws IOException {
    if (peer != 0) {
      close(peer);
      peer = 0;
    }
  }

  private static native void close(long peer);
}
