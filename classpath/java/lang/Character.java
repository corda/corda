package java.lang;

public final class Character {
  public static final Class TYPE = Class.forName("C");

  private final char value;

  public Character(char value) {
    this.value = value;
  }

  public char charValue() {
    return value;
  }

  public static char toLowerCase(char c) {
    if (c >= 'A' && c <= 'Z') {
      return (char) ((c - 'A') + 'a');
    } else {
      return c;
    }
  }

  public static char toUpperCase(char c) {
    if (c >= 'a' && c <= 'z') {
      return (char) ((c - 'a') + 'A');
    } else {
      return c;
    }
  }

  public static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  public static boolean isLetter(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  public static boolean isLowerCase(char c) {
    return (c >= 'a' && c <= 'z');
  }

  public static boolean isUpperCase(char c) {
    return (c >= 'A' && c <= 'Z');
  }

  public static boolean isWhitespace(char c) {
    return c == ' ' || c == '\t' || c == '\n';
  }
}
