package net.corda.node.messaging

import net.corda.core.messaging.RPCOps
import net.corda.core.serialization.SerializedBytes
import net.corda.core.utilities.LogHelper
import net.corda.node.services.RPCUserService
import net.corda.node.services.User
import net.corda.node.services.messaging.*
import net.corda.node.utilities.AffinityExecutor
import org.apache.activemq.artemis.api.core.Message
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.TransportConfiguration
import org.apache.activemq.artemis.api.core.client.ActiveMQClient
import org.apache.activemq.artemis.api.core.client.ClientMessage
import org.apache.activemq.artemis.api.core.client.ClientProducer
import org.apache.activemq.artemis.api.core.client.ClientSession
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.test.*

class RPCPermissionsTest {
    companion object {
        const val DUMMY_FLOW = "StartFlow.net.corda.flows.DummyFlow"
        const val OTHER_FLOW = "StartFlow.net.corda.flows.OtherFlow"
        const val ALL_ALLOWED = "ALL"
    }

    lateinit var artemis: EmbeddedActiveMQ
    lateinit var serverSession: ClientSession
    lateinit var clientSession: ClientSession
    lateinit var producer: ClientProducer
    lateinit var serverThread: AffinityExecutor.ServiceAffinityExecutor

    @Before
    fun setup() {
        // Set up an in-memory Artemis with an RPC requests queue.
        artemis = EmbeddedActiveMQ()
        artemis.setConfiguration(ConfigurationImpl().apply {
            acceptorConfigurations = setOf(TransportConfiguration(InVMAcceptorFactory::class.java.name))
            isSecurityEnabled = false
            isPersistenceEnabled = false
        })
        artemis.start()

        val serverLocator = ActiveMQClient.createServerLocatorWithoutHA(TransportConfiguration(InVMConnectorFactory::class.java.name))
        val sessionFactory = serverLocator.createSessionFactory()
        serverSession = sessionFactory.createSession()
        serverSession.start()

        serverSession.createTemporaryQueue(ArtemisMessagingComponent.RPC_REQUESTS_QUEUE, ArtemisMessagingComponent.RPC_REQUESTS_QUEUE)
        producer = serverSession.createProducer()
        serverThread = AffinityExecutor.ServiceAffinityExecutor("unit-tests-rpc-dispatch-thread", 1)
        serverSession.createTemporaryQueue("activemq.notifications", "rpc.qremovals", "_AMQ_NotifType = 'BINDING_REMOVED'")

        clientSession = sessionFactory.createSession()
        clientSession.start()

        LogHelper.setLevel("+net.corda.rpc")
    }

    @After
    fun shutdown() {
        producer.close()
        clientSession.stop()
        serverSession.stop()
        artemis.stop()
        serverThread.shutdownNow()
    }

    /*
     * RPC operation.
     */
    interface TestOps : RPCOps {
        fun validatePermission(str: String)
    }

    class TestOpsImpl : TestOps {
        override val protocolVersion = 1
        override fun validatePermission(str: String) = requirePermission(str)
    }

    /**
     * Create an RPC proxy for the given user.
     */
    private fun proxyFor(rpcUser: User): TestOps {
        val userService = object : RPCUserService {
            override fun getUser(username: String): User? = if (username == rpcUser.username) rpcUser else null
            override val users: List<User> get() = listOf(rpcUser)
        }

        val dispatcher = object : RPCDispatcher(TestOpsImpl(), userService, "SomeName") {
            override fun send(data: SerializedBytes<*>, toAddress: String) {
                val msg = serverSession.createMessage(false).apply {
                    writeBodyBufferBytes(data.bytes)
                    // Use the magic deduplication property built into Artemis as our message identity too
                    putStringProperty(Message.HDR_DUPLICATE_DETECTION_ID, SimpleString(UUID.randomUUID().toString()))
                }
                producer.send(toAddress, msg)
            }

            override fun getUser(message: ClientMessage): User = rpcUser
        }

        val serverNotifConsumer = serverSession.createConsumer("rpc.qremovals")
        val serverConsumer = serverSession.createConsumer(ArtemisMessagingComponent.RPC_REQUESTS_QUEUE)
        dispatcher.start(serverConsumer, serverNotifConsumer, serverThread)
        return CordaRPCClientImpl(clientSession, ReentrantLock(), rpcUser.username).proxyFor(TestOps::class.java)
    }

    @Test
    fun `empty user cannot use any flows`() {
        val emptyUser = userOf("empty", emptySet())
        val proxy = proxyFor(emptyUser)
        assertFailsWith(PermissionException::class,
                "User ${emptyUser.username} should not be allowed to use $DUMMY_FLOW.",
                { proxy.validatePermission(DUMMY_FLOW) })
    }

    @Test
    fun `admin user can use any flow`() {
        val adminUser = userOf("admin", setOf(ALL_ALLOWED))
        val proxy = proxyFor(adminUser)
        proxy.validatePermission(DUMMY_FLOW)
    }

    @Test
    fun `joe user is allowed to use DummyFlow`() {
        val joeUser = userOf("joe", setOf(DUMMY_FLOW))
        val proxy = proxyFor(joeUser)
        proxy.validatePermission(DUMMY_FLOW)
    }

    @Test
    fun `joe user is not allowed to use OtherFlow`() {
        val joeUser = userOf("joe", setOf(DUMMY_FLOW))
        val proxy = proxyFor(joeUser)
        assertFailsWith(PermissionException::class,
                "User ${joeUser.username} should not be allowed to use $OTHER_FLOW",
                { proxy.validatePermission(OTHER_FLOW) })
    }

    @Test
    fun `check ALL is implemented the correct way round` () {
        val joeUser = userOf("joe", setOf(DUMMY_FLOW))
        val proxy = proxyFor(joeUser)
        assertFailsWith(PermissionException::class,
                "Permission $ALL_ALLOWED should not do anything for User ${joeUser.username}",
                { proxy.validatePermission(ALL_ALLOWED) })
    }

    private fun userOf(name: String, permissions: Set<String>) = User(name, "password", permissions)
}
