public class Exceptions {
  static class ThrowError {
    static {
      if (true) throw new AssertionError();
    }

    static void foo() { }
  }

  static class ThrowException {
    static {
      if (true) throw new RuntimeException();
    }

    static void foo() { }
  }

  private static void evenMoreDangerous() {
    throw new RuntimeException("chaos! panic! overwhelming anxiety!");
  }

  private static void moreDangerous() {
    evenMoreDangerous();
  }

  private static void dangerous() {
    moreDangerous();
  }

  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  public static void main(String[] args) {
    boolean threw = false;
    try {
      dangerous();
    } catch (Exception e) {
      e.printStackTrace();
      threw = true;
    }
    expect(threw);
    threw = false;

    try {
      ThrowError.foo();
    } catch (AssertionError e) {
      e.printStackTrace();
      threw = true;
    }
    expect(threw);
    threw = false;

    try {
      ThrowException.foo();
    } catch (ExceptionInInitializerError e) {
      e.printStackTrace();
      threw = true;
    }
    expect(threw);
  }

}
