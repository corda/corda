package net.corda.testing.driver;

import jdk.incubator.foreign.MemoryHandles;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.invoke.VarHandle;
import java.net.ServerSocket;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

// This was originally (re)written in Java to access internal JDK APIs. Since it's no longer doing that, this can be converted back to Kotlin.
public class SharedMemoryIncremental extends PortAllocation {
    private static final int DEFAULT_START_PORT = 10_000;
    private static final int FIRST_EPHEMERAL_PORT = 30_000;

    private final int startPort;
    private final int endPort;

    private final MemorySegment memorySegment;
    private final VarHandle intHandle;
    private final MappedByteBuffer unsafeBuffer;

    private SharedMemoryIncremental(int startPort, int endPort) {
        this.startPort = startPort;
        this.endPort = endPort;
        Path file = Path.of(System.getProperty("user.home"), "corda-" + startPort + "-to-" + endPort + "-port-allocator.bin");
        try {
            try {
                Files.createFile(file);
            } catch (FileAlreadyExistsException ignored) {}
            if (isFfmAvailable()) {
                memorySegment = MemorySegment.mapFile(file, 0, Integer.SIZE, MapMode.READ_WRITE, ResourceScope.globalScope());
                intHandle = MemoryHandles.varHandle(int.class, ByteOrder.nativeOrder());
                unsafeBuffer = null;
            } else {
                LoggerFactory.getLogger(getClass()).warn("Using unsafe port allocator which may lead to the same port being allocated " +
                        "twice. Consider adding --add-modules=jdk.incubator.foreign to the test JVM.");
                memorySegment = null;
                intHandle = null;
                unsafeBuffer = FileChannel.open(file, READ, WRITE).map(MapMode.READ_WRITE, 0, Integer.SIZE);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isFfmAvailable() {
        try {
            Class.forName("jdk.incubator.foreign.MemorySegment");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static SharedMemoryIncremental INSTANCE = new SharedMemoryIncremental(DEFAULT_START_PORT, FIRST_EPHEMERAL_PORT);

    @Override
    public int nextPort() {
        while (true) {
            int oldValue;
            if (intHandle != null) {
                oldValue = (int) intHandle.getVolatile(memorySegment, 0L);
            } else {
                oldValue = unsafeBuffer.getInt(0);
            }
            int newValue;
            if (oldValue + 1 >= endPort || oldValue < startPort) {
                newValue = startPort;
            } else {
                newValue = (oldValue + 1);
            }
            if (intHandle != null) {
                if (!intHandle.compareAndSet(memorySegment, 0L, oldValue, newValue)) {
                    continue;
                }
            } else {
                unsafeBuffer.putInt(0, newValue);
            }
            if (isLocalPortAvailable(newValue)) {
                return newValue;
            }
        }
    }

    private boolean isLocalPortAvailable(int portToTest) {
        try (ServerSocket ignored = new ServerSocket(portToTest)) {
            return true;
        } catch (IOException e) {
            // Don't catch anything other than IOException here in case we
            // accidentally create an infinite loop. For example, installing
            // a SecurityManager could throw AccessControlException.
            return false;
        }
    }
}
