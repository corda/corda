package net.corda.kotlin.rpc

import java.io.FilterInputStream
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration.ofSeconds
import java.util.Currency
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import net.corda.client.rpc.CordaRPCConnection
import net.corda.client.rpc.notUsed
import net.corda.core.contracts.*
import net.corda.core.getOrThrow
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.sizedInputStreamAndHash
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.loggerFor
import net.corda.flows.CashIssueFlow
import net.corda.nodeapi.User
import org.junit.After
import org.junit.Before
import org.junit.Test

class StandaloneCordaRPClientTest {
    private companion object {
        val log = loggerFor<StandaloneCordaRPClientTest>()
        val buildDir: Path = Paths.get(System.getProperty("build.dir"))
        val nodesDir: Path = buildDir.resolve("nodes")
        val user = User("user1", "test", permissions = setOf("ALL"))
        val factory = NodeProcess.Factory(nodesDir)
        val port = AtomicInteger(15000)
        const val attachmentSize = 2116
        const val timeout = 60L
    }

    private lateinit var notary: NodeProcess
    private lateinit var rpcProxy: CordaRPCOps
    private lateinit var connection: CordaRPCConnection
    private lateinit var notaryIdentity: Party

    private val notaryConfig = NodeConfig(
        party = DUMMY_NOTARY,
        p2pPort = port.andIncrement,
        rpcPort = port.andIncrement,
        webPort = port.andIncrement,
        extraServices = listOf("corda.notary.validating"),
        users = listOf(user)
    )

    @Before
    fun setUp() {
        notary = factory.create(notaryConfig)
        connection = notary.connect()
        rpcProxy = connection.proxy
        notaryIdentity = fetchNotaryIdentity()
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
    fun `test attachment upload`() {
        val attachment = sizedInputStreamAndHash(attachmentSize)
        assertFalse(rpcProxy.attachmentExists(attachment.sha256))
        val id = WrapperStream(attachment.inputStream).use { rpcProxy.uploadAttachment(it) }
        assertEquals(id, attachment.sha256, "Attachment has incorrect SHA256 hash")
    }

    @Test
    fun `test starting flow`() {
        rpcProxy.startFlow(::CashIssueFlow, 127.POUNDS, OpaqueBytes.of(0), notaryIdentity, notaryIdentity)
            .returnValue.getOrThrow(ofSeconds(timeout))
    }

    @Test
    fun `test starting tracked flow`() {
        var trackCount = 0
        val handle = rpcProxy.startTrackedFlow(
            ::CashIssueFlow, 429.DOLLARS, OpaqueBytes.of(0), notaryIdentity, notaryIdentity
        )
        handle.progress.subscribe { msg ->
            log.info("Flow>> $msg")
            ++trackCount
        }
        handle.returnValue.getOrThrow(ofSeconds(timeout))
        assertNotEquals(0, trackCount)
    }

    @Test
    fun `test network map`() {
        assertEquals(DUMMY_NOTARY.name, notaryIdentity.name)
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
        rpcProxy.startFlow(::CashIssueFlow, 513.SWISS_FRANCS, OpaqueBytes.of(0), notaryIdentity, notaryIdentity)
            .returnValue.getOrThrow(ofSeconds(timeout))
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
        rpcProxy.startFlow(::CashIssueFlow, 629.POUNDS, OpaqueBytes.of(0), notaryIdentity, notaryIdentity)
            .returnValue.getOrThrow(ofSeconds(timeout))
        assertNotEquals(0, updateCount)

        // Check that this cash exists in the vault
        val cashBalance = rpcProxy.getCashBalances()
        log.info("Cash Balances: $cashBalance")
        assertEquals(1, cashBalance.size)
        assertEquals(629.POUNDS, cashBalance[Currency.getInstance("GBP")])
    }


    private fun fetchNotaryIdentity(): Party {
        val (nodeInfo, nodeUpdates) = rpcProxy.networkMapUpdates()
        nodeUpdates.notUsed()
        assertEquals(1, nodeInfo.size)
        return nodeInfo[0].legalIdentity
    }

    // This InputStream cannot have been whitelisted.
    private class WrapperStream(input: InputStream) : FilterInputStream(input)
}
