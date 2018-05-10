/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.client.jfx

import net.corda.client.jfx.model.NodeMonitorModel
import net.corda.client.jfx.model.ProgressTrackingEvent
import net.corda.core.context.InvocationOrigin
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.crypto.keys
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.StateMachineTransactionMapping
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.messaging.startFlow
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.Vault
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.USD
import net.corda.finance.flows.CashExitFlow
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.testing.core.*
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.User
import org.junit.ClassRule
import org.junit.Test
import rx.Observable

class NodeMonitorModelTest : IntegrationTest() {
    private lateinit var aliceNode: NodeInfo
    private lateinit var bobNode: NodeInfo
    private lateinit var notaryParty: Party

    private lateinit var rpc: CordaRPCOps
    private lateinit var rpcBob: CordaRPCOps
    private lateinit var stateMachineTransactionMapping: Observable<StateMachineTransactionMapping>
    private lateinit var stateMachineUpdates: Observable<StateMachineUpdate>
    private lateinit var stateMachineUpdatesBob: Observable<StateMachineUpdate>
    private lateinit var progressTracking: Observable<ProgressTrackingEvent>
    private lateinit var transactions: Observable<SignedTransaction>
    private lateinit var vaultUpdates: Observable<Vault.Update<ContractState>>
    private lateinit var networkMapUpdates: Observable<NetworkMapCache.MapChange>
    private lateinit var newNode: (CordaX500Name) -> NodeInfo

    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(*listOf(ALICE_NAME, BOB_NAME, CHARLIE_NAME, DUMMY_NOTARY_NAME)
                .map { it.toDatabaseSchemaName() }.toTypedArray())
    }

    private fun setup(runTest: () -> Unit) {
        driver(DriverParameters(extraCordappPackagesToScan = listOf("net.corda.finance"))) {
            val cashUser = User("user1", "test", permissions = setOf(
                    startFlow<CashIssueFlow>(),
                    startFlow<CashPaymentFlow>(),
                    startFlow<CashExitFlow>(),
                    invokeRpc(CordaRPCOps::notaryIdentities),
                    invokeRpc("vaultTrackBy"),
                    invokeRpc("vaultQueryBy"),
                    invokeRpc(CordaRPCOps::internalVerifiedTransactionsFeed),
                    invokeRpc(CordaRPCOps::stateMachineRecordedTransactionMappingFeed),
                    invokeRpc(CordaRPCOps::stateMachinesFeed),
                    invokeRpc(CordaRPCOps::networkMapFeed))
            )
            val aliceNodeHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(cashUser)).getOrThrow()
            aliceNode = aliceNodeHandle.nodeInfo
            newNode = { nodeName -> startNode(providedName = nodeName).getOrThrow().nodeInfo }
            val monitor = NodeMonitorModel()
            stateMachineTransactionMapping = monitor.stateMachineTransactionMapping.bufferUntilSubscribed()
            stateMachineUpdates = monitor.stateMachineUpdates.bufferUntilSubscribed()
            progressTracking = monitor.progressTracking.bufferUntilSubscribed()
            transactions = monitor.transactions.bufferUntilSubscribed()
            vaultUpdates = monitor.vaultUpdates.bufferUntilSubscribed()
            networkMapUpdates = monitor.networkMap.bufferUntilSubscribed()

            monitor.register(aliceNodeHandle.rpcAddress, cashUser.username, cashUser.password)
            rpc = monitor.proxyObservable.value!!.cordaRPCOps
            notaryParty = defaultNotaryIdentity

            val bobNodeHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(cashUser)).getOrThrow()
            bobNode = bobNodeHandle.nodeInfo
            val monitorBob = NodeMonitorModel()
            stateMachineUpdatesBob = monitorBob.stateMachineUpdates.bufferUntilSubscribed()
            monitorBob.register(bobNodeHandle.rpcAddress, cashUser.username, cashUser.password)
            rpcBob = monitorBob.proxyObservable.value!!.cordaRPCOps
            runTest()
        }
    }

    @Test
    fun `network map update`() = setup {
        val charlieNode = newNode(CHARLIE_NAME)
        val nonServiceIdentities = aliceNode.legalIdentitiesAndCerts + bobNode.legalIdentitiesAndCerts + charlieNode.legalIdentitiesAndCerts
        networkMapUpdates.filter { it.node.legalIdentitiesAndCerts.any { it in nonServiceIdentities } }
                .expectEvents(isStrict = false) {
                    sequence(
                            // TODO : Add test for remove when driver DSL support individual node shutdown.
                            expect { output: NetworkMapCache.MapChange ->
                                require(output.node.legalIdentities.any { it.name == ALICE_NAME }) { "Expecting : ${ALICE_NAME}, Actual : ${output.node.legalIdentities.map(Party::name)}" }
                            },
                            expect { output: NetworkMapCache.MapChange ->
                                require(output.node.legalIdentities.any { it.name == BOB_NAME }) { "Expecting : ${BOB_NAME}, Actual : ${output.node.legalIdentities.map(Party::name)}" }
                            },
                            expect { output: NetworkMapCache.MapChange ->
                                require(output.node.legalIdentities.any { it.name == CHARLIE_NAME }) { "Expecting : ${CHARLIE_NAME}, Actual : ${output.node.legalIdentities.map(Party::name)}" }
                            }
                    )
                }
    }

    @Test
    fun `cash issue works end to end`() = setup {
        rpc.startFlow(::CashIssueFlow,
                Amount(100, USD),
                OpaqueBytes(ByteArray(1, { 1 })),
                notaryParty
        )

        vaultUpdates.expectEvents(isStrict = false) {
            sequence(
                    // SNAPSHOT
                    expect { (consumed, produced) ->
                        require(consumed.isEmpty()) { consumed.size }
                        require(produced.isEmpty()) { produced.size }
                    },
                    // ISSUE
                    expect { (consumed, produced) ->
                        require(consumed.isEmpty()) { consumed.size }
                        require(produced.size == 1) { produced.size }
                    }
            )
        }
    }

    @Test
    fun `cash issue and move`() = setup {
        val (_, issueIdentity) = rpc.startFlow(::CashIssueFlow, 100.DOLLARS, OpaqueBytes.of(1), notaryParty).returnValue.getOrThrow()
        rpc.startFlow(::CashPaymentFlow, 100.DOLLARS, bobNode.chooseIdentity()).returnValue.getOrThrow()

        var issueSmId: StateMachineRunId? = null
        var moveSmId: StateMachineRunId? = null
        var issueTx: SignedTransaction? = null
        var moveTx: SignedTransaction? = null
        stateMachineUpdates.expectEvents(isStrict = false) {
            sequence(
                    // ISSUE
                    expect { add: StateMachineUpdate.Added ->
                        issueSmId = add.id
                        val context = add.stateMachineInfo.invocationContext
                        require(context.origin is InvocationOrigin.RPC && context.principal().name == "user1")
                    },
                    expect { remove: StateMachineUpdate.Removed ->
                        require(remove.id == issueSmId)
                    },
                    // MOVE - N.B. There are other framework flows that happen in parallel for the remote resolve transactions flow
                    expect(match = { it.stateMachineInfo.flowLogicClassName == CashPaymentFlow::class.java.name }) { add: StateMachineUpdate.Added ->
                        moveSmId = add.id
                        val context = add.stateMachineInfo.invocationContext
                        require(context.origin is InvocationOrigin.RPC && context.principal().name == "user1")
                    },
                    expect(match = { it is StateMachineUpdate.Removed && it.id == moveSmId }) {
                    }
            )
        }

        stateMachineUpdatesBob.expectEvents {
            sequence(
                    // MOVE
                    expect { add: StateMachineUpdate.Added ->
                        val context = add.stateMachineInfo.invocationContext
                        require(context.origin is InvocationOrigin.Peer && aliceNode.isLegalIdentity(aliceNode.identityFromX500Name((context.origin as InvocationOrigin.Peer).party)))
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
                        val aliceKey = aliceNode.chooseIdentity().owningKey
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
                        require(issueIdentity!!.owningKey.isFulfilledBy(signaturePubKeys))
                        require(notaryParty.owningKey.isFulfilledBy(signaturePubKeys))
                        moveTx = stx
                    }
            )
        }

        vaultUpdates.expectEvents {
            sequence(
                    // SNAPSHOT
                    expect { (consumed, produced) ->
                        require(consumed.isEmpty()) { consumed.size }
                        require(produced.isEmpty()) { produced.size }
                    },
                    // ISSUE
                    expect { (consumed, produced) ->
                        require(consumed.isEmpty()) { consumed.size }
                        require(produced.size == 1) { produced.size }
                    },
                    // MOVE
                    expect { (consumed, produced) ->
                        require(consumed.size == 1) { consumed.size }
                        require(produced.isEmpty()) { produced.size }
                    }
            )
        }

        stateMachineTransactionMapping.expectEvents {
            sequence(
                    // ISSUE
                    expect { (stateMachineRunId, transactionId) ->
                        require(stateMachineRunId == issueSmId)
                        require(transactionId == issueTx!!.id)
                    },
                    // MOVE
                    expect { (stateMachineRunId, transactionId) ->
                        require(stateMachineRunId == moveSmId)
                        require(transactionId == moveTx!!.id)
                    }
            )
        }
    }
}
