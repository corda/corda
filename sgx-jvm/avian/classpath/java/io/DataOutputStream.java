/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.io;

public class DataOutputStream extends OutputStream implements DataOutput {
  private OutputStream out;

  public DataOutputStream(OutputStream out) {
    this.out = out;
  }

  public void close() throws IOException {
    out.close();
  }

  public void flush() throws IOException {
    out.flush();
  }

  public void write(byte[] buffer) throws IOException {
    out.write(buffer);
  }

  public void write(byte[] buffer, int offset, int length) throws IOException {
    out.write(buffer, offset, length);
  }

  public void write(int b) throws IOException {
    out.write(b);
  }

  public void writeBoolean(boolean b) throws IOException {
    writeByte(b ? 1 : 0);
  }

  public void writeByte(int b) throws IOException {
    out.write(b);
  }

  public void writeShort(int s) throws IOException {
    write((byte)(s >> 8));
    write((byte)s);
  }

  public void writeInt(int i) throws IOException {
    write((byte)(i >> 24));
    write((byte)(i >> 16));
    write((byte)(i >> 8));
    write((byte)i);
  }

  public void writeFloat(float f) throws IOException {
    writeInt(Float.floatToIntBits(f));
  }

  public void writeDouble(double d) throws IOException {
    writeLong(Double.doubleToLongBits(d));
  }

  public void writeLong(long l) throws IOException {
    write((byte)(l >> 56));
    write((byte)(l >> 48));
    write((byte)(l >> 40));
    write((byte)(l >> 32));
    write((byte)(l >> 24));
    write((byte)(l >> 16));
    write((byte)(l >> 8));
    write((byte)l);
  }

  public void writeChar(int ch) throws IOException {
    write((byte)(ch >> 8));
    write((byte)ch);
  }

  public void writeChars(String s) throws IOException {
    for (char ch : s.toCharArray()) {
      writeChar(ch & 0xffff);
    }
  }

  public void writeBytes(String s) throws IOException {
    out.write(s.getBytes());
  }

  public void writeUTF(String s) throws IOException {
    byte[] bytes = s.getBytes("UTF-8");
    writeShort((short)bytes.length);
    out.write(bytes);
  }
}
