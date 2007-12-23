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

  public static void main(String[] args) {
    small();
    medium();
    large();
  }

}
