public class StackOverflow {
  private static int add(int[] numbers, int offset, int length) {
    if (length == 0) {
      return 0;
    } else {
      return numbers[offset] + add(numbers, offset + 1, length - 1);
    }
  }
  private static int add(int ... numbers) {
    return add(numbers, 0, numbers.length);
  }

  private static int test1() {
    add(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    return test1() + 1;
  }

  private static int test2() {
    return test3() + 1;
  }

  private static int test3() {
    return test2() + 1;
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
