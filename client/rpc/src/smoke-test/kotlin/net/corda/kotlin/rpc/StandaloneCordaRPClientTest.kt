package net.corda.kotlin.rpc

import com.google.common.hash.Hashing
import com.google.common.hash.HashingInputStream
import net.corda.client.rpc.CordaRPCConnection
import net.corda.client.rpc.notUsed
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.DOLLARS
import net.corda.core.contracts.POUNDS
import net.corda.core.contracts.SWISS_FRANCS
import net.corda.core.crypto.SecureHash
import net.corda.core.getOrThrow
import net.corda.core.messaging.*
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.*
import net.corda.core.seconds
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.sizedInputStreamAndHash
import net.corda.core.utilities.loggerFor
import net.corda.flows.CashIssueFlow
import net.corda.flows.CashPaymentFlow
import net.corda.nodeapi.User
import net.corda.smoketesting.NodeConfig
import net.corda.smoketesting.NodeProcess
import org.apache.commons.io.output.NullOutputStream
import org.bouncycastle.asn1.x500.X500Name
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.FilterInputStream
import java.io.InputStream
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class StandaloneCordaRPClientTest {
    private companion object {
        val log = loggerFor<StandaloneCordaRPClientTest>()
        val user = User("user1", "test", permissions = setOf("ALL"))
        val port = AtomicInteger(15000)
        const val attachmentSize = 2116
        val timeout = 60.seconds
    }

    private lateinit var notary: NodeProcess
    private lateinit var rpcProxy: CordaRPCOps
    private lateinit var connection: CordaRPCConnection
    private lateinit var notaryNode: NodeInfo

    private val notaryConfig = NodeConfig(
        legalName = X500Name("CN=Notary Service,O=R3,OU=corda,L=Zurich,C=CH"),
        p2pPort = port.andIncrement,
        rpcPort = port.andIncrement,
        webPort = port.andIncrement,
        extraServices = listOf("corda.notary.validating"),
        users = listOf(user)
    )

    @Before
    fun setUp() {
        notary = NodeProcess.Factory().create(notaryConfig)
        connection = notary.connect()
        rpcProxy = connection.proxy
        notaryNode = fetchNotaryIdentity()
    }

    @After
    fun done() {
        try {
            connection.close()
        } finally {
            notary.close()
        }
    }

    @Test
    fun `test attachments`() {
        val attachment = sizedInputStreamAndHash(attachmentSize)
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
        rpcProxy.startFlow(::CashIssueFlow, 127.POUNDS, OpaqueBytes.of(0), notaryNode.legalIdentity, notaryNode.notaryIdentity)
            .returnValue.getOrThrow(timeout)
    }

    @Test
    fun `test starting tracked flow`() {
        var trackCount = 0
        val handle = rpcProxy.startTrackedFlow(
            ::CashIssueFlow, 429.DOLLARS, OpaqueBytes.of(0), notaryNode.legalIdentity, notaryNode.notaryIdentity
        )
        handle.progress.subscribe { msg ->
            log.info("Flow>> $msg")
            ++trackCount
        }
        handle.returnValue.getOrThrow(timeout)
        assertNotEquals(0, trackCount)
    }

    @Test
    fun `test network map`() {
        assertEquals(notaryConfig.legalName, notaryNode.legalIdentity.name)
    }

    @Test
    fun `test state machines`() {
        val (stateMachines, updates) = rpcProxy.stateMachinesAndUpdates()
        assertEquals(0, stateMachines.size)

        var updateCount = 0
        updates.subscribe { update ->
            if (update is StateMachineUpdate.Added) {
                log.info("StateMachine>> Id=${update.id}")
                ++updateCount
            }
        }

        // Now issue some cash
        rpcProxy.startFlow(::CashIssueFlow, 513.SWISS_FRANCS, OpaqueBytes.of(0), notaryNode.legalIdentity, notaryNode.notaryIdentity)
            .returnValue.getOrThrow(timeout)
        assertEquals(1, updateCount)
    }

    @Test
    fun `test vault`() {
        val (vault, vaultUpdates) = rpcProxy.vaultAndUpdates()
        assertEquals(0, vault.size)

        var updateCount = 0
        vaultUpdates.subscribe { update ->
            log.info("Vault>> FlowId=${update.flowId}")
            ++updateCount
        }

        // Now issue some cash
        rpcProxy.startFlow(::CashIssueFlow, 629.POUNDS, OpaqueBytes.of(0), notaryNode.legalIdentity, notaryNode.notaryIdentity)
            .returnValue.getOrThrow(timeout)
        assertNotEquals(0, updateCount)

        // Check that this cash exists in the vault
        val cashBalance = rpcProxy.getCashBalances()
        log.info("Cash Balances: $cashBalance")
        assertEquals(1, cashBalance.size)
        assertEquals(629.POUNDS, cashBalance[Currency.getInstance("GBP")])
    }

    @Test
    fun `test vault track by`() {
        val (vault, vaultUpdates) = rpcProxy.vaultTrackBy<Cash.State>()
        assertEquals(0, vault.totalStatesAvailable)

        var updateCount = 0
        vaultUpdates.subscribe { update ->
            log.info("Vault>> FlowId=${update.flowId}")
            ++updateCount
        }

        // Now issue some cash
        rpcProxy.startFlow(::CashIssueFlow, 629.POUNDS, OpaqueBytes.of(0), notaryNode.legalIdentity, notaryNode.notaryIdentity)
                .returnValue.getOrThrow(timeout)
        assertNotEquals(0, updateCount)

        // Check that this cash exists in the vault
        val cashBalance = rpcProxy.getCashBalances()
        log.info("Cash Balances: $cashBalance")
        assertEquals(1, cashBalance.size)
        assertEquals(629.POUNDS, cashBalance[Currency.getInstance("GBP")])
    }

    @Test
    fun `test vault query by`() {
        // Now issue some cash
        rpcProxy.startFlow(::CashIssueFlow, 629.POUNDS, OpaqueBytes.of(0), notaryNode.legalIdentity, notaryNode.notaryIdentity)
                .returnValue.getOrThrow(timeout)

        val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)
        val paging = PageSpecification(DEFAULT_PAGE_NUM, 10)
        val sorting = Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))

        val queryResults = rpcProxy.vaultQueryBy<Cash.State>(criteria, paging, sorting)
        assertEquals(1, queryResults.totalStatesAvailable)
        assertEquals(queryResults.states.first().state.data.amount.quantity, 629.POUNDS.quantity)

        rpcProxy.startFlow(::CashPaymentFlow, 100.POUNDS, notaryNode.legalIdentity).returnValue.getOrThrow()

        val moreResults = rpcProxy.vaultQueryBy<Cash.State>(criteria, paging, sorting)
        assertEquals(3, moreResults.totalStatesAvailable)   // 629 - 100 + 100

        // Check that this cash exists in the vault
        val cashBalance = rpcProxy.getCashBalances()
        log.info("Cash Balances: $cashBalance")
        assertEquals(1, cashBalance.size)
        assertEquals(629.POUNDS, cashBalance[Currency.getInstance("GBP")])
    }

    private fun fetchNotaryIdentity(): NodeInfo {
        val (nodeInfo, nodeUpdates) = rpcProxy.networkMapFeed()
        nodeUpdates.notUsed()
        assertEquals(1, nodeInfo.size)
        return nodeInfo[0]
    }

    // This InputStream cannot have been whitelisted.
    private class WrapperStream(input: InputStream) : FilterInputStream(input)
}
