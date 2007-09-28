package java.util;

public class Cell <T> {
  public T value;
  public Cell<T> next;
  
  public Cell(T value, Cell<T> next) {
    this.value = value;
    this.next = next;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("(");
    for (Cell c = this; c != null; c = c.next) {
      sb.append(value);
      if (c.next != null) {
        sb.append(" ");
      }
    }
    sb.append(")");
    return sb.toString();
  }
}
