package java.lang;

public final class String {
  private Object data;
  private int offset;
  private int length;
  private int hash;

  public int length() {
    return length;
  }

  public static String valueOf(int v) {
    return valueOf((long) v);
  }

  public native void getChars(int offset, int length,
                              char[] dst, int dstLength);

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

      return vm.Strings.wrap(array, index, Max - index);
    }
  }
}
