public class Busy {
  private static volatile int foo = 0;
  private static volatile boolean go;

  public static void main(String[] args) {
    final Object lock = new Object();

    synchronized (lock) {
      new Thread() {
        public void run() {
          while (foo < 100) {
            go = true;
          }
        }
      }.start();

      while (foo < 100) {
        while (! go) { }
        go = false;
        byte[] array = new byte[256 * 1024];
        ++ foo;
      }
    }
  }
}