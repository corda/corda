/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.testing.core.*
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.OutOfProcess
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.internal.toDatabaseSchemaNames
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import net.corda.testing.node.internal.DummyClusterSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import rx.Observable
import java.util.*

class DistributedServiceTests : IntegrationTest() {
    private lateinit var alice: NodeHandle
    private lateinit var notaryNodes: List<OutOfProcess>
    private lateinit var aliceProxy: CordaRPCOps
    private lateinit var raftNotaryIdentity: Party
    private lateinit var notaryStateMachines: Observable<Pair<Party, StateMachineUpdate>>
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(*DUMMY_NOTARY_NAME.toDatabaseSchemaNames("_0", "_1", "_2").toTypedArray(),
                ALICE_NAME.toDatabaseSchemaName())
    }
    private fun setup(compositeIdentity: Boolean = false, testBlock: () -> Unit) {
        val testUser = User("test", "test", permissions = setOf(
                startFlow<CashIssueFlow>(),
                startFlow<CashPaymentFlow>(),
                invokeRpc(CordaRPCOps::nodeInfo),
                invokeRpc(CordaRPCOps::stateMachinesFeed))
        )
        driver(DriverParameters(
                extraCordappPackagesToScan = listOf("net.corda.finance.contracts", "net.corda.finance.schemas"),
                notarySpecs = listOf(
                        NotarySpec(
                                DUMMY_NOTARY_NAME,
                                rpcUsers = listOf(testUser),
                                cluster = DummyClusterSpec(clusterSize = 3, compositeServiceIdentity = compositeIdentity))
                )
        )) {
            alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(testUser)).getOrThrow()
            raftNotaryIdentity = defaultNotaryIdentity
            notaryNodes = defaultNotaryHandle.nodeHandles.getOrThrow().map { it as OutOfProcess }

            assertThat(notaryNodes).hasSize(3)

            for (notaryNode in notaryNodes) {
                assertThat(notaryNode.nodeInfo.legalIdentities).contains(raftNotaryIdentity)
            }

            // Check that each notary has different identity as a node.
            assertThat(notaryNodes.flatMap { it.nodeInfo.legalIdentities - raftNotaryIdentity }.toSet()).hasSameSizeAs(notaryNodes)

            // Connect to Alice and the notaries
            fun connectRpc(node: NodeHandle): CordaRPCOps {
                val client = CordaRPCClient(node.rpcAddress)
                return client.start("test", "test").proxy
            }
            aliceProxy = connectRpc(alice)
            val rpcClientsToNotaries = notaryNodes.map(::connectRpc)

            notaryStateMachines = Observable.from(rpcClientsToNotaries.map { proxy ->
                proxy.stateMachinesFeed().updates.map { Pair(proxy.nodeInfo().legalIdentitiesAndCerts.first().party, it) }
            }).flatMap { it.onErrorResumeNext(Observable.empty()) }.bufferUntilSubscribed()

            testBlock()
        }
    }

    // TODO This should be in RaftNotaryServiceTests
    @Test
    fun `cluster survives if a notary is killed`() {
        setup {
            // Issue 100 pounds, then pay ourselves 10x5 pounds
            issueCash(100.POUNDS)

            for (i in 1..10) {
                paySelf(5.POUNDS)
            }

            // Now kill a notary node
            with(notaryNodes[0].process) {
                destroy()
                waitFor()
            }

            // Pay ourselves another 20x5 pounds
            for (i in 1..20) {
                paySelf(5.POUNDS)
            }

            val notarisationsPerNotary = HashMap<Party, Int>()
            notaryStateMachines.expectEvents(isStrict = false) {
                replicate<Pair<Party, StateMachineUpdate>>(30) {
                    expect(match = { it.second is StateMachineUpdate.Added }) { (notary, update) ->
                        update as StateMachineUpdate.Added
                        notarisationsPerNotary.compute(notary) { _, number -> number?.plus(1) ?: 1 }
                    }
                }
            }

            println("Notarisation distribution: $notarisationsPerNotary")
            require(notarisationsPerNotary.size == 3)
        }
    }

    // TODO Use a dummy distributed service rather than a Raft Notary Service as this test is only about Artemis' ability
    // to handle distributed services
    @Test
    fun `requests are distributed evenly amongst the nodes`() {
        setup {
            checkRequestsDistributedEvenly()
        }
    }

    @Test
    fun `requests are distributed evenly amongst the nodes with a composite public key`() {
        setup(true) {
            checkRequestsDistributedEvenly()
        }
    }

    private fun checkRequestsDistributedEvenly() {
        // Issue 100 pounds, then pay ourselves 50x2 pounds
        issueCash(100.POUNDS)

        for (i in 1..50) {
            paySelf(2.POUNDS)
        }

        // The state machines added in the notaries should map one-to-one to notarisation requests
        val notarisationsPerNotary = HashMap<Party, Int>()
        notaryStateMachines.expectEvents(isStrict = false) {
            replicate<Pair<Party, StateMachineUpdate>>(50) {
                expect(match = { it.second is StateMachineUpdate.Added }) { (notary, update) ->
                    update as StateMachineUpdate.Added
                    notarisationsPerNotary.compute(notary) { _, number -> number?.plus(1) ?: 1 }
                }
            }
        }

        // The distribution of requests should be very close to sg like 16/17/17 as by default artemis does round robin
        println("Notarisation distribution: $notarisationsPerNotary")
        require(notarisationsPerNotary.size == 3)
        // We allow some leeway for artemis as it doesn't always produce perfect distribution
        require(notarisationsPerNotary.values.all { it > 10 })
    }

    private fun issueCash(amount: Amount<Currency>) {
        aliceProxy.startFlow(::CashIssueFlow, amount, OpaqueBytes.of(0), raftNotaryIdentity).returnValue.getOrThrow()
    }

    private fun paySelf(amount: Amount<Currency>) {
        aliceProxy.startFlow(::CashPaymentFlow, amount, alice.nodeInfo.singleIdentity()).returnValue.getOrThrow()
    }
}