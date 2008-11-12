/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

package java.lang;

import java.io.UnsupportedEncodingException;
import java.util.regex.Pattern;
import java.io.ByteArrayOutputStream;

public final class String implements Comparable<String>, CharSequence {
  private final Object data;
  private final int offset;
  private final int length;
  private int hashCode;

  public String(char[] data, int offset, int length, boolean copy) {
    this((Object) data, offset, length, copy);
  }

  public String(char[] data, int offset, int length) {
    this(data, offset, length, true);
  }

  public String(char[] data) {
    this(data, 0, data.length);
  }

  public String(byte[] data, int offset, int length, boolean copy) {
    this((Object) data, offset, length, copy);
  }

  public String(byte[] data, int offset, int length) {
    this(data, offset, length, true);
  }

  public String(byte[] data) {
    this(data, 0, data.length);
  }

  public String(String s) {
    this(s.toCharArray());
  }

  public String(byte[] data, String charset)
    throws UnsupportedEncodingException
    {
      this(data);
      if (! charset.equals("US-ASCII")) {
        throw new UnsupportedEncodingException(charset);
      }
    }

  private String(Object data, int offset, int length, boolean copy) {
    int l;
    if (data instanceof char[]) {
      l = ((char[]) data).length;
    } else {
      l = ((byte[]) data).length;
    }

    if (offset < 0 || offset + length > l) {
      throw new IndexOutOfBoundsException
        (offset + " < 0 or " + offset + " + " + length + " > " + l);
    }

    if(!copy && isUTF8(data)) copy = true;

    if (copy) {
      Object c;
      if (data instanceof char[]) {
        c = new char[length];
        System.arraycopy(data, offset, c, 0, length);
      } else {
        c = decodeUTF8((byte[])data, offset, length);
        if(c instanceof char[]) length = ((char[])c).length;
      }
      
      this.data = c;
      this.offset = 0;
      this.length = length;
    } else {
      this.data = data;
      this.offset = offset;
      this.length = length;
    }
  }

  private static boolean isUTF8(Object data) {
    if(!(data instanceof byte[])) return false;
    byte[] b = (byte[])data;
    for(int i = 0; i < b.length; ++i) {
      if(((int)b[i] & 0x080) != 0) return true;
    }
    return false;
  }

  private static byte[] encodeUTF8(char[] s16, int offset, int length) {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    for(int i = offset; i < offset+length; ++i) {
      char c = s16[i];
      if(c == '\u0000') {     // null char
        buf.write(0);
        buf.write(0);
      } else if(c < 0x080) {  // 1 byte char
        buf.write(c);
      } else if(c < 0x0800) { // 2 byte char
        buf.write(0x0c0 | (c >>> 6));
        buf.write(0x080 | (c & 0x03f));
      } else {                // 3 byte char
        buf.write(0x0e0 | ((c >>> 12) & 0x0f));
        buf.write(0x080 | ((c >>> 6) & 0x03f));
        buf.write(0x080 | (c & 0x03f));
      }
    }
    return buf.toByteArray();
  }

  private static void decodeUTF8_insert(Object data, int index, int val) {
    if(data instanceof byte[]) ((byte[])data)[index] = (byte)val;
    else                       ((char[])data)[index] = (char)val;
  }

  private static Object decodeUTF8_widen(Object data, int length, int capacity) {
    byte[] src = (byte[])data;
    char[] result = new char[capacity];
    for(int i = 0; i < length; ++i) result[i] = (char)((int)src[i] & 0x0ff);
    return result;
  }

  private static Object decodeUTF8_trim(Object data, int length) {
    if(data instanceof byte[]) return data;
    if(((char[])data).length == length) return data;
    char[] result = new char[length];
    System.arraycopy(data, 0, result, 0, length);
    return result;
  }

  private static Object decodeUTF8(byte[] s8, int offset, int length) {
    Object buf = new byte[s8.length];
    boolean isMultiByte = false;
    int i=offset, j=0;
    while(i < offset+length) {
      int x = s8[i++];
      if((x & 0x080) == 0x0) {          // 1 byte char
        if(x == 0) ++i;                 // 2 byte null char
        decodeUTF8_insert(buf, j++, x);
      } else if((x & 0x0e0) == 0x0c0) { // 2 byte char
        if(!isMultiByte) {
          buf = decodeUTF8_widen(buf, j, s8.length-1);
          isMultiByte = true;
        }
        int y = s8[i++];
        decodeUTF8_insert(buf, j++, ((x & 0x1f) << 6) | (y & 0x3f));
      } else if((x & 0x0f0) == 0x0e0) { // 3 byte char
        if(!isMultiByte) {
          buf = decodeUTF8_widen(buf, j, s8.length-2);
          isMultiByte = true;
        }
        int y = s8[i++]; int z = s8[i++];
        decodeUTF8_insert(buf, j++, ((x & 0xf) << 12) | ((y & 0x3f) << 6) | (z & 0x3f));
      }
    }

    return decodeUTF8_trim(buf, j);
  }

