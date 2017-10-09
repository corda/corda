package net.corda.node

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.PermissionException
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.Issued
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.crypto.keys
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.Party
import net.corda.core.messaging.*
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.GBP
import net.corda.finance.USD
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.node.internal.StartedNode
import net.corda.node.services.FlowPermissions.Companion.startFlowPermission
import net.corda.node.services.messaging.CURRENT_RPC_CONTEXT
import net.corda.node.services.messaging.RpcContext
import net.corda.nodeapi.User
import net.corda.testing.*
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import org.apache.commons.io.IOUtils
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Test
import rx.Observable
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CordaRPCOpsImplTest {

    private companion object {
        val testJar = "net/corda/node/testing/test.jar"
    }

    lateinit var mockNet: MockNetwork
    lateinit var aliceNode: StartedNode<MockNode>
    lateinit var notaryNode: StartedNode<MockNode>
    lateinit var notary: Party
    lateinit var rpc: CordaRPCOps
    lateinit var stateMachineUpdates: Observable<StateMachineUpdate>
    lateinit var transactions: Observable<SignedTransaction>
    lateinit var vaultTrackCash: Observable<Vault.Update<Cash.State>>

    @Before
    fun setup() {
        setCordappPackages("net.corda.finance.contracts.asset")

        mockNet = MockNetwork()
        aliceNode = mockNet.createNode()
        notaryNode = mockNet.createNotaryNode(validating = false)
        rpc = aliceNode.rpcOps
        CURRENT_RPC_CONTEXT.set(RpcContext(User("user", "pwd", permissions = setOf(
                startFlowPermission<CashIssueFlow>(),
                startFlowPermission<CashPaymentFlow>()
        ))))

        mockNet.runNetwork()
        mockNet.networkMapNode.internals.ensureRegistered()
        notary = rpc.notaryIdentities().first()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
        unsetCordappPackages()
    }

    @Test
    fun `cash issue accepted`() {
        aliceNode.database.transaction {
            stateMachineUpdates = rpc.stateMachinesFeed().updates
            vaultTrackCash = rpc.vaultTrackBy<Cash.State>().updates
        }

        val quantity = 1000L
        val ref = OpaqueBytes(ByteArray(1) { 1 })

        // Check the monitoring service wallet is empty
        aliceNode.database.transaction {
            assertFalse(aliceNode.services.vaultService.queryBy<ContractState>().totalStatesAvailable > 0)
        }

        // Tell the monitoring service node to issue some cash
        val recipient = aliceNode.info.chooseIdentity()
        val result = rpc.startFlow(::CashIssueFlow, Amount(quantity, GBP), ref, notary)
        mockNet.runNetwork()

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

        val anonymisedRecipient = result.returnValue.getOrThrow().recipient!!
        val expectedState = Cash.State(Amount(quantity,
                Issued(aliceNode.info.chooseIdentity().ref(ref), GBP)),
                anonymisedRecipient)

        // Query vault via RPC
        val cash = rpc.vaultQueryBy<Cash.State>()
        assertEquals(expectedState, cash.states.first().state.data)

        vaultTrackCash.expectEvents {
            expect { update ->
                val actual = update.produced.single().state.data
                assertEquals(expectedState, actual)
            }
        }
    }

    @Test
    fun `issue and move`() {
        aliceNode.database.transaction {
            stateMachineUpdates = rpc.stateMachinesFeed().updates
            transactions = rpc.internalVerifiedTransactionsFeed().updates
            vaultTrackCash = rpc.vaultTrackBy<Cash.State>().updates
        }

        val result = rpc.startFlow(::CashIssueFlow,
                100.DOLLARS,
                OpaqueBytes(ByteArray(1, { 1 })),
                notary
        )

        mockNet.runNetwork()

        rpc.startFlow(::CashPaymentFlow, 100.DOLLARS, aliceNode.info.chooseIdentity())

        mockNet.runNetwork()

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

        result.returnValue.getOrThrow()
        transactions.expectEvents {
            sequence(
                    // ISSUE
                    expect { stx ->
                        require(stx.tx.inputs.isEmpty())
                        require(stx.tx.outputs.size == 1)
                        val signaturePubKeys = stx.sigs.map { it.by }.toSet()
                        // Only Alice signed, as issuer
                        val aliceKey = aliceNode.info.chooseIdentity().owningKey
                        require(signaturePubKeys.size <= aliceKey.keys.size)
                        require(aliceKey.isFulfilledBy(signaturePubKeys))
                    },
                    // MOVE
                    expect { stx ->
                        require(stx.tx.inputs.size == 1)
                        require(stx.tx.outputs.size == 1)
                        val signaturePubKeys = stx.sigs.map { it.by }.toSet()
                        // Alice and Notary signed
                        require(aliceNode.services.keyManagementService.filterMyKeys(signaturePubKeys).toList().isNotEmpty())
                        require(notary.owningKey.isFulfilledBy(signaturePubKeys))
                    }
            )
        }

        vaultTrackCash.expectEvents {
            sequence(
                    // ISSUE
                    expect { (consumed, produced) ->
                        require(consumed.isEmpty()) { consumed.size }
                        require(produced.size == 1) { produced.size }
                    },
                    // MOVE
                    expect { (consumed, produced) ->
                        require(consumed.size == 1) { consumed.size }
                        require(produced.size == 1) { produced.size }
                    }
            )
        }
    }

    @Test
    fun `cash command by user not permissioned for cash`() {
        CURRENT_RPC_CONTEXT.set(RpcContext(User("user", "pwd", permissions = emptySet())))
        assertThatExceptionOfType(PermissionException::class.java).isThrownBy {
            rpc.startFlow(::CashIssueFlow, Amount(100, USD), OpaqueBytes(ByteArray(1, { 1 })), notary)
        }
    }

    @Test
    fun `can upload an attachment`() {
        val inputJar = Thread.currentThread().contextClassLoader.getResourceAsStream(testJar)
        val secureHash = rpc.uploadAttachment(inputJar)
        assertTrue(rpc.attachmentExists(secureHash))
    }

    @Test
    fun `can download an uploaded attachment`() {
        val inputJar = Thread.currentThread().contextClassLoader.getResourceAsStream(testJar)
        val secureHash = rpc.uploadAttachment(inputJar)
        val bufferFile = ByteArrayOutputStream()
        val bufferRpc = ByteArrayOutputStream()

        IOUtils.copy(Thread.currentThread().contextClassLoader.getResourceAsStream(testJar), bufferFile)
        IOUtils.copy(rpc.openAttachment(secureHash), bufferRpc)

        assertArrayEquals(bufferFile.toByteArray(), bufferRpc.toByteArray())
    }

    @Test
    fun `attempt to start non-RPC flow`() {
        CURRENT_RPC_CONTEXT.set(RpcContext(User("user", "pwd", permissions = setOf(
                startFlowPermission<NonRPCFlow>()
        ))))
        assertThatExceptionOfType(IllegalArgumentException::class.java).isThrownBy {
            rpc.startFlow(::NonRPCFlow)
        }
    }

    class NonRPCFlow : FlowLogic<Unit>() {
        @Suspendable
        override fun call() = Unit
    }

    @Test
    fun `attempt to start RPC flow with void return`() {
        CURRENT_RPC_CONTEXT.set(RpcContext(User("user", "pwd", permissions = setOf(
                startFlowPermission<VoidRPCFlow>()
        ))))
        val result = rpc.startFlow(::VoidRPCFlow)
        mockNet.runNetwork()
        assertNull(result.returnValue.getOrThrow())
    }

    @StartableByRPC
    class VoidRPCFlow : FlowLogic<Void?>() {
        @Suspendable
        override fun call() : Void? = null
    }
}
