/**
 * Demonstrates slow multithreaded memory access.
 */
public class MemoryRamp implements Runnable {

    private static final int ARRAY_SIZE = 10 * 1024 * 1024; //10 MB in byte
    private static final boolean ACCESS_ARRAY = true;

    @Override
    public void run() {
        mem();
    }

    public static void main(String[] args) {
        // get timing for single thread
        long singleTime = mem();
        System.out.println("Single thread: " + singleTime + "ms");

        // run the same method with different thread numbers
        for (int threadCount : new int[] {2, 3, 4}) {
            long time = memMulti(threadCount);
            double timeFactor = 1.0 * singleTime * threadCount / time;
            System.out.println(threadCount + " threads: " + time + "ms (" + timeFactor + "x)");
        }
    }

    /**
     * Creates and accesses a ARRAY_SIZE big byte[].
     * @return time to create and access array in milliseconds
     */
    private static long mem() {
        final byte[] array = new byte[ARRAY_SIZE];
        if (ACCESS_ARRAY) {
            for (int i = 0; i < array.length; i++) {
                //array[i] = (byte) 170; //write
                byte x = array[i]; //read
            }
        }
        return 0;
    }

    /**
     * Starts multiple threads and runs mem() in each one.
     * @return total time for all threads
     */
    private static long memMulti(int numOfThreads) {
        Thread[] threads = new Thread[numOfThreads];

        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(new MemoryRamp(), "mem-");
            threads[i].start();
        }

        try {
            for (Thread thread : threads) {
                thread.join();
            }
        }
        catch (InterruptedException iex) {
            throw new RuntimeException(iex);
        }
        return 0;
    }
}
