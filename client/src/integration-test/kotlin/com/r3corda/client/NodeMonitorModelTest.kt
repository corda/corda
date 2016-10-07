package com.r3corda.client

import com.google.common.util.concurrent.SettableFuture
import com.r3corda.client.model.NodeMonitorModel
import com.r3corda.client.model.ProgressTrackingEvent
import com.r3corda.core.bufferUntilSubscribed
import com.r3corda.core.contracts.*
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.ServiceInfo
import com.r3corda.core.node.services.StateMachineTransactionMapping
import com.r3corda.core.node.services.Vault
import com.r3corda.core.protocols.StateMachineRunId
import com.r3corda.core.serialization.OpaqueBytes
import com.r3corda.core.transactions.SignedTransaction
import com.r3corda.node.driver.driver
import com.r3corda.node.driver.startClient
import com.r3corda.node.services.messaging.NodeMessagingClient
import com.r3corda.node.services.messaging.StateMachineUpdate
import com.r3corda.node.services.transactions.SimpleNotaryService
import com.r3corda.testing.*
import org.junit.*
import rx.Observable
import rx.Observer
import kotlin.concurrent.thread

class NodeMonitorModelTest {

    lateinit var aliceNode: NodeInfo
    lateinit var notaryNode: NodeInfo
    lateinit var aliceClient: NodeMessagingClient
    val driverStarted = SettableFuture.create<Unit>()
    val stopDriver = SettableFuture.create<Unit>()
    val driverStopped = SettableFuture.create<Unit>()

    lateinit var stateMachineTransactionMapping: Observable<StateMachineTransactionMapping>
    lateinit var stateMachineUpdates: Observable<StateMachineUpdate>
    lateinit var progressTracking: Observable<ProgressTrackingEvent>
    lateinit var transactions: Observable<SignedTransaction>
    lateinit var vaultUpdates: Observable<Vault.Update>
    lateinit var clientToService: Observer<ClientToServiceCommand>

    @Before
    fun start() {
        thread {
            driver {
                val aliceNodeFuture = startNode("Alice")
                val notaryNodeFuture = startNode("Notary", advertisedServices = setOf(ServiceInfo(SimpleNotaryService.type)))

                aliceNode = aliceNodeFuture.get()
                notaryNode = notaryNodeFuture.get()
                aliceClient = startClient(aliceNode).get()

                val monitor = NodeMonitorModel()

                stateMachineTransactionMapping = monitor.stateMachineTransactionMapping.bufferUntilSubscribed()
                stateMachineUpdates = monitor.stateMachineUpdates.bufferUntilSubscribed()
                progressTracking = monitor.progressTracking.bufferUntilSubscribed()
                transactions = monitor.transactions.bufferUntilSubscribed()
                vaultUpdates = monitor.vaultUpdates.bufferUntilSubscribed()
                clientToService = monitor.clientToService

                monitor.register(aliceNode, aliceClient.config.certificatesPath)
                driverStarted.set(Unit)
                stopDriver.get()
            }
            driverStopped.set(Unit)
        }
        driverStarted.get()
    }

    @After
    fun stop() {
        stopDriver.set(Unit)
        driverStopped.get()
    }

    @Test
    fun cashIssueWorksEndToEnd() {
        clientToService.onNext(ClientToServiceCommand.IssueCash(
                amount = Amount(100, USD),
                issueRef = OpaqueBytes(ByteArray(1, { 1 })),
                recipient = aliceNode.legalIdentity,
                notary = notaryNode.notaryIdentity
        ))

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
    fun issueAndMoveWorks() {

        clientToService.onNext(ClientToServiceCommand.IssueCash(
                amount = Amount(100, USD),
                issueRef = OpaqueBytes(ByteArray(1, { 1 })),
                recipient = aliceNode.legalIdentity,
                notary = notaryNode.notaryIdentity
        ))

        clientToService.onNext(ClientToServiceCommand.PayCash(
                amount = Amount(100, Issued(PartyAndReference(aliceNode.legalIdentity, OpaqueBytes(ByteArray(1, { 1 }))), USD)),
                recipient = aliceNode.legalIdentity
        ))

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
                    expect { tx ->
                        require(tx.tx.inputs.isEmpty())
                        require(tx.tx.outputs.size == 1)
                        val signaturePubKeys = tx.sigs.map { it.by }.toSet()
                        // Only Alice signed
                        require(signaturePubKeys.size == 1)
                        require(signaturePubKeys.contains(aliceNode.legalIdentity.owningKey))
                        issueTx = tx
                    },
                    // MOVE
                    expect { tx ->
                        require(tx.tx.inputs.size == 1)
                        require(tx.tx.outputs.size == 1)
                        val signaturePubKeys = tx.sigs.map { it.by }.toSet()
                        // Alice and Notary signed
                        require(signaturePubKeys.size == 2)
                        require(signaturePubKeys.contains(aliceNode.legalIdentity.owningKey))
                        require(signaturePubKeys.contains(notaryNode.notaryIdentity.owningKey))
                        moveTx = tx
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
