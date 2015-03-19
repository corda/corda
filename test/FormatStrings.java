/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

/*
 * @author bcg
 */
public class FormatStrings {

  public static void main(String... args) throws Exception {
    FormatStrings test = new FormatStrings();
    test.testLiteral();
    test.testString();
    test.testNewline();
    test.testPercent();
    test.testBoolean();
    test.testCharacter();
    test.testHashCode();
    test.testIntegers();
    test.testWidths();
    test.testPrecisions();
  }

  private void _testFormat(String expected, String format, Object... args) {
    String actual = String.format(format, args);
    ensureEquals(expected, actual);
    System.err.println("Expected: " + expected + ", Actual: " + actual);
  }

  private static void ensureEquals(String expected, String actual) {
    if (expected != actual) {
      if ((expected == null || actual == null) || !(expected.equals(actual))) {
        throw new IllegalArgumentException(
          "Expected `" + expected + "` but was actually `" + actual + "`.");
      }
    }
  }

  public void testLiteral() {
    _testFormat("test Literal 1", "test Literal 1");
    _testFormat("test Literal 2", "test Literal 2", (Object[]) null);
    _testFormat("test Literal 3", "test Literal 3", new Object[0]);
  }

  public void testString() {
    _testFormat("test String 1", "test %s", "String 1");
    _testFormat("test String null", "test String %s", new Object[]{null} );
    _testFormat("test String 2", "test %2$s", "String 1", "String 2");
    _testFormat("String `string`", "String `%s`", new String("string"));
    _testFormat("String `STRING`", "String `%S`", new String("string"));
    _testFormat("String `another string`", "String `%s`", new String("another string"));
    _testFormat("String `ANOTHER STRING`", "String `%S`", new String("another string"));
    _testFormat("String `null`", "String `%s`", (String)null);
    _testFormat("String `NULL`", "String `%S`", (String)null);
    _testFormat("String `true`", "String `%s`", new Boolean("true"));
    _testFormat("String `TRUE`", "String `%S`", new Boolean("true"));
    _testFormat("String `false`", "String `%s`", new Boolean("false"));
    _testFormat("String `FALSE`", "String `%S`", new Boolean("false"));
  }

  public void testNewline() {
    final String newline = System.getProperty("line.separator");
    _testFormat(
        "<<<" + newline + "    test newlines" + newline + ">>>", 
        "<<<%n    test newlines%n>>>"
    );
  }

  public void testBoolean() {
    _testFormat("test boolean true", "test boolean %b", true);
    _testFormat("test Boolean true", "test Boolean %b", Boolean.TRUE);
    _testFormat("test boolean false", "test boolean %b", false);
    _testFormat("test Boolean false", "test Boolean %b", Boolean.FALSE);
    _testFormat("test null Boolean (false)", "test null Boolean (%b)", new Object[]{(Boolean)null});
    _testFormat("test non-null Boolean (true)", "test non-null Boolean (%b)", new Object());
    _testFormat("test boolean string (true)", "test boolean string (%b)", "false");
    _testFormat("Boolean `true`", "Boolean `%b`", new Boolean("true"));
    _testFormat("Boolean `TRUE`", "Boolean `%B`", new Boolean("true"));
    _testFormat("Boolean `false`", "Boolean `%b`", new Boolean("false"));
    _testFormat("Boolean `FALSE`", "Boolean `%B`", new Boolean("false"));
    _testFormat("Boolean `false`", "Boolean `%b`", (String)null);
    _testFormat("Boolean `FALSE`", "Boolean `%B`", (String)null);
    _testFormat("Boolean `true`", "Boolean `%b`", new String(""));
    _testFormat("Boolean `TRUE`", "Boolean `%B`", new String(""));
    _testFormat("Boolean `true`", "Boolean `%b`", new String("true"));
    _testFormat("Boolean `TRUE`", "Boolean `%B`", new String("true"));
    _testFormat("Boolean `true`", "Boolean `%b`", new String("false"));
    _testFormat("Boolean `TRUE`", "Boolean `%B`", new String("false"));
  }

  public void testPercent() {
    _testFormat("Percents work 100%", "Percents work 100%%");
  }

  public void testCharacter() {
    _testFormat("test character such as a", "test character such as %c", 'a');
    _testFormat("test character such as b", "test character such as %c", (int) 98);
    _testFormat("test character such as c", "test character such as %c", (byte) 99);
    _testFormat("test character such as d", "test character such as %c", (short) 100);
  }

  public void testHashCode() {
    final Object obj1 = new Object();
    final Object obj2 = new Object();
    final String hc1 = Integer.toHexString(obj1.hashCode());
    final String hc2 = Integer.toHexString(obj2.hashCode());
    _testFormat("test hashcode 1 (" + hc1 + ")" , "test hashcode 1 (%h)", obj1, obj2);
    _testFormat("test hashcode 2 (" + hc2 + ")" , "test hashcode 2 (%2$h)", obj1, obj2);
    _testFormat("test hashcode null", "test hashcode %h", (String) null);
  }

