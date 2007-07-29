package java.lang;

public final class String implements Comparable<String> {
  private Object data;
  private int offset;
  private int length;
  private int hash;

  public String(char[] data, int offset, int length, boolean copy) {
    this((Object) data, offset, length, copy);
  }

  public String(byte[] data, int offset, int length, boolean copy) {
    this((Object) data, offset, length, copy);
  }

  private String(Object data, int offset, int length, boolean copy) {
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

  public int length() {
    return length;
  }

  public int hashCode() {
    if (hash == 0) {
      for (int i = 0; i < length; ++i) hash = (hash * 31) + charAt(i);
    }
    return hash;
  }

  public boolean equals(Object o) {
    return o instanceof String && compareTo((String) o) == 0;
  }

  public boolean equalsIgnoreCase(String s) {
    return compareToIgnoreCase(s) == 0;
  }

  public int compareTo(String s) {
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

  public int indexOf(String s) {
    if (s.length == 0) return 0;

    for (int i = 0; i < length - s.length; ++i) {
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

  public byte[] getBytes() {
    byte[] b = new byte[length];
    getBytes(0, length, b, 0);
    return b;
  }

  public void getBytes(int srcOffset, int srcLength,
                       byte[] dst, int dstOffset)
  {
    if (srcOffset + srcLength > length) {
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

  public void getChars(int srcOffset, int srcLength,
                       char[] dst, int dstOffset)
  {
    if (srcOffset + srcLength > length) {
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

  public static String valueOf(int v) {
    return valueOf((long) v);
  }

  public static String valueOf(long v) {
    if (v == 0) {
      return valueOf('0');
    } else {
      final int Max = 21;
      char[] array = new char[Max];
      int index = Max;
      long x = (v >= 0 ? v : -v);

      while (x != 0) {
        array[--index] = (char) ('0' + (x % 10));
        x /= 10;
      }

      if (v < 0) {
        array[--index] = '-';
      }

      return new String(array, index, Max - index, false);
    }
  }
}
