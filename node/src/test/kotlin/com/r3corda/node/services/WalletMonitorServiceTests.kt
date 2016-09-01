package com.r3corda.node.services

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.contracts.asset.Cash
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.crypto.newSecureRandom
import com.r3corda.core.messaging.MessageHandlerRegistration
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.DEFAULT_SESSION_ID
import com.r3corda.core.node.services.Wallet
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.random63BitValue
import com.r3corda.core.serialization.OpaqueBytes
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.serialization.serialize
import com.r3corda.core.utilities.DUMMY_NOTARY
import com.r3corda.core.utilities.DUMMY_PUBKEY_1
import com.r3corda.testing.node.MockNetwork
import com.r3corda.node.services.monitor.*
import org.junit.Before
import org.junit.Test
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.*

/**
 * Unit tests for the wallet monitoring service.
 */
class WalletMonitorServiceTests {
    lateinit var network: MockNetwork

    @Before
    fun setup() {
        network = MockNetwork()
    }

    /**
     * Authenticate the register node with the monitor service node.
     */
    private fun authenticate(monitorServiceNode: MockNetwork.MockNode, registerNode: MockNetwork.MockNode): Long {
        network.runNetwork()
        val sessionID = random63BitValue()
        val authenticatePsm = registerNode.services.startProtocol(WalletMonitorService.REGISTER_TOPIC,
                TestRegisterPSM(monitorServiceNode.info, sessionID))
        network.runNetwork()
        authenticatePsm.get(1, TimeUnit.SECONDS)
        return sessionID
    }

    class TestReceiveWalletUpdatePSM(val sessionID: Long)
    : ProtocolLogic<ServiceToClientEvent.OutputState>() {
        override val topic: String get() = WalletMonitorService.IN_EVENT_TOPIC
        @Suspendable
        override fun call(): ServiceToClientEvent.OutputState
            = receive<ServiceToClientEvent.OutputState>(sessionID).validate { it }
    }

    class TestRegisterPSM(val server: NodeInfo, val sessionID: Long)
    : ProtocolLogic<RegisterResponse>() {
        override val topic: String get() = WalletMonitorService.REGISTER_TOPIC
        @Suspendable
        override fun call(): RegisterResponse {
            val req = RegisterRequest(serviceHub.networkService.myAddress, sessionID)
            return sendAndReceive<RegisterResponse>(server.identity, 0, sessionID, req).validate { it }
        }
    }

    /**
     * Test a very simple case of trying to register against the service.
     */
    @Test
    fun `success with network`() {
        val (monitorServiceNode, registerNode) = network.createTwoNodes()

        network.runNetwork()
        val sessionID = random63BitValue()
        val authenticatePsm = registerNode.services.startProtocol(WalletMonitorService.REGISTER_TOPIC,
                TestRegisterPSM(monitorServiceNode.info, sessionID))
        network.runNetwork()
        val result = authenticatePsm.get(1, TimeUnit.SECONDS)
        assertTrue(result.success)
    }

    /**
     * Test that having registered, changes are relayed correctly.
     */
    @Test
    fun `event received`() {
        val (monitorServiceNode, registerNode) = network.createTwoNodes()
        val sessionID = authenticate(monitorServiceNode, registerNode)
        var receivePsm = registerNode.services.startProtocol(WalletMonitorService.IN_EVENT_TOPIC,
                TestReceiveWalletUpdatePSM(sessionID))
        var expected = Wallet.Update(emptySet(), emptySet())
        monitorServiceNode.inNodeWalletMonitorService!!.notifyWalletUpdate(expected)
        network.runNetwork()
        var actual = receivePsm.get(1, TimeUnit.SECONDS)
        assertEquals(expected.consumed, actual.consumed)
        assertEquals(expected.produced, actual.produced)

        // Check that states are passed through correctly
        receivePsm = registerNode.services.startProtocol(WalletMonitorService.IN_EVENT_TOPIC,
                TestReceiveWalletUpdatePSM(sessionID))
        val consumed = setOf(StateRef(SecureHash.randomSHA256(), 0))
        val producedState = TransactionState(DummyContract.SingleOwnerState(newSecureRandom().nextInt(), DUMMY_PUBKEY_1), DUMMY_NOTARY)
        val produced = setOf(StateAndRef(producedState, StateRef(SecureHash.randomSHA256(), 0)))
        expected = Wallet.Update(consumed, produced)
        monitorServiceNode.inNodeWalletMonitorService!!.notifyWalletUpdate(expected)
        network.runNetwork()
        actual = receivePsm.get(1, TimeUnit.SECONDS)
        assertEquals(expected.produced, actual.produced)
        assertEquals(expected.consumed, actual.consumed)
    }

    @Test
    fun `cash issue accepted`() {
        val (monitorServiceNode, registerNode) = network.createTwoNodes()
        val sessionID = authenticate(monitorServiceNode, registerNode)
        val quantity = 1000L
        val events = Collections.synchronizedList(ArrayList<ServiceToClientEvent>())
        val ref = OpaqueBytes(ByteArray(1) {1})

        registerNode.net.addMessageHandler(WalletMonitorService.IN_EVENT_TOPIC + ".0") { msg, reg ->
            events.add(msg.data.deserialize<ServiceToClientEvent>())
        }

        // Check the monitoring service wallet is empty
        assertFalse(monitorServiceNode.services.walletService.currentWallet.states.iterator().hasNext())

        // Tell the monitoring service node to issue some cash
        val recipient = monitorServiceNode.services.storageService.myLegalIdentity
        val outEvent = ClientToServiceCommand.IssueCash(Amount(quantity, GBP), ref, recipient, DUMMY_NOTARY)
        val message = registerNode.net.createMessage(WalletMonitorService.OUT_EVENT_TOPIC, DEFAULT_SESSION_ID,
                ClientToServiceCommandMessage(sessionID, registerNode.net.myAddress, outEvent).serialize().bits)
        registerNode.net.send(message, monitorServiceNode.net.myAddress)
        network.runNetwork()

        val expectedState = Cash.State(Amount(quantity,
                Issued(monitorServiceNode.services.storageService.myLegalIdentity.ref(ref), GBP)),
                recipient.owningKey)

        // Check we've received a response
        events.forEach { event ->
            when (event) {
                is ServiceToClientEvent.TransactionBuild -> {
                    // Check the returned event is correct
                    val tx = (event.state as TransactionBuildResult.ProtocolStarted).transaction
                    assertNotNull(tx)
                    assertEquals(expectedState, tx!!.tx.outputs.single().data)
                }
                is ServiceToClientEvent.OutputState -> {
                    // Check the generated state is correct
                    val actual = event.produced.single().state.data
                    assertEquals(expectedState, actual)
                }
                else -> fail("Unexpected in event ${event}")
            }
        }
    }

    @Test
    fun `cash move accepted`() {
        val (monitorServiceNode, registerNode) = network.createTwoNodes()
        val sessionID = authenticate(monitorServiceNode, registerNode)
        val quantity = 1000L
        val events = Collections.synchronizedList(ArrayList<ServiceToClientEvent>())
        val ref = OpaqueBytes(ByteArray(1) {1})
        var handlerReg: MessageHandlerRegistration? = null

        registerNode.net.addMessageHandler(WalletMonitorService.IN_EVENT_TOPIC + ".0") { msg, reg ->
            events.add(msg.data.deserialize<ServiceToClientEvent>())
            handlerReg = reg
        }

        // Check the monitoring service wallet is empty
        assertFalse(monitorServiceNode.services.walletService.currentWallet.states.iterator().hasNext())

        // Tell the monitoring service node to issue some cash
        val recipient = monitorServiceNode.services.storageService.myLegalIdentity
        val outEvent = ClientToServiceCommand.IssueCash(Amount(quantity, GBP), ref, recipient, DUMMY_NOTARY)
        val message = registerNode.net.createMessage(WalletMonitorService.OUT_EVENT_TOPIC, DEFAULT_SESSION_ID,
                ClientToServiceCommandMessage(sessionID, registerNode.net.myAddress, outEvent).serialize().bits)
        registerNode.net.send(message, monitorServiceNode.net.myAddress)
        network.runNetwork()

        val expectedState = Cash.State(Amount(quantity,
                Issued(monitorServiceNode.services.storageService.myLegalIdentity.ref(ref), GBP)),
                recipient.owningKey)

        // Check we've received a response
        events.forEach { event ->
            when (event) {
                is ServiceToClientEvent.TransactionBuild -> {
                    // Check the returned event is correct
                    val tx = (event.state as TransactionBuildResult.ProtocolStarted).transaction
                    assertNotNull(tx)
                    assertEquals(expectedState, tx!!.tx.outputs.single().data)
                }
                is ServiceToClientEvent.OutputState -> {
                    // Check the generated state is correct
                    val actual = event.produced.single().state.data
                    assertEquals(expectedState, actual)
                }
                else -> fail("Unexpected in event ${event}")
            }
        }
    }
}
