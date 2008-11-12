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
  private static void test(boolean throw_) {
    int x = 42;
    int y = 99;
    int a = 0;
    try {
      try {
        int z = x + y;
        if (throw_) throw new DummyException();
        Integer.valueOf(z).toString();
      } finally {
        a = x + y;
      }
      expect(a == x + y);
    } catch (DummyException ignored) { }
  }

  public static void main(String[] args) {
    test(false);
    test(true);
  }

  private static class DummyException extends RuntimeException { }
}
