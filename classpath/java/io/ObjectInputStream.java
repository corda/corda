/* Copyright (c) 2008-2013, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.io;

import static java.io.ObjectOutputStream.STREAM_MAGIC;
import static java.io.ObjectOutputStream.STREAM_VERSION;
import static java.io.ObjectOutputStream.TC_NULL;
import static java.io.ObjectOutputStream.TC_REFERENCE;
import static java.io.ObjectOutputStream.TC_CLASSDESC;
import static java.io.ObjectOutputStream.TC_OBJECT;
import static java.io.ObjectOutputStream.TC_STRING;
import static java.io.ObjectOutputStream.TC_ARRAY;
import static java.io.ObjectOutputStream.TC_CLASS;
import static java.io.ObjectOutputStream.TC_BLOCKDATA;
import static java.io.ObjectOutputStream.TC_ENDBLOCKDATA;
import static java.io.ObjectOutputStream.TC_RESET;
import static java.io.ObjectOutputStream.TC_BLOCKDATALONG;
import static java.io.ObjectOutputStream.TC_EXCEPTION;
import static java.io.ObjectOutputStream.TC_LONGSTRING;
import static java.io.ObjectOutputStream.TC_PROXYCLASSDESC;
import static java.io.ObjectOutputStream.TC_ENUM;
import static java.io.ObjectOutputStream.SC_WRITE_METHOD;
import static java.io.ObjectOutputStream.SC_BLOCK_DATA;
import static java.io.ObjectOutputStream.SC_SERIALIZABLE;
import static java.io.ObjectOutputStream.SC_EXTERNALIZABLE;
import static java.io.ObjectOutputStream.SC_ENUM;

import avian.VMClass;

import java.util.HashMap;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class ObjectInputStream extends InputStream implements DataInput {
  private final InputStream in;

  public ObjectInputStream(InputStream in) throws IOException {
    this.in = in;
    short signature = (short)rawShort();
    if (signature != STREAM_MAGIC) {
      throw new IOException("Unrecognized signature: 0x"
          + Integer.toHexString(signature));
    }
    int version = rawShort();
    if (version != STREAM_VERSION) {
      throw new IOException("Unsupported version: " + version);
    }
  }

  public int read() throws IOException {
    return in.read();
  }

  private int rawByte() throws IOException {
    int c = read();
    if (c < 0) {
      throw new EOFException();
    }
    return c;
  }

  private int rawShort() throws IOException {
    return (rawByte() << 8) | rawByte();
  }

  private int rawInt() throws IOException {
    return (rawShort() << 16) | rawShort();
  }

  private long rawLong() throws IOException {
    return ((rawInt() & 0xffffffffl) << 32) | rawInt();
  }

  private String rawString() throws IOException {
    int length = rawShort();
    byte[] array = new byte[length];
    readFully(array);
    return new String(array);
  }

  public int read(byte[] b, int offset, int length) throws IOException {
    return in.read(b, offset, length);
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

  public void close() throws IOException {
    in.close();
  }

  public Object readObject() throws IOException, ClassNotFoundException {
    throw new UnsupportedOperationException();
  }
  private int remainingBlockData;

  private int rawBlockDataByte() throws IOException {
    while (remainingBlockData <= 0) {
      int b = rawByte();
      if (b == TC_BLOCKDATA) {
        remainingBlockData = rawByte();
      } else {
        throw new UnsupportedOperationException("Unknown token: 0x"
            + Integer.toHexString(b));
      }
    }
    --remainingBlockData;
    return rawByte();
  }

  private int rawBlockDataShort() throws IOException {
    return (rawBlockDataByte() << 8) | rawBlockDataByte();
  }

  private int rawBlockDataInt() throws IOException {
    return (rawBlockDataShort() << 16) | rawBlockDataShort();
  }

  private long rawBlockDataLong() throws IOException {
    return ((rawBlockDataInt() & 0xffffffffl) << 32) | rawBlockDataInt();
  }

  public boolean readBoolean() throws IOException {
    return rawBlockDataByte() != 0;
  }

  public byte readByte() throws IOException {
    return (byte)rawBlockDataByte();
  }

  public char readChar() throws IOException {
    return (char)rawBlockDataShort();
  }

  public short readShort() throws IOException {
    return (short)rawBlockDataShort();
  }

  public int readInt() throws IOException {
    return rawBlockDataInt();
  }

  public long readLong() throws IOException {
    return rawBlockDataLong();
  }

  public float readFloat() throws IOException {
    return Float.intBitsToFloat(rawBlockDataInt());
  }

  public double readDouble() throws IOException {
    return Double.longBitsToDouble(rawBlockDataLong());
  }

  public int readUnsignedByte() throws IOException {
    return rawBlockDataByte();
  }

  public int readUnsignedShort() throws IOException {
    return rawBlockDataShort();
  }

  public String readUTF() throws IOException {
    int length = rawBlockDataShort();
    if (remainingBlockData < length) {
      throw new IOException("Short block data: "
          + remainingBlockData + " < " + length);
    }
    byte[] bytes = new byte[length];
    readFully(bytes);
    remainingBlockData -= length;
    return new String(bytes, "UTF-8");
  }

  public int skipBytes(int count) throws IOException {
    int i = 0;
    while (i < count) {
      if (read() < 0) {
        return i;
      }
      ++i;
    }
    return count;
  }


  private static native Object makeInstance(VMClass c);
}
