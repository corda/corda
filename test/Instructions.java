public class Instructions {
  private String foo(String s) {
    return s;
  }

  public String bar(String s) {
    return s;
  }

  public static void main(String[] args) {
    int a = 2;
    int b = 2;
    int c = a + b;

    Instructions i = new Instructions();
    i.foo("hello");
    i.bar("hello");
  }
}
