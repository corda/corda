package net.corda.testing.driver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

// Note, originallly (re)written in Java to access internal JDK APIs. Since it's no longer doing that, this can be converted back to Kotlin.
public class SharedMemoryIncremental extends PortAllocation {

    static private final int DEFAULT_START_PORT = 10_000;
    static private final int FIRST_EPHEMERAL_PORT = 30_000;

    private final int startPort;
    private final int endPort;

    private final MappedByteBuffer mb;

    private SharedMemoryIncremental(int startPort, int endPort) {
        this.startPort = startPort;
        this.endPort = endPort;
        try {
            Path file = Path.of(System.getProperty("user.home"), "corda-" + startPort + "-to-" + endPort + "-port-allocator.bin");
            mb = FileChannel.open(file, CREATE, READ, WRITE).map(MapMode.READ_WRITE, 0, Integer.SIZE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static SharedMemoryIncremental INSTANCE = new SharedMemoryIncremental(DEFAULT_START_PORT, FIRST_EPHEMERAL_PORT);

    @Override
    public int nextPort() {
        while (true) {
            int oldValue = mb.getInt(0);
            int newValue;
            if (oldValue + 1 >= endPort || oldValue < startPort) {
                newValue = startPort;
            } else {
                newValue = (oldValue + 1);
            }
            mb.putInt(0, newValue);
            if (isLocalPortAvailable(newValue)) {
                // Still possible for a race condition to occur here
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
