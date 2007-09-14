package java.text;

import java.util.Locale;

public class MessageFormat extends Format {
  private String pattern;
  private final Locale locale;

  public MessageFormat(String pattern, Locale locale) {
    this.pattern = pattern;
    this.locale = locale;
  }

  public MessageFormat(String pattern) {
    this(pattern, Locale.getDefault());
  }

  public StringBuffer format(Object[] args, StringBuffer target,
                             FieldPosition p)
  {
    // todo
    return target.append(pattern);
  }

  public StringBuffer format(Object args, StringBuffer target, FieldPosition p)
  {
    return format((Object[]) args, target, p);
  }

  public static String format(String pattern, Object ... args) {
    return new MessageFormat
      (pattern).format(args, new StringBuffer(), new FieldPosition(0))
      .toString();
  }

  public void applyPattern(String pattern) {
    this.pattern = pattern;
  }
}
