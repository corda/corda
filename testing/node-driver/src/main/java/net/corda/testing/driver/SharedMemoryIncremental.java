package net.corda.testing.driver;

import sun.misc.Unsafe;
import sun.nio.ch.DirectBuffer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * JDK11 upgrade: rewritten in Java to gain access to private internal JDK classes via module directives (not available to Kotlin compiler):
 * import sun.misc.Unsafe;
 * import sun.nio.ch.DirectBuffer;
 */
class SharedMemoryIncremental extends PortAllocation {

    static private final int DEFAULT_START_PORT = 10_000;
    static private final int FIRST_EPHEMERAL_PORT = 30_000;

    private int startPort;
    private int endPort;

    private SharedMemoryIncremental(int startPort, int endPort) {
        this.startPort = startPort;
        this.endPort = endPort;
        try {
            mb = backingFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, 16);
            startingAddress = ((DirectBuffer) mb).address();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File file = new File(System.getProperty("user.home"), "corda-" + startPort + "-to-" + endPort + "-port-allocator.bin");
    private RandomAccessFile backingFile;
    {
        try {
            backingFile = new RandomAccessFile(file, "rw");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
    private MappedByteBuffer mb;
    private Long startingAddress;

    public static SharedMemoryIncremental INSTANCE = new SharedMemoryIncremental(DEFAULT_START_PORT, FIRST_EPHEMERAL_PORT);

    static private Unsafe UNSAFE = getUnsafe();
    static private Unsafe getUnsafe() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe) f.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public int nextPort() {
        Long oldValue;
        Long newValue;
        do {
            oldValue = UNSAFE.getLongVolatile(null, startingAddress);
            if (oldValue + 1 >= endPort || oldValue < startPort) {
                newValue = Long.valueOf(startPort);
            } else {
                newValue = (oldValue + 1);
            }
        } while (!UNSAFE.compareAndSwapLong(null, startingAddress, oldValue, newValue));

        return newValue.intValue();
    }
}
