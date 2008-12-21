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

  private static Object gimmeNull() {
    return null;
  }

  private static Object queryDefault(Object default_) {
    Object o = gimmeNull();
    return (o == null ? default_ : o);
  }

  public static void main(String[] args) {
    expect(queryDefault(new Object()) != null);

    { Foo foo = new Foo();
      int x = foo.a + foo.b + foo.c;
      bar(foo.a, foo.b, foo.c);
    }

    byte2 = 0;
    expect(byte2 == 0);

    boolean v = Boolean.valueOf("true");

    ClassLoader.getSystemClassLoader().toString();

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

    { boolean foo = false;
      boolean iconic = false;
      do {
        zap();
        iconic = foo ? true : false;
      } while (foo);
      zap();
    }

    { int x = 0;
      if (x == 0) {
        x = 1;
        do {
          int y = x;
          x = 1;
        } while (x != 1);
      }
    }
  }
}
