package net.corda.node.services.transactions

import io.atomix.catalyst.transport.Address
import io.atomix.copycat.client.ConnectionStrategies
import io.atomix.copycat.client.CopycatClient
import io.atomix.copycat.server.CopycatServer
import io.atomix.copycat.server.storage.Storage
import io.atomix.copycat.server.storage.StorageLevel
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.network.NetworkMapService
import net.corda.node.utilities.CordaPersistence
import net.corda.node.utilities.configureDatabase
import net.corda.testing.*
import net.corda.testing.node.makeTestDataSourceProperties
import net.corda.testing.node.makeTestDatabaseProperties
import net.corda.testing.node.makeTestIdentityService
import org.jetbrains.exposed.sql.Transaction
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DistributedImmutableMapTests : TestDependencyInjectionBase() {
    data class Member(val client: CopycatClient, val server: CopycatServer)

    lateinit var cluster: List<Member>
    lateinit var transaction: Transaction
    private val databases: List<CordaPersistence> = mutableListOf()

    @Before
    fun setup() {
        LogHelper.setLevel("-org.apache.activemq")
        LogHelper.setLevel(NetworkMapService::class)
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
        databases.forEach { it.close() }
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
        val database = configureDatabase(makeTestDataSourceProperties(), makeTestDatabaseProperties("serverNameTablePrefix", "${myAddress.port}"), identitySvc = ::makeTestIdentityService)
        val stateMachineFactory = { DistributedImmutableMap(database, RaftUniquenessProvider.Companion::createKeyMap ) }

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