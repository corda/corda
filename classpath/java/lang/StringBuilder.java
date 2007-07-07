package java.lang;

public class StringBuilder {
  private Cell chain;
  private int length;

  public StringBuilder append(String s) {
    chain = new Cell(s, chain);
    length += s.length();
    return this;
  }

  public StringBuilder append(int v) {
    append(String.valueOf(v));
    return this;
  }

  public StringBuilder append(long v) {
    append(String.valueOf(v));
    return this;
  }

  public String toString() {
    char[] array = new char[length];
    int index = length;
    for (Cell c = chain; c != null; c = c.next) {
      index -= c.value.length();
      c.value.getChars(0, c.value.length(), array, index);
    }
    return new String(array, 0, array.length, false);
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
