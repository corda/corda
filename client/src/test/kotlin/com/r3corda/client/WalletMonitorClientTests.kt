package com.r3corda.client

import co.paralleluniverse.strands.SettableFuture
import com.r3corda.client.testing.*
import com.r3corda.core.contracts.*
import com.r3corda.core.serialization.OpaqueBytes
import com.r3corda.node.driver.driver
import com.r3corda.node.driver.startClient
import com.r3corda.node.services.monitor.ServiceToClientEvent
import com.r3corda.node.services.monitor.TransactionBuildResult
import com.r3corda.node.services.transactions.SimpleNotaryService
import com.r3corda.node.utilities.AddOrRemove
import org.junit.Test
import org.reactfx.EventSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.test.fail

val log: Logger = LoggerFactory.getLogger(WalletMonitorServiceTests::class.java)

class WalletMonitorServiceTests {
    @Test
    fun cashIssueWorksEndToEnd() {

        driver {
            val aliceNodeFuture = startNode("Alice")
            val notaryNodeFuture = startNode("Notary", advertisedServices = setOf(SimpleNotaryService.Type))

            val aliceNode = aliceNodeFuture.get()
            val notaryNode = notaryNodeFuture.get()
            val client = startClient(aliceNode).get()

            log.info("Alice is ${aliceNode.identity}")
            log.info("Notary is ${notaryNode.identity}")

            val aliceInStream = EventSource<ServiceToClientEvent>()
            val aliceOutStream = EventSource<ClientToServiceCommand>()

            val aliceMonitorClient = WalletMonitorClient(client, aliceNode, aliceOutStream, aliceInStream)
            require(aliceMonitorClient.register().get())

            aliceOutStream.push(ClientToServiceCommand.IssueCash(
                    amount = Amount(100, USD),
                    issueRef = OpaqueBytes(ByteArray(1, { 1 })),
                    recipient = aliceNode.identity,
                    notary = notaryNode.identity
            ))

            val buildFuture = SettableFuture<ServiceToClientEvent.TransactionBuild>()
            val eventFuture = SettableFuture<ServiceToClientEvent.OutputState>()
            aliceInStream.subscribe {
                if (it is ServiceToClientEvent.OutputState)
                    eventFuture.set(it)
                else if (it is ServiceToClientEvent.TransactionBuild)
                    buildFuture.set(it)
                else
                    log.warn("Unexpected event $it")
            }

            val buildEvent = buildFuture.get()
            val state = buildEvent.state
            if (state is TransactionBuildResult.Failed) {
                fail(state.message)
            }

            val outputEvent = eventFuture.get()
            require(outputEvent.consumed.size == 0)
            require(outputEvent.produced.size == 1)
        }
    }

    @Test
    fun issueAndMoveWorks() {
        driver {
            val aliceNodeFuture = startNode("Alice")
            val notaryNodeFuture = startNode("Notary", advertisedServices = setOf(SimpleNotaryService.Type))

            val aliceNode = aliceNodeFuture.get()
            val notaryNode = notaryNodeFuture.get()
            val client = startClient(aliceNode).get()

            log.info("Alice is ${aliceNode.identity}")
            log.info("Notary is ${notaryNode.identity}")

            val aliceInStream = EventSource<ServiceToClientEvent>()
            val aliceOutStream = EventSource<ClientToServiceCommand>()

            val aliceMonitorClient = WalletMonitorClient(client, aliceNode, aliceOutStream, aliceInStream)
            require(aliceMonitorClient.register().get())

            aliceOutStream.push(ClientToServiceCommand.IssueCash(
                    amount = Amount(100, USD),
                    issueRef = OpaqueBytes(ByteArray(1, { 1 })),
                    recipient = aliceNode.identity,
                    notary = notaryNode.identity
            ))

            aliceOutStream.push(ClientToServiceCommand.PayCash(
                    amount = Amount(100, Issued(PartyAndReference(aliceNode.identity, OpaqueBytes(ByteArray(1, { 1 }))), USD)),
                    recipient = aliceNode.identity
            ))

            aliceInStream.expectEvents(sequence(
                    // ISSUE
                    parallel(
                            sequence(
                                    expect { add: ServiceToClientEvent.StateMachine ->
                                        require(add.addOrRemove == AddOrRemove.ADD)
                                    },
                                    expect { remove: ServiceToClientEvent.StateMachine ->
                                        require(remove.addOrRemove == AddOrRemove.REMOVE)
                                    }
                            ),
                            expect { tx: ServiceToClientEvent.Transaction ->
                                require(tx.transaction.tx.inputs.isEmpty())
                                require(tx.transaction.tx.outputs.size == 1)
                                val signaturePubKeys = tx.transaction.sigs.map { it.by }.toSet()
                                // Only Alice signed
                                require(signaturePubKeys.size == 1)
                                require(signaturePubKeys.contains(aliceNode.identity.owningKey))
                            },
                            expect { build: ServiceToClientEvent.TransactionBuild ->
                                val state = build.state
                                when (state) {
                                    is TransactionBuildResult.ProtocolStarted -> {
                                    }
                                    is TransactionBuildResult.Failed -> fail(state.message)
                                }
                            },
                            expect { output: ServiceToClientEvent.OutputState ->
                                require(output.consumed.size == 0)
                                require(output.produced.size == 1)
                            }
                    ),

                    // MOVE
                    parallel(
                            sequence(
                                    expect { add: ServiceToClientEvent.StateMachine ->
                                        require(add.addOrRemove == AddOrRemove.ADD)
                                    },
                                    expect { add: ServiceToClientEvent.StateMachine ->
                                        require(add.addOrRemove == AddOrRemove.REMOVE)
                                    }
                            ),
                            expect { tx: ServiceToClientEvent.Transaction ->
                                require(tx.transaction.tx.inputs.size == 1)
                                require(tx.transaction.tx.outputs.size == 1)
                                val signaturePubKeys = tx.transaction.sigs.map { it.by }.toSet()
                                // Alice and Notary signed
                                require(signaturePubKeys.size == 2)
                                require(signaturePubKeys.contains(aliceNode.identity.owningKey))
                                require(signaturePubKeys.contains(notaryNode.identity.owningKey))
                            },
                            sequence(
                                    expect { build: ServiceToClientEvent.TransactionBuild ->
                                        val state = build.state
                                        when (state) {
                                            is TransactionBuildResult.ProtocolStarted -> {
                                                log.info("${state.message}")
                                            }
                                            is TransactionBuildResult.Failed -> fail(state.message)
                                        }
                                    },
                                    expect { build: ServiceToClientEvent.Progress ->
                                        // Requesting signature by notary service
                                    },
                                    expect { build: ServiceToClientEvent.Progress ->
                                        // Structural step change in child of Requesting signature by notary service
                                    },
                                    expect { build: ServiceToClientEvent.Progress ->
                                        // Requesting signature by notary service
                                    },
                                    expect { build: ServiceToClientEvent.Progress ->
                                        // Validating response from Notary service
                                    },
                                    expect { build: ServiceToClientEvent.Progress ->
                                        // Done
                                    },
                                    expect { build: ServiceToClientEvent.Progress ->
                                        // Broadcasting transaction to participants
                                    },
                                    expect { build: ServiceToClientEvent.Progress ->
                                        // Done
                                    }
                            ),
                            expect { output: ServiceToClientEvent.OutputState ->
                                require(output.consumed.size == 1)
                                require(output.produced.size == 1)
                            }
                    )
            ))
        }
    }

    @Test
    fun movingCashOfDifferentIssueRefsFails() {
        driver {
            val aliceNodeFuture = startNode("Alice")
            val notaryNodeFuture = startNode("Notary", advertisedServices = setOf(SimpleNotaryService.Type))

            val aliceNode = aliceNodeFuture.get()
            val notaryNode = notaryNodeFuture.get()
            val client = startClient(aliceNode).get()

            log.info("Alice is ${aliceNode.identity}")
            log.info("Notary is ${notaryNode.identity}")

            val aliceInStream = EventSource<ServiceToClientEvent>()
            val aliceOutStream = EventSource<ClientToServiceCommand>()

            val aliceMonitorClient = WalletMonitorClient(client, aliceNode, aliceOutStream, aliceInStream)
            require(aliceMonitorClient.register().get())

            aliceOutStream.push(ClientToServiceCommand.IssueCash(
                    amount = Amount(100, USD),
                    issueRef = OpaqueBytes(ByteArray(1, { 1 })),
                    recipient = aliceNode.identity,
                    notary = notaryNode.identity
            ))

            aliceOutStream.push(ClientToServiceCommand.IssueCash(
                    amount = Amount(100, USD),
                    issueRef = OpaqueBytes(ByteArray(1, { 2 })),
                    recipient = aliceNode.identity,
                    notary = notaryNode.identity
            ))

            aliceOutStream.push(ClientToServiceCommand.PayCash(
                    amount = Amount(200, Issued(PartyAndReference(aliceNode.identity, OpaqueBytes(ByteArray(1, { 1 }))), USD)),
                    recipient = aliceNode.identity
            ))

            aliceInStream.expectEvents(sequence(
                    // ISSUE 1
                    parallel(
                            sequence(
                                    expect { add: ServiceToClientEvent.StateMachine ->
                                        require(add.addOrRemove == AddOrRemove.ADD)
                                    },
                                    expect { remove: ServiceToClientEvent.StateMachine ->
                                        require(remove.addOrRemove == AddOrRemove.REMOVE)
                                    }
                            ),
                            expect { tx: ServiceToClientEvent.Transaction -> },
                            expect { build: ServiceToClientEvent.TransactionBuild -> },
                            expect { output: ServiceToClientEvent.OutputState -> }
                    ),

                    // ISSUE 2
                    parallel(
                            sequence(
                                    expect { add: ServiceToClientEvent.StateMachine ->
                                        require(add.addOrRemove == AddOrRemove.ADD)
                                    },
                                    expect { remove: ServiceToClientEvent.StateMachine ->
                                        require(remove.addOrRemove == AddOrRemove.REMOVE)
                                    }
                            ),
                            expect { tx: ServiceToClientEvent.Transaction -> },
                            expect { build: ServiceToClientEvent.TransactionBuild -> },
                            expect { output: ServiceToClientEvent.OutputState -> }
                    ),

                    // MOVE, should fail
                    expect { build: ServiceToClientEvent.TransactionBuild ->
                        val state = build.state
                        require(state is TransactionBuildResult.Failed)
                    }
            ))
        }
    }
}
