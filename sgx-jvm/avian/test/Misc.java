import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

public class Misc {
  private static class μClass {
    public int μField;

    public void μMethod(int i) {
      μField = i;
    }
  }

  private interface Bar {
    public int baz();
  }

  private static abstract class Bim implements Bar { }

  private static class Baz extends Bim {
    public int baz() {
      return 42;
    }
  }

  private static class Static {
    static {
      staticRan = true;
    }

    public static void run() { }
  }

  private static boolean staticRan;

  private static int alpha;
  private static int beta;
  private static byte byte1, byte2, byte3;

  private static volatile int volatileStatic;

  private static volatile long volatileStaticLong;

  private final int NonStaticConstant = 42;

  private int gamma;
  private int pajama;
  private boolean boolean1;
  private boolean boolean2;
  private long time;
  private volatile int volatileMember;

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

  private static class Zam {
    public void bim() { }
  }

  private static class Zim {
    public Object zum() {
      return null;
    }
  }

  private static Zim zim = new Zim();

  private static void zam() {
    Zam z;
    while ((z = (Zam) zim.zum()) != null) {
      z.bim();
    }
  }

  private static synchronized void testStaticNotify() {
    Misc.class.notify();
  }
  
  public static void main(String[] args) throws Exception {
    zam();

    Bim bim = new Baz();
    expect(bim.baz() == 42);

    expect(queryDefault(new Object()) != null);

    { Foo foo = new Foo();
      int x = foo.a + foo.b + foo.c;
      bar(foo.a, foo.b, foo.c);
    }

    byte2 = 0;
    expect(byte2 == 0);

    boolean v = Boolean.valueOf("true");

    ClassLoader.getSystemClassLoader().toString();

    testStaticNotify();
    
    { Misc m = new Misc();
      m.toString();

      expect(m.NonStaticConstant == 42);

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
      volatileStatic = 55;
      volatileStaticLong = 9L;
      int e = beta;
      int f = alpha;
      m.volatileMember = 23;
      m.gamma = 44;
      m.volatileMember = 27;

      expect(beta == 42);
      expect(alpha == 43);
      expect(m.gamma == 44);
      expect(volatileStatic == 55);
      expect(volatileStaticLong == 9L);
      expect(m.volatileMember == 27);
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

    System.out.println('x');
    System.out.println(true);
    System.out.println(42);
    System.out.println(123456789012345L);
    System.out.println(75.62);
    System.out.println(75.62d);
    System.out.println(new char[] { 'h', 'i' });

    expect(! (((Object) new int[0]) instanceof Object[]));

    { μClass μInstance = new μClass();
      μInstance.μMethod(8933);
      expect(μInstance.μField == 8933);
    }

    expect(new int[0] instanceof Cloneable);
    expect(new int[0] instanceof java.io.Serializable);

    expect(new Object[0] instanceof Cloneable);
    expect(new Object[0] instanceof java.io.Serializable);

    expect((Baz.class.getModifiers() & java.lang.reflect.Modifier.STATIC)
           != 0);

    expect((Protected.class.getModifiers() & java.lang.reflect.Modifier.PUBLIC)
           == 0);

    try {
      int count = 0;
      boolean test = false, extraDir = false;
      ClassLoader loader = Misc.class.getClassLoader();
      Enumeration<URL> resources = loader.getResources("multi-classpath-test.txt");
      while (resources.hasMoreElements()) {
        ++count;
        String url = resources.nextElement().toString();
        if (url.contains("extra-dir")) {
          extraDir = true;
        } else if (url.contains("test")) {
          test = true;
        }
      }
      // This test is only relevant if multi-classpath-test.txt
      // actually exists in somewhere under the classpath from which
      // Misc.class was loaded.  Since we run this test from an
      // AOT-compiled boot image as well as straight from the
      // filesystem, and the boot image does not contain
      // multi-classpath-test.txt, we'll skip the test if it's not
      // present.
      if (count != 0) {
        expect(count == 2);
        expect(test);
        expect(extraDir);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    expect(! staticRan);
    Static.run();
    expect(staticRan);

    expect(System.getProperty("java.class.path").equals
           (System.getProperties().getProperty("java.class.path")));

    expect(System.getProperty("path.separator").equals
           (System.getProperties().getProperty("path.separator")));

    expect(System.getProperty("user.dir").equals
           (System.getProperties().getProperty("user.dir")));

    expect(System.getProperty("java.io.tmpdir").equals
           (System.getProperties().getProperty("java.io.tmpdir")));

    System.setProperty("buzzy.buzzy.bim.bam", "dippy dopey flim flam");

    expect(System.getProperty("buzzy.buzzy.bim.bam").equals
           (System.getProperties().getProperty("buzzy.buzzy.bim.bam")));

    expect(System.getProperty("buzzy.buzzy.bim.bam").equals
           ("dippy dopey flim flam"));

    System.getProperties().put("buzzy.buzzy.bim.bam", "yippy yappy yin yang");

    expect(System.getProperty("buzzy.buzzy.bim.bam").equals
           (System.getProperties().getProperty("buzzy.buzzy.bim.bam")));

    expect(System.getProperty("buzzy.buzzy.bim.bam").equals
           ("yippy yappy yin yang"));

    // just test that it's there; don't care what it returns:
    Runtime.getRuntime().totalMemory();
    Runtime.getRuntime().maxMemory();
  }

  protected class Protected { }
}