  public String toString() {
    return this;
  }

  public int length() {
    return length;
  }

  public int hashCode() {
    if (hashCode == 0) {
      int h = 0;
      for (int i = 0; i < length; ++i) h = (h * 31) + charAt(i);
      hashCode = h;
    }
    return hashCode;
  }

  public boolean equals(Object o) {
    return this == o || (o instanceof String && compareTo((String) o) == 0);
  }

  public boolean equalsIgnoreCase(String s) {
    return this == s || compareToIgnoreCase(s) == 0;
  }

  public int compareTo(String s) {
    if (this == s) return 0;

    int idx = 0;
    int result;

    int end = (length < s.length ? length : s.length);

    while (idx < end) {
      if ((result = charAt(idx) - s.charAt(idx)) != 0) {
        return result;
      }
      idx++;
    }
    return length - s.length;
  }

  public int compareToIgnoreCase(String s) {
    if (this == s) return 0;

    int idx = 0;
    int result;

    int end = (length < s.length ? length : s.length);

    while (idx < end) {
      if ((result =
           Character.toLowerCase(charAt(idx)) -
           Character.toLowerCase(s.charAt(idx))) != 0) {
        return result;
      }
      idx++;
    }
    return length - s.length;
  }

  public String trim() {
    int start = -1;
    for (int i = 0; i < length; ++i) {
      char c = charAt(i);
      if (start == -1 && ! Character.isWhitespace(c)) {
        start = i;
        break;
      }
    }

    int end = -1;
    for (int i = length - 1; i >= 0; --i) {
      char c = charAt(i);
      if (end == -1 && ! Character.isWhitespace(c)) {
        end = i + 1;
        break;
      }
    }

    if (start >= end) {
      return "";
    } else {
      return substring(start, end);
    }
  }

  public String toLowerCase() {
    char[] b = new char[length];
    for (int i = 0; i < length; ++i) {
      b[i] = Character.toLowerCase(charAt(i));
    }
    return new String(b, 0, length, false);
  }

  public String toUpperCase() {
    char[] b = new char[length];
    for (int i = 0; i < length; ++i) {
      b[i] = Character.toUpperCase(charAt(i));
    }
    return new String(b, 0, length, false);
  }

  public int indexOf(int c) {
    return indexOf(c, 0);
  }

  public int indexOf(int c, int start) {
    for (int i = start; i < length; ++i) {
      if (charAt(i) == c) {
        return i;
      }
    }

    return -1;
  }

  public int lastIndexOf(int ch) {
    return lastIndexOf(ch, length-1);
  }

  public int indexOf(String s) {
    return indexOf(s, 0);
  }

  public int indexOf(String s, int start) {
    if (s.length == 0) return start;

    for (int i = start; i < length - s.length + 1; ++i) {
      int j = 0;
      for (; j < s.length; ++j) {
        if (charAt(i + j) != s.charAt(j)) {
          break;
        }
      }
      if (j == s.length) {
        return i;
      }
    }

    return -1;
  }

  public int lastIndexOf(String s) {
    if (s.length == 0) return length;

    for (int i = length - s.length; i >= 0; --i) {
      int j = 0;
      for (; j < s.length && i + j < length; ++j) {
        if (charAt(i + j) != s.charAt(j)) {
          break;
        }
      }
      if (j == s.length) {
        return i;
      }
    }

    return -1;
  }

  public String replace(char oldChar, char newChar) {
    if (data instanceof char[]) {
      char[] buf = new char[length];
      for (int i=0; i < length; i++) {
        if (charAt(i) == oldChar) {
          buf[i] = newChar;
        } else {
          buf[i] = charAt(i);
        }
      }
      return new String(buf, 0, length, false);
    } else {
      byte[] buf = new byte[length];
      byte[] orig = (byte[])data;
      byte oldByte = (byte)oldChar;
      byte newByte = (byte)newChar;
      for (int i=0; i < length; i++) {
        if (orig[i+offset] == oldByte) {
          buf[i] = newByte;
        } else {
          buf[i] = orig[i+offset];
        }
      }
      return new String(buf, 0, length, false);
    }
  }

  public String substring(int start) {
    return substring(start, length);
  }

  public String substring(int start, int end) {
    if (start >= 0 && end >= start && end <= length) {
      if (start == 0 && end == length) {
        return this;
      } else if (end - start == 0) {
        return "";
      } else  {
        return new String(data, offset + start, end - start, false);
      }
    } else {
      throw new IndexOutOfBoundsException
        (start + " not in (0, " + end + ") or " + end + " > " + length);
    }
  }

