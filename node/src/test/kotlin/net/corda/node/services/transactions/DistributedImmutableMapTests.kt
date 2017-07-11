package net.corda.node.services.transactions

import io.atomix.catalyst.transport.Address
import io.atomix.copycat.client.ConnectionStrategies
import io.atomix.copycat.client.CopycatClient
import io.atomix.copycat.server.CopycatServer
import io.atomix.copycat.server.storage.Storage
import io.atomix.copycat.server.storage.StorageLevel
import net.corda.core.getOrThrow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.testing.LogHelper
import net.corda.node.services.network.NetworkMapService
import net.corda.node.utilities.CordaPersistence
import net.corda.node.utilities.configureDatabase
import net.corda.testing.freeLocalHostAndPort
import net.corda.testing.node.makeTestDataSourceProperties
import org.jetbrains.exposed.sql.Transaction
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DistributedImmutableMapTests {
    data class Member(val client: CopycatClient, val server: CopycatServer)

    lateinit var cluster: List<Member>
    lateinit var transaction: Transaction
    lateinit var database: CordaPersistence

    @Before
    fun setup() {
        LogHelper.setLevel("-org.apache.activemq")
        LogHelper.setLevel(NetworkMapService::class)
        database = configureDatabase(makeTestDataSourceProperties())
        cluster = setUpCluster()
    }

    @After
    fun tearDown() {
        LogHelper.reset("org.apache.activemq")
        LogHelper.reset(NetworkMapService::class)
        cluster.forEach {
            it.client.close()
            it.server.shutdown()
        }
        database.close()
    }

    @Test
    fun `stores entries correctly`() {
        val client = cluster.last().client

        val entries = mapOf("key1" to "value1", "key2" to "value2")

        val conflict = client.submit(DistributedImmutableMap.Commands.PutAll(entries)).getOrThrow()
        assertTrue { conflict.isEmpty() }

        val value1 = client.submit(DistributedImmutableMap.Commands.Get<String, String>("key1"))
        val value2 = client.submit(DistributedImmutableMap.Commands.Get<String, String>("key2"))

        assertEquals(value1.getOrThrow(), "value1")
        assertEquals(value2.getOrThrow(), "value2")
    }

    @Test
    fun `returns conflict for duplicate entries`() {
        val client = cluster.last().client

        val entries = mapOf("key1" to "value1", "key2" to "value2")

        var conflict = client.submit(DistributedImmutableMap.Commands.PutAll(entries)).getOrThrow()
        assertTrue { conflict.isEmpty() }
        conflict = client.submit(DistributedImmutableMap.Commands.PutAll(entries)).getOrThrow()
        assertTrue { conflict == entries }
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

        val stateMachineFactory = { DistributedImmutableMap<String, ByteArray>(database, "commited_states_${myAddress.port}") }

        val server = CopycatServer.builder(address)
                .withStateMachine(stateMachineFactory)
                .withStorage(storage)
                .build()

        val serverInitFuture = if (clusterAddress != null) {
            val cluster = Address(clusterAddress.host, clusterAddress.port)
            server.join(cluster)
        } else {
            server.bootstrap()
        }

        val client = CopycatClient.builder(address)
                .withConnectionStrategy(ConnectionStrategies.EXPONENTIAL_BACKOFF)
                .build()
        return serverInitFuture.thenCompose { client.connect(address) }.thenApply { Member(it, server) }
    }
}