package net.corda.node.amqp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.time.Duration;

/**
 * This class provides a runnable that can be used to initialize a {@link NioSslServer} thread.
 * <p/>
 * Run starts the server, which will start listening to the configured IP address and port for
 * new SSL/TLS connections and serve the ones already connected to it.
 * <p/>
 * Also a stop method is provided in order to gracefully close the server and stop the thread.
 *
 * @author <a href="mailto:alex.a.karnezis@gmail.com">Alex Karnezis</a>
 */
public class ServerThread implements AutoCloseable {

    private final static Logger log = LoggerFactory.getLogger(ServerThread.class);

    private static final long JOIN_TIMEOUT_MS = 10000;

    private final NioSslServer server;

    private Thread serverThread;

    public ServerThread(KeyManagerFactory keyManagerFactory, TrustManagerFactory trustManagerFactory, int port) throws Exception {
        this(keyManagerFactory, trustManagerFactory, port, null);
    }

    public ServerThread(KeyManagerFactory keyManagerFactory, TrustManagerFactory trustManagerFactory, int port, @Nullable Duration handshakeDelay) throws Exception {
        server = new NioSslServer(keyManagerFactory, trustManagerFactory, "localhost", port, handshakeDelay);
    }

    public void start() {

        Runnable serverRunnable = () -> {
            try {
                server.start();
            } catch (Exception e) {
                log.error("Exception starting server", e);
            }
        };

        serverThread = new Thread(serverRunnable, this.getClass().getSimpleName() + "-ServerThread");
        serverThread.start();
    }

    /**
     * Should be called in order to gracefully stop the server.
     */
    public void stop() throws InterruptedException {
        server.stop();
        serverThread.join(JOIN_TIMEOUT_MS);
    }

    public boolean isActive() {
        return server.isActive();
    }

    @Override
    public void close() throws Exception {
        stop();
    }
}