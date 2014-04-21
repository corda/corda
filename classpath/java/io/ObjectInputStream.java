/* Copyright (c) 2008-2014, Avian Contributors

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
import static java.io.ObjectOutputStream.getReadOrWriteMethod;

import avian.VMClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class ObjectInputStream extends InputStream implements DataInput {
  private final static int HANDLE_OFFSET = 0x7e0000;

  private final InputStream in;
  private final ArrayList references;

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
    references = new ArrayList();
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

  private static Class charToPrimitiveType(int c) {
    if (c == 'B') {
      return Byte.TYPE;
    } else if (c == 'C') {
      return Character.TYPE;
    } else if (c == 'D') {
      return Double.TYPE;
    } else if (c == 'F') {
      return Float.TYPE;
    } else if (c == 'I') {
      return Integer.TYPE;
    } else if (c == 'J') {
      return Long.TYPE;
    } else if (c == 'S') {
      return Short.TYPE;
    } else if (c == 'Z') {
      return Boolean.TYPE;
    }
    throw new RuntimeException("Unhandled char: " + (char)c);
  }

  private void expectToken(int token) throws IOException {
    int c = rawByte();
    if (c != token) {
      throw new UnsupportedOperationException("Unexpected token: 0x"
          + Integer.toHexString(c));
    }
  }

  private void field(Field field, Object o)
    throws IOException, IllegalArgumentException, IllegalAccessException,
      ClassNotFoundException
  {
    Class type = field.getType();
    if (!type.isPrimitive()) {
      field.set(o, readObject());
    } else {
      if (type == Byte.TYPE) {
        field.setByte(o, (byte)rawByte());
      } else if (type == Character.TYPE) {
        field.setChar(o, (char)rawShort());
      } else if (type == Double.TYPE) {
        field.setDouble(o, Double.longBitsToDouble(rawLong()));
      } else if (type == Float.TYPE) {
        field.setFloat(o, Float.intBitsToFloat(rawInt()));
      } else if (type == Integer.TYPE) {
        field.setInt(o, rawInt());
      } else if (type == Long.TYPE) {
        field.setLong(o, rawLong());
      } else if (type == Short.TYPE) {
        field.setShort(o, (short)rawShort());
      } else if (type == Boolean.TYPE) {
        field.setBoolean(o, rawByte() != 0);
      } else {
        throw new IOException("Unhandled type: " + type);
      }
    }
  }

  public Object readObject() throws IOException, ClassNotFoundException {
    int c = rawByte();
    if (c == TC_NULL) {
      return null;
    }
    if (c == TC_STRING) {
      int length = rawShort();
      byte[] bytes = new byte[length];
      readFully(bytes);
      String s = new String(bytes, "UTF-8");
      references.add(s);
      return s;
    }
    if (c == TC_REFERENCE) {
      int handle = rawInt();
      return references.get(handle - HANDLE_OFFSET);
    }
    if (c != TC_OBJECT) {
      throw new IOException("Unexpected token: 0x"
        + Integer.toHexString(c));
    }

    // class desc
    c = rawByte();
    ClassDesc classDesc;
    if (c == TC_REFERENCE) {
      int handle = rawInt() - HANDLE_OFFSET;
      classDesc = (ClassDesc)references.get(handle);
    } else if (c == TC_CLASSDESC) {
      classDesc = classDesc();
    } else {
      throw new UnsupportedOperationException("Unexpected token: 0x"
          + Integer.toHexString(c));
    }

    try {
      Object o = makeInstance(classDesc.clazz.vmClass);
      references.add(o);

      do {
        Object o1 = classDesc.clazz.cast(o);
        boolean customized = (classDesc.flags & SC_WRITE_METHOD) != 0;
        Method readMethod = customized ?
          getReadOrWriteMethod(o, "readObject") : null;
        if (readMethod == null) {
          if (customized) {
            throw new IOException("Could not find required readObject method "
              + "in " + classDesc.clazz);
          }
          defaultReadObject(o, classDesc.fields);
        } else {
          current = o1;
          currentFields = classDesc.fields;
          readMethod.invoke(o, this);
          current = null;
          currentFields = null;
          expectToken(TC_ENDBLOCKDATA);
        }
      } while ((classDesc = classDesc.superClassDesc) != null);

      return o;
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  private static class ClassDesc {
    Class clazz;
    int flags;
    Field[] fields;
    ClassDesc superClassDesc;
  }

  private ClassDesc classDesc() throws ClassNotFoundException, IOException {
    ClassDesc result = new ClassDesc();
    String className = rawString();
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    result.clazz = loader.loadClass(className);
    long serialVersionUID = rawLong();
    try {
      Field field = result.clazz.getField("serialVersionUID");
      long expected = field.getLong(null);
      if (expected != serialVersionUID) {
        throw new IOException("Incompatible serial version UID: 0x"
            + Long.toHexString(serialVersionUID) + " != 0x"
            + Long.toHexString(expected));
      }
    } catch (Exception ignored) { }
    references.add(result);

    result.flags = rawByte();
    if ((result.flags & ~(SC_SERIALIZABLE | SC_WRITE_METHOD)) != 0) {
      throw new UnsupportedOperationException("Cannot handle flags: 0x"
          + Integer.toHexString(result.flags));
    }

    int fieldCount = rawShort();
    result.fields = new Field[fieldCount];
    for (int i = 0; i < result.fields.length; i++) {
      int typeChar = rawByte();
      String fieldName = rawString();
      try {
        result.fields[i] = result.clazz.getDeclaredField(fieldName);
      } catch (Exception e) {
        throw new IOException(e);
      }
      Class type;
      if (typeChar == '[' || typeChar == 'L') {
        String typeName = (String)readObject();
        if (typeName.startsWith("L") && typeName.endsWith(";")) {
          typeName = typeName.substring(1, typeName.length() - 1)
            .replace('/', '.');
        }
        type = loader.loadClass(typeName);
      } else {
        type = charToPrimitiveType(typeChar);
      }
      if (result.fields[i].getType() != type) {
        throw new IOException("Unexpected type of field " + fieldName
            + ": expected " + result.fields[i].getType() + " but got " + type);
      }
    }
    expectToken(TC_ENDBLOCKDATA);
    int c = rawByte();
    if (c == TC_CLASSDESC) {
      result.superClassDesc = classDesc();
    } else if (c != TC_NULL) {
      throw new UnsupportedOperationException("Unexpected token: 0x"
          + Integer.toHexString(c));
    }

    return result;
  }

  private Object current;
  private Field[] currentFields;

  public void defaultReadObject() throws IOException {
    defaultReadObject(current, currentFields);
  }

  private void defaultReadObject(Object o, Field[] fields) throws IOException {
    try {
      for (Field field : fields) {
        field(field, o);
      }
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  private static native Object makeInstance(VMClass c);
}
