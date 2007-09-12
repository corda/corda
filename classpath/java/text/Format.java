package java.text;

public abstract class Format {
  public final String format(Object o) {
    return format(o, new StringBuffer(), new FieldPosition(0)).toString();
  }

  public abstract StringBuffer format(Object o, StringBuffer target,
                                      FieldPosition p);
}
