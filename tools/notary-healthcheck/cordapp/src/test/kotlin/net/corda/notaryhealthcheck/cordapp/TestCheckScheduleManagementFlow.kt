/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.notaryhealthcheck.cordapp

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.notaryhealthcheck.cordapp.StartAllChecksFlow.Companion.getTargets
import net.corda.notaryhealthcheck.utils.Monitorable
import net.corda.testing.core.getTestPartyAndCertificate
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.lang.Thread.sleep
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestCheckScheduleManagementFlow {
    private lateinit var mockNet: MockNetwork
    private lateinit var nodeA: StartedMockNode
    private lateinit var notary1: Party
    private lateinit var notary2: Party
    @Before
    fun setup() {
        mockNet = MockNetwork(
                threadPerNode = true,
                cordappPackages = listOf("net.corda.notaryhealthcheck.contract", "net.corda.notaryhealthcheck.cordapp"),
                notarySpecs = listOf(
                        MockNetworkNotarySpec(CordaX500Name.parse("O=Notary1, L=London, C=GB")),
                        MockNetworkNotarySpec(CordaX500Name.parse("O=Notary2, L=New York, C=US"))))

        nodeA = mockNet.createPartyNode()
        notary1 = mockNet.notaryNodes.first().info.legalIdentities.first()
        notary2 = mockNet.notaryNodes.last().info.legalIdentities.first()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun testStopCheckScheduleFlow() {
        val target = Monitorable(notary1, notary1)
        val target2 = Monitorable(notary2, notary2)
        nodeA.startFlow(InstallCheckScheduleStateFlow(listOf(nodeA.info.legalIdentities.first()), target, emptyList(), Instant.now().plusSeconds(1), Instant.MIN, 1, 5, UniqueIdentifier(null)))
        nodeA.startFlow(InstallCheckScheduleStateFlow(listOf(nodeA.info.legalIdentities.first()), target2, emptyList(), Instant.now().plusSeconds(1), Instant.MIN, 1, 5, UniqueIdentifier(null)))
        Thread.sleep(4.seconds.toMillis())
        nodeA.transaction {
            val successfulChecks = nodeA.services.vaultService.queryBy<SuccessfulCheckState>()
            assertTrue { successfulChecks.states.size > 4 }

            val pendingChecks = nodeA.services.vaultService.queryBy<ScheduledCheckState>(criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED))
            assertEquals(2, pendingChecks.states.size, "Expected exactly 2 pending (scheduled) checks, got ${pendingChecks.states.size}")
        }

        nodeA.startFlow(StopCheckScheduleFlow(target)).get()

        val pendingChecksAfterCleanUp = nodeA.transaction {
            nodeA.services.vaultService.queryBy<ScheduledCheckState>(criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED))
        }
        assertEquals(1, pendingChecksAfterCleanUp.states.size, "Expected 1 pending check to be removed, got ${pendingChecksAfterCleanUp.states.size}")
    }

    @Test
    fun testStoppingTwoForOneTarget() {
        val target = Monitorable(notary1, notary1)
        nodeA.startFlow(InstallCheckScheduleStateFlow(listOf(nodeA.info.legalIdentities.first()), target, emptyList(), Instant.now().plusSeconds(1), Instant.MIN, 2, 5, UniqueIdentifier(null)))
        nodeA.startFlow(InstallCheckScheduleStateFlow(listOf(nodeA.info.legalIdentities.first()), target, emptyList(), Instant.now().plusSeconds(1), Instant.MIN, 2, 5, UniqueIdentifier(null)))
        Thread.sleep(4.seconds.toMillis())
        nodeA.transaction {
            val successfulChecks = nodeA.services.vaultService.queryBy<SuccessfulCheckState>()
            assertTrue(successfulChecks.states.size > 3, "Expected at least 2 successful checks per notary by now, got ${successfulChecks.states.size}")

            val pendingChecks = nodeA.services.vaultService.queryBy<ScheduledCheckState>(criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED))
            assertEquals(2, pendingChecks.states.size, "Expected exactly 2 pending (scheduled) checks, got ${pendingChecks.states.size}")
        }

        nodeA.startFlow(StopCheckScheduleFlow(target)).get()

        val pendingChecksAfterCleanUp = nodeA.transaction {
            nodeA.services.vaultService.queryBy<ScheduledCheckState>(criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED))
        }
        assertTrue(pendingChecksAfterCleanUp.states.isEmpty(), "Expected all pending checks to be removed, got ${pendingChecksAfterCleanUp.states.size}")
    }

    @Test
    fun testStartAllFlows() {
        nodeA.startFlow(StartAllChecksFlow(2, 5))
        sleep(1.seconds.toMillis())
        nodeA.transaction {
            val pendingChecks = nodeA.services.vaultService.queryBy<ScheduledCheckState>(criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED))
            assertEquals(2, pendingChecks.states.size, "Expected exactly 2 pending (scheduled) checks, got ${pendingChecks.states.size}")
        }
        nodeA.startFlow(StopAllChecksFlow()).getOrThrow()
        val pendingChecksAfterCleanUp = nodeA.transaction {
            nodeA.services.vaultService.queryBy<ScheduledCheckState>(criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED))
        }
        assertTrue(pendingChecksAfterCleanUp.states.isEmpty(), "Expected all pending checks to be removed, got ${pendingChecksAfterCleanUp.states.size}")
    }

    @Test
    fun testGetTargets() {
        val notaries = listOf(
                Party(CordaX500Name("Notary1", "London", "GB"), generateKeyPair().public),
                Party(CordaX500Name("Notary2", "Ney York", "US"), generateKeyPair().public))

        val notary2Parties = listOf(
                Party(CordaX500Name("Notary2-1", "New York", "US"), generateKeyPair().public),
                Party(CordaX500Name("Notary2-2", "New York", "US"), generateKeyPair().public)
        )

        val nodeInfos = listOf(
                NodeInfo(
                        listOf(NetworkHostAndPort("localhost", 12345)),
                        listOf(getTestPartyAndCertificate(notaries[0])),
                        1,
                        1),
                NodeInfo(
                        listOf(NetworkHostAndPort("localhost", 12346)),
                        listOf(
                                getTestPartyAndCertificate(notary2Parties[0]),
                                getTestPartyAndCertificate(notaries[1])),
                        1,
                        2),
                NodeInfo(
                        listOf(NetworkHostAndPort("localhost", 12347)),
                        listOf(
                                getTestPartyAndCertificate(notary2Parties[1]),
                                getTestPartyAndCertificate(notaries[1])),
                        1,
                        3),
                NodeInfo(
                        listOf(NetworkHostAndPort("localhost", 12348)),
                        listOf(
                                getTestPartyAndCertificate(Party(CordaX500Name("Some node", "New York", "US"), generateKeyPair().public))),
                        1,
                        4)
        )

        val networkMap = mock<NetworkMapCache>().also {
            doReturn(notaries).whenever(it).notaryIdentities
            doReturn(nodeInfos).whenever(it).allNodes
        }

        val targets = getTargets(networkMap)
        assertEquals(4, targets.size, "Expected 4 targets. got ${targets.size}")
        assertTrue(notaries.all { it in targets.map { it.party } })
        assertTrue(notary2Parties.all { it in targets.map { it.party } })
    }

}