package net.corda.client

import net.corda.core.contracts.DOLLARS
import net.corda.core.getOrThrow
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.random63BitValue
import net.corda.core.serialization.OpaqueBytes
import net.corda.flows.CashCommand
import net.corda.flows.CashFlow
import net.corda.node.driver.NodeInfoAndConfig
import net.corda.node.driver.driver
import net.corda.node.services.User
import net.corda.node.services.config.configureTestSSL
import net.corda.node.services.messaging.ArtemisMessagingComponent.Companion.toHostAndPort
import net.corda.node.services.messaging.CordaRPCClient
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.ValidatingNotaryService
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class CordaRPCClientTest {

    private val rpcUser = User("user1", "test", permissions = setOf(startFlowPermission<CashFlow>()))
    private val stopDriver = CountDownLatch(1)
    private var driverThread: Thread? = null
    private lateinit var client: CordaRPCClient
    private lateinit var driverInfo: NodeInfoAndConfig

    @Before
    fun start() {
        val driverStarted = CountDownLatch(1)
        driverThread = thread {
            driver(isDebug = true) {
                driverInfo = startNode(rpcUsers = listOf(rpcUser), advertisedServices = setOf(ServiceInfo(ValidatingNotaryService.type))).getOrThrow()
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

    @Test
    fun `indefinite block bug`() {
        println("Starting client")
        client.start(rpcUser.username, rpcUser.password)
        println("Creating proxy")
        val proxy = client.proxy()
        println("Starting flow")
        val flowHandle = proxy.startFlow(::CashFlow, CashCommand.IssueCash(20.DOLLARS, OpaqueBytes.of(0), driverInfo.nodeInfo.legalIdentity, driverInfo.nodeInfo.legalIdentity))
        println("Started flow, waiting on result")
        flowHandle.progress.subscribe {
            println("PROGRESS $it")
        }
        println("Result: ${flowHandle.returnValue.toBlocking().first()}")
    }
}
