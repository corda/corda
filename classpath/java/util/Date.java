package java.util;

public class Date {
  public final long when;

  public Date() {
    when = System.currentTimeMillis();
  }

  public Date(long when) {
    this.when = when;
  }

  public long getTime() {
    return when;
  }
}
