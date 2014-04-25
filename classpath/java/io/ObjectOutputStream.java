/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.io;

import java.util.ArrayList;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class ObjectOutputStream extends OutputStream implements DataOutput {
  final static short STREAM_MAGIC = (short)0xaced;
  final static short STREAM_VERSION = 5;
  final static byte TC_NULL = (byte)0x70;
  final static byte TC_REFERENCE = (byte)0x71;
  final static byte TC_CLASSDESC = (byte)0x72;
  final static byte TC_OBJECT = (byte)0x73;
  final static byte TC_STRING = (byte)0x74;
  final static byte TC_ARRAY = (byte)0x75;
  final static byte TC_CLASS = (byte)0x76;
  final static byte TC_BLOCKDATA = (byte)0x77;
  final static byte TC_ENDBLOCKDATA = (byte)0x78;
  final static byte TC_RESET = (byte)0x79;
  final static byte TC_BLOCKDATALONG = (byte)0x7a;
  final static byte TC_EXCEPTION = (byte)0x7b;
  final static byte TC_LONGSTRING = (byte)0x7c;
  final static byte TC_PROXYCLASSDESC = (byte)0x7d;
  final static byte TC_ENUM = (byte)0x7e;
  final static byte SC_WRITE_METHOD = 0x01; //if SC_SERIALIZABLE
  final static byte SC_BLOCK_DATA = 0x08;   //if SC_EXTERNALIZABLE
  final static byte SC_SERIALIZABLE = 0x02;
  final static byte SC_EXTERNALIZABLE = 0x04;
  final static byte SC_ENUM = 0x10;

  private final OutputStream out;

  public ObjectOutputStream(OutputStream out) throws IOException {
    this.out = out;
    rawShort(STREAM_MAGIC);
    rawShort(STREAM_VERSION);
  }

  public void write(int c) throws IOException {
    out.write(c);
  }

  public void write(byte[] b, int offset, int length) throws IOException {
    out.write(b, offset, length);
  }

  public void flush() throws IOException {
    out.flush();
  }

  public void close() throws IOException {
    out.close();
  }

  private void rawByte(int v) throws IOException {
    out.write((byte)(v & 0xff));
  }

  private void rawShort(int v) throws IOException {
    rawByte(v >> 8);
    rawByte(v);
  }

  private void rawInt(int v) throws IOException {
    rawShort(v >> 16);
    rawShort(v);
  }

  private void rawLong(long v) throws IOException {
    rawInt((int)(v >> 32));
    rawInt((int)(v & 0xffffffffl));
  }

  private void blockData(int... bytes) throws IOException {
    blockData(bytes, null, null);
  }

  private void blockData(int[] bytes, byte[] bytes2, char[] chars) throws IOException {
    int count = (bytes == null ? 0 : bytes.length)
      + (bytes2 == null ? 0 : bytes2.length)
      + (chars == null ? 0 : chars.length * 2);
    if (count < 0x100) {
      rawByte(TC_BLOCKDATA);
      rawByte(count);
    } else {
      rawByte(TC_BLOCKDATALONG);
      rawInt(count);
    }
    if (bytes != null) {
      for (int b : bytes) {
        rawByte(b);
      }
    }
    if (bytes2 != null) {
      for (byte b : bytes2) {
        rawByte(b & 0xff);
      }
    }
    if (chars != null) {
      for (char c : chars) {
        rawShort((short)c);
      }
    }
  }

  public void writeBoolean(boolean v) throws IOException {
    blockData(v ? 1 : 0);
  }

  public void writeByte(int v) throws IOException {
    blockData(v);
  }

  public void writeShort(int v) throws IOException {
    blockData(v >> 8, v);
  }

  public void writeChar(int v) throws IOException {
    blockData(v >> 8, v);
  }

  public void writeInt(int v) throws IOException {
    blockData(v >> 24, v >> 16, v >> 8, v);
  }

  public void writeLong(long v) throws IOException {
    int u = (int)(v >> 32), l = (int)(v & 0xffffffff);
    blockData(u >> 24, u >> 16, u >> 8, u, l >> 24, l >> 16, l >> 8, l);
  }

  public void writeFloat(float v) throws IOException {
    writeInt(Float.floatToIntBits(v));
  }

  public void writeDouble(double v) throws IOException {
    writeLong(Double.doubleToLongBits(v));
  }

  public void writeBytes(String s) throws IOException {
    blockData(null, s.getBytes(), null);
  }

  public void writeChars(String s) throws IOException {
    blockData(null, null, s.toCharArray());
  }

  public void writeUTF(String s) throws IOException {
    byte[] bytes = s.getBytes();
    int length = bytes.length;
    blockData(new int[] { length >> 8, length }, bytes, null);
  }

  private int classHandle;

  private void string(String s) throws IOException {
    int length = s.length();
    rawShort(length);
    for (byte b : s.getBytes()) {
      rawByte(b);
    }
  }

  private static char primitiveTypeChar(Class type) {
    if (type == Byte.TYPE) {
      return 'B';
    } else if (type == Character.TYPE) {
      return 'C';
    } else if (type == Double.TYPE) {
      return 'D';
    } else if (type == Float.TYPE) {
      return 'F';
    } else if (type == Integer.TYPE) {
      return 'I';
    } else if (type == Long.TYPE) {
      return 'J';
    } else if (type == Short.TYPE) {
      return 'S';
    } else if (type == Boolean.TYPE) {
      return 'Z';
    }
    throw new RuntimeException("Unhandled primitive type: " + type);
  }

  private void classDesc(Class clazz, int scFlags) throws IOException {
    rawByte(TC_CLASSDESC);

    // class name
    string(clazz.getName());

    // serial version UID
    long serialVersionUID = 1l;
    try {
      Field field = clazz.getField("serialVersionUID");
      serialVersionUID = field.getLong(null);
    } catch (Exception ignored) {}
    rawLong(serialVersionUID);

    // handle
    rawByte(SC_SERIALIZABLE | scFlags);

    Field[] fields = getFields(clazz);
    rawShort(fields.length);
    for (Field field : fields) {
      Class fieldType = field.getType();
      if (fieldType.isPrimitive()) {
        rawByte(primitiveTypeChar(fieldType));
        string(field.getName());
      } else {
        rawByte(fieldType.isArray() ? '[' : 'L');
        string(field.getName());
        rawByte(TC_STRING);
        string("L" + fieldType.getName().replace('.', '/') + ";");
      }
    }
    rawByte(TC_ENDBLOCKDATA); // TODO: write annotation
    rawByte(TC_NULL); // super class desc
  }

  private void field(Object o, Field field) throws IOException {
    try {
      field.setAccessible(true);
      Class type = field.getType();
      if (!type.isPrimitive()) {
        writeObject(field.get(o));
      } else if (type == Byte.TYPE) {
        rawByte(field.getByte(o));
      } else if (type == Character.TYPE) {
        char c = field.getChar(o);
        rawShort((short)c);
      } else if (type == Double.TYPE) {
        double d = field.getDouble(o);
        rawLong(Double.doubleToLongBits(d));
      } else if (type == Float.TYPE) {
        float f = field.getFloat(o);
        rawInt(Float.floatToIntBits(f));
      } else if (type == Integer.TYPE) {
        int i = field.getInt(o);
        rawInt(i);
      } else if (type == Long.TYPE) {
        long l = field.getLong(o);
        rawLong(l);
      } else if (type == Short.TYPE) {
        short s = field.getShort(o);
        rawShort(s);
      } else if (type == Boolean.TYPE) {
        boolean b = field.getBoolean(o);
        rawByte(b ? 1 : 0);
      } else {
        throw new UnsupportedOperationException("Field '" + field.getName()
          + "' has unsupported type: " + type);
      }
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  private static Field[] getFields(Class clazz) {
    ArrayList<Field> list = new ArrayList<Field>();
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      if (0 == (field.getModifiers() &
          (Modifier.STATIC | Modifier.TRANSIENT))) {
        list.add(field);
      }
    }
    return list.toArray(new Field[list.size()]);
  }

  public void writeObject(Object o) throws IOException {
    if (o == null) {
      rawByte(TC_NULL);
      return;
    }
    if (o instanceof String) {
      byte[] bytes = ((String)o).getBytes("UTF-8");
      rawByte(TC_STRING);
      rawShort(bytes.length);
      write(bytes);
      return;
    }
    rawByte(TC_OBJECT);
    Method writeObject = getReadOrWriteMethod(o, "writeObject");
    if (writeObject == null) {
      classDesc(o.getClass(), 0);
      defaultWriteObject(o);
    } else try {
      classDesc(o.getClass(), SC_WRITE_METHOD);
      current = o;
      writeObject.invoke(o, this);
      current = null;
      rawByte(TC_ENDBLOCKDATA);
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  static Method getReadOrWriteMethod(Object o, String methodName) {
    try {
      Method method = o.getClass().getDeclaredMethod(methodName,
        new Class[] { methodName.startsWith("write") ?
          ObjectOutputStream.class : ObjectInputStream.class });
      method.setAccessible(true);
      int modifiers = method.getModifiers();
      if ((modifiers & Modifier.STATIC) == 0 ||
          (modifiers & Modifier.PRIVATE) != 0) {
        return method;
      }
    } catch (NoSuchMethodException ignored) { }
    return null;
  }

  private Object current;

  public void defaultWriteObject() throws IOException {
    defaultWriteObject(current);
  }

  private void defaultWriteObject(Object o) throws IOException {
    for (Field field : getFields(o.getClass())) {
      field(o, field);
    }
  }
}
