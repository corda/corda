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

import com.codahale.metrics.Gauge
import com.codahale.metrics.MetricRegistry
import io.atomix.catalyst.transport.Address
import io.atomix.catalyst.transport.Transport
import io.atomix.catalyst.transport.netty.NettyTransport
import io.atomix.catalyst.transport.netty.SslProtocol
import io.atomix.copycat.client.ConnectionStrategies
import io.atomix.copycat.client.CopycatClient
import io.atomix.copycat.client.RecoveryStrategies
import io.atomix.copycat.server.CopycatServer
import io.atomix.copycat.server.cluster.Member
import io.atomix.copycat.server.storage.Storage
import io.atomix.copycat.server.storage.StorageLevel
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.identity.Party
import net.corda.core.internal.notary.NotaryInternalException
import net.corda.core.internal.notary.UniquenessProvider
import net.corda.core.schemas.PersistentStateRef
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.services.config.RaftConfig
import net.corda.node.services.transactions.RaftTransactionCommitLog.Commands.CommitTransaction
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.config.NodeSSLConfiguration
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.CompletableFuture
import javax.annotation.concurrent.ThreadSafe
import javax.persistence.Column
import javax.persistence.EmbeddedId
import javax.persistence.Entity
import javax.persistence.Table

/**
 * A uniqueness provider that records committed input states in a distributed collection replicated and
 * persisted in a Raft cluster, using the Copycat framework (http://atomix.io/copycat/).
 *
 * The uniqueness provider maintains both a Copycat cluster node (server) and a client through which it can submit
 * requests to the cluster. In Copycat, a client request is first sent to the server it's connected to and then redirected
 * to the cluster leader to be actioned.
 */
@ThreadSafe
class RaftUniquenessProvider(
        private val transportConfiguration: NodeSSLConfiguration,
        private val db: CordaPersistence,
        private val clock: Clock,
        private val metrics: MetricRegistry,
        private val raftConfig: RaftConfig
) : UniquenessProvider, SingletonSerializeAsToken() {
    companion object {
        private val log = contextLogger()
        fun createMap(): AppendOnlyPersistentMap<StateRef, Pair<Long, SecureHash>, CommittedState, PersistentStateRef> =
                AppendOnlyPersistentMap(
                        "RaftUniquenessProvider_transactions",
                        toPersistentEntityKey = { PersistentStateRef(it) },
                        fromPersistentEntity = {
                            val txId = it.id.txId
                            val index = it.id.index
                            Pair(
                                    StateRef(txhash = SecureHash.parse(txId), index = index),
                                    Pair(it.index, SecureHash.parse(it.value) as SecureHash))

                        },
                        toPersistentEntity = { k: StateRef, (first, second) ->
                            CommittedState(
                                    PersistentStateRef(k),
                                    second.toString(),
                                    first)

                        },
                        persistentEntityClass = CommittedState::class.java
                )

        fun StateRef.encoded() = "$txhash:$index"
        fun String.parseStateRef() = split(":").let { StateRef(SecureHash.parse(it[0]), it[1].toInt()) }
    }

    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}raft_committed_states")
    class CommittedState(
            @EmbeddedId
            val id: PersistentStateRef,
            @Column(name = "consuming_transaction_id", nullable = true)
            var value: String? = "",
            @Column(name = "raft_log_index", nullable = false)
            var index: Long = 0
    )

    /** Directory storing the Raft log and state machine snapshots */
    private val storagePath: Path = transportConfiguration.baseDirectory
    private lateinit var _clientFuture: CompletableFuture<CopycatClient>
    private lateinit var server: CopycatServer

    /**
     * Copycat clients are responsible for connecting to the cluster and submitting commands and queries that operate
     * on the cluster's replicated state machine.
     */
    private val client: CopycatClient
        get() = _clientFuture.get()

    fun start() {
        log.info("Creating Copycat server, log stored in: ${storagePath.toAbsolutePath()}")
        val stateMachineFactory = {
            RaftTransactionCommitLog(db, clock, RaftUniquenessProvider.Companion::createMap)
        }
        val address = raftConfig.nodeAddress.let { Address(it.host, it.port) }
        val storage = buildStorage(storagePath)
        val transport = buildTransport(transportConfiguration)

        server = CopycatServer.builder(address)
                .withStateMachine(stateMachineFactory)
                .withStorage(storage)
                .withServerTransport(transport)
                .withSerializer(RaftTransactionCommitLog.serializer)
                .build()

        val serverFuture = if (raftConfig.clusterAddresses.isNotEmpty()) {
            log.info("Joining an existing Copycat cluster at ${raftConfig.clusterAddresses}")
            val cluster = raftConfig.clusterAddresses.map { Address(it.host, it.port) }
            server.join(cluster)
        } else {
            log.info("Bootstrapping a Copycat cluster at $address")
            server.bootstrap()
        }

        registerMonitoring()

        val client = CopycatClient.builder(address)
                .withTransport(transport) // TODO: use local transport for client-server communications
                .withConnectionStrategy(ConnectionStrategies.EXPONENTIAL_BACKOFF)
                .withSerializer(RaftTransactionCommitLog.serializer)
                .withRecoveryStrategy(RecoveryStrategies.RECOVER)
                .build()
        _clientFuture = serverFuture.thenCompose { client.connect(address) }
    }

    fun stop() {
        server.shutdown()
    }

    private fun buildStorage(storagePath: Path): Storage? {
        return Storage.builder()
                .withDirectory(storagePath.toFile())
                .withStorageLevel(StorageLevel.DISK)
                .build()
    }

    private fun buildTransport(config: SSLConfiguration): Transport? {
        return NettyTransport.builder()
                .withSsl()
                .withSslProtocol(SslProtocol.TLSv1_2)
                .withKeyStorePath(config.sslKeystore.toString())
                .withKeyStorePassword(config.keyStorePassword)
                .withTrustStorePath(config.trustStoreFile.toString())
                .withTrustStorePassword(config.trustStorePassword)
                .build()
    }

    private fun registerMonitoring() {
        metrics.register("RaftCluster.ThisServerStatus", Gauge<String> {
            server.state().name
        })
        metrics.register("RaftCluster.MembersCount", Gauge<Int> {
            server.cluster().members().size
        })
        metrics.register("RaftCluster.Members", Gauge<List<String>> {
            server.cluster().members().map { it.address().toString() }
        })

        metrics.register("RaftCluster.AvailableMembers", Gauge<List<String>> {
            server.cluster().members().filter { it.status() == Member.Status.AVAILABLE }.map { it.address().toString() }
        })

        metrics.register("RaftCluster.AvailableMembersCount", Gauge<Int> {
            server.cluster().members().filter { it.status() == Member.Status.AVAILABLE }.size
        })
    }

    override fun commit(
            states: List<StateRef>,
            txId: SecureHash,
            callerIdentity: Party,
            requestSignature: NotarisationRequestSignature,
            timeWindow: TimeWindow?,
            references: List<StateRef>
    ) {
        log.debug { "Attempting to commit input states: ${states.joinToString()}" }
        val commitCommand = CommitTransaction(
                states,
                txId,
                callerIdentity.name.toString(),
                requestSignature.serialize().bytes,
                timeWindow,
                references
        )
        val commitError = client.submit(commitCommand).get()
        if (commitError != null) throw NotaryInternalException(commitError)
        log.debug { "All input states of transaction $txId have been committed" }
    }
}


