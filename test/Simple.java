public class Simple {
  private static int pow(int a, int b) {
    int c = 1;
    for (int i = 0; i < b; ++i) c *= a;
    return c;
  }

  public static void main(String[] args) {
    pow(2, 3);
  }
}
