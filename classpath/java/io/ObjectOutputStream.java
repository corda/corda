package java.io;

import java.util.IdentityHashMap;

public class ObjectOutputStream extends OutputStream {
  private final PrintStream out;

  public ObjectOutputStream(OutputStream out) {
    this.out = new PrintStream(out);
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

  public void writeObject(Object o) throws IOException {
    writeObject(o, new IdentityHashMap(), 0);
  }

  public void writeBoolean(boolean v) {
    out.print("z");
    out.print((v ? 1 : 0));
  }

  public void writeByte(byte v) {
    out.print("b");
    out.print((int) v);
  }

  public void writeChar(char v) {
    out.print("c");
    out.print((int) v);
  }

  public void writeShort(short v) {
    out.print("s");
    out.print((int) v);
  }

  public void writeInt(int v) {
    out.print("i");
    out.print(v);
  }

  public void writeLong(long v) {
    out.print("j");
    out.print(v);
  }

  public void writeFloat(float v) {
    out.print("f");
    out.print(v);
  }

  public void writeDouble(double v) {
    out.print("d");
    out.print(v);
  }

  private void writeObject(Object o, IdentityHashMap<Object, Integer> map,
                           int nextId)
    throws IOException
  {
    if (o == null) {
      out.print("n");
    } else {
      Integer id = map.get(new Identity(o));
      if (id == null) {
        map.put(new Identity(o), nextId);

        Class c = o.getClass();
        if (c.isArray()) {
          serializeArray(o, map, nextId);
        } else if (Serializable.class.isAssignableFrom(c)) {
          serialize(o, map, nextId);
        } else {
          throw new NotSerializableException(c.getName());
        }
      } else {
        out.print("r");
        out.print(id.intValue());
      }
    }
  }

  private void serializeArray(Object o, IdentityHashMap<Object, Integer> map,
                              int nextId)
    throws IOException
  {
    Class c = o.getClass();
    Class t = c.getComponentType();
    int length = Array.getLength(o);

    out.print("a(");
    out.print(nextId++);
    out.print(" ");
    out.print(c.getName());
    out.print(" ");
    out.print(length);

    for (int i = 0; i < length; ++i) {
      out.print(" ");
      if (t.equals(boolean.class)) {
        writeBoolean(Array.getBoolean(o));
      } else if (t.equals(byte.class)) {
        writeByte(Array.getByte(o));
      } else if (t.equals(char.class)) {
        writeChar(Array.getChar(o));
      } else if (t.equals(short.class)) {
        writeShort(Array.getShort(o));
      } else if (t.equals(int.class)) {
        writeInt(Array.getInt(o));
      } else if (t.equals(long.class)) {
        writeLong(Array.getLong(o));
      } else if (t.equals(float.class)) {
        writeFloat(Array.getFloat(o));
      } else if (t.equals(double.class)) {
        writeDouble(Array.getDouble(o));
      } else {
        writeObject(Array.get(o), map, nextId);
      }
    }

    out.print(")");
  }
  
  private void serialize(Object o, IdentityHashMap<Object, Integer> map,
                         int nextId)
    throws IOException
  {
    Class c = o.getClass();

    out.print("l(");
    out.print(nextId++);
    out.print(" ");
    out.print(c.getName());

    for (Field f: c.getFields()) {
      int modifiers = f.getModifiers();
      if ((modifiers & (Modifier.TRANSIENT | Modifier.STATIC)) == 0) {
        out.print(" ");
        Class t = f.getType();
        if (t.equals(boolean.class)) {
          writeBoolean(f.getBoolean(o));
        } else if (t.equals(byte.class)) {
          writeByte(f.getByte(o));
        } else if (t.equals(char.class)) {
          writeChar(f.getChar(o));
        } else if (t.equals(short.class)) {
          writeShort(f.getShort(o));
        } else if (t.equals(int.class)) {
          writeInt(f.getInt(o));
        } else if (t.equals(long.class)) {
          writeLong(f.getLong(o));
        } else if (t.equals(float.class)) {
          writeFloat(f.getFloat(o));
        } else if (t.equals(double.class)) {
          writeDouble(f.getDouble(o));
        } else {
          writeObject(f.get(o), map, nextId);
        }
      }
    }

    out.print(")");
  }
  
}
