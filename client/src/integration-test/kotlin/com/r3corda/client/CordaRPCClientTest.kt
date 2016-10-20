package com.r3corda.client

import com.r3corda.core.random63BitValue
import com.r3corda.node.driver.driver
import com.r3corda.node.services.config.configureTestSSL
import com.r3corda.node.services.messaging.ArtemisMessagingComponent.Companion.toHostAndPort
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class CordaRPCClientTest {

    private val validUsername = "user1"
    private val validPassword = "test"
    private val stopDriver = CountDownLatch(1)
    private var driverThread: Thread? = null
    private lateinit var client: CordaRPCClient

    @Before
    fun start() {
        val driverStarted = CountDownLatch(1)
        driverThread = thread {
            driver {
                val nodeInfo = startNode().get()
                client = CordaRPCClient(toHostAndPort(nodeInfo.address), configureTestSSL())
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
        client.start(validUsername, validPassword)
    }

    @Test
    fun `log in with unknown user`() {
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            client.start(random63BitValue().toString(), validPassword)
        }
    }

    @Test
    fun `log in with incorrect password`() {
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            client.start(validUsername, random63BitValue().toString())
        }
    }

 }