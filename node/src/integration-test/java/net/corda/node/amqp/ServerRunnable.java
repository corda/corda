package net.corda.node.amqp;

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

    NioSslServer server;

    @Override
    public void run() {
        try {
            server = new NioSslServer("TLSv1.2", "localhost", 9222);
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Should be called in order to gracefully stop the server.
     */
    public void stop() {
        server.stop();
    }
}
