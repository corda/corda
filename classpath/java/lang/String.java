package java.lang;

public final class String implements Comparable<String> {
  private Object data;
  private int offset;
  private int length;
  private int hash;

  public String(char[] data, int offset, int length, boolean copy) {
    this((Object) data, offset, length, copy);
  }

  public String(char[] data, int offset, int length) {
    this(data, offset, length, true);
  }

  public String(char[] data) {
    this(data, 0, data.length);
  }

  public String(byte[] data, int offset, int length, boolean copy) {
    this((Object) data, offset, length, copy);
  }

  public String(byte[] data, int offset, int length) {
    this(data, offset, length, true);
  }

  public String(byte[] data) {
    this(data, 0, data.length);
  }

  private String(Object data, int offset, int length, boolean copy) {
    int l;
    if (data instanceof char[]) {
      l = ((char[]) data).length;
    } else {
      l = ((byte[]) data).length;
    }

    if (offset < 0 || offset + length > l) {
      throw new IndexOutOfBoundsException
        (offset + " < 0 or " + offset + " + " + length + " > " + l);
    }

    if (copy) {
      Object c;
      if (data instanceof char[]) {
        c = new char[length];
      } else {
        c = new byte[length];
      }
      System.arraycopy(data, offset, c, 0, length);
      
      this.data = c;
      this.length = length;
    } else {
      this.data = data;
      this.offset = offset;
      this.length = length;
    }
  }

  public String toString() {
    return this;
  }

  public int length() {
    return length;
  }

  public int hashCode() {
    if (hash == 0) {
      int h = 0;
      for (int i = 0; i < length; ++i) h = (h * 31) + charAt(i);
      hash = h;
    }
    return hash;
  }

  public boolean equals(Object o) {
    return this == o || (o instanceof String && compareTo((String) o) == 0);
  }

  public boolean equalsIgnoreCase(String s) {
    return this == s || compareToIgnoreCase(s) == 0;
  }

  public int compareTo(String s) {
    if (this == s) return 0;

    int d = length - s.length;
    if (d != 0) {
      return d;
    } else {
      for (int i = 0; i < length; ++i) {
        d = charAt(i) - s.charAt(i);
        if (d != 0) {
          return d;
        }
      }
      return 0;
    }
  }

  public int compareToIgnoreCase(String s) {
    if (this == s) return 0;

    int d = length - s.length;
    if (d != 0) {
      return d;
    } else {
      for (int i = 0; i < length; ++i) {
        d = Character.toLowerCase(charAt(i))
          - Character.toLowerCase(s.charAt(i));
        if (d != 0) {
          return d;
        }
      }
      return 0;
    }
  }

  public String trim() {
    int start = -1;
    for (int i = 0; i < length; ++i) {
      char c = charAt(i);
      if (start == -1 && ! Character.isWhitespace(c)) {
        start = i;
        break;
      }
    }

    int end = -1;
    for (int i = length - 1; i >= 0; --i) {
      char c = charAt(i);
      if (end == -1 && ! Character.isWhitespace(c)) {
        end = i + 1;
        break;
      }
    }

    if (start >= end) {
      return "";
    } else {
      return substring(start, end);
    }
  }

  public String toLowerCase() {
    char[] b = new char[length];
    for (int i = 0; i < length; ++i) {
      b[i] = Character.toLowerCase(charAt(i));
    }
    return new String(b, 0, length, false);
  }

  public String toUpperCase() {
    char[] b = new char[length];
    for (int i = 0; i < length; ++i) {
      b[i] = Character.toUpperCase(charAt(i));
    }
    return new String(b, 0, length, false);
  }

  public int indexOf(int c) {
    return indexOf(c, 0);
  }

  public int indexOf(int c, int start) {
    for (int i = start; i < length; ++i) {
      if (charAt(i) == c) {
        return i;
      }
    }

    return -1;
  }

  public int lastIndexOf(int c) {
    for (int i = length - 1; i >= 0; --i) {
      if (charAt(i) == c) {
        return i;
      }
    }

    return -1;
  }

  public int indexOf(String s) {
    return indexOf(s, 0);
  }

  public int indexOf(String s, int start) {
    if (s.length == 0) return start;

    for (int i = start; i < length - s.length + 1; ++i) {
      int j = 0;
      for (; j < s.length; ++j) {
        if (charAt(i + j) != s.charAt(j)) {
          break;
        }
      }
      if (j == s.length) {
        return i;
      }
    }

    return -1;
  }

  public int lastIndexOf(String s) {
    if (s.length == 0) return length;

    for (int i = length - s.length; i >= 0; --i) {
      int j = 0;
      for (; j < s.length && i + j < length; ++j) {
        if (charAt(i + j) != s.charAt(j)) {
          break;
        }
      }
      if (j == s.length) {
        return i;
      }
    }

    return -1;
  }

  public String substring(int start) {
    return substring(start, length);
  }

  public String substring(int start, int end) {
    if (start >= 0 && end >= start && end <= length) {
      if (start == 0 && end == length) {
        return this;
      } else if (end - start == 0) {
        return "";
      } else  {
        return new String(data, offset + start, end - start, false);
      }
    } else {
      throw new IndexOutOfBoundsException
        (start + " not in (0, " + end + ") or " + end + " > " + length);
    }
  }

  public boolean startsWith(String s) {
    if (length >= s.length) {
      return substring(0, s.length).compareTo(s) == 0;
    } else {
      return false;
    }
  }

  public boolean endsWith(String s) {
    if (length >= s.length) {
      return substring(length - s.length).compareTo(s) == 0;
    } else {
      return false;
    }
  }

  public String concat(String s) {
    if (s.length() == 0) {
      return this;
    } else {
      return this + s;
    }
  }

  public void getBytes(int srcOffset, int srcLength,
                       byte[] dst, int dstOffset)
  {
    if (srcOffset < 0 || srcOffset + srcLength > length) {
      throw new IndexOutOfBoundsException();
    }

    if (data instanceof char[]) {
      char[] src = (char[]) data;
      for (int i = 0; i < srcLength; ++i) {
        dst[i + dstOffset] = (byte) src[i + offset + srcOffset];
      }
    } else {
      byte[] src = (byte[]) data;
      System.arraycopy(src, offset + srcOffset, dst, dstOffset, srcLength);
    }
  }

  public byte[] getBytes() {
    byte[] b = new byte[length];
    getBytes(0, length, b, 0);
    return b;
  }

  public byte[] getBytes(String format) {
    return getBytes();
  }

  public void getChars(int srcOffset, int srcLength,
                       char[] dst, int dstOffset)
  {
    if (srcOffset < 0 || srcOffset + srcLength > length) {
      throw new IndexOutOfBoundsException();
    }

    if (data instanceof char[]) {
      char[] src = (char[]) data;
      System.arraycopy(src, offset + srcOffset, dst, dstOffset, srcLength);
    } else {
      byte[] src = (byte[]) data;
      for (int i = 0; i < srcLength; ++i) {
        dst[i + dstOffset] = (char) src[i + offset + srcOffset];
      }      
    }
  }

  public char[] toCharArray() {
    char[] b = new char[length];
    getChars(0, length, b, 0);
    return b;
  }

  public char charAt(int index) {
    if (index < 0 || index > length) {
      throw new IndexOutOfBoundsException();
    }
    
    if (data instanceof char[]) {
      return ((char[]) data)[index + offset];
    } else {
      return (char) ((byte[]) data)[index + offset];
    }
  }

  public native String intern();

  public static String valueOf(Object s) {
    return s.toString();
  }

  public static String valueOf(boolean v) {
    return Boolean.toString(v);
  }

  public static String valueOf(byte v) {
    return Byte.toString(v);
  }

  public static String valueOf(short v) {
    return Short.toString(v);
  }

  public static String valueOf(char v) {
    return Character.toString(v);
  }

  public static String valueOf(int v) {
    return Integer.toString(v);
  }

  public static String valueOf(long v) {
    return Long.toString(v);
  }

  public static String valueOf(float v) {
    return Float.toString(v);
  }

  public static String valueOf(double v) {
    return Double.toString(v);
  }
}
