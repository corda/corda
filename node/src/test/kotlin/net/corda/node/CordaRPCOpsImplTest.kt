package net.corda.node

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.PermissionException
import net.corda.client.rpc.RPCException
import net.corda.core.context.AuthServiceId
import net.corda.core.context.InvocationContext
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.Issued
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.crypto.keys
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.Party
import net.corda.core.internal.RPC_UPLOADER
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.messaging.vaultTrackBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.AttachmentQueryCriteria
import net.corda.core.node.services.vault.ColumnPredicate
import net.corda.core.node.services.vault.EqualityComparisonOperator
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.GBP
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.node.internal.security.AuthorizingSubject
import net.corda.node.internal.security.RPCSecurityManagerImpl
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.node.services.rpc.CURRENT_RPC_CONTEXT
import net.corda.node.services.rpc.RpcAuthContext
import net.corda.nodeapi.exceptions.NonRpcFlowException
import net.corda.nodeapi.internal.config.User
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.expect
import net.corda.testing.core.expectEvents
import net.corda.testing.core.sequence
import net.corda.testing.internal.fromUserList
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.testActor
import org.apache.commons.io.IOUtils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatThrownBy
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

// Mock an AuthorizingSubject instance sticking to a fixed set of permissions
private fun buildSubject(principal: String, permissionStrings: Set<String>): AuthorizingSubject {
    return RPCSecurityManagerImpl.fromUserList(
            id = AuthServiceId("TEST"),
            users = listOf(User(
                    username = principal,
                    password = "",
                    permissions = permissionStrings
            ))
    ).buildSubject(principal)
}

class CordaRPCOpsImplTest {
    private companion object {
        const val testJar = "net/corda/node/testing/test.jar"
    }

    private lateinit var mockNet: InternalMockNetwork
    private lateinit var aliceNode: TestStartedNode
    private lateinit var alice: Party
    private lateinit var notary: Party
    private lateinit var rpc: CordaRPCOps
    private lateinit var stateMachineUpdates: Observable<StateMachineUpdate>
    private lateinit var transactions: Observable<SignedTransaction>
    private lateinit var vaultTrackCash: Observable<Vault.Update<Cash.State>>

    @Before
    fun setup() {
        mockNet = InternalMockNetwork(cordappsForAllNodes = FINANCE_CORDAPPS)
        aliceNode = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
        rpc = aliceNode.cordaRPCOps
        CURRENT_RPC_CONTEXT.set(RpcAuthContext(InvocationContext.rpc(testActor()), buildSubject("TEST_USER", emptySet())))

        mockNet.runNetwork()
        withPermissions(invokeRpc(CordaRPCOps::notaryIdentities)) {
            notary = rpc.notaryIdentities().single()
        }
        alice = aliceNode.services.myInfo.identityFromX500Name(ALICE_NAME)
    }

    @After
    fun cleanUp() {
        if (::mockNet.isInitialized) {
            mockNet.stopNodes()
        }
    }

    @Test(timeout=300_000)
	fun `cash issue accepted`() {
        CURRENT_RPC_CONTEXT.set(RpcAuthContext(InvocationContext.rpc(testActor()), buildSubject("TEST_USER", emptySet())))
        withPermissions(
                invokeRpc("vaultTrackBy"),
                invokeRpc("vaultQueryBy"),
                invokeRpc(CordaRPCOps::stateMachinesFeed),
                startFlow<CashIssueFlow>()
        ) {
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
                    Issued(alice.ref(ref), GBP)),
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
    }

    @Test(timeout=300_000)
    @Suppress("DEPRECATION")
    fun `issue and move`() {
        CURRENT_RPC_CONTEXT.set(RpcAuthContext(InvocationContext.rpc(testActor()), buildSubject("TEST_USER", emptySet())))
        withPermissions(
                invokeRpc(CordaRPCOps::stateMachinesFeed),
                invokeRpc(CordaRPCOps::internalVerifiedTransactionsFeed),
                invokeRpc("vaultTrackBy"),
                startFlow<CashIssueFlow>(),
                startFlow<CashPaymentFlow>()
        ) {
            aliceNode.database.transaction {
                stateMachineUpdates = rpc.stateMachinesFeed().updates
                transactions = rpc.internalVerifiedTransactionsFeed().updates
                vaultTrackCash = rpc.vaultTrackBy<Cash.State>().updates
            }

            val result = rpc.startFlow(::CashIssueFlow, 100.DOLLARS, OpaqueBytes.of(1), notary)

            mockNet.runNetwork()

            rpc.startFlow(::CashPaymentFlow, 100.DOLLARS, alice)

            mockNet.runNetwork()

            var issueSmId: StateMachineRunId? = null
            var paymentSmId: StateMachineRunId? = null
            var paymentRecSmId: StateMachineRunId? = null
            stateMachineUpdates.expectEvents {
                sequence(
                        // ISSUE
                        expect { add: StateMachineUpdate.Added ->
                            issueSmId = add.id
                        },
                        expect { remove: StateMachineUpdate.Removed ->
                            assertThat(remove.id).isEqualTo(issueSmId)
                        },
                        // PAYMENT
                        expect { add: StateMachineUpdate.Added ->
                            paymentSmId = add.id
                        },
                        expect { add: StateMachineUpdate.Added ->
                            paymentRecSmId = add.id
                        },
                        expect { remove: StateMachineUpdate.Removed ->
                            assertThat(remove.id).isEqualTo(paymentRecSmId)
                        },
                        expect { remove: StateMachineUpdate.Removed ->
                            assertThat(remove.id).isEqualTo(paymentSmId)
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
                            val aliceKey = alice.owningKey
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
    }

    @Test(timeout=300_000)
	fun `cash command by user not permissioned for cash`() {
        CURRENT_RPC_CONTEXT.set(RpcAuthContext(InvocationContext.rpc(testActor()), buildSubject("TEST_USER", emptySet())))
        withoutAnyPermissions {
            assertThatExceptionOfType(PermissionException::class.java).isThrownBy {
                rpc.startFlow(::CashIssueFlow, 100.DOLLARS, OpaqueBytes.of(1), notary)
            }
        }
    }

    @Test(timeout=300_000)
	fun `can upload an attachment`() {
        CURRENT_RPC_CONTEXT.set(RpcAuthContext(InvocationContext.rpc(testActor()), buildSubject("TEST_USER", emptySet())))
        withPermissions(invokeRpc(CordaRPCOps::uploadAttachment), invokeRpc(CordaRPCOps::attachmentExists)) {
            val inputJar = Thread.currentThread().contextClassLoader.getResourceAsStream(testJar)
            val secureHash = rpc.uploadAttachment(inputJar)
            assertTrue(rpc.attachmentExists(secureHash))
        }
    }

    @Test(timeout=300_000)
	fun `cannot upload the same attachment`() {
        CURRENT_RPC_CONTEXT.set(RpcAuthContext(InvocationContext.rpc(testActor()), buildSubject("TEST_USER", emptySet())))
        withPermissions(invokeRpc(CordaRPCOps::uploadAttachment), invokeRpc(CordaRPCOps::attachmentExists)) {
            val inputJar1 = Thread.currentThread().contextClassLoader.getResourceAsStream(testJar)
            val inputJar2 = Thread.currentThread().contextClassLoader.getResourceAsStream(testJar)
            rpc.uploadAttachment(inputJar1)
            assertThatExceptionOfType(java.nio.file.FileAlreadyExistsException::class.java).isThrownBy {
                rpc.uploadAttachment(inputJar2)
            }
        }
    }

    @Test(timeout=300_000)
	fun `can download an uploaded attachment`() {
        CURRENT_RPC_CONTEXT.set(RpcAuthContext(InvocationContext.rpc(testActor()), buildSubject("TEST_USER", emptySet())))
        withPermissions(invokeRpc(CordaRPCOps::uploadAttachment), invokeRpc(CordaRPCOps::openAttachment)) {
            val inputJar = Thread.currentThread().contextClassLoader.getResourceAsStream(testJar)
            val secureHash = rpc.uploadAttachment(inputJar)
            val bufferFile = ByteArrayOutputStream()
            val bufferRpc = ByteArrayOutputStream()

            IOUtils.copy(Thread.currentThread().contextClassLoader.getResourceAsStream(testJar), bufferFile)
            IOUtils.copy(rpc.openAttachment(secureHash), bufferRpc)

            assertArrayEquals(bufferFile.toByteArray(), bufferRpc.toByteArray())
        }
    }

    @Test(timeout=300_000)
	fun `can upload attachment with metadata`() {
        CURRENT_RPC_CONTEXT.set(RpcAuthContext(InvocationContext.rpc(testActor()), buildSubject("TEST_USER", emptySet())))
        withPermissions(invokeRpc(CordaRPCOps::uploadAttachmentWithMetadata), invokeRpc(CordaRPCOps::attachmentExists)) {
            val inputJar = Thread.currentThread().contextClassLoader.getResourceAsStream(testJar)
            val secureHash = rpc.uploadAttachmentWithMetadata(inputJar, "Iron Fist", "Season 2")
            assertTrue(rpc.attachmentExists(secureHash))
        }
    }

    @Test(timeout=300_000)
	fun `attachment uploaded with metadata has specified filename`() {
        CURRENT_RPC_CONTEXT.set(RpcAuthContext(InvocationContext.rpc(testActor()), buildSubject("TEST_USER", emptySet())))
        withPermissions(invokeRpc(CordaRPCOps::uploadAttachmentWithMetadata), invokeRpc(CordaRPCOps::queryAttachments)) {
            val inputJar = Thread.currentThread().contextClassLoader.getResourceAsStream(testJar)
            rpc.uploadAttachmentWithMetadata(inputJar, "The Punisher", "Season 1")
            assertEquals(
                rpc.queryAttachments(
                    AttachmentQueryCriteria.AttachmentsQueryCriteria(
                        filenameCondition = ColumnPredicate.EqualityComparison(
                            EqualityComparisonOperator.EQUAL,
                            "Season 1"
                        )
                    ), null
                ).size, 1
            )
        }
    }

    @Test(timeout=300_000)
	fun `attachment uploaded with metadata can be from a privileged user`() {
        CURRENT_RPC_CONTEXT.set(RpcAuthContext(InvocationContext.rpc(testActor()), buildSubject("TEST_USER", emptySet())))
        withPermissions(invokeRpc(CordaRPCOps::uploadAttachmentWithMetadata), invokeRpc(CordaRPCOps::attachmentExists)) {
            val inputJar = Thread.currentThread().contextClassLoader.getResourceAsStream(testJar)
            val secureHash = rpc.uploadAttachmentWithMetadata(inputJar, RPC_UPLOADER, "Season 1")
            assertTrue(rpc.attachmentExists(secureHash))
        }
    }

    @Test(timeout=300_000)
    fun `trying to open attachment which doesnt exist throws error`() {
        CURRENT_RPC_CONTEXT.set(RpcAuthContext(InvocationContext.rpc(testActor()), buildSubject("TEST_USER", emptySet())))
        withPermissions(invokeRpc(CordaRPCOps::openAttachment)) {
            assertThatThrownBy {
                rpc.openAttachment(SecureHash.zeroHash)
            }.isInstanceOf(RPCException::class.java)
                    .withFailMessage("Unable to open attachment with id: ${SecureHash.zeroHash}")
        }
    }

    @Test(timeout=300_000)
	fun `attachment uploaded with metadata has specified uploader`() {
        CURRENT_RPC_CONTEXT.set(RpcAuthContext(InvocationContext.rpc(testActor()), buildSubject("TEST_USER", emptySet())))
        withPermissions(invokeRpc(CordaRPCOps::uploadAttachmentWithMetadata), invokeRpc(CordaRPCOps::queryAttachments)) {
            val inputJar = Thread.currentThread().contextClassLoader.getResourceAsStream(testJar)
            rpc.uploadAttachmentWithMetadata(inputJar, "Daredevil", "Season 3")
            assertEquals(
                rpc.queryAttachments(
                    AttachmentQueryCriteria.AttachmentsQueryCriteria(
                        uploaderCondition = ColumnPredicate.EqualityComparison(
                            EqualityComparisonOperator.EQUAL,
                            "Daredevil"
                        )
                    ), null
                ).size, 1
            )
        }
    }

    @Test(timeout=300_000)
	fun `attempt to start non-RPC flow`() {
        CURRENT_RPC_CONTEXT.set(RpcAuthContext(InvocationContext.rpc(testActor()), buildSubject("TEST_USER", emptySet())))
        withPermissions(startFlow<NonRPCFlow>()) {
            assertThatExceptionOfType(NonRpcFlowException::class.java).isThrownBy {
                rpc.startFlow(::NonRPCFlow)
            }
        }
    }

    @Test(timeout=300_000)
	fun `kill a nonexistent flow through RPC`() {
        CURRENT_RPC_CONTEXT.set(RpcAuthContext(InvocationContext.rpc(testActor()), buildSubject("TEST_USER", emptySet())))
        withPermissions(invokeRpc(CordaRPCOps::killFlow)) {
            val nonexistentFlowId = StateMachineRunId.createRandom()
            val killed = rpc.killFlow(nonexistentFlowId)
            assertThat(killed).isFalse()
        }
    }

    @Test(timeout=300_000)
	fun `non-ContractState class for the contractStateType param in vault queries`() {
        CURRENT_RPC_CONTEXT.set(RpcAuthContext(InvocationContext.rpc(testActor()), buildSubject("TEST_USER", emptySet())))
        val nonContractStateClass: Class<out ContractState> = uncheckedCast(Cash::class.java)
        withPermissions(invokeRpc("vaultTrack"), invokeRpc("vaultQuery")) {
            assertThatThrownBy { rpc.vaultQuery(nonContractStateClass) }.hasMessageContaining(Cash::class.java.name)
            assertThatThrownBy { rpc.vaultTrack(nonContractStateClass) }.hasMessageContaining(Cash::class.java.name)
        }
    }

    @Test(timeout=300_000)
    fun `attempt to start RPC flow with void return`() {
        CURRENT_RPC_CONTEXT.set(RpcAuthContext(InvocationContext.rpc(testActor()), buildSubject("TEST_USER", emptySet())))
        withPermissions(startFlow<VoidRPCFlow>()) {
            val result = rpc.startFlow(::VoidRPCFlow)
            mockNet.runNetwork()
            assertNull(result.returnValue.getOrThrow())
        }
    }

    class NonRPCFlow : FlowLogic<Unit>() {
        @Suspendable
        override fun call() = Unit
    }



    @StartableByRPC
    class VoidRPCFlow : FlowLogic<Void?>() {
        @Suspendable
        override fun call(): Void? = null
    }

    private inline fun withPermissions(vararg permissions: String, action: () -> Unit) {
        val previous = CURRENT_RPC_CONTEXT.get()
        try {
            CURRENT_RPC_CONTEXT.set(previous.copy(authorizer = buildSubject(previous.principal, permissions.toSet())))
            action.invoke()
        } finally {
            CURRENT_RPC_CONTEXT.set(previous)
        }
    }

    private inline fun withoutAnyPermissions(action: () -> Unit) = withPermissions(action = action)
}
