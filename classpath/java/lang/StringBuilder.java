package java.lang;

public class StringBuilder {
  private Cell chain;
  private int length;

  public StringBuilder append(String s) {
    chain = new Cell(s, chain);
    length += s.length();
    return this;
  }

  public StringBuilder append(Object o) {
    return append(o == null ? "null" : o.toString());
  }

  public StringBuilder append(int v) {
    return append(String.valueOf(v));
  }

  public StringBuilder append(long v) {
    return append(String.valueOf(v));
  }

  public int length() {
    return length;
  }

  public void getChars(int srcOffset, int srcLength, char[] dst, int dstOffset)
  {
    if (srcOffset + srcLength > length) {
      throw new IndexOutOfBoundsException();
    }

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
    public final String value;
    public final Cell next;

    public Cell(String value, Cell next) {
      this.value = value;
      this.next = next;
    }
  }
}
