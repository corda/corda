package net.corda.client.jfx

import net.corda.client.jfx.model.NodeMonitorModel
import net.corda.client.jfx.model.ProgressTrackingEvent
import net.corda.core.bufferUntilSubscribed
import net.corda.core.contracts.Amount
import net.corda.core.contracts.DOLLARS
import net.corda.core.contracts.USD
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.crypto.keys
import net.corda.core.flows.StateMachineRunId
import net.corda.core.getOrThrow
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.messaging.startFlow
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.StateMachineTransactionMapping
import net.corda.core.node.services.Vault
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_BANK_A
import net.corda.core.utilities.DUMMY_BANK_B
import net.corda.core.utilities.DUMMY_BANK_C
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.flows.CashExitFlow
import net.corda.flows.CashIssueFlow
import net.corda.flows.CashPaymentFlow
import net.corda.node.driver.driver
import net.corda.node.services.network.NetworkMapService
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.nodeapi.User
import net.corda.testing.expect
import net.corda.testing.expectEvents
import net.corda.testing.node.DriverBasedTest
import net.corda.testing.sequence
import org.junit.Test
import rx.Observable

class NodeMonitorModelTest : DriverBasedTest() {
    lateinit var bankANode: NodeInfo
    lateinit var notaryNode: NodeInfo

    lateinit var rpc: CordaRPCOps
    lateinit var stateMachineTransactionMapping: Observable<StateMachineTransactionMapping>
    lateinit var stateMachineUpdates: Observable<StateMachineUpdate>
    lateinit var progressTracking: Observable<ProgressTrackingEvent>
    lateinit var transactions: Observable<SignedTransaction>
    lateinit var vaultUpdates: Observable<Vault.Update>
    lateinit var networkMapUpdates: Observable<NetworkMapCache.MapChange>
    lateinit var newNode: (String) -> NodeInfo

    override fun setup() = driver {
        val cashUser = User("user1", "test", permissions = setOf(
                startFlowPermission<CashIssueFlow>(),
                startFlowPermission<CashPaymentFlow>(),
                startFlowPermission<CashExitFlow>())
        )
        val bankANodeFuture = startNode(DUMMY_BANK_A.name, rpcUsers = listOf(cashUser))
        val notaryNodeFuture = startNode(DUMMY_NOTARY.name, advertisedServices = setOf(ServiceInfo(SimpleNotaryService.type)))
        val bankANodeHandle = bankANodeFuture.getOrThrow()
        val notaryNodeHandle = notaryNodeFuture.getOrThrow()
        bankANode = bankANodeHandle.nodeInfo
        notaryNode = notaryNodeHandle.nodeInfo
        newNode = { nodeName -> startNode(nodeName).getOrThrow().nodeInfo }
        val monitor = NodeMonitorModel()

        stateMachineTransactionMapping = monitor.stateMachineTransactionMapping.bufferUntilSubscribed()
        stateMachineUpdates = monitor.stateMachineUpdates.bufferUntilSubscribed()
        progressTracking = monitor.progressTracking.bufferUntilSubscribed()
        transactions = monitor.transactions.bufferUntilSubscribed()
        vaultUpdates = monitor.vaultUpdates.bufferUntilSubscribed()
        networkMapUpdates = monitor.networkMap.bufferUntilSubscribed()

        monitor.register(bankANodeHandle.configuration.rpcAddress!!, cashUser.username, cashUser.password)
        rpc = monitor.proxyObservable.value!!
        runTest()
    }

    @Test
    fun `network map update`() {
        newNode(DUMMY_BANK_B.name)
        newNode(DUMMY_BANK_C.name)
        networkMapUpdates.filter { !it.node.advertisedServices.any { it.info.type.isNotary() } }
                .filter { !it.node.advertisedServices.any { it.info.type == NetworkMapService.type } }
                .expectEvents(isStrict = false) {
                    sequence(
                            // TODO : Add test for remove when driver DSL support individual node shutdown.
                            expect { output: NetworkMapCache.MapChange ->
                                require(output.node.legalIdentity.name == DUMMY_BANK_A.name) { "Expecting : ${DUMMY_BANK_A.name}, Actual : ${output.node.legalIdentity.name}" }
                            },
                            expect { output: NetworkMapCache.MapChange ->
                                require(output.node.legalIdentity.name == DUMMY_BANK_B.name) { "Expecting : ${DUMMY_BANK_B.name}, Actual : ${output.node.legalIdentity.name}" }
                            },
                            expect { output: NetworkMapCache.MapChange ->
                                require(output.node.legalIdentity.name == DUMMY_BANK_C.name) { "Expecting : ${DUMMY_BANK_C.name}, Actual : ${output.node.legalIdentity.name}" }
                            }
                    )
                }
    }

    @Test
    fun `cash issue works end to end`() {
        rpc.startFlow(::CashIssueFlow,
                Amount(100, USD),
                OpaqueBytes(ByteArray(1, { 1 })),
                bankANode.legalIdentity,
                notaryNode.notaryIdentity
        )

        vaultUpdates.expectEvents(isStrict = false) {
            sequence(
                    // SNAPSHOT
                    expect { output: Vault.Update ->
                        require(output.consumed.size == 0) { output.consumed.size }
                        require(output.produced.size == 0) { output.produced.size }
                    },
                    // ISSUE
                    expect { output: Vault.Update ->
                        require(output.consumed.size == 0) { output.consumed.size }
                        require(output.produced.size == 1) { output.produced.size }
                    }
            )
        }
    }

    @Test
    fun `cash issue and move`() {
        rpc.startFlow(::CashIssueFlow, 100.DOLLARS, OpaqueBytes.of(1), bankANode.legalIdentity, notaryNode.notaryIdentity).returnValue.getOrThrow()
        rpc.startFlow(::CashPaymentFlow, 100.DOLLARS, bankANode.legalIdentity).returnValue.getOrThrow()

        var issueSmId: StateMachineRunId? = null
        var moveSmId: StateMachineRunId? = null
        var issueTx: SignedTransaction? = null
        var moveTx: SignedTransaction? = null
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
                    expect { stx ->
                        require(stx.tx.inputs.isEmpty())
                        require(stx.tx.outputs.size == 1)
                        val signaturePubKeys = stx.sigs.map { it.by }.toSet()
                        // Only Alice signed
                        val aliceKey = bankANode.legalIdentity.owningKey
                        require(signaturePubKeys.size <= aliceKey.keys.size)
                        require(aliceKey.isFulfilledBy(signaturePubKeys))
                        issueTx = stx
                    },
                    // MOVE
                    expect { stx ->
                        require(stx.tx.inputs.size == 1)
                        require(stx.tx.outputs.size == 1)
                        val signaturePubKeys = stx.sigs.map { it.by }.toSet()
                        // Alice and Notary signed
                        require(bankANode.legalIdentity.owningKey.isFulfilledBy(signaturePubKeys))
                        require(notaryNode.notaryIdentity.owningKey.isFulfilledBy(signaturePubKeys))
                        moveTx = stx
                    }
            )
        }

        vaultUpdates.expectEvents {
            sequence(
                    // SNAPSHOT
                    expect { output: Vault.Update ->
                        require(output.consumed.size == 0) { output.consumed.size }
                        require(output.produced.size == 0) { output.produced.size }
                    },
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

        stateMachineTransactionMapping.expectEvents {
            sequence(
                    // ISSUE
                    expect { mapping ->
                        require(mapping.stateMachineRunId == issueSmId)
                        require(mapping.transactionId == issueTx!!.id)
                    },
                    // MOVE
                    expect { mapping ->
                        require(mapping.stateMachineRunId == moveSmId)
                        require(mapping.transactionId == moveTx!!.id)
                    }
            )
        }
    }
}
