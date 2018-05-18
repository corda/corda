package com.r3.corda.networkmanage

import net.corda.core.toFuture
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2P_PREFIX
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.PEER_USER
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.protonwrapper.messages.MessageStatus
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPClient
import net.corda.nodeapi.internal.protonwrapper.netty.AMQPServer
import net.corda.testing.core.*
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import java.nio.file.Paths
import java.security.Security
import kotlin.test.assertEquals

/**
 * This test is to perform manual testing of the SSL connection using local key stores. It aims to assess the
 * correct behaviour of the SSL connection between 2 nodes with respect to the CRL validation.
 * In order to debug the certificate path validation please use the following JVM parameters when running the test:
 * -Djavax.net.debug=ssl,handshake -Djava.security.debug=certpath
 */
@Ignore
class CertificateRevocationListNodeTests {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private val serverPort = freePort()

    private val serverSslKeyStore: Path = Paths.get("/certificatesServer/sslkeystore.jks")
    private val clientSslKeyStore: Path = Paths.get("/certificatesClient/sslkeystore.jks")
    private val serverTrustStore: Path = Paths.get("/certificatesServer/truststore.jks")
    private val clientTrustStore: Path = Paths.get("/certificatesClient/truststore.jks")

    @Before
    fun setUp() {
        Security.addProvider(BouncyCastleProvider())
    }

    @Test
    fun `Simple AMPQ Client to Server connection works`() {
        val amqpServer = createServer(serverPort)
        amqpServer.use {
            amqpServer.start()
            val receiveSubs = amqpServer.onReceive.subscribe {
                assertEquals(BOB_NAME.toString(), it.sourceLegalName)
                assertEquals(P2P_PREFIX + "Test", it.topic)
                assertEquals("Test", String(it.payload))
                it.complete(true)
            }
            val amqpClient = createClient(serverPort)
            amqpClient.use {
                val serverConnected = amqpServer.onConnection.toFuture()
                val clientConnected = amqpClient.onConnection.toFuture()
                amqpClient.start()
                val serverConnect = serverConnected.get()
                assertEquals(true, serverConnect.connected)
                val clientConnect = clientConnected.get()
                assertEquals(true, clientConnect.connected)
                val msg = amqpClient.createMessage("Test".toByteArray(),
                        P2P_PREFIX + "Test",
                        ALICE_NAME.toString(),
                        emptyMap())
                amqpClient.write(msg)
                assertEquals(MessageStatus.Acknowledged, msg.onComplete.get())
                receiveSubs.unsubscribe()
            }
        }
    }

    private fun createClient(targetPort: Int): AMQPClient {
        val tS = X509KeyStore.fromFile(clientTrustStore, "trustpass").internal
        val sslS = X509KeyStore.fromFile(clientSslKeyStore, "cordacadevpass").internal
        return AMQPClient(
                listOf(NetworkHostAndPort("localhost", targetPort)),
                setOf(ALICE_NAME, CHARLIE_NAME),
                PEER_USER,
                PEER_USER,
                sslS,
                "cordacadevpass",
                tS,
                false,
                MAX_MESSAGE_SIZE)
    }

    private fun createServer(port: Int): AMQPServer {
        val tS = X509KeyStore.fromFile(serverTrustStore, "trustpass").internal
        val sslS = X509KeyStore.fromFile(serverSslKeyStore, "cordacadevpass").internal
        return AMQPServer(
                "0.0.0.0",
                port,
                PEER_USER,
                PEER_USER,
                sslS,
                "cordacadevpass",
                tS,
                false,
                MAX_MESSAGE_SIZE)
    }
}
