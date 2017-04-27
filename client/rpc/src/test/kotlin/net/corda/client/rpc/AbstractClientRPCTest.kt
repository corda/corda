package net.corda.client.rpc

import net.corda.core.messaging.RPCOps
import net.corda.core.serialization.SerializedBytes
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.LogHelper
import net.corda.node.services.RPCUserService
import net.corda.node.services.messaging.RPCDispatcher
import net.corda.node.utilities.AffinityExecutor
import net.corda.nodeapi.ArtemisMessagingComponent
import net.corda.nodeapi.User
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
import org.bouncycastle.asn1.x500.X500Name
import org.junit.After
import org.junit.Before
import java.util.*
import java.util.concurrent.locks.ReentrantLock

abstract class AbstractClientRPCTest {
    lateinit var artemis: EmbeddedActiveMQ
    lateinit var serverSession: ClientSession
    lateinit var clientSession: ClientSession
    lateinit var producer: ClientProducer
    lateinit var serverThread: AffinityExecutor.ServiceAffinityExecutor

    @Before
    fun rpcSetup() {
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
    fun rpcShutdown() {
        safeClose(producer)
        clientSession.stop()
        serverSession.stop()
        artemis.stop()
        serverThread.shutdownNow()
    }

    fun <T : RPCOps> rpcProxyFor(rpcUser: User, rpcImpl: T, type: Class<T>): T {
        val userService = object : RPCUserService {
            override fun getUser(username: String): User? = if (username == rpcUser.username) rpcUser else null
            override val users: List<User> get() = listOf(rpcUser)
        }

        val dispatcher = object : RPCDispatcher(rpcImpl, userService, X500Name(ALICE.name)) {
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
        return CordaRPCClientImpl(clientSession, ReentrantLock(), rpcUser.username).proxyFor(type)
    }

    fun safeClose(obj: Any) {
        try {
            (obj as AutoCloseable).close()
        } catch (e: Exception) {
        }
    }
}
