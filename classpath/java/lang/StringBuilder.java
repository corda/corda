package java.lang;

public class StringBuilder {
  private static final int BufferSize = 32;

  private Cell chain;
  private int length;
  private char[] buffer;
  private int position;

  public StringBuilder(String s) {
    append(s);
  }

  public StringBuilder(int capacity) { }

  public StringBuilder() {
    this(0);
  }

  private void flush() {
    if (position > 0) {
      char[] b = buffer;
      int p = position;
      buffer = null;
      position = 0;
      append(new String(b, 0, p, false));
      length -= p;
    }
  }

  public StringBuilder append(String s) {
    if (s == null) {
      return append("null");
    } else {
      if (s.length() > 0) {
        if (buffer != null && s.length() <= buffer.length - position) {
          s.getChars(0, s.length(), buffer, position);
          position += s.length();
        } else {
          flush();
          chain = new Cell(s, chain);
        }
        length += s.length();
      }
      return this;
    }
  }

  public StringBuilder append(char[] b, int offset, int length) {
    return append(new String(b, offset, length));
  }

  public StringBuilder append(Object o) {
    return append(o == null ? "null" : o.toString());
  }

  public StringBuilder append(char v) {
    if (buffer == null) {
      buffer = new char[BufferSize];
    } else if (position >= buffer.length) {
      flush();
      buffer = new char[BufferSize];
    }

    buffer[position++] = v;
    ++ length;

    return this;
  }

  public StringBuilder append(boolean v) {
    return append(String.valueOf(v));
  }

  public StringBuilder append(int v) {
    return append(String.valueOf(v));
  }

  public StringBuilder append(long v) {
    return append(String.valueOf(v));
  }

  public char charAt(int i) {
    if (i < 0 || i >= length) {
      throw new IndexOutOfBoundsException();
    }

    flush();

    int index = length;
    -- length;
    Cell p = null;
    for (Cell c = chain; c != null; c = c.next) {
      int start = index - c.value.length();
      index = start;
      
      if (i >= start) {
        return c.value.charAt(i - start);
      }
    }

    throw new RuntimeException();
  }

  public StringBuilder insert(int i, String s) {
    if (i < 0 || i >= length) {
      throw new IndexOutOfBoundsException();
    }

    if (i == length) {
      append(s);
    } else {
      flush();

      int index = length;
      -- length;
      for (Cell c = chain; c != null; c = c.next) {
        int start = index - c.value.length();
        index = start;
      
        if (i >= start) {
          if (i == start) {
            c.next = new Cell(s, c.next);
          } else {
            String v = c.value;
            c.value = v.substring(i - start, v.length());
            c.next = new Cell(s, new Cell(v.substring(0, i - start), c.next));
          }
          break;
        }
      }
    }

    return this;
  }

  public StringBuilder deleteCharAt(int i) {
    if (i < 0 || i >= length) {
      throw new IndexOutOfBoundsException();
    }

    flush();

    int index = length;
    -- length;
    Cell p = null;
    for (Cell c = chain; c != null; c = c.next) {
      int start = index - c.value.length();
      index = start;
      
      if (i >= start) {
        if (c.value.length() == 1) {
          if (p == null) {
            chain = c.next;
          } else {
            p.next = c.next;
          }
        } else if (i == start) {
          c.value = c.value.substring(1);
        } else if (i == start + c.value.length() - 1) {
          c.value = c.value.substring(0, c.value.length() - 1);
        } else {
          String v = c.value;
          c.value = v.substring(i - start + 1, v.length());
          c.next = new Cell(v.substring(0, i - start), c.next);
        }
        break;
      }
    }

    return this;
  }

  public int length() {
    return length;
  }

  public void setLength(int v) {
    if (v < 0) {
      throw new IndexOutOfBoundsException();
    }

    if (v == 0) {
      length = 0;
      chain = null;
      return;
    }

    flush();

    int index = length;
    length = v;
    for (Cell c = chain; c != null; c = c.next) {
      int start = index - c.value.length();

      if (v > start) {
        if (v < index) {
          c.value = c.value.substring(0, v - start);
        }
        break;
      }

      chain = c.next;
      index = start;
    }
  }

  public void getChars(int srcOffset, int srcLength, char[] dst, int dstOffset)
  {
    if (srcOffset < 0 || srcOffset + srcLength > length) {
      throw new IndexOutOfBoundsException();
    }

    flush();

    int index = length;
    for (Cell c = chain; c != null; c = c.next) {
      int start = index - c.value.length();
      int end = index;
      index = start;

      if (start < srcOffset) {
        start = srcOffset;
      }

      if (end > srcOffset + srcLength) {
        end = srcOffset + srcLength;
      }

      if (start < end) {
        c.value.getChars(start - index, end - start,
                         dst, dstOffset + (start - srcOffset));
      }
    }
  }

  public String toString() {
    char[] array = new char[length];
    getChars(0, length, array, 0);
    return new String(array, 0, length, false);
  }

  private static class Cell {
    public String value;
    public Cell next;

    public Cell(String value, Cell next) {
      this.value = value;
      this.next = next;
    }
  }
}
