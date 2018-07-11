package net.corda.node.services.transactions

import io.atomix.catalyst.transport.Address
import io.atomix.copycat.client.ConnectionStrategies
import io.atomix.copycat.client.CopycatClient
import io.atomix.copycat.server.CopycatServer
import io.atomix.copycat.server.storage.Storage
import io.atomix.copycat.server.storage.StorageLevel
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.NotaryError
import net.corda.core.internal.concurrent.asCordaFuture
import net.corda.core.internal.concurrent.transpose
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.configureDatabase
import net.corda.node.services.schema.NodeSchemaService
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.freeLocalHostAndPort
import net.corda.testing.internal.LogHelper
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.hamcrest.Matchers.instanceOf
import org.junit.*
import org.junit.Assert.assertThat
import java.time.Clock
import java.time.Instant
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RaftTransactionCommitLogTests {
    data class Member(val client: CopycatClient, val server: CopycatServer)

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private lateinit var cluster: List<Member>
    private val databases: MutableList<CordaPersistence> = mutableListOf()

    @Before
    fun setup() {
        LogHelper.setLevel("-org.apache.activemq")
        LogHelper.setLevel("+io.atomix")
        cluster = setUpCluster()
    }

    @After
    fun tearDown() {
        LogHelper.reset("org.apache.activemq", "io.atomix")
        cluster.map { it.client.close().asCordaFuture() }.transpose().getOrThrow()
        cluster.map { it.server.shutdown().asCordaFuture() }.transpose().getOrThrow()
        databases.forEach { it.close() }
    }

    @Test
    fun `stores entries correctly`() {
        val client = cluster.last().client

        val states = listOf(StateRef(SecureHash.randomSHA256(), 0), StateRef(SecureHash.randomSHA256(), 0))
        val txId: SecureHash = SecureHash.randomSHA256()
        val requestingPartyName = ALICE_NAME
        val requestSignature = ByteArray(1024)

        val commitCommand = RaftTransactionCommitLog.Commands.CommitTransaction(states, txId, requestingPartyName.toString(), requestSignature)
        val commitError = client.submit(commitCommand).getOrThrow()
        assertNull(commitError)

        val value1 = client.submit(RaftTransactionCommitLog.Commands.Get(states[0]))
        val value2 = client.submit(RaftTransactionCommitLog.Commands.Get(states[1]))

        assertEquals(value1.getOrThrow(), txId)
        assertEquals(value2.getOrThrow(), txId)
    }

    @Test
    fun `returns conflict for duplicate entries`() {
        val client = cluster.last().client

        val states = listOf(StateRef(SecureHash.randomSHA256(), 0), StateRef(SecureHash.randomSHA256(), 0))
        val txIdFirst = SecureHash.randomSHA256()
        val txIdSecond = SecureHash.randomSHA256()
        val requestingPartyName = ALICE_NAME
        val requestSignature = ByteArray(1024)

        val commitCommandFirst = RaftTransactionCommitLog.Commands.CommitTransaction(states, txIdFirst, requestingPartyName.toString(), requestSignature)
        var commitError = client.submit(commitCommandFirst).getOrThrow()
        assertNull(commitError)

        val commitCommandSecond = RaftTransactionCommitLog.Commands.CommitTransaction(states, txIdSecond, requestingPartyName.toString(), requestSignature)
        commitError = client.submit(commitCommandSecond).getOrThrow()
        val conflict = commitError as NotaryError.Conflict
        assertEquals(states.toSet(), conflict.consumedStates.keys)
    }

    @Test
    fun `transactions outside their time window are rejected`() {
        val client = cluster.last().client

        val states = listOf(StateRef(SecureHash.randomSHA256(), 0), StateRef(SecureHash.randomSHA256(), 0))
        val txId: SecureHash = SecureHash.randomSHA256()
        val requestingPartyName = ALICE_NAME
        val requestSignature = ByteArray(1024)
        val timeWindow = TimeWindow.fromOnly(Instant.MAX)

        val commitCommand = RaftTransactionCommitLog.Commands.CommitTransaction(
                states, txId, requestingPartyName.toString(), requestSignature, timeWindow
        )
        val commitError = client.submit(commitCommand).getOrThrow()
        assertThat(commitError, instanceOf(NotaryError.TimeWindowInvalid::class.java))
    }

    @Test
    fun `transactions can be re-notarised outside their time window`() {
        val client = cluster.last().client

        val states = listOf(StateRef(SecureHash.randomSHA256(), 0), StateRef(SecureHash.randomSHA256(), 0))
        val txId: SecureHash = SecureHash.randomSHA256()
        val requestingPartyName = ALICE_NAME
        val requestSignature = ByteArray(1024)
        val timeWindow = TimeWindow.fromOnly(Instant.MIN)

        val commitCommand = RaftTransactionCommitLog.Commands.CommitTransaction(
                states, txId, requestingPartyName.toString(), requestSignature, timeWindow
        )
        val commitError = client.submit(commitCommand).getOrThrow()
        assertNull(commitError)

        val expiredTimeWindow = TimeWindow.untilOnly(Instant.MIN)
        val commitCommand2 = RaftTransactionCommitLog.Commands.CommitTransaction(
                states, txId, requestingPartyName.toString(), requestSignature, expiredTimeWindow
        )
        val commitError2 = client.submit(commitCommand2).getOrThrow()
        assertNull(commitError2)
    }

    private fun setUpCluster(nodeCount: Int = 3): List<Member> {
        val clusterAddress = freeLocalHostAndPort()
        val cluster = mutableListOf(createReplica(clusterAddress))
        for (i in 1..nodeCount) cluster.add(createReplica(freeLocalHostAndPort(), clusterAddress))
        return cluster.map { it.getOrThrow() }
    }

    private fun createReplica(myAddress: NetworkHostAndPort, clusterAddress: NetworkHostAndPort? = null): CompletableFuture<Member> {
        val storage = Storage.builder().withStorageLevel(StorageLevel.MEMORY).build()
        val address = Address(myAddress.host, myAddress.port)
        val database = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(serverNameTablePrefix = "PORT_${myAddress.port}_"), { null }, { null }, NodeSchemaService(includeNotarySchemas = true))
        databases.add(database)
        val stateMachineFactory = { RaftTransactionCommitLog(database, Clock.systemUTC(), RaftUniquenessProvider.Companion::createMap) }

        val server = CopycatServer.builder(address)
                .withStateMachine(stateMachineFactory)
                .withStorage(storage)
                .withSerializer(RaftTransactionCommitLog.serializer)
                .build()

        val serverInitFuture = if (clusterAddress != null) {
            val cluster = Address(clusterAddress.host, clusterAddress.port)
            server.join(cluster)
        } else {
            server.bootstrap()
        }

        val client = CopycatClient.builder(address)
                .withConnectionStrategy(ConnectionStrategies.EXPONENTIAL_BACKOFF)
                .withSerializer(RaftTransactionCommitLog.serializer)
                .build()
        return serverInitFuture.thenCompose { client.connect(address) }.thenApply { Member(it, server) }
    }
}