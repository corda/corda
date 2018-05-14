/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.kotlin.rpc

import com.google.common.hash.Hashing
import com.google.common.hash.HashingInputStream
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.*
import net.corda.core.messaging.*
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.*
import net.corda.core.utilities.*
import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.finance.SWISS_FRANCS
import net.corda.finance.USD
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.getCashBalance
import net.corda.finance.contracts.getCashBalances
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.nodeapi.internal.config.User
import net.corda.smoketesting.NodeConfig
import net.corda.smoketesting.NodeProcess
import org.apache.commons.io.output.NullOutputStream
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.streams.toList
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StandaloneCordaRPClientTest {
    private companion object {
        private val log = contextLogger()
        val user = User("user1", "test", permissions = setOf("ALL"))
        val port = AtomicInteger(15200)
        const val attachmentSize = 2116
        val timeout = 60.seconds
    }

    private lateinit var factory: NodeProcess.Factory
    private lateinit var notary: NodeProcess
    private lateinit var rpcProxy: CordaRPCOps
    private lateinit var connection: CordaRPCConnection
    private lateinit var notaryNode: NodeInfo
    private lateinit var notaryNodeIdentity: Party

    private val notaryConfig = NodeConfig(
            legalName = CordaX500Name(organisation = "Notary Service", locality = "Zurich", country = "CH"),
            p2pPort = port.andIncrement,
            rpcPort = port.andIncrement,
            rpcAdminPort = port.andIncrement,
            isNotary = true,
            users = listOf(user)
    )

    @Before
    fun setUp() {
        factory = NodeProcess.Factory()
        copyFinanceCordapp()
        notary = factory.create(notaryConfig)
        connection = notary.connect()
        rpcProxy = connection.proxy
        notaryNode = fetchNotaryIdentity()
        notaryNodeIdentity = rpcProxy.nodeInfo().legalIdentitiesAndCerts.first().party
    }

    @After
    fun done() {
        try {
            connection.close()
        } finally {
            notary.close()
        }
    }

    private fun copyFinanceCordapp() {
        val cordappsDir = (factory.baseDirectory(notaryConfig) / NodeProcess.CORDAPPS_DIR_NAME).createDirectories()
        // Find the finance jar file for the smoke tests of this module
        val financeJar = Paths.get("build", "resources", "smokeTest").list {
            it.filter { "corda-finance" in it.toString() }.toList().single()
        }
        financeJar.copyToDirectory(cordappsDir)
    }

    @Test
    fun `test attachments`() {
        val attachment = InputStreamAndHash.createInMemoryTestZip(attachmentSize, 1)
        assertFalse(rpcProxy.attachmentExists(attachment.sha256))
        val id = WrapperStream(attachment.inputStream).use { rpcProxy.uploadAttachment(it) }
        assertEquals(attachment.sha256, id, "Attachment has incorrect SHA256 hash")

        val hash = HashingInputStream(Hashing.sha256(), rpcProxy.openAttachment(id)).use { it ->
            it.copyTo(NullOutputStream())
            SecureHash.SHA256(it.hash().asBytes())
        }
        assertEquals(attachment.sha256, hash)
    }

    @Test
    fun `test starting flow`() {
        rpcProxy.startFlow(::CashIssueFlow, 127.POUNDS, OpaqueBytes.of(0), notaryNodeIdentity)
                .returnValue.getOrThrow(timeout)
    }

    @Test
    fun `test starting tracked flow`() {
        var trackCount = 0
        val handle = rpcProxy.startTrackedFlow(
                ::CashIssueFlow, 429.DOLLARS, OpaqueBytes.of(0), notaryNodeIdentity
        )
        val updateLatch = CountDownLatch(1)
        handle.progress.subscribe { msg ->
            log.info("Flow>> $msg")
            ++trackCount
            updateLatch.countDown()
        }
        handle.returnValue.getOrThrow(timeout)
        updateLatch.await()
        assertNotEquals(0, trackCount)
    }

    @Test
    fun `test network map`() {
        assertEquals(notaryConfig.legalName, notaryNodeIdentity.name)
    }

    @Test
    fun `test state machines`() {
        val (stateMachines, updates) = rpcProxy.stateMachinesFeed()
        assertEquals(0, stateMachines.size)

        val updateLatch = CountDownLatch(1)
        val updateCount = AtomicInteger(0)
        updates.subscribe { update ->
            if (update is StateMachineUpdate.Added) {
                log.info("StateMachine>> Id=${update.id}")
                updateCount.incrementAndGet()
                updateLatch.countDown()
            }
        }

        // Now issue some cash
        rpcProxy.startFlow(::CashIssueFlow, 513.SWISS_FRANCS, OpaqueBytes.of(0), notaryNodeIdentity)
                .returnValue.getOrThrow(timeout)
        updateLatch.await()
        assertEquals(1, updateCount.get())
    }

    @Test
    fun `test vault track by`() {
        val (vault, vaultUpdates) = rpcProxy.vaultTrackBy<Cash.State>(paging = PageSpecification(DEFAULT_PAGE_NUM))
        assertEquals(0, vault.totalStatesAvailable)

        val updateLatch = CountDownLatch(1)
        vaultUpdates.subscribe { update ->
            log.info("Vault>> FlowId=${update.flowId}")
            updateLatch.countDown()
        }

        // Now issue some cash
        rpcProxy.startFlow(::CashIssueFlow, 629.POUNDS, OpaqueBytes.of(0), notaryNodeIdentity)
                .returnValue.getOrThrow(timeout)
        updateLatch.await()

        // Check that this cash exists in the vault
        val cashBalance = rpcProxy.getCashBalances()
        log.info("Cash Balances: $cashBalance")
        assertEquals(1, cashBalance.size)
        assertEquals(629.POUNDS, cashBalance[Currency.getInstance("GBP")])
    }

    @Test
    fun `test vault query by`() {
        // Now issue some cash
        rpcProxy.startFlow(::CashIssueFlow, 629.POUNDS, OpaqueBytes.of(0), notaryNodeIdentity)
                .returnValue.getOrThrow(timeout)

        val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)
        val paging = PageSpecification(DEFAULT_PAGE_NUM, 10)
        val sorting = Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))

        val queryResults = rpcProxy.vaultQueryBy<Cash.State>(criteria, paging, sorting)
        assertEquals(1, queryResults.totalStatesAvailable)
        assertEquals(queryResults.states.first().state.data.amount.quantity, 629.POUNDS.quantity)

        rpcProxy.startFlow(::CashPaymentFlow, 100.POUNDS, notaryNodeIdentity).returnValue.getOrThrow()

        val moreResults = rpcProxy.vaultQueryBy<Cash.State>(criteria, paging, sorting)
        assertEquals(3, moreResults.totalStatesAvailable)   // 629 - 100 + 100

        // Check that this cash exists in the vault
        val cashBalances = rpcProxy.getCashBalances()
        log.info("Cash Balances: $cashBalances")
        assertEquals(1, cashBalances.size)
        assertEquals(629.POUNDS, cashBalances[Currency.getInstance("GBP")])
    }

    @Test
    fun `test cash balances`() {
        val startCash = rpcProxy.getCashBalances()
        println(startCash)
        assertTrue(startCash.isEmpty(), "Should not start with any cash")

        val flowHandle = rpcProxy.startFlow(::CashIssueFlow, 629.DOLLARS, OpaqueBytes.of(0), notaryNodeIdentity)
        println("Started issuing cash, waiting on result")
        flowHandle.returnValue.get()

        val balance = rpcProxy.getCashBalance(USD)
        println("Balance: $balance")
        assertEquals(629.DOLLARS, balance)
    }

    private fun fetchNotaryIdentity(): NodeInfo {
        val nodeInfo = rpcProxy.networkMapSnapshot()
        assertEquals(1, nodeInfo.size)
        return nodeInfo[0]
    }

    // This InputStream cannot have been whitelisted.
    private class WrapperStream(input: InputStream) : FilterInputStream(input)
}
