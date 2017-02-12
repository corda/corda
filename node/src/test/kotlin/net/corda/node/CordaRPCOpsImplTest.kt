package net.corda.node

import net.corda.contracts.asset.Cash
import net.corda.core.contracts.*
import net.corda.core.flows.StateMachineRunId
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.Vault
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.flows.CashIssueFlow
import net.corda.flows.CashPaymentFlow
import net.corda.node.internal.CordaRPCOpsImpl
import net.corda.node.services.User
import net.corda.node.services.messaging.CURRENT_RPC_USER
import net.corda.node.services.messaging.PermissionException
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.startFlowPermission
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
        CURRENT_RPC_USER.set(User("user", "pwd", permissions = setOf(
                startFlowPermission<CashIssueFlow>(),
                startFlowPermission<CashPaymentFlow>()
        )))

        databaseTransaction(aliceNode.database) {
            stateMachineUpdates = rpc.stateMachinesAndUpdates().second
            transactions = rpc.verifiedTransactions().second
            vaultUpdates = rpc.vaultAndUpdates().second
        }
    }

    @Test
    fun `cash issue accepted`() {
        val quantity = 1000L
        val ref = OpaqueBytes(ByteArray(1) { 1 })

        // Check the monitoring service wallet is empty
        databaseTransaction(aliceNode.database) {
            assertFalse(aliceNode.services.vaultService.currentVault.states.iterator().hasNext())
        }

        // Tell the monitoring service node to issue some cash
        val recipient = aliceNode.info.legalIdentity
        rpc.startFlow(::CashIssueFlow, Amount(quantity, GBP), ref, recipient, notaryNode.info.notaryIdentity)
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

        rpc.startFlow(::CashIssueFlow,
                Amount(100, USD),
                OpaqueBytes(ByteArray(1, { 1 })),
                aliceNode.info.legalIdentity,
                notaryNode.info.notaryIdentity
        )

        network.runNetwork()

        rpc.startFlow(::CashPaymentFlow,
                Amount(100, Issued(PartyAndReference(aliceNode.info.legalIdentity, OpaqueBytes(ByteArray(1, { 1 }))), USD)),
                aliceNode.info.legalIdentity
        )

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
                        val aliceKey = aliceNode.info.legalIdentity.owningKey
                        require(signaturePubKeys.size <= aliceKey.keys.size)
                        require(aliceKey.isFulfilledBy(signaturePubKeys))
                    },
                    // MOVE
                    expect { tx ->
                        require(tx.tx.inputs.size == 1)
                        require(tx.tx.outputs.size == 1)
                        val signaturePubKeys = tx.sigs.map { it.by }.toSet()
                        // Alice and Notary signed
                        require(aliceNode.info.legalIdentity.owningKey.isFulfilledBy(signaturePubKeys))
                        require(notaryNode.info.notaryIdentity.owningKey.isFulfilledBy(signaturePubKeys))
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
            rpc.startFlow(::CashIssueFlow,
                    Amount(100, USD),
                    OpaqueBytes(ByteArray(1, { 1 })),
                    aliceNode.info.legalIdentity,
                    notaryNode.info.notaryIdentity
            )
        }
    }
}
