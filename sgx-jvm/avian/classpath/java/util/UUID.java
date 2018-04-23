/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.util;

public class UUID {
  private final byte[] data;

  private UUID(byte[] data) {
    this.data = data;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    toHex(sb, data, 0, 4); sb.append('-');
    toHex(sb, data, 4, 2); sb.append('-');
    toHex(sb, data, 6, 2); sb.append('-');
    toHex(sb, data, 8, 2); sb.append('-');
    toHex(sb, data, 10, 6);
    return sb.toString();
  }

  private static char toHex(int i) {
    return (char) (i < 10 ? i + '0' : (i - 10) + 'A');
  }

  private static void toHex(StringBuilder sb, byte[] array, int offset,
                            int length)
  {
    for (int i = offset; i < offset + length; ++i) {
      sb.append(toHex((array[i] >> 4) & 0xf));
      sb.append(toHex((array[i]     ) & 0xf));
    }
  }
}
