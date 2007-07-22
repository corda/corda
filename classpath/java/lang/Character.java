package java.lang;

public final class Character {
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
}
