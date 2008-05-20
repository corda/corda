public class Misc {
  private static int alpha;
  private static int beta;
  private static byte byte1, byte2, byte3;

  private int gamma;
  private int pajama;
  private boolean boolean1;
  private boolean boolean2;
  private long time;

  public Misc() {
    expect(! boolean1);
    expect(! boolean2);
    
    time = 0xffffffffffffffffL;
    
    expect(! boolean1);
    expect(! boolean2);
  }

  private String foo(String s) {
    return s;
  }

  public String bar(String s) {
    return s;
  }

  private static String baz(String s) {
    return s;
  }
  
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  private synchronized byte sync() {
    byte[] array = new byte[123];
    return array[42];
  }

  private static synchronized byte syncStatic(boolean throw_) {
    byte[] array = new byte[123];
    if (throw_) {
      throw new RuntimeException();
    } else {
      return array[42];
    }
  }

  public static void putInt(int val, byte[] dst, int offset) {
    System.out.println("put " + val);
    dst[offset]   = (byte)((val >> 24) & 0xff);
    dst[offset+1] = (byte)((val >> 16) & 0xff);
    dst[offset+2] = (byte)((val >>  8) & 0xff);
    dst[offset+3] = (byte)((val      ) & 0xff);
  }

  public static void putLong(long val, byte[] dst, int offset) {
    putInt((int)(val >> 32), dst, offset);
    putInt((int)val, dst, offset + 4);
  }

  public String toString() {
    return super.toString();
  }

  private static int zap() {
    return 42;
  }

  private static int zip() {
    return 5 + zap();
  }

  private static int zup() {
    return zap() + 5;
  }

  private static class Foo {
    public int a;
    public int b;
    public int c;
    public int[] array;
  }

  private static int bar(int a, int b, int c) {
    return a + b + c;
  }

  private static long roundUp(long a, long b) {
    a += b - 1L;
    return a - (a % b);
  }

  public static void main(String[] args) {
    expect(roundUp(156, 2) == 156);

    { Foo foo = new Foo();
      int x = foo.a + foo.b + foo.c;
      bar(foo.a, foo.b, foo.c);
    }

    { int get_buffer = 2144642881;
      int bits_left = 30;
      int l = 9;
      int code = (((get_buffer >> (bits_left -= (l)))) & ((1<<(l))-1));
      expect(code == 510);
    }

    { int width = 8;
      int height = 8;
      int depth = 24;
      int scanlinePad = 4;

      int bytesPerLine = (((width * depth + 7) / 8) + (scanlinePad - 1))
        / scanlinePad * scanlinePad;
      expect(bytesPerLine == 24);
    }

    { int a = -5;
      int b = 2;
      expect(a >> b == -5 >> 2);
      expect(a >>> b == -5 >>> 2);
      expect(a << b == -5 << 2);
      expect(a + b == -5 + 2);
      expect(a - b == -5 - 2);
      expect(a * b == -5 * 2);
      expect(a / b == -5 / 2);
      expect(a % b == -5 % 2);
      expect((a & b) == (-5 & 2));
      expect((a | b) == (-5 | 2));
      expect((a ^ b) == (-5 ^ 2));
      expect(-a == 5);
      expect(~a == ~-5);

      a = 5;
      b = 2;
      expect(a >> b == 5 >> 2);
      expect(a >>> b == 5 >>> 2);
      expect(a << b == 5 << 2);
      expect(a + b == 5 + 2);
      expect(a - b == 5 - 2);
      expect(a * b == 5 * 2);
      expect(a / b == 5 / 2);
      expect(a % b == 5 % 2);
      expect((a & b) == (5 & 2));
      expect((a | b) == (5 | 2));
      expect((a ^ b) == (5 ^ 2));
      expect(-a == -5);
      expect(~a == ~5);
    }

    { long a = -5;
      long b = 2;
      expect(a >> b == -5L >> 2);
      expect(a >>> b == -5L >>> 2);
      expect(a << b == -5L << 2);
      expect(a + b == -5L + 2L);
      expect(a - b == -5L - 2L);
      expect(a * b == -5L * 2L);
      expect(a / b == -5L / 2L);
      expect(a % b == -5L % 2L);
      expect((a & b) == (-5L & 2L));
      expect((a | b) == (-5L | 2L));
      expect((a ^ b) == (-5L ^ 2L));
      expect(-a == 5L);
      expect(~a == ~-5L);

      a = 5;
      b = 2;
      expect(a >> b == 5L >> 2);
      expect(a >>> b == 5L >>> 2);
      expect(a << b == 5L << 2);
      expect(a + b == 5L + 2L);
      expect(a - b == 5L - 2L);
      expect(a * b == 5L * 2L);
      expect(a / b == 5L / 2L);
      expect(a % b == 5L % 2L);
      expect((a & b) == (5L & 2L));
      expect((a | b) == (5L | 2L));
      expect((a ^ b) == (5L ^ 2L));
      expect(-a == -5L);
      expect(~a == ~5L);
    }

    byte2 = 0;
    expect(byte2 == 0);

    expect(Long.valueOf(231L) == 231L);

    { long x = 231;
      expect((x >> 32) == 0);
      expect((x >>> 32) == 0);
      expect((x << 32) == 992137445376L);

      int shift = 32;
      expect((x >> shift) == 0);
      expect((x >>> shift) == 0);
      expect((x << shift) == 992137445376L);

      long y = -231;
      expect((y >> 32) == 0xffffffffffffffffL);
      expect((y >>> 32) == 0xffffffffL);
    }

    { byte[] array = new byte[8];
      putLong(231, array, 0);
      expect((array[0] & 0xff) == 0);
      expect((array[1] & 0xff) == 0);
      expect((array[2] & 0xff) == 0);
      expect((array[3] & 0xff) == 0);
      expect((array[4] & 0xff) == 0);
      expect((array[5] & 0xff) == 0);
      expect((array[6] & 0xff) == 0);
      expect((array[7] & 0xff) == 231);
    }

    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(8);
    buffer.putLong(231);
    buffer.flip();
    expect(buffer.getLong() == 231);

    boolean v = Boolean.valueOf("true");

    ClassLoader.getSystemClassLoader().toString();

    { int a = 2;
      int b = 2;
      int c = a + b;
    }

    { Misc m = new Misc();
      m.toString();

      expect(m.time == 0xffffffffffffffffL);
      long t = m.time;
      expect(t == 0xffffffffffffffffL);

      String s = "hello";
      m.foo(s);
      m.bar(s);
      baz(s);

      m.sync();
      syncStatic(false);
      try {
        syncStatic(true);
      } catch (RuntimeException e) {
        e.printStackTrace();
      }

      int d = alpha;
      beta = 42;
      alpha = 43;
      int e = beta;
      int f = alpha;
      m.gamma = 44;

      expect(beta == 42);
      expect(alpha == 43);
      expect(m.gamma == 44);
    }

    expect(zip() == 47);
    expect(zup() == 47);

    { int[] array = new int[0];
      Exception exception = null;
      try {
        int x = array[0];
      } catch (ArrayIndexOutOfBoundsException e) {
        exception = e;
      }

      expect(exception != null);
    }

    { int[] array = new int[3];
      int i = 0;
      array[i++] = 1;
      array[i++] = 2;
      array[i++] = 3;

      expect(array[--i] == 3);
      expect(array[--i] == 2);
      expect(array[--i] == 1);
    }

    { Object[][] array = new Object[1][1];
      expect(array.length == 1);
      expect(array[0].length == 1);
    }

    {
      Object a = new Object();
      Object b = new Object();
      expect(a != b);

      Object c = a;
      Object d = b;
      expect(c != d);

      c = (c == a) ? b : a;
      d = (d == a) ? b : a;

      expect(c != d);
    }

    { Foo foo = new Foo();
      foo.array = new int[3];
      foo.a = (foo.a + 1) % foo.array.length;
    }
  }
}
