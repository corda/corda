package net.corda.testing.driver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.net.ServerSocket;
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
    private final VarHandle intHandle = ValueLayout.JAVA_INT.varHandle();

    private SharedMemoryIncremental(int startPort, int endPort) {
        this.startPort = startPort;
        this.endPort = endPort;
        Path file = Path.of(System.getProperty("user.home"), "corda-" + startPort + "-to-" + endPort + "-port-allocator.bin");
        try {
            try {
                Files.createFile(file);
            } catch (FileAlreadyExistsException ignored) {}
            // The mapping remains valid after the channel is closed, for the lifetime of the JVM.
            try (FileChannel channel = FileChannel.open(file, READ, WRITE)) {
                memorySegment = channel.map(MapMode.READ_WRITE, 0, Integer.SIZE, Arena.global());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static SharedMemoryIncremental INSTANCE = new SharedMemoryIncremental(DEFAULT_START_PORT, FIRST_EPHEMERAL_PORT);

    @Override
    public int nextPort() {
        while (true) {
            int oldValue = (int) intHandle.getVolatile(memorySegment, 0L);
            int newValue;
            if (oldValue + 1 >= endPort || oldValue < startPort) {
                newValue = startPort;
            } else {
                newValue = (oldValue + 1);
            }
            if (!intHandle.compareAndSet(memorySegment, 0L, oldValue, newValue)) {
                continue;
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
