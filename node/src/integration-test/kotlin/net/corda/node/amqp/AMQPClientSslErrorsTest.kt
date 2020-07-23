package net.corda.node.amqp

import net.corda.testing.driver.internal.incrementalPortAllocation
import org.junit.Test

class AMQPClientSslErrorsTest {

    private val portAllocation = incrementalPortAllocation()

    @Test(timeout = 300_000)
    fun trivialClientServerExchange() {
        val serverPort = portAllocation.nextPort()
        val serverRunnable = ServerRunnable(serverPort)
        val serverThread = Thread(serverRunnable)
        serverThread.start()

        // System.setProperty("javax.net.debug", "all");

        val client = NioSslClient("TLSv1.2", "localhost", serverPort)
        client.connect()
        client.write("Hello! I am a client!")
        client.read()
        client.shutdown()

        val client2 = NioSslClient("TLSv1.2", "localhost", serverPort)
        val client3 = NioSslClient("TLSv1.2", "localhost", serverPort)
        val client4 = NioSslClient("TLSv1.2", "localhost", serverPort)

        client2.connect()
        client2.write("Hello! I am another client!")
        client2.read()
        client2.shutdown()

        client3.connect()
        client4.connect()
        client3.write("Hello from client3!!!")
        client4.write("Hello from client4!!!")
        client3.read()
        client4.read()
        client3.shutdown()
        client4.shutdown()

        serverRunnable.stop()
        serverThread.join()
    }
}