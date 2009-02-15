public class Subroutine {
  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  // This test is intended to cover the jsr and ret instructions.
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

  public static void main(String[] args) {
    test(false, false);
    test(false, true);
    test(true, false);
  }

  private static class DummyException extends RuntimeException { }
}
