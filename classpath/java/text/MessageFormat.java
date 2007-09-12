package java.text;

import java.util.Locale;

public class MessageFormat extends Format {
  private final String pattern;
  private final Locale locale;

  public MessageFormat(String pattern, Locale locale) {
    this.pattern = pattern;
    this.locale = locale;
  }

  public MessageFormat(String pattern) {
    this(pattern, Locale.getDefault());
  }

  public StringBuffer format(Object o, StringBuffer target, FieldPosition p) {
    // todo
    return target.append(pattern);
  }
}
