public class Misc {
  private static int alpha;
  private static int beta;

  private String foo(String s) {
    return s;
  }

  public String bar(String s) {
    return s;
  }

  private static String baz(String s) {
    return s;
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

    int d = alpha;
    beta = 42;
    alpha = 43;
    int e = beta;
    int f = alpha;
  }
}