  public void testIntegers() {

    _testFormat("Long 1", "Long %d", new Long(1));
    _testFormat("Long 2", "Long %2$d", new Long(1), new Long(2));
    _testFormat("Integer 1", "Integer %d", new Integer(1));
    _testFormat("Integer 2", "Integer %2$d", new Integer(1), new Integer(2));
    _testFormat("Short 1", "Short %d", new Short((short)1));
    _testFormat("Short 2", "Short %2$d", new Short((short)1), new Short((short)2));
    _testFormat("Byte 1", "Byte %d", new Byte((byte)1));
    _testFormat("Byte 2", "Byte %2$d", new Byte((byte)1), new Byte((byte)2));

    _testFormat("Long 144", "Long %o", new Long(100));
    _testFormat("Long 310", "Long %2$o", new Long(100), new Long(200));
    _testFormat("Integer 144", "Integer %o", new Integer(100));
    _testFormat("Integer 310", "Integer %2$o", new Integer(100), new Integer(200));
    _testFormat("Short 144", "Short %o", new Short((short)100));
    _testFormat("Short 310", "Short %2$o", new Short((short)100), new Short((short)200));
    _testFormat("Byte 144", "Byte %o", new Byte((byte)100));
    _testFormat("Byte 310", "Byte %2$o", new Byte((byte)100), new Byte((byte)200));

    _testFormat("Long 64", "Long %x", new Long(100));
    _testFormat("Long c8", "Long %2$x", new Long(100), new Long(200));
    _testFormat("Long C8", "Long %2$X", new Long(100), new Long(200));
    _testFormat("Integer 64", "Integer %x", new Integer(100));
    _testFormat("Integer c8", "Integer %2$x", new Integer(100), new Integer(200));
    _testFormat("Short 64", "Short %x", new Short((short)100));
    _testFormat("Short C8", "Short %2$X", new Short((short)100), new Short((short)200));
    _testFormat("Byte 64", "Byte %x", new Byte((byte)100));
    _testFormat("Byte c8", "Byte %2$x", new Byte((byte)100), new Byte((byte)200));
 
    _testFormat("Decimal `1`", "Decimal `%d`", new Integer((int)1));
    _testFormat("Decimal `0`", "Decimal `%d`", new Integer((int)0));
    _testFormat("Decimal `100`", "Decimal `%d`", new Integer((int)100));
    _testFormat("Decimal `100000`", "Decimal `%d`", new Integer((int)100000));
    _testFormat("Decimal `63`", "Decimal `%d`", new Integer((int)63));
    _testFormat("Decimal `64`", "Decimal `%d`", new Integer((int)64));
    _testFormat("Decimal `-1`", "Decimal `%d`", new Integer((int)-1));
    _testFormat("Decimal `-100`", "Decimal `%d`", new Integer((int)-100));
    _testFormat("Decimal `-100000`", "Decimal `%d`", new Integer((int)-100000));
    _testFormat("Decimal `1`", "Decimal `%d`", new Byte((byte)1));
    _testFormat("Decimal `0`", "Decimal `%d`", new Byte((byte)0));
    _testFormat("Decimal `100`", "Decimal `%d`", new Byte((byte)100));
    _testFormat("Decimal `63`", "Decimal `%d`", new Byte((byte)63));
    _testFormat("Decimal `64`", "Decimal `%d`", new Byte((byte)64));
    _testFormat("Decimal `-1`", "Decimal `%d`", new Byte((byte)-1));
    _testFormat("Decimal `-100`", "Decimal `%d`", new Byte((byte)-100));
    _testFormat("Decimal `1`", "Decimal `%d`", new Long((long)1));
    _testFormat("Decimal `0`", "Decimal `%d`", new Long((long)0));
    _testFormat("Decimal `100`", "Decimal `%d`", new Long((long)100));
    _testFormat("Decimal `100000`", "Decimal `%d`", new Long((long)100000));
    _testFormat("Decimal `63`", "Decimal `%d`", new Long((long)63));
    _testFormat("Decimal `64`", "Decimal `%d`", new Long((long)64));
    _testFormat("Decimal `-1`", "Decimal `%d`", new Long((long)-1));
    _testFormat("Decimal `-100`", "Decimal `%d`", new Long((long)-100));
    _testFormat("Decimal `-100000`", "Decimal `%d`", new Long((long)-100000));
    _testFormat("Decimal `1`", "Decimal `%d`", new Short((short)1));
    _testFormat("Decimal `0`", "Decimal `%d`", new Short((short)0));
    _testFormat("Decimal `100`", "Decimal `%d`", new Short((short)100));
    _testFormat("Decimal `63`", "Decimal `%d`", new Short((short)63));
    _testFormat("Decimal `64`", "Decimal `%d`", new Short((short)64));
    _testFormat("Decimal `-1`", "Decimal `%d`", new Short((short)-1));
    _testFormat("Decimal `-100`", "Decimal `%d`", new Short((short)-100));

    _testFormat("Octal `1`", "Octal `%o`", new Integer((int)1));
    _testFormat("Octal `0`", "Octal `%o`", new Integer((int)0));
    _testFormat("Octal `144`", "Octal `%o`", new Integer((int)100));
    _testFormat("Octal `303240`", "Octal `%o`", new Integer((int)100000));
    _testFormat("Octal `77`", "Octal `%o`", new Integer((int)63));
    _testFormat("Octal `100`", "Octal `%o`", new Integer((int)64));
    _testFormat("Octal `37777777777`", "Octal `%o`", new Integer((int)-1));
    _testFormat("Octal `37777777634`", "Octal `%o`", new Integer((int)-100));
    _testFormat("Octal `37777474540`", "Octal `%o`", new Integer((int)-100000));
    _testFormat("Octal `1`", "Octal `%o`", new Byte((byte)1));
    _testFormat("Octal `0`", "Octal `%o`", new Byte((byte)0));
    _testFormat("Octal `144`", "Octal `%o`", new Byte((byte)100));
    _testFormat("Octal `77`", "Octal `%o`", new Byte((byte)63));
    _testFormat("Octal `100`", "Octal `%o`", new Byte((byte)64));
    _testFormat("Octal `377`", "Octal `%o`", new Byte((byte)-1));
    _testFormat("Octal `234`", "Octal `%o`", new Byte((byte)-100));
    _testFormat("Octal `1`", "Octal `%o`", new Long((long)1));
    _testFormat("Octal `0`", "Octal `%o`", new Long((long)0));
    _testFormat("Octal `144`", "Octal `%o`", new Long((long)100));
    _testFormat("Octal `303240`", "Octal `%o`", new Long((long)100000));
    _testFormat("Octal `77`", "Octal `%o`", new Long((long)63));
    _testFormat("Octal `100`", "Octal `%o`", new Long((long)64));
    _testFormat("Octal `1`", "Octal `%o`", new Short((short)1));
    _testFormat("Octal `0`", "Octal `%o`", new Short((short)0));
    _testFormat("Octal `144`", "Octal `%o`", new Short((short)100));
    _testFormat("Octal `77`", "Octal `%o`", new Short((short)63));
    _testFormat("Octal `100`", "Octal `%o`", new Short((short)64));
    _testFormat("Octal `177777`", "Octal `%o`", new Short((short)-1));
    _testFormat("Octal `177634`", "Octal `%o`", new Short((short)-100));

    _testFormat("HexDec `1`", "HexDec `%x`", new Integer((int)1));
    _testFormat("HexDec `1`", "HexDec `%X`", new Integer((int)1));
    _testFormat("HexDec `0`", "HexDec `%x`", new Integer((int)0));
    _testFormat("HexDec `0`", "HexDec `%X`", new Integer((int)0));
    _testFormat("HexDec `64`", "HexDec `%x`", new Integer((int)100));
    _testFormat("HexDec `64`", "HexDec `%X`", new Integer((int)100));
    _testFormat("HexDec `186a0`", "HexDec `%x`", new Integer((int)100000));
    _testFormat("HexDec `186A0`", "HexDec `%X`", new Integer((int)100000));
    _testFormat("HexDec `3f`", "HexDec `%x`", new Integer((int)63));
    _testFormat("HexDec `3F`", "HexDec `%X`", new Integer((int)63));
    _testFormat("HexDec `40`", "HexDec `%x`", new Integer((int)64));
    _testFormat("HexDec `40`", "HexDec `%X`", new Integer((int)64));
    _testFormat("HexDec `ffffffff`", "HexDec `%x`", new Integer((int)-1));
    _testFormat("HexDec `FFFFFFFF`", "HexDec `%X`", new Integer((int)-1));
    _testFormat("HexDec `ffffff9c`", "HexDec `%x`", new Integer((int)-100));
    _testFormat("HexDec `FFFFFF9C`", "HexDec `%X`", new Integer((int)-100));
    _testFormat("HexDec `fffe7960`", "HexDec `%x`", new Integer((int)-100000));
    _testFormat("HexDec `FFFE7960`", "HexDec `%X`", new Integer((int)-100000));
    _testFormat("HexDec `1`", "HexDec `%x`", new Byte((byte)1));
    _testFormat("HexDec `1`", "HexDec `%X`", new Byte((byte)1));
    _testFormat("HexDec `0`", "HexDec `%x`", new Byte((byte)0));
    _testFormat("HexDec `0`", "HexDec `%X`", new Byte((byte)0));
    _testFormat("HexDec `64`", "HexDec `%x`", new Byte((byte)100));
    _testFormat("HexDec `64`", "HexDec `%X`", new Byte((byte)100));
    _testFormat("HexDec `3f`", "HexDec `%x`", new Byte((byte)63));
    _testFormat("HexDec `3F`", "HexDec `%X`", new Byte((byte)63));
    _testFormat("HexDec `40`", "HexDec `%x`", new Byte((byte)64));
    _testFormat("HexDec `40`", "HexDec `%X`", new Byte((byte)64));
    _testFormat("HexDec `ff`", "HexDec `%x`", new Byte((byte)-1));
    _testFormat("HexDec `FF`", "HexDec `%X`", new Byte((byte)-1));
    _testFormat("HexDec `9c`", "HexDec `%x`", new Byte((byte)-100));
    _testFormat("HexDec `9C`", "HexDec `%X`", new Byte((byte)-100));
    _testFormat("HexDec `1`", "HexDec `%x`", new Long((long)1));
    _testFormat("HexDec `1`", "HexDec `%X`", new Long((long)1));
    _testFormat("HexDec `0`", "HexDec `%x`", new Long((long)0));
    _testFormat("HexDec `0`", "HexDec `%X`", new Long((long)0));
    _testFormat("HexDec `64`", "HexDec `%x`", new Long((long)100));
    _testFormat("HexDec `64`", "HexDec `%X`", new Long((long)100));
    _testFormat("HexDec `186a0`", "HexDec `%x`", new Long((long)100000));
    _testFormat("HexDec `186A0`", "HexDec `%X`", new Long((long)100000));
    _testFormat("HexDec `3f`", "HexDec `%x`", new Long((long)63));
    _testFormat("HexDec `3F`", "HexDec `%X`", new Long((long)63));
    _testFormat("HexDec `40`", "HexDec `%x`", new Long((long)64));
    _testFormat("HexDec `40`", "HexDec `%X`", new Long((long)64));
    _testFormat("HexDec `ffffffffffffffff`", "HexDec `%x`", new Long((long)-1));
    _testFormat("HexDec `FFFFFFFFFFFFFFFF`", "HexDec `%X`", new Long((long)-1));
    _testFormat("HexDec `ffffffffffffff9c`", "HexDec `%x`", new Long((long)-100));
    _testFormat("HexDec `FFFFFFFFFFFFFF9C`", "HexDec `%X`", new Long((long)-100));
    _testFormat("HexDec `fffffffffffe7960`", "HexDec `%x`", new Long((long)-100000));
    _testFormat("HexDec `FFFFFFFFFFFE7960`", "HexDec `%X`", new Long((long)-100000));
    _testFormat("HexDec `1`", "HexDec `%x`", new Short((short)1));
    _testFormat("HexDec `1`", "HexDec `%X`", new Short((short)1));
    _testFormat("HexDec `0`", "HexDec `%x`", new Short((short)0));
    _testFormat("HexDec `0`", "HexDec `%X`", new Short((short)0));
    _testFormat("HexDec `64`", "HexDec `%x`", new Short((short)100));
    _testFormat("HexDec `64`", "HexDec `%X`", new Short((short)100));
    _testFormat("HexDec `3f`", "HexDec `%x`", new Short((short)63));
    _testFormat("HexDec `3F`", "HexDec `%X`", new Short((short)63));
    _testFormat("HexDec `40`", "HexDec `%x`", new Short((short)64));
    _testFormat("HexDec `40`", "HexDec `%X`", new Short((short)64));
    _testFormat("HexDec `ffff`", "HexDec `%x`", new Short((short)-1));
    _testFormat("HexDec `FFFF`", "HexDec `%X`", new Short((short)-1));
    _testFormat("HexDec `ff9c`", "HexDec `%x`", new Short((short)-100));
    _testFormat("HexDec `FF9C`", "HexDec `%X`", new Short((short)-100));
  }

  public void testWidths() {
    _testFormat("0001", "%04d", 1);
    _testFormat("   1", "%4d", 1);
    _testFormat("  11", "%4x", 17);
    _testFormat("0011", "%04x", 17);
    _testFormat(" a", "%2x", 10);
    _testFormat(" A", "%2X", 10);
    _testFormat("a ", "%-2x", 10);
    _testFormat("A ", "%-2X", 10);
    _testFormat("10000", "%4d", 10000);
    _testFormat("Hello World    ", "%-15s", "Hello World");
    _testFormat("    Hello World", "%15s", "Hello World");
  }

  public void testPrecisions() {
    _testFormat("Hello", "%-1.5s", "Hello World");
    _testFormat("Hello", "%1.5s", "Hello World");
  }

}
