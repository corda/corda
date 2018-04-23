public class Strings {
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  private static boolean equal(Object a, Object b) {
    return a == b || (a != null && a.equals(b));
  }

  private static boolean arraysEqual(byte[] a, byte[] b) {
    if (a.length != b.length) {
      return false;
    }

    for (int i = 0; i < a.length; ++i) {
      if (a[i] != b[i]) {
        return false;
      }
    }

    return true;
  }

  private static byte[] append(byte[] a, byte[] b) {
    byte[] c = new byte[a.length + b.length];
    for (int i = 0; i < a.length; ++i) {
      c[i] = a[i];
    }
    for (int i = 0; i < b.length; ++i) {
      c[i + a.length] = b[i];
    }
    return c;
  }

  private static boolean arraysEqual(Object[] a, Object[] b) {
    if (a.length != b.length) {
      return false;
    }

    for (int i = 0; i < a.length; ++i) {
      if (! equal(a[i], b[i])) {
        return false;
      }
    }

    return true;
  }

  private static void testDecode(final boolean prematureEOS) throws Exception {
    java.io.Reader r = new java.io.InputStreamReader
      (new java.io.InputStream() {
          int state = 0;

          public int read() {
            throw new UnsupportedOperationException();
          }

          public int read(byte[] b, int offset, int length) {
            if (length == 0) return 0;

            switch (state) {
            case 0:
              b[offset] = (byte) 0xc2;
              state = 1;
              return 1;

            case 1:
              b[offset] = (byte) 0xae;
              state = 2;
              return 1;

            case 2:
              b[offset] = (byte) 0xea;
              state = 3;
              return 1;

            case 3:
              b[offset] = (byte) 0xba;
              state = prematureEOS ? 5 : 4;
              return 1;

            case 4:
              b[offset] = (byte) 0xaf;
              state = 5;
              return 1;

            case 5:
              return -1;

            default:
              throw new RuntimeException();
            }
          }
        }, "UTF-8");

    char[] buffer = new char[2];
    int offset = 0;
    while (offset < buffer.length) {
      int c = r.read(buffer, offset, buffer.length - offset);
      if (c == -1) break;
      offset += c;
    }

    expect(new String(buffer, 0, offset).equals
           (prematureEOS ? "\u00ae\ufffd" : "\u00ae\uaeaf"));
  }

  public static void testTrivialPattern() throws Exception {
    expect("?7".matches("\\0777"));
    expect("\007".matches("\\a"));
    expect("\\".matches("\\\\"));
    expect("J".matches("\\x4A"));
    expect("a".matches("\\x61"));
    expect("\0078".matches("\\078"));
  }

  public static void main(String[] args) throws Exception {
    expect(new String(new byte[] { 99, 111, 109, 46, 101, 99, 111, 118, 97,
                                   116, 101, 46, 110, 97, 116, 46, 98, 117,
                                   115, 46, 83, 121, 109, 98, 111, 108 }, "UTF-8")
      .equals("com.ecovate.nat.bus.Symbol"));
    
    final String months = "Jan\u00aeFeb\u00aeMar\u00ae";
    expect(months.split("\u00ae").length == 3);
    expect(months.replaceAll("\u00ae", ".").equals("Jan.Feb.Mar."));

    // Java 8 changed the semantics of String.split relative to
    // previous versions, therefore we accept multiple possible
    // results:
    expect(arraysEqual
           ("xyz".split("",  0), new String[] { "", "x", "y", "z" })
           || arraysEqual
           ("xyz".split("",  0), new String[] { "x", "y", "z" }));
    expect(arraysEqual
           ("xyz".split("",  1), new String[] { "xyz" }));
    expect(arraysEqual
           ("xyz".split("",  2), new String[] { "", "xyz" })
           || arraysEqual
           ("xyz".split("",  2), new String[] { "x", "yz" }));
    expect(arraysEqual
           ("xyz".split("",  3), new String[] { "", "x", "yz" })
           || arraysEqual
           ("xyz".split("",  3), new String[] { "x", "y", "z" }));
    expect(arraysEqual
           ("xyz".split("",  4), new String[] { "", "x", "y", "z" })
           || arraysEqual
           ("xyz".split("",  4), new String[] { "x", "y", "z", "" }));
    expect(arraysEqual
           ("xyz".split("",  5), new String[] { "", "x", "y", "z", "" })
           || arraysEqual
           ("xyz".split("",  5), new String[] { "x", "y", "z", "" }));
    expect(arraysEqual
           ("xyz".split("",  6), new String[] { "", "x", "y", "z", "" })
           || arraysEqual
           ("xyz".split("",  6), new String[] { "x", "y", "z", "" }));
    expect(arraysEqual
           ("xyz".split("", -1), new String[] { "", "x", "y", "z", "" })
           || arraysEqual
           ("xyz".split("", -1), new String[] { "x", "y", "z", "" }));

    expect(arraysEqual("".split("xyz",  0), new String[] { "" }));
    expect(arraysEqual("".split("xyz",  1), new String[] { "" }));
    expect(arraysEqual("".split("xyz", -1), new String[] { "" }));

    expect(arraysEqual("".split("",  0), new String[] { "" }));
    expect(arraysEqual("".split("",  1), new String[] { "" }));
    expect(arraysEqual("".split("", -1), new String[] { "" }));

    expect("foo_foofoo__foo".replaceAll("_", "__")
           .equals("foo__foofoo____foo"));

    expect("foo_foofoo__foo".replaceFirst("_", "__")
           .equals("foo__foofoo__foo"));

    expect("stereomime".matches("stereomime"));
    expect(! "stereomime".matches("stereomim"));
    expect(! "stereomime".matches("tereomime"));
    expect(! "stereomime".matches("sterEomime"));

    StringBuilder sb = new StringBuilder();
    sb.append('$');
    sb.append('2');
    expect(sb.substring(1).equals("2"));

    expect(Character.forDigit(Character.digit('0', 10), 10) == '0');
    expect(Character.forDigit(Character.digit('9', 10), 10) == '9');
    expect(Character.forDigit(Character.digit('b', 16), 16) == 'b');
    expect(Character.forDigit(Character.digit('f', 16), 16) == 'f');
    expect(Character.forDigit(Character.digit('z', 36), 36) == 'z');

    testDecode(false);
    testDecode(true);

    expect
      (java.text.MessageFormat.format
       ("{0} enjoy {1} {2}.  do {4}?  {4} do?",
        "I", "grape", "nuts", "foobar",
        new Object() { public String toString() { return "you"; } })
       .equals("I enjoy grape nuts.  do you?  you do?"));

    { java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
      java.io.PrintStream pout = new java.io.PrintStream(bout, true, "UTF-8");
      String s = "I ♥ grape nuts";
      System.out.println(s);
      pout.println(s);

      expect
        (arraysEqual
         (bout.toByteArray(),
          (s + System.getProperty("line.separator")).getBytes("UTF-8")));

      expect
        (arraysEqual
         (bout.toByteArray(), append
          (new byte[] { 73, 32, -30, -103, -91, 32, 103, 114, 97, 112, 101,
                        32, 110, 117, 116, 115 },
            System.getProperty("line.separator").getBytes("UTF-8"))));
    }

    expect("abc".lastIndexOf('b', 100) == 1);

    testTrivialPattern();

    { String s = "hello, world!";
      java.nio.CharBuffer buffer = java.nio.CharBuffer.allocate(s.length());
      new java.io.InputStreamReader
        (new java.io.ByteArrayInputStream(s.getBytes("UTF-8"))).read(buffer);
      expect(s.equals(new String(buffer.array())));
    }
  }
}
