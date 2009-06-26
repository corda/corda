public class Subroutine {
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  // These tests are intended to cover the jsr and ret instructions.
  // However, recent Sun javac versions avoid generating these
  // instructions by default, so we must compile this class using
  // -source 1.2 -target 1.1 -XDjsrlimit=0.
  //
  // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4381996
  //

  private static void test(boolean throw_, boolean predicate) {
    int x = 42;
    int y = 99;
    int a = 0;
    try {
      try {
        int z = x + y;
        if (throw_) throw new DummyException();
        if (predicate) {
          return;
        }
        Integer.valueOf(z).toString();
      } finally {
        a = x + y;
        System.gc();
      }
      expect(a == x + y);
    } catch (DummyException e) {
      e.printStackTrace();
    }
  }

  private static Object test2(int path) {
    try {
      try {
        switch (path) {
        case 1:
          return new Object();

        case 2: {
          int a = 42;
          return Integer.valueOf(a);
        }

        case 3:
          throw new DummyException();
        }
      } finally {
        System.gc();
      }
      return null;
    } catch (DummyException e) {
      e.printStackTrace();
      return null;
    }
  }

  public static void main(String[] args) {
    test(false, false);
    test(false, true);
    test(true, false);

    String.valueOf(test2(1));
    String.valueOf(test2(2));
    String.valueOf(test2(3));
  }

  private static class DummyException extends RuntimeException { }
}
