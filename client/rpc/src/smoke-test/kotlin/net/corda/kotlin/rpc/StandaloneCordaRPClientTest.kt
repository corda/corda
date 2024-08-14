package net.corda.kotlin.rpc

import com.google.common.hash.Hashing
import com.google.common.hash.HashingInputStream
import net.corda.client.rpc.CordaRPCConnection
import net.corda.client.rpc.PermissionException
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.InputStreamAndHash
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.messaging.vaultTrackBy
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.DEFAULT_PAGE_NUM
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.node.services.vault.SortAttribute
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import net.corda.finance.DOLLARS
import net.corda.finance.GBP
import net.corda.finance.POUNDS
import net.corda.finance.SWISS_FRANCS
import net.corda.finance.USD
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.finance.workflows.getCashBalance
import net.corda.finance.workflows.getCashBalances
import net.corda.nodeapi.internal.config.User
import net.corda.sleeping.SleepingFlow
import net.corda.smoketesting.NodeParams
import net.corda.smoketesting.NodeProcess
import org.hamcrest.text.MatchesPattern
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream.nullOutputStream
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StandaloneCordaRPClientTest {
    companion object {
        private val log = contextLogger()
        val superUser = User("superUser", "test", permissions = setOf("ALL"))
        val nonUser = User("nonUser", "test", permissions = emptySet())
        val rpcUser = User("rpcUser", "test", permissions = setOf("InvokeRpc.startFlow", "InvokeRpc.killFlow"))
        val flowUser = User("flowUser", "test", permissions = setOf("StartFlow.net.corda.finance.flows.CashIssueFlow"))
        val port = AtomicInteger(15200)
        const val ATTACHMENT_SIZE = 2116
        val timeout = 60.seconds

        private val factory = NodeProcess.Factory()

        private lateinit var notary: NodeProcess

        private val notaryConfig = NodeParams(
                legalName = CordaX500Name(organisation = "Notary Service", locality = "Zurich", country = "CH"),
                p2pPort = port.andIncrement,
                rpcPort = port.andIncrement,
                rpcAdminPort = port.andIncrement,
                users = listOf(superUser, nonUser, rpcUser, flowUser),
                cordappJars = gatherCordapps()
        )

        @BeforeClass
        @JvmStatic
        fun startNotary() {
            notary = factory.createNotaries(notaryConfig)[0]
        }

        @AfterClass
        @JvmStatic
        fun close() {
            factory.close()
        }

        @JvmStatic
        fun gatherCordapps(): List<Path> {
            return Path("build", "resources", "smokeTest").listDirectoryEntries("cordapp*.jar")
        }
    }

    private lateinit var connection: CordaRPCConnection
    private lateinit var rpcProxy: CordaRPCOps
    private lateinit var notaryNodeIdentity: Party

    @get:Rule
    val exception: ExpectedException = ExpectedException.none()

    @Before
    fun setUp() {
        connection = notary.connect(superUser)
        rpcProxy = connection.proxy
        notaryNodeIdentity = rpcProxy.nodeInfo().legalIdentitiesAndCerts.first().party
    }

    @After
    fun closeConnection() {
        connection.close()
    }

    @Test(timeout=300_000)
	fun `test attachments`() {
        val attachment = InputStreamAndHash.createInMemoryTestZip(ATTACHMENT_SIZE, 1)
        assertFalse(rpcProxy.attachmentExists(attachment.sha256))
        val id = attachment.inputStream.use { rpcProxy.uploadAttachment(it) }
        assertEquals(attachment.sha256, id, "Attachment has incorrect SHA256 hash")

        val hash = HashingInputStream(Hashing.sha256(), rpcProxy.openAttachment(id)).use {
            it.copyTo(nullOutputStream())
            SecureHash.createSHA256(it.hash().asBytes())
        }
        assertEquals(attachment.sha256, hash)
    }

    @Ignore("CORDA-1520 - After switching from Kryo to AMQP this test won't work")
    @Test(timeout=300_000)
	fun `test wrapped attachments`() {
        val attachment = InputStreamAndHash.createInMemoryTestZip(ATTACHMENT_SIZE, 1)
        assertFalse(rpcProxy.attachmentExists(attachment.sha256))
        val id = WrapperStream(attachment.inputStream).use { rpcProxy.uploadAttachment(it) }
        assertEquals(attachment.sha256, id, "Attachment has incorrect SHA256 hash")

        val hash = HashingInputStream(Hashing.sha256(), rpcProxy.openAttachment(id)).use {
            it.copyTo(nullOutputStream())
            SecureHash.createSHA256(it.hash().asBytes())
        }
        assertEquals(attachment.sha256, hash)
    }

    @Test(timeout=300_000)
	fun `test starting flow`() {
        rpcProxy.startFlow(::CashIssueFlow, 127.POUNDS, OpaqueBytes.of(0), notaryNodeIdentity)
                .returnValue.getOrThrow(timeout)
    }

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
	fun `test network map`() {
        assertEquals(notaryConfig.legalName, notaryNodeIdentity.name)
    }

    @Test(timeout=300_000)
	fun `test state machines`() {
        val (_, updates) = rpcProxy.stateMachinesFeed()

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

    @Test(timeout=300_000)
	fun `test vault track by`() {
        val initialGbpBalance = rpcProxy.getCashBalance(GBP)

        val (_, vaultUpdates) = rpcProxy.vaultTrackBy<Cash.State>(paging = PageSpecification(DEFAULT_PAGE_NUM))

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
        assertEquals(629.POUNDS, cashBalance[GBP]!! - initialGbpBalance)
    }

    @Test(timeout=300_000)
	fun `test vault query by`() {
        val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)
        val paging = PageSpecification(DEFAULT_PAGE_NUM, 10)
        val sorting = Sort(setOf(Sort.SortColumn(SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.DESC)))

        val initialStateCount = rpcProxy.vaultQueryBy<Cash.State>(criteria, paging, sorting).totalStatesAvailable
        val initialGbpBalance = rpcProxy.getCashBalance(GBP)

        // Now issue some cash
        rpcProxy.startFlow(::CashIssueFlow, 629.POUNDS, OpaqueBytes.of(0), notaryNodeIdentity)
                .returnValue.getOrThrow(timeout)

        val queryResults = rpcProxy.vaultQueryBy<Cash.State>(criteria, paging, sorting)
        assertEquals(1, queryResults.totalStatesAvailable - initialStateCount)
        assertEquals(queryResults.states.first().state.data.amount.quantity, 629.POUNDS.quantity)

        rpcProxy.startFlow(::CashPaymentFlow, 100.POUNDS, notaryNodeIdentity, true, notaryNodeIdentity).returnValue.getOrThrow()

        val moreResults = rpcProxy.vaultQueryBy<Cash.State>(criteria, paging, sorting)
        assertEquals(3, moreResults.totalStatesAvailable - initialStateCount)   // 629 - 100 + 100

        // Check that this cash exists in the vault
        val cashBalances = rpcProxy.getCashBalances()
        log.info("Cash Balances: $cashBalances")
        assertEquals(629.POUNDS, cashBalances[GBP]!! - initialGbpBalance)
    }

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
	fun `test kill flow without killFlow permission`() {
        exception.expect(PermissionException::class.java)
        exception.expectMessage(MatchesPattern(Pattern.compile("User not authorized to perform RPC call .*killFlow.*")))

        val flowHandle = rpcProxy.startFlow(::SleepingFlow, 1.minutes)
        notary.connect(nonUser).use { connection ->
            val rpcProxy = connection.proxy
            rpcProxy.killFlow(flowHandle.id)
        }
    }

    @Test(timeout=300_000)
	fun `test kill flow with killFlow permission`() {
        val flowHandle = rpcProxy.startFlow(::SleepingFlow, 1.minutes)
        notary.connect(rpcUser).use { connection ->
            val rpcProxy = connection.proxy
            assertTrue(rpcProxy.killFlow(flowHandle.id))
        }
    }

    private fun fetchNotaryIdentity(): NodeInfo {
        val nodeInfo = rpcProxy.networkMapSnapshot()
        assertEquals(1, nodeInfo.size)
        return nodeInfo[0]
    }

    // This InputStream cannot have been whitelisted.
    private class WrapperStream(input: InputStream) : FilterInputStream(input)
}
