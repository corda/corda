public class StackOverflow {
  private static void test1() {
    test1();
  }

  private static void test2() {
    test3();
  }

  private static void test3() {
    test2();
  }

  public static void main(String[] args) {
    try {
      test1();
      throw new RuntimeException();
    } catch (StackOverflowError e) {
      e.printStackTrace();
    }

    try {
      test2();
      throw new RuntimeException();
    } catch (StackOverflowError e) {
      e.printStackTrace();
    }
  }
}
