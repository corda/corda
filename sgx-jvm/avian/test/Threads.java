public class Threads implements Runnable {
  private static boolean success = false;

  private static void expect(boolean v) {
    if (! v) throw new RuntimeException();
  }

  public static void main(String[] args) throws Exception {
    Thread.currentThread().getThreadGroup()
      .uncaughtException(Thread.currentThread(), new Exception());

    { Threads test = new Threads();
      Thread thread = new Thread(test);

      synchronized (test) {
        thread.start();
        test.wait();
      }
    }

    { Thread thread = new Thread() {
        public void run() {
          while (true) {
            System.out.print(".");
            try {
              sleep(1000);
            } catch (Exception e) {
              System.out.println("thread interrupted? " + interrupted());
              break;
            }
          }
        }
      };
      thread.start();

      System.out.println("\nAbout to interrupt...");
      thread.interrupt();
      System.out.println("\nInterrupted!");
    }

    { Thread thread = new Thread() {
        @Override
        public void run() {
          // do nothing
        }
      };

      thread.start();
      thread.join();
    }

    System.out.println("finished; success? " + success);

    if (! success) {
      System.exit(-1);
    }
  }

  public void run() {
    int i = 0;
    try {
      expect(! Thread.holdsLock(this));
      synchronized (this) {
        expect(Thread.holdsLock(this));

        System.out.println("I'm running in a separate thread!");

        Thread.yield(); // just to prove Thread.yield exists and is callable

        final int arrayCount = 16;
        final int arraySize = 4;
        System.out.println("Allocating and discarding " + arrayCount +
                           " arrays of " + arraySize + "MB each");
        for (; i < arrayCount; ++i) {
          byte[] array = new byte[arraySize * 1024 * 1024];
        }

        long nap = 500;
        System.out.println("sleeping for " + nap + " milliseconds");
        Thread.sleep(nap);
        notifyAll();
      }
      success = true;
    } catch (Throwable e) {
      System.err.println("caught something in second thread after " + i +
                         " iterations");
      e.printStackTrace();
    } finally {
      synchronized (this) {
        notifyAll();
      }
    }
  }
}
