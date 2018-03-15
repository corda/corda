/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.transactions

import io.atomix.catalyst.transport.Address
import io.atomix.copycat.client.ConnectionStrategies
import io.atomix.copycat.client.CopycatClient
import io.atomix.copycat.server.CopycatServer
import io.atomix.copycat.server.storage.Storage
import io.atomix.copycat.server.storage.StorageLevel
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
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
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        val conflict = client.submit(commitCommand).getOrThrow()
        assertTrue { conflict.isEmpty() }

        val value1 = client.submit(RaftTransactionCommitLog.Commands.Get(states[0]))
        val value2 = client.submit(RaftTransactionCommitLog.Commands.Get(states[1]))

        assertEquals(value1.getOrThrow(), txId)
        assertEquals(value2.getOrThrow(), txId)
    }

    @Test
    fun `returns conflict for duplicate entries`() {
        val client = cluster.last().client

        val states = listOf(StateRef(SecureHash.randomSHA256(), 0), StateRef(SecureHash.randomSHA256(), 0))
        val txId: SecureHash = SecureHash.randomSHA256()
        val requestingPartyName = ALICE_NAME
        val requestSignature = ByteArray(1024)

        val commitCommand = RaftTransactionCommitLog.Commands.CommitTransaction(states, txId, requestingPartyName.toString(), requestSignature)
        var conflict = client.submit(commitCommand).getOrThrow()
        assertTrue { conflict.isEmpty() }

        conflict = client.submit(commitCommand).getOrThrow()
        assertEquals(conflict.keys, states.toSet())
        conflict.forEach { assertEquals(it.value, txId) }
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
        val database = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(runMigration = true), rigorousMock(), NodeSchemaService(includeNotarySchemas = true))
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