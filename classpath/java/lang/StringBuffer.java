package java.lang;

public class StringBuffer {
  private final StringBuilder sb = new StringBuilder();

  public synchronized StringBuffer append(String s) {
    sb.append(s);
    return this;
  }

  public synchronized StringBuffer append(Object o) {
    sb.append(o);
    return this;
  }

  public synchronized StringBuffer append(int v) {
    sb.append(v);
    return this;
  }

  public synchronized StringBuffer append(long v) {
    sb.append(v);
    return this;
  }

  public synchronized StringBuffer deleteCharAt(int i) {
    sb.deleteCharAt(i);
    return this;
  }

  public synchronized int length() {
    return sb.length();
  }

  public synchronized void setLength(int v) {
    sb.setLength(v);
  }

  public synchronized void getChars(int srcOffset, int srcLength, char[] dst,
                                    int dstOffset)
  {
    sb.getChars(srcOffset, srcLength, dst, dstOffset);
  }

  public synchronized String toString() {
    return sb.toString();
  }
}