  public boolean startsWith(String s) {
    if (length >= s.length) {
      return substring(0, s.length).compareTo(s) == 0;
    } else {
      return false;
    }
  }

  public boolean startsWith(String s, int start) {
    if (length >= s.length + start) {
      return substring(start, s.length).compareTo(s) == 0;
    } else {
      return false;
    }
  }
  
  public boolean endsWith(String s) {
    if (length >= s.length) {
      return substring(length - s.length).compareTo(s) == 0;
    } else {
      return false;
    }
  }

  public String concat(String s) {
    if (s.length() == 0) {
      return this;
    } else {
      return this + s;
    }
  }

  public void getBytes(int srcOffset, int srcLength,
                       byte[] dst, int dstOffset)
  {
    if (srcOffset < 0 || srcOffset + srcLength > length) {
      throw new IndexOutOfBoundsException();
    }

    if (data instanceof char[]) {
      char[] src = (char[]) data;
      for (int i = 0; i < srcLength; ++i) {
        dst[i + dstOffset] = (byte) src[i + offset + srcOffset];
      }
    } else {
      byte[] src = (byte[]) data;
      System.arraycopy(src, offset + srcOffset, dst, dstOffset, srcLength);
    }
  }

  public byte[] getBytes() {
    if(data instanceof byte[]) {
      byte[] b = new byte[length];
      getBytes(0, length, b, 0);
      return b;
    }
    return encodeUTF8((char[])data, offset, length);
  }

  public byte[] getBytes(String format)
    throws java.io.UnsupportedEncodingException
  {
    return getBytes();
  }

  public void getChars(int srcOffset, int srcEnd,
                       char[] dst, int dstOffset)
  {
    if (srcOffset < 0 || srcEnd > length) {
      throw new IndexOutOfBoundsException();
    }
    int srcLength = srcEnd-srcOffset;
    if (data instanceof char[]) {
      char[] src = (char[]) data;
      System.arraycopy(src, offset + srcOffset, dst, dstOffset, srcLength);
    } else {
      byte[] src = (byte[]) data;
      for (int i = 0; i < srcLength; ++i) {
        dst[i + dstOffset] = (char) src[i + offset + srcOffset];
      }      
    }
  }

  public char[] toCharArray() {
    char[] b = new char[length];
    getChars(0, length, b, 0);
    return b;
  }

  public char charAt(int index) {
    if (index < 0 || index > length) {
      throw new IndexOutOfBoundsException();
    }
    
    if (data instanceof char[]) {
      return ((char[]) data)[index + offset];
    } else {
      return (char) ((byte[]) data)[index + offset];
    }
  }

  public String[] split(String s) {
    String[] array = new String[(length / s.length) + 1];
    int index = 0;
    int last = 0;
    int position = 0;
    for (int i = 0; i < length - s.length + 1;) {
      int j;
      for (j = 0; j < s.length; ++j) {
        if (charAt(i + j) != s.charAt(j)) {
          break;
        }
      }

      if (j == s.length) {
        if (i > 0) {
          if (i > position) {
            last = index;
          }
          array[index++] = substring(position, i);
        }
        i = position = i + s.length;
      } else {
        ++ i;
      }
    }

    if (position < length) {
      last = index;
      array[index] = substring(position, length);
    }

    if (last + 1 < array.length) {
      String[] a = new String[last + 1];
      System.arraycopy(array, 0, a, 0, last + 1);
      array = a;
    }

    return array;
  }

  public CharSequence subSequence(int start, int end) {
    return substring(start, end);
  }
  
  public boolean matches(String regex) {
    return Pattern.matches(regex, this);
  }
  
  public native String intern();

  public static String valueOf(Object s) {
    return s == null ? "null" : s.toString();
  }

  public static String valueOf(boolean v) {
    return Boolean.toString(v);
  }

  public static String valueOf(byte v) {
    return Byte.toString(v);
  }

  public static String valueOf(short v) {
    return Short.toString(v);
  }

  public static String valueOf(char v) {
    return Character.toString(v);
  }

  public static String valueOf(int v) {
    return Integer.toString(v);
  }

  public static String valueOf(long v) {
    return Long.toString(v);
  }

  public static String valueOf(float v) {
    return Float.toString(v);
  }

  public static String valueOf(double v) {
    return Double.toString(v);
  }

  public int lastIndexOf(int ch, int lastIndex) {
    for (int i = lastIndex ; i >= 0; --i) {
      if (charAt(i) == ch) {
        return i;
      }
    }

    return -1;
  }
}
