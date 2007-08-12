package java.io;

public class ObjectInputStream extends InputStream {
  private final InputStream in;
  private final Reader r;

  public ObjectInputStream(InputStream in) {
    this.in = in;
    this.r = new PushbackReader(new InputStreamReader(in));
  }

  public int read() throws IOException {
    return in.read();
  }

  public int read(byte[] b, int offset, int length) throws IOException {
    return in.read(b, offset, length);
  }

  public void close() throws IOException {
    in.close();
  }

  public Object readObject() throws IOException {
    return readObject(new HashMap());
  }

  public boolean readBoolean() throws IOException {
    read('z');
    return readLongToken() != 0;
  }

  public byte readByte() throws IOException {
    read('b');
    return (byte) readLongToken();
  }

  public char readChar() throws IOException {
    read('c');
    return (char) readLongToken();
  }

  public short readShort() throws IOException {
    read('s');
    return (short) readLongToken();
  }

  public int readInt() throws IOException {
    read('i');
    return (int) readLongToken();
  }

  public long readLong() throws IOException {
    read('j');
    return readLongToken();
  }

  public float readFloat() throws IOException {
    read('f');
    return (float) readDoubleToken();
  }

  public double readDouble() throws IOException {
    read('d');
    return readDoubleToken();
  }

  private void skipSpace() throws IOException {
    int c;
    while ((c = r.read()) != -1 && Character.isSpace((char) c));
    if (c != -1) {
      r.unread(c);
    }
  }

  private void read(char v) throws IOException {
    skipSpace();

    int c = r.read();
    if (c != v) {
      if (c == -1) {
        throw new EOFException();
      } else {
        throw new StreamCorruptedException();
      }
    }
  }

  private String readStringToken() throws IOException {
    skipSpace();

    StringBuilder sb = new StringBuilder();
    int c;
    while ((c = r.read()) != -1 && ! Character.isSpace((char) c)) {
      sb.append((char) c);
    }
    if (c != -1) {
      r.unread(c);
    }
    return sb.toString();
  }

  private long readLongToken() throws IOException {
    return Long.parseLong(readStringToken());
  }

  private double readDoubleToken() throws IOException {
    return Double.parseDouble(readStringToken());
  }

  private Object readObject(HashMap<Integer, Object> map) throws IOException {
    skipSpace();
    switch (r.read()) {
    case 'a':
      return deserializeArray(map);
    case 'l':
      return deserialize(map);
    case 'n':
      return null;
    case -1:
      throw new EOFException();
    default:
      throw new StreamCorruptedException();
    }
  }

  private Object deserializeArray(HashMap<Integer, Object> map)
    throws IOException
  {
    read('(');
    int id = (int) readLongToken();
    Class c = Class.forName(readStringToken());
    int length = (int) readLongToken();
    Object o = Array.newInstance(c.getComponentType(), length);

    map.put(id, o);
  
    for (int i = 0; i < length; ++i) {
      skipSpace();
    
      switch (r.read()) {
      case 'a':
        Array.set(o, i, deserializeArray(map));
        break;

      case 'l':
        Array.set(o, i, deserialize(map));
        break;

      case 'r':
        Array.set(o, i, map.get((int) readLongToken()));
        break;

      case 'n':
        Array.set(o, i, null);
        break;

      case 'z':
        f.setBoolean(o, readLongToken() != 0);
        break;

      case 'b':
        f.setByte(o, (byte) readLongToken());
        break;

      case 'c':
        f.setChar(o, (char) readLongToken());
        break;

      case 's':
        f.setShort(o, (short) readLongToken());
        break;

      case 'i':
        f.setInt(o, (int) readLongToken());
        break;

      case 'j':
        f.setLong(o, readLongToken());
        break;

      case 'f':
        f.setFloat(o, (float) readDoubleToken());
        break;

      case 'd':
        f.setDouble(o, readDoubleToken());
        break;

      case -1:
        throw new EOFException();

      default:
        throw new StreamCorruptedException();
      }
    }

    read(')');
  }

  private Object deserialize(HashMap<Integer, Object> map)
    throws IOException
  {
    read('(');
    int id = (int) readLongToken();
    Class c = Class.forName(readStringToken());
    Object o = c.newInstance();

    map.put(id, o);
  
    for (Field f: c.getFields()) {
      int modifiers = f.getModifiers();
      if ((modifiers & (Modifier.TRANSIENT | Modifier.STATIC)) == 0) {
        skipSpace();
    
        switch (r.read()) {
        case 'a':
          Array.set(o, i, deserializeArray(map));
          break;

        case 'l':
          Array.set(o, i, deserialize(map));
          break;

        case 'r':
          Array.set(o, i, map.get((int) readLongToken()));
          break;

        case 'n':
          Array.set(o, i, null);
          break;

        case 'z':
          Array.setBoolean(o, i, readLongToken() != 0);
          break;

        case 'b':
          Array.setByte(o, i, (byte) readLongToken());
          break;

        case 'c':
          Array.setChar(o, i, (char) readLongToken());
          break;

        case 's':
          Array.setShort(o, i, (short) readLongToken());
          break;

        case 'i':
          Array.setInt(o, i, (int) readLongToken());
          break;

        case 'j':
          Array.setLong(o, i, readLongToken());
          break;

        case 'f':
          Array.setFloat(o, i, (float) readDoubleToken());
          break;

        case 'd':
          Array.setDouble(o, i, readDoubleToken());
          break;

        case -1:
          throw new EOFException();

        default:
          throw new StreamCorruptedException();
        }
      }
    }

    read(')');
  }
}
