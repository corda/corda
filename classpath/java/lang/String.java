package java.lang;

public final class String {
  private Object data;
  private int offset;
  private int length;
  private int hash;

  public String(char[] data, int offset, int length, boolean copy) {
    if (copy) {
      char[] c = new char[length];
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

  public static String valueOf(int v) {
    return valueOf((long) v);
  }

  public void getChars(int srcOffset, int srcLength,
                       char[] dst, int dstOffset)
  {
    if (srcOffset + srcLength > length) {
      throw new ArrayIndexOutOfBoundsException();
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
