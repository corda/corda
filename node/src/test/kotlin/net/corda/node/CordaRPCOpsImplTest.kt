package net.corda.node

import net.corda.contracts.asset.Cash
import net.corda.core.contracts.*
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.Vault
import net.corda.core.protocols.StateMachineRunId
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.node.internal.CordaRPCOpsImpl
import net.corda.node.services.User
import net.corda.node.services.messaging.CURRENT_RPC_USER
import net.corda.node.services.messaging.PermissionException
import net.corda.node.services.messaging.StateMachineUpdate
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.node.utilities.databaseTransaction
import net.corda.testing.expect
import net.corda.testing.expectEvents
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import net.corda.testing.sequence
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Before
import org.junit.Test
import rx.Observable
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CordaRPCOpsImplTest {

    lateinit var network: MockNetwork
    lateinit var aliceNode: MockNode
    lateinit var notaryNode: MockNode
    lateinit var rpc: CordaRPCOpsImpl
    lateinit var stateMachineUpdates: Observable<StateMachineUpdate>
    lateinit var transactions: Observable<SignedTransaction>
    lateinit var vaultUpdates: Observable<Vault.Update>

    @Before
    fun setup() {
        network = MockNetwork()
        val networkMap = network.createNode(advertisedServices = ServiceInfo(NetworkMapService.type))
        aliceNode = network.createNode(networkMapAddress = networkMap.info.address)
        notaryNode = network.createNode(advertisedServices = ServiceInfo(SimpleNotaryService.type), networkMapAddress = networkMap.info.address)
        rpc = CordaRPCOpsImpl(aliceNode.services, aliceNode.smm, aliceNode.database)
        CURRENT_RPC_USER.set(User("user", "pwd", permissions = setOf(CordaRPCOpsImpl.CASH_PERMISSION)))

        stateMachineUpdates = rpc.stateMachinesAndUpdates().second
        transactions = rpc.verifiedTransactions().second
        vaultUpdates = rpc.vaultAndUpdates().second
    }

    @Test
    fun `cash issue accepted`() {
        val quantity = 1000L
        val ref = OpaqueBytes(ByteArray(1) {1})

        // Check the monitoring service wallet is empty
        databaseTransaction(aliceNode.database) {
            assertFalse(aliceNode.services.vaultService.currentVault.states.iterator().hasNext())
        }

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
    fun `issue and move`() {

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

    @Test
    fun `cash command by user not permissioned for cash`() {
        CURRENT_RPC_USER.set(User("user", "pwd", permissions = emptySet()))
        assertThatExceptionOfType(PermissionException::class.java).isThrownBy {
            rpc.executeCommand(ClientToServiceCommand.IssueCash(
                    amount = Amount(100, USD),
                    issueRef = OpaqueBytes(ByteArray(1, { 1 })),
                    recipient = aliceNode.info.legalIdentity,
                    notary = notaryNode.info.notaryIdentity
            ))
        }
    }
}
