public class TestThreads implements Runnable {

  public static void main(String[] args) {
    TestThreads test = new TestThreads();
    Thread thread = new Thread(test);

    try {
      synchronized (test) {
        thread.start();
        test.wait();
      }
    } catch (Throwable e) {
      e.printStackTrace();
    }

    System.out.println("finished");
  }

  public void run() {
    synchronized (this) {
      int i = 0;
      try {
        System.out.println("I'm running in a seperate thread!");

        final int arrayCount = 64;
        final int arraySize = 4;
        System.out.println("Allocating and discarding " + arrayCount +
                           " arrays of " + arraySize + "MB each");
        for (; i < arrayCount; ++i) {
          byte[] array = new byte[arraySize * 1024 * 1024];
        }

        long nap = 5;
        System.out.println("sleeping for " + nap + " seconds");
        Thread.sleep(nap * 1000);
      } catch (Throwable e) {
        System.err.println("caught something in second thread after " + i +
                           " iterations");
        e.printStackTrace();
      } finally {
        notifyAll();
      }
    }
  }
}
