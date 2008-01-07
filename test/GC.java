public class GC {

  private static void small() {
    for (int i = 0; i < 1024; ++i) {
      byte[] a = new byte[4 * 1024];
    }
  }

  private static void medium() {
    for (int i = 0; i < 8; ++i) {
      Object[] array = new Object[32];
      for (int j = 0; j < 32; ++j) {
        array[j] = new byte[32 * 1024];
      }
    }
  }

  private static void large() {
    for (int i = 0; i < 8; ++i) {
      byte[] a = new byte[16 * 1024 * 1024];
    }

    for (int i = 0; i < 8; ++i) {
      byte[] a = new byte[16 * 1024 * 1024];
      for (int j = 0; j < 32; ++j) {
        byte[] b = new byte[32 * 1024];
      }
    }
  }

  private static void stackMap1(boolean predicate) {
    if (predicate) {
      Object a = null;
    }

    System.gc();
  }

  private static void stackMap2(boolean predicate) {
    if (predicate) {
      int a = 42;
    } else {
      Object a = null;
    }

    System.gc();
  }

  private static void stackMap3(boolean predicate) {
    int i = 2;
    if (predicate) {
      Object a = null;
    } else {
      Object a = null;
    }

    do {
      System.gc();
      int a = 42;
      -- i;
    } while (i >= 0);
  }

  public static void main(String[] args) {
    Object[] array = new Object[1024 * 1024];
    array[0] = new Object();

    small();

    array[1] = new Object();

    medium();

    array[2] = new Object();

    large();

    array[0].toString();
    array[1].toString();
    array[2].toString();

    stackMap1(true);
    stackMap1(false);

    stackMap2(true);
    stackMap2(false);

    stackMap3(true);
    stackMap3(false);
  }

}
