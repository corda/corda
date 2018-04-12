/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.enterprise.perftestcordapp

import co.paralleluniverse.fibers.Suspendable
import com.google.common.base.Stopwatch
import com.r3.corda.enterprise.perftestcordapp.flows.CashIssueAndPaymentNoSelection
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.minutes
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.testing.core.*
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.InProcess
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.internalServices
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.performance.div
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.internal.toDatabaseSchemaNames
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import net.corda.testing.node.internal.InternalDriverDSL
import net.corda.testing.node.internal.performance.startPublishingFixedRateInjector
import net.corda.testing.node.internal.performance.startReporter
import net.corda.testing.node.internal.performance.startTightLoopInjector
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Test
import java.util.*
import java.util.concurrent.TimeUnit

@Ignore("Run these locally")
class NodePerformanceTests : IntegrationTest() {
     companion object {
        val ALICE = TestIdentity(ALICE_NAME, 70).party
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(*DUMMY_NOTARY_NAME.toDatabaseSchemaNames("_0", "_1", "_2").toTypedArray(),
                DUMMY_BANK_A_NAME.toDatabaseSchemaName())
    }

    @StartableByRPC
    class EmptyFlow : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
        }
    }

    private data class FlowMeasurementResult(
            val flowPerSecond: Double,
            val averageMs: Double
    )

    @Test
    fun `empty flow per second`() {
        driver(DriverParameters(startNodesInProcess = true)) {
            val a = startNode(rpcUsers = listOf(User("A", "A", setOf(startFlow<EmptyFlow>())))).get()
            
            CordaRPCClient(a.rpcAddress).use("A", "A") { connection ->
                val timings = Collections.synchronizedList(ArrayList<Long>())
                val N = 10000
                val overallTiming = Stopwatch.createStarted().apply {
                    startTightLoopInjector(
                            parallelism = 8,
                            numberOfInjections = N,
                            queueBound = 50
                    ) {
                        val timing = Stopwatch.createStarted().apply {
                            connection.proxy.startFlow(NodePerformanceTests::EmptyFlow).returnValue.getOrThrow()
                        }.stop().elapsed(TimeUnit.MICROSECONDS)
                        timings.add(timing)
                    }
                }.stop().elapsed(TimeUnit.MICROSECONDS)
                println(
                        FlowMeasurementResult(
                                flowPerSecond = N / (overallTiming * 0.000001),
                                averageMs = timings.average() * 0.001
                        )
                )
            }
        }
    }

    @Test
    fun `empty flow rate`() {
        driver(DriverParameters(startNodesInProcess = true)) {
            val a = startNode(rpcUsers = listOf(User("A", "A", setOf(startFlow<EmptyFlow>())))).get()
            a as InProcess
            val metricRegistry = startReporter((this as InternalDriverDSL).shutdownManager, a.internalServices.monitoringService.metrics)
            CordaRPCClient(a.rpcAddress).use("A", "A") { connection ->
                startPublishingFixedRateInjector(
                        metricRegistry = metricRegistry,
                        parallelism = 16,
                        overallDuration = 5.minutes,
                        injectionRate = 2000L / TimeUnit.SECONDS,
                        workBound = 50
                ) {
                    connection.proxy.startFlow(NodePerformanceTests::EmptyFlow).returnValue
                }
            }
        }
    }

    @Test
    fun `issue flow rate`() {
        driver(DriverParameters( startNodesInProcess = true, extraCordappPackagesToScan = listOf("net.corda.finance"))) {
            val a = startNode(rpcUsers = listOf(User("A", "A", setOf(startFlow<CashIssueFlow>())))).get()
            a as InProcess
            val metricRegistry = startReporter((this as InternalDriverDSL).shutdownManager, a.internalServices.monitoringService.metrics)
            CordaRPCClient(a.rpcAddress).use("A", "A") { connection ->
                startPublishingFixedRateInjector(
                        metricRegistry = metricRegistry,
                        parallelism = 16,
                        overallDuration = 5.minutes,
                        injectionRate = 2000L / TimeUnit.SECONDS,
                        workBound = 50
                ) {
                    connection.proxy.startFlow(::CashIssueFlow, 1.DOLLARS, OpaqueBytes.of(0), ALICE).returnValue
                }
            }
        }
    }

    @Test
    fun `self pay rate`() {
        val user = User("A", "A", setOf(startFlow<CashIssueAndPaymentFlow>()))
        driver(DriverParameters(
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, rpcUsers = listOf(user))),
                startNodesInProcess = true,
                extraCordappPackagesToScan = listOf("net.corda.finance", "com.r3.corda.enterprise.perftestcordapp"),
                portAllocation = PortAllocation.Incremental(20000)
        )) {
            val notary = defaultNotaryNode.getOrThrow() as InProcess
            val metricRegistry = startReporter((this as InternalDriverDSL).shutdownManager, notary.internalServices.monitoringService.metrics)
            CordaRPCClient(notary.rpcAddress).use("A", "A") { connection ->
                startPublishingFixedRateInjector(
                        metricRegistry = metricRegistry,
                        parallelism = 64,
                        overallDuration = 5.minutes,
                        injectionRate = 300L / TimeUnit.SECONDS,
                        workBound = 50
                ) {
                    connection.proxy.startFlow(::CashIssueAndPaymentFlow, 1.DOLLARS, OpaqueBytes.of(0), defaultNotaryIdentity, false, defaultNotaryIdentity).returnValue
                }
            }
        }
    }

    @Test
    fun `self pay rate without selection`() {
        val user = User("A", "A", setOf(startFlow<CashIssueAndPaymentNoSelection>()))
        driver(DriverParameters(
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME)),
                startNodesInProcess = true,
                portAllocation = PortAllocation.Incremental(20000)
        )) {
            val aliceFuture = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), startInSameProcess = true)
            val alice = aliceFuture.getOrThrow() as InProcess
            val metricRegistry = startReporter((this as InternalDriverDSL).shutdownManager, alice.internalServices.monitoringService.metrics)
            CordaRPCClient(alice.rpcAddress).use("A", "A") { connection ->
                startPublishingFixedRateInjector(
                        metricRegistry = metricRegistry,
                        parallelism = 64,
                        overallDuration = 5.minutes,
                        injectionRate = 50L / TimeUnit.SECONDS,
                        workBound = 500
                ) {
                    connection.proxy.startFlow(::CashIssueAndPaymentNoSelection, 1.DOLLARS, OpaqueBytes.of(0), alice.nodeInfo.legalIdentities[0], false, defaultNotaryIdentity).returnValue
                }
            }
        }
    }

    @Test
    fun `single pay`() {
        val user = User("A", "A", setOf(startFlow<CashIssueAndPaymentNoSelection>()))
        driver(DriverParameters(
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME)),
                startNodesInProcess = true,
                portAllocation = PortAllocation.Incremental(20000)
        )) {
            val aliceFuture = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user))
            val bobFuture = startNode(providedName = BOB_NAME, rpcUsers = listOf(user))
            val alice = aliceFuture.getOrThrow() as InProcess
            val bob = bobFuture.getOrThrow() as InProcess
            CordaRPCClient(alice.rpcAddress).use("A", "A") { connection ->
                connection.proxy.startFlow(::CashIssueAndPaymentNoSelection, 1.DOLLARS, OpaqueBytes.of(0), bob.nodeInfo.legalIdentities[0], false, defaultNotaryIdentity).returnValue.getOrThrow()
            }
        }
    }
}