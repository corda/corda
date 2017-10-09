package net.corda.client.rpc

import co.paralleluniverse.fibers.Suspendable
import com.esotericsoftware.kryo.KryoException
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.unwrap
import net.corda.node.internal.Node
import net.corda.node.internal.StartedNode
import net.corda.nodeapi.User
import net.corda.testing.*
import net.corda.testing.node.NodeBasedTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

@CordaSerializable
data class Packet(val x: () -> Long)

class BlacklistKotlinClosureTest : NodeBasedTest() {
    companion object {
        @Suppress("UNUSED") val logger = loggerFor<BlacklistKotlinClosureTest>()
        const val EVIL: Long = 666
    }

    @StartableByRPC
    @InitiatingFlow
    class FlowC(private val remoteParty: Party, private val data: Packet) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val session = initiateFlow(remoteParty)
            val x = session.sendAndReceive<Packet>(data).unwrap { x -> x }
            logger.info("FlowC: ${x.x()}")
        }
    }

    @InitiatedBy(FlowC::class)
    class RemoteFlowC(private val session: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val packet = session.receive<Packet>().unwrap { x -> x }
            logger.info("RemoteFlowC: ${packet.x() + 1}")
            session.send(Packet({ packet.x() + 1 }))
        }
    }

    @JvmField
    @Rule
    val expectedEx: ExpectedException = ExpectedException.none()

    private val rpcUser = User("user1", "test", permissions = setOf("ALL"))
    private lateinit var aliceNode: StartedNode<Node>
    private lateinit var bobNode: StartedNode<Node>
    private lateinit var aliceClient: CordaRPCClient
    private var connection: CordaRPCConnection? = null

    private fun login(username: String, password: String) {
        connection = aliceClient.start(username, password)
    }

    @Before
    fun setUp() {
        setCordappPackages("net.corda.client.rpc")
        aliceNode = startNode(ALICE.name, rpcUsers = listOf(rpcUser)).getOrThrow()
        bobNode = startNode(BOB.name, rpcUsers = listOf(rpcUser)).getOrThrow()
        bobNode.registerInitiatedFlow(RemoteFlowC::class.java)
        aliceClient = CordaRPCClient(aliceNode.internals.configuration.rpcAddress!!)
    }

    @After
    fun done() {
        connection?.close()
        bobNode.internals.stop()
        aliceNode.internals.stop()
        unsetCordappPackages()
    }

    @Test
    fun `closure sent via RPC`() {
        login(rpcUser.username, rpcUser.password)
        val proxy = connection!!.proxy
        expectedEx.expect(KryoException::class.java)
        expectedEx.expectMessage("is not annotated or on the whitelist, so cannot be used in serialization")
        proxy.startFlow(::FlowC, bobNode.info.chooseIdentity(), Packet{ EVIL }).returnValue.getOrThrow()
    }
}