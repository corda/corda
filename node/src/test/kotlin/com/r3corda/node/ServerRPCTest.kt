package com.r3corda.node

import com.r3corda.contracts.asset.Cash
import com.r3corda.core.contracts.*
import com.r3corda.core.node.services.ServiceInfo
import com.r3corda.core.node.services.Vault
import com.r3corda.core.protocols.StateMachineRunId
import com.r3corda.core.serialization.OpaqueBytes
import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.core.utilities.DUMMY_NOTARY
import com.r3corda.node.internal.ServerRPCOps
import com.r3corda.node.services.messaging.StateMachineUpdate
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.services.transactions.SimpleNotaryService
import com.r3corda.testing.expect
import com.r3corda.testing.expectEvents
import com.r3corda.testing.node.MockNetwork
import com.r3corda.testing.node.MockNetwork.MockNode
import com.r3corda.testing.sequence
import org.junit.Before
import org.junit.Test
import rx.Observable
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Unit tests for the node monitoring service.
 */
class ServerRPCTest {
    lateinit var network: MockNetwork
    lateinit var aliceNode: MockNode
    lateinit var notaryNode: MockNode
    lateinit var rpc: ServerRPCOps
    lateinit var stateMachineUpdates: Observable<StateMachineUpdate>
    lateinit var transactions: Observable<SignedTransaction>
    lateinit var vaultUpdates: Observable<Vault.Update>

    @Before
    fun setup() {
        network = MockNetwork()
        val networkMap = network.createNode(advertisedServices = ServiceInfo(NetworkMapService.type))
        aliceNode = network.createNode(networkMapAddress = networkMap.info.address)
        notaryNode = network.createNode(advertisedServices = ServiceInfo(SimpleNotaryService.type), networkMapAddress = networkMap.info.address)
        rpc = ServerRPCOps(aliceNode.services, aliceNode.smm, aliceNode.database)

        stateMachineUpdates = rpc.stateMachinesAndUpdates().second
        transactions = rpc.verifiedTransactions().second
        vaultUpdates = rpc.vaultAndUpdates().second
    }

    @Test
    fun `cash issue accepted`() {
        val quantity = 1000L
        val ref = OpaqueBytes(ByteArray(1) {1})

        // Check the monitoring service wallet is empty
        assertFalse(aliceNode.services.vaultService.currentVault.states.iterator().hasNext())

        // Tell the monitoring service node to issue some cash
        val recipient = aliceNode.info.legalIdentity
        val outEvent = ClientToServiceCommand.IssueCash(Amount(quantity, GBP), ref, recipient, notaryNode.info.notaryIdentity)
        rpc.executeCommand(outEvent)
        network.runNetwork()

        val expectedState = Cash.State(Amount(quantity,
                Issued(aliceNode.info.legalIdentity.ref(ref), GBP)),
                recipient.owningKey)

        var issueSmId: StateMachineRunId? = null
        stateMachineUpdates.expectEvents {
            sequence(
                    // ISSUE
                    expect { add: StateMachineUpdate.Added ->
                        issueSmId = add.id
                    },
                    expect { remove: StateMachineUpdate.Removed ->
                        require(remove.id == issueSmId)
                    }
            )
        }

        transactions.expectEvents {
            expect { tx ->
                assertEquals(expectedState, tx.tx.outputs.single().data)
            }
        }

        vaultUpdates.expectEvents {
            expect { update ->
                val actual = update.produced.single().state.data
                assertEquals(expectedState, actual)
            }
        }
    }

    @Test
    fun issueAndMoveWorks() {

        rpc.executeCommand(ClientToServiceCommand.IssueCash(
                amount = Amount(100, USD),
                issueRef = OpaqueBytes(ByteArray(1, { 1 })),
                recipient = aliceNode.info.legalIdentity,
                notary = notaryNode.info.notaryIdentity
        ))

        network.runNetwork()

        rpc.executeCommand(ClientToServiceCommand.PayCash(
                amount = Amount(100, Issued(PartyAndReference(aliceNode.info.legalIdentity, OpaqueBytes(ByteArray(1, { 1 }))), USD)),
                recipient = aliceNode.info.legalIdentity
        ))

        network.runNetwork()

        var issueSmId: StateMachineRunId? = null
        var moveSmId: StateMachineRunId? = null
        stateMachineUpdates.expectEvents {
            sequence(
                    // ISSUE
                    expect { add: StateMachineUpdate.Added ->
                        issueSmId = add.id
                    },
                    expect { remove: StateMachineUpdate.Removed ->
                        require(remove.id == issueSmId)
                    },
                    // MOVE
                    expect { add: StateMachineUpdate.Added ->
                        moveSmId = add.id
                    },
                    expect { remove: StateMachineUpdate.Removed ->
                        require(remove.id == moveSmId)
                    }
            )
        }

        transactions.expectEvents {
            sequence(
                    // ISSUE
                    expect { tx ->
                        require(tx.tx.inputs.isEmpty())
                        require(tx.tx.outputs.size == 1)
                        val signaturePubKeys = tx.sigs.map { it.by }.toSet()
                        // Only Alice signed
                        require(signaturePubKeys.size == 1)
                        require(signaturePubKeys.contains(aliceNode.info.legalIdentity.owningKey))
                    },
                    // MOVE
                    expect { tx ->
                        require(tx.tx.inputs.size == 1)
                        require(tx.tx.outputs.size == 1)
                        val signaturePubKeys = tx.sigs.map { it.by }.toSet()
                        // Alice and Notary signed
                        require(signaturePubKeys.size == 2)
                        require(signaturePubKeys.contains(aliceNode.info.legalIdentity.owningKey))
                        require(signaturePubKeys.contains(notaryNode.info.notaryIdentity.owningKey))
                    }
            )
        }

        vaultUpdates.expectEvents {
            sequence(
                    // ISSUE
                    expect { update ->
                        require(update.consumed.size == 0) { update.consumed.size }
                        require(update.produced.size == 1) { update.produced.size }
                    },
                    // MOVE
                    expect { update ->
                        require(update.consumed.size == 1) { update.consumed.size }
                        require(update.produced.size == 1) { update.produced.size }
                    }
            )
        }
    }
}
