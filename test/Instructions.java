public class Instructions {
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
    int a = 2;
    int b = 2;
    int c = a + b;

    Instructions i = new Instructions();
    i.foo("hello");
    i.bar("hello");
    baz("hello");

    int d = alpha;
    beta = 42;
    alpha = 43;
    int e = beta;
    int f = alpha;
  }
}
