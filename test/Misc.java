public class Misc {
  private static int alpha;
  private static int beta;
  private int gamma;

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

  public static void main(String[] args) {
    boolean v = Boolean.valueOf("true");

    ClassLoader.getSystemClassLoader().toString();

    int a = 2;
    int b = 2;
    int c = a + b;

    Misc m = new Misc();
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
}
