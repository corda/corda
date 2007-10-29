package java.lang;

public class StringBuffer {
  private final StringBuilder sb;

  public StringBuffer(String s) {
    sb = new StringBuilder(s);
  }

  public StringBuffer(int capacity) {
    sb = new StringBuilder(capacity);
  }

  public StringBuffer() {
    this(0);
  }

  public synchronized StringBuffer append(String s) {
    sb.append(s);
    return this;
  }

  public synchronized StringBuffer append(Object o) {
    sb.append(o);
    return this;
  }

  public synchronized StringBuffer append(char v) {
    sb.append(v);
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

  public synchronized StringBuffer append(float v) {
    sb.append(v);
    return this;
  }

  public synchronized StringBuffer append(char[] b, int offset, int length) {
    sb.append(b, offset, length);
    return this;
  }

  public synchronized StringBuffer insert(int i, String s) {
    sb.insert(i, s);
    return this;
  }

  public synchronized StringBuffer insert(int i, char c) {
    sb.insert(i, c);
    return this;
  }

  public synchronized StringBuffer delete(int start, int end) {
    sb.delete(start, end);
    return this;
  }

  public synchronized StringBuffer deleteCharAt(int i) {
    sb.deleteCharAt(i);
    return this;
  }

  public synchronized char charAt(int i) {
    return sb.charAt(i);
  }

  public synchronized int length() {
    return sb.length();
  }

  public synchronized StringBuffer replace(int start, int end, String str) {
    sb.replace(start, end, str);
    return this;
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
