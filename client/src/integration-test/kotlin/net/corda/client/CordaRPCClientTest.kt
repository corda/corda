package net.corda.client

import net.corda.core.random63BitValue
import net.corda.node.driver.driver
import net.corda.node.services.User
import net.corda.node.services.config.configureTestSSL
import net.corda.node.services.messaging.ArtemisMessagingComponent.Companion.toHostAndPort
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class CordaRPCClientTest {

    private val rpcUser = User("user1", "test", permissions = emptySet())
    private val stopDriver = CountDownLatch(1)
    private var driverThread: Thread? = null
    private lateinit var client: CordaRPCClient

    @Before
    fun start() {
        val driverStarted = CountDownLatch(1)
        driverThread = thread {
            driver {
                val driverInfo = startNode(rpcUsers = listOf(rpcUser)).get()
                client = CordaRPCClient(toHostAndPort(driverInfo.nodeInfo.address), configureTestSSL())
                driverStarted.countDown()
                stopDriver.await()
            }
        }
        driverStarted.await()
    }

    @After
    fun stop() {
        stopDriver.countDown()
        driverThread?.join()
    }

    @Test
    fun `log in with valid username and password`() {
        client.start(rpcUser.username, rpcUser.password)
    }

    @Test
    fun `log in with unknown user`() {
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            client.start(random63BitValue().toString(), rpcUser.password)
        }
    }

    @Test
    fun `log in with incorrect password`() {
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            client.start(rpcUser.username, random63BitValue().toString())
        }
    }

 }
