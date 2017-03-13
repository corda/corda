/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util.regex;

/**
 * A class to match classes of characters.
 * <p>
 * This class is intended to be the working horse behind character classes
 * such as {@code [a-z]}.
 * </p>
 * @author Johannes Schindelin
 */
class CharacterMatcher {
  private boolean[] map;
  private boolean inversePattern;

  public static CharacterMatcher parse(String description) {
    return parse(description.toCharArray());
  }

  public static CharacterMatcher parse(char[] description) {
    Parser parser = new Parser(description);
    CharacterMatcher result = parser.parseClass();
    if (parser.getEndOffset() != description.length) {
      throw new RuntimeException("Short character class @"
        + parser.getEndOffset() + ": " + new String(description));
    }
    return result;
  }

  public boolean matches(char c) {
    int index = c;
    return (map.length > index && map[index]) ^ inversePattern;
  }

  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("[");
    if (inversePattern) {
      builder.append("^");
    }
    for (int i = 0; i < map.length; ++ i) {
      if (!map[i]) {
        continue;
      }
      builder.append(i >= ' ' && i <= 0x7f ?
        "" + (char)i : ("\\x" + Integer.toHexString(i)));
      int j = i + 1;
      while (j < map.length && map[j]) {
        ++ j;
      }
      -- j;
      if (j > i) {
        if (j > i + 1) {
          builder.append('-');
        }
        builder.append(j >= ' ' && j <= 0x7f ?
          "" + (char)j : ("\\x" + Integer.toHexString(j)));
        i = j;
      }
    }
    builder.append("]");
    return builder.toString();
  }

  private static String specialClass(int c) {
    if ('d' == c) {
      return "[0-9]";
    }
    if ('D' == c) {
      return "[^0-9]";
    }
    if ('s' == c) {
      return "[ \\t\\n\\x0B\\f\\r]";
    }
    if ('S' == c) {
      return "[^ \\t\\n\\x0B\\f\\r]";
    }
    if ('w' == c) {
      return "[a-zA-Z_0-9]";
    }
    if ('W' == c) {
      return "[^a-zA-Z_0-9]";
    }
    return null;
  }

  private CharacterMatcher(boolean[] map, boolean inversePattern) {
    this.map = map;
    this.inversePattern = inversePattern;
  }

  private void setMatch(int c) {
    ensureCapacity(c + 1);
    map[c] = true;
  }

  private void ensureCapacity(int length) {
    if (map.length >= length) {
      return;
    }
    int size = map.length;
    if (size < 32) {
      size = 32;
    }
    while (size < length) {
      size <<= 1;
    }
    map = java.util.Arrays.copyOf(map, size);
  }

  private void merge(CharacterMatcher other) {
    boolean inversePattern = this.inversePattern || other.inversePattern;
    if ((map.length < other.map.length) ^ inversePattern) {
      map = java.util.Arrays.copyOf(map, other.map.length);
    }
    for (int i = 0; i < map.length; ++ i) {
      map[i] = (matches((char)i) || other.matches((char)i)) ^ inversePattern;
    }
    this.inversePattern = inversePattern;
  }

  private void intersect(CharacterMatcher other) {
    boolean inversePattern = this.inversePattern && other.inversePattern;
    if ((map.length > other.map.length) ^ inversePattern) {
      map = java.util.Arrays.copyOf(map, other.map.length);
    }
    for (int i = 0; i < map.length; ++ i) {
      map[i] = (matches((char)i) && other.matches((char)i)) ^ inversePattern;
    }
    this.inversePattern = inversePattern;
  }

  static class Parser {
    private final char[] description;
    private int offset;

    public Parser(char[] description) {
      this.description = description;
    }

    public int getEndOffset() {
      return offset;
    }

    /**
     * Parses an escaped character.
     * 
     * @param start the offset <u>after</u> the backslash
     * @return the escaped character, or -1 if no character was recognized
     */
    public int parseEscapedCharacter(int start) {
      offset = start;
      return parseEscapedCharacter();
    }

    private int parseEscapedCharacter() {
      if (offset == description.length) {
        throw new IllegalArgumentException("Short escaped character");
      }
      char c = description[offset++];
      if (c == '0') {
        int len = digits(offset, 3, 8);
        if (len == 3 && description[offset] > '3') {
          --len;
        }
        c = (char)Integer.parseInt(new String(description, offset, len), 8);
        offset += len;
        return c;
      }
      if (c == 'x' || c == 'u') {
        int len = digits(offset, 4, 16);
        c = (char)Integer.parseInt(new String(description, offset, len), 16);
        offset += len;
        return c;
      }
      switch (c) {
      case 'a':
         return 0x0007;
      case 'e':
         return 0x001B;
      case 'f':
         return 0x000C;
      case 'n':
         return 0x000A;
      case 'r':
         return 0x000D;
      case 't':
         return 0x0009;
      case '\\':
      case '.':
      case '*':
      case '+':
      case '?':
      case '|':
      case '[':
      case ']':
      case '{':
      case '}':
      case '(':
      case ')':
      case '^':
      case '$':
        return c;
      }
      return -1;
    }

    public int digits(int offset, int maxLength, int base) {
      for (int i = 0; ; ++i) {
        if (i == maxLength || offset + i >= description.length) {
          return i;
        }
        int value = description[offset + i] - '0';
        if (value < 0) {
          return i;
        }
        if (base > 10 && value >= 10) {
          value += 10 - (value >= 'a' - '0' ? 'a' - '0' : 'A' - '0');
        }
        if (value >= base) {
          return i;
        }
      }
    }

    public CharacterMatcher parseClass(int start) {
      offset = start;
      return parseClass();
    }

    public CharacterMatcher parseClass() {
      if (description[offset] != '[') {
        if (description[offset] == '\\') {
          String range = specialClass(description[++ offset]);
          if (range != null) {
            ++ offset;
            return CharacterMatcher.parse(range);
          }
        }
        return null;
      }
      CharacterMatcher matcher = new CharacterMatcher(new boolean[0],
        description[++ offset] == '^');
      if (matcher.inversePattern) {
        ++ offset;
      }

      int previous = -1;
      boolean firstCharacter = true;
      for (;;) {
        if (offset >= description.length) {
          unsupported("short regex");
        }
        char c = description[offset++];
        if (c == '-' && !firstCharacter && description[offset] != ']') {
          if (previous < 0) {
            unsupported("invalid range");
          }
          int rangeEnd = description[offset];
          if ('\\' == rangeEnd) {
            rangeEnd = parseEscapedCharacter();
            if (rangeEnd < 0) {
              unsupported("invalid range");
            }
          }
          matcher.ensureCapacity(rangeEnd + 1);
          for (int j = previous + 1; j <= rangeEnd; j++) {
            matcher.map[j] = true;
          }
        } else if (c == '\\') {
          int saved = offset;
          previous = parseEscapedCharacter();
          if (previous < 0) {
            offset = saved - 1;
            CharacterMatcher clazz = parseClass();
            if (clazz == null) {
              unsupported("escape");
            }
            matcher.merge(clazz);
          } else {
            matcher.setMatch(previous);
          }
        } else if (c == '[') {
          Parser parser = new Parser(description);
          CharacterMatcher other = parser.parseClass(offset - 1);
          if (other == null) {
            unsupported("invalid merge");
          }
          matcher.merge(other);
          offset = parser.getEndOffset();
          previous = -1;
        } else if (c == '&') {
          if (offset + 2 > description.length || description[offset] != '&'
              || description[offset + 1] != '[') {
            unsupported("operation");
          }
          Parser parser = new Parser(description);
          CharacterMatcher other = parser.parseClass(offset + 1);
          if (other == null) {
            unsupported("invalid intersection");
          }
          matcher.intersect(other);
          offset = parser.getEndOffset();
          previous = -1;
        } else if (c == ']') {
          break;
        } else {
          previous = c;
          matcher.setMatch(previous);
        }
        firstCharacter = false;
      }

      return matcher;
    }

    private void unsupported(String msg) throws UnsupportedOperationException {
      throw new UnsupportedOperationException("Unsupported " + msg + " @"
        + offset + ": "
        + new String(description, 0, description.length));
    }
  }
}
