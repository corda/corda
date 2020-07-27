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
public class ServerRunnable implements Runnable {

    private final static Logger log = LoggerFactory.getLogger(ServerRunnable.class);

    private final KeyManagerFactory keyManagerFactory;
    private final TrustManagerFactory trustManagerFactory;
    private final int port;
    @Nullable
    private final Duration handshakeDelay;

    NioSslServer server;

    public ServerRunnable(KeyManagerFactory keyManagerFactory, TrustManagerFactory trustManagerFactory, int port) {
        this(keyManagerFactory, trustManagerFactory, port, null);
    }

    public ServerRunnable(KeyManagerFactory keyManagerFactory, TrustManagerFactory trustManagerFactory, int port, @Nullable Duration handshakeDelay) {
        this.keyManagerFactory = keyManagerFactory;
        this.trustManagerFactory = trustManagerFactory;
        this.port = port;
        this.handshakeDelay = handshakeDelay;
    }

    @Override
    public void run() {
        try {
            server = new NioSslServer(keyManagerFactory, trustManagerFactory, "localhost", port, handshakeDelay);
            server.start();
        } catch (Exception e) {
            log.error("Exception starting server", e);
        }
    }

    /**
     * Should be called in order to gracefully stop the server.
     */
    public void stop() {
        server.stop();
    }

    public boolean isActive() {
        return server.isActive();
    }
}