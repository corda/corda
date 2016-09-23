package com.r3corda.node.services

import com.google.common.util.concurrent.ListenableFuture
import com.r3corda.contracts.asset.Cash
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.crypto.newSecureRandom
import com.r3corda.core.messaging.createMessage
import com.r3corda.core.node.services.DEFAULT_SESSION_ID
import com.r3corda.core.node.services.Vault
import com.r3corda.core.random63BitValue
import com.r3corda.core.serialization.OpaqueBytes
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.serialization.serialize
import com.r3corda.core.utilities.DUMMY_NOTARY
import com.r3corda.core.utilities.DUMMY_PUBKEY_1
import com.r3corda.node.services.monitor.*
import com.r3corda.node.services.monitor.NodeMonitorService.Companion.IN_EVENT_TOPIC
import com.r3corda.node.services.monitor.NodeMonitorService.Companion.REGISTER_TOPIC
import com.r3corda.node.utilities.AddOrRemove
import com.r3corda.testing.expect
import com.r3corda.testing.expectEvents
import com.r3corda.testing.node.MockNetwork
import com.r3corda.testing.node.MockNetwork.MockNode
import com.r3corda.testing.parallel
import com.r3corda.testing.sequence
import org.junit.Before
import org.junit.Test
import rx.subjects.ReplaySubject
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for the node monitoring service.
 */
class NodeMonitorServiceTests {
    lateinit var network: MockNetwork

    @Before
    fun setup() {
        network = MockNetwork()
    }

    /**
     * Authenticate the register node with the monitor service node.
     */
    private fun authenticate(monitorServiceNode: MockNode, registerNode: MockNode): Long {
        network.runNetwork()
        val sessionId = random63BitValue()
        val authenticatePsm = register(registerNode, monitorServiceNode, sessionId)
        network.runNetwork()
        authenticatePsm.get(1, TimeUnit.SECONDS)
        return sessionId
    }

    /**
     * Test a very simple case of trying to register against the service.
     */
    @Test
    fun `success with network`() {
        val (monitorServiceNode, registerNode) = network.createTwoNodes()

        network.runNetwork()
        val authenticatePsm = register(registerNode, monitorServiceNode, random63BitValue())
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
        var receivePsm = receiveWalletUpdate(registerNode, sessionID)
        var expected = Vault.Update(emptySet(), emptySet())
        monitorServiceNode.inNodeMonitorService!!.notifyVaultUpdate(expected)
        network.runNetwork()
        var actual = receivePsm.get(1, TimeUnit.SECONDS)
        assertEquals(expected.consumed, actual.consumed)
        assertEquals(expected.produced, actual.produced)

        // Check that states are passed through correctly
        receivePsm = receiveWalletUpdate(registerNode, sessionID)
        val consumed = setOf(StateRef(SecureHash.randomSHA256(), 0))
        val producedState = TransactionState(DummyContract.SingleOwnerState(newSecureRandom().nextInt(), DUMMY_PUBKEY_1), DUMMY_NOTARY)
        val produced = setOf(StateAndRef(producedState, StateRef(SecureHash.randomSHA256(), 0)))
        expected = Vault.Update(consumed, produced)
        monitorServiceNode.inNodeMonitorService!!.notifyVaultUpdate(expected)
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
        val events = ReplaySubject.create<ServiceToClientEvent>()
        val ref = OpaqueBytes(ByteArray(1) {1})

        registerNode.net.addMessageHandler(IN_EVENT_TOPIC, sessionID) { msg, reg ->
            events.onNext(msg.data.deserialize<ServiceToClientEvent>())
        }

        // Check the monitoring service wallet is empty
        assertFalse(monitorServiceNode.services.vaultService.currentVault.states.iterator().hasNext())

        // Tell the monitoring service node to issue some cash
        val recipient = monitorServiceNode.services.storageService.myLegalIdentity
        val outEvent = ClientToServiceCommand.IssueCash(Amount(quantity, GBP), ref, recipient, DUMMY_NOTARY)
        val message = registerNode.net.createMessage(NodeMonitorService.OUT_EVENT_TOPIC, DEFAULT_SESSION_ID,
                ClientToServiceCommandMessage(sessionID, registerNode.net.myAddress, outEvent).serialize().bits)
        registerNode.net.send(message, monitorServiceNode.net.myAddress)
        network.runNetwork()

        val expectedState = Cash.State(Amount(quantity,
                Issued(monitorServiceNode.services.storageService.myLegalIdentity.ref(ref), GBP)),
                recipient.owningKey)

        // Check we've received a response
        events.expectEvents {
            parallel(
                    sequence(
                            expect { event: ServiceToClientEvent.StateMachine ->
                                require(event.addOrRemove == AddOrRemove.ADD)
                            },
                            expect { event: ServiceToClientEvent.StateMachine ->
                                require(event.addOrRemove == AddOrRemove.REMOVE)
                            }
                    ),
                    expect { event: ServiceToClientEvent.Transaction -> },
                    expect { event: ServiceToClientEvent.TransactionBuild ->
                        // Check the returned event is correct
                        val tx = (event.state as TransactionBuildResult.ProtocolStarted).transaction
                        assertNotNull(tx)
                        assertEquals(expectedState, tx!!.tx.outputs.single().data)
                    },
                    expect { event: ServiceToClientEvent.OutputState ->
                        // Check the generated state is correct
                        val actual = event.produced.single().state.data
                        assertEquals(expectedState, actual)
                    }
            )
        }
    }

    @Test
    fun `cash move accepted`() {
        val (monitorServiceNode, registerNode) = network.createTwoNodes()
        val sessionID = authenticate(monitorServiceNode, registerNode)
        val quantity = 1000L
        val events = ReplaySubject.create<ServiceToClientEvent>()

        registerNode.net.addMessageHandler(IN_EVENT_TOPIC, sessionID) { msg, reg ->
            events.onNext(msg.data.deserialize<ServiceToClientEvent>())
        }

        val recipient = monitorServiceNode.services.storageService.myLegalIdentity

        // Tell the monitoring service node to issue some cash so we can spend it later
        val issueCommand = ClientToServiceCommand.IssueCash(Amount(quantity, GBP), OpaqueBytes.of(0), recipient, recipient)
        val issueMessage = registerNode.net.createMessage(NodeMonitorService.OUT_EVENT_TOPIC, DEFAULT_SESSION_ID,
                ClientToServiceCommandMessage(sessionID, registerNode.net.myAddress, issueCommand).serialize().bits)
        registerNode.net.send(issueMessage, monitorServiceNode.net.myAddress)
        val payCommand = ClientToServiceCommand.PayCash(Amount(quantity, Issued(recipient.ref(0), GBP)), recipient)
        val payMessage = registerNode.net.createMessage(NodeMonitorService.OUT_EVENT_TOPIC, DEFAULT_SESSION_ID,
                ClientToServiceCommandMessage(sessionID, registerNode.net.myAddress, payCommand).serialize().bits)
        registerNode.net.send(payMessage, monitorServiceNode.net.myAddress)
        network.runNetwork()

        events.expectEvents(isStrict = false) {
            sequence(
                    // ISSUE
                    parallel(
                            sequence(
                                    expect { event: ServiceToClientEvent.StateMachine ->
                                        require(event.addOrRemove == AddOrRemove.ADD)
                                    },
                                    expect { event: ServiceToClientEvent.StateMachine ->
                                        require(event.addOrRemove == AddOrRemove.REMOVE)
                                    }
                            ),
                            expect { event: ServiceToClientEvent.Transaction -> },
                            expect { event: ServiceToClientEvent.TransactionBuild -> },
                            expect { event: ServiceToClientEvent.OutputState -> }
                    ),
                    // MOVE
                    parallel(
                            sequence(
                                    expect { event: ServiceToClientEvent.StateMachine ->
                                        require(event.addOrRemove == AddOrRemove.ADD)
                                    },
                                    expect { event: ServiceToClientEvent.StateMachine ->
                                        require(event.addOrRemove == AddOrRemove.REMOVE)
                                    }
                            ),
                            expect { event: ServiceToClientEvent.Transaction ->
                                require(event.transaction.sigs.size == 1)
                                event.transaction.sigs.map { it.by }.containsAll(
                                        listOf(
                                                monitorServiceNode.services.storageService.myLegalIdentity.owningKey
                                        )
                                )
                            },
                            expect { event: ServiceToClientEvent.TransactionBuild ->
                                require(event.state is TransactionBuildResult.ProtocolStarted)
                            },
                            expect { event: ServiceToClientEvent.OutputState ->
                                require(event.consumed.size == 1)
                                require(event.produced.size == 1)
                            }
                    )
            )
        }
    }

    private fun register(registerNode: MockNode, monitorServiceNode: MockNode, sessionId: Long): ListenableFuture<RegisterResponse> {
        val req = RegisterRequest(registerNode.services.networkService.myAddress, sessionId)
        return registerNode.sendAndReceive<RegisterResponse>(REGISTER_TOPIC, monitorServiceNode, req)
    }

    private fun receiveWalletUpdate(registerNode: MockNode, sessionId: Long): ListenableFuture<ServiceToClientEvent.OutputState> {
        return registerNode.receive<ServiceToClientEvent.OutputState>(IN_EVENT_TOPIC, sessionId)
    }

}
