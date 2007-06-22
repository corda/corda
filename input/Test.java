public class Test {

  public static void main(String[] args) {
    for (int i = 0; i < 1024; ++i) {
      byte[] a = new byte[4 * 1024];
    }

    for (int i = 0; i < 8; ++i) {
      Object[] array = new Object[32];
      for (int j = 0; j < 32; ++j) {
        array[j] = new byte[32 * 1024];
      }
    }

    for (int i = 0; i < 8; ++i) {
      byte[] a = new byte[16 * 1024 * 1024];
    }
  }

}
