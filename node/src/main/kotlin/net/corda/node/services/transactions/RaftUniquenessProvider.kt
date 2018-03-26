package net.corda.node.services.transactions

import com.codahale.metrics.Gauge
import com.codahale.metrics.MetricRegistry
import io.atomix.catalyst.buffer.BufferInput
import io.atomix.catalyst.buffer.BufferOutput
import io.atomix.catalyst.serializer.Serializer
import io.atomix.catalyst.serializer.TypeSerializer
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
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryInternalException
import net.corda.core.flows.StateConsumptionDetails
import net.corda.core.identity.Party
import net.corda.core.node.services.UniquenessProvider
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.node.services.config.RaftConfig
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.config.NodeSSLConfiguration
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import javax.annotation.concurrent.ThreadSafe
import javax.persistence.*

/**
 * A uniqueness provider that records committed input states in a distributed collection replicated and
 * persisted in a Raft cluster, using the Copycat framework (http://atomix.io/copycat/).
 *
 * The uniqueness provider maintains both a Copycat cluster node (server) and a client through which it can submit
 * requests to the cluster. In Copycat, a client request is first sent to the server it's connected to and then redirected
 * to the cluster leader to be actioned.
 */
@ThreadSafe
class RaftUniquenessProvider(private val transportConfiguration: NodeSSLConfiguration, private val db: CordaPersistence, private val metrics: MetricRegistry, private val raftConfig: RaftConfig) : UniquenessProvider, SingletonSerializeAsToken() {
    companion object {
        private val log = contextLogger()
        fun createMap(): AppendOnlyPersistentMap<String, Pair<Long, Any>, RaftState, String> =
                AppendOnlyPersistentMap(
                        toPersistentEntityKey = { it },
                        fromPersistentEntity = {
                            Pair(it.key, Pair(it.index, it.value.deserialize(context = SerializationDefaults.STORAGE_CONTEXT)))
                        },
                        toPersistentEntity = { k: String, v: Pair<Long, Any> ->
                            RaftState().apply {
                                key = k
                                value = v.second.serialize(context = SerializationDefaults.STORAGE_CONTEXT).bytes
                                index = v.first
                            }
                        },
                        persistentEntityClass = RaftState::class.java
                )
    }

    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}raft_committed_states")
    class RaftState(
            @Id
            @Column(name = "id")
            var key: String = "",

            @Lob
            @Column(name = "state_value")
            var value: ByteArray = ByteArray(0),

            @Column(name = "state_index")
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
        log.info("Creating Copycat server, log stored in: ${storagePath.toFile()}")
        val stateMachineFactory = {
            DistributedImmutableMap(db, RaftUniquenessProvider.Companion::createMap)
        }
        val address = raftConfig.nodeAddress.let { Address(it.host, it.port) }
        val storage = buildStorage(storagePath)
        val transport = buildTransport(transportConfiguration)
        val serializer = Serializer().apply {
            // Add serializers so Catalyst doesn't attempt to fall back on Java serialization for these types, which is disabled process-wide:
            register(DistributedImmutableMap.Commands.PutAll::class.java) {
                object : TypeSerializer<DistributedImmutableMap.Commands.PutAll<*, *>> {
                    override fun write(obj: DistributedImmutableMap.Commands.PutAll<*, *>,
                                       buffer: BufferOutput<out BufferOutput<*>>,
                                       serializer: Serializer) {
                        writeMap(obj.entries, buffer, serializer)
                    }

                    override fun read(type: Class<DistributedImmutableMap.Commands.PutAll<*, *>>,
                                      buffer: BufferInput<out BufferInput<*>>,
                                      serializer: Serializer): DistributedImmutableMap.Commands.PutAll<Any, Any> {
                        return DistributedImmutableMap.Commands.PutAll(readMap(buffer, serializer))
                    }
                }
            }
            register(LinkedHashMap::class.java) {
                object : TypeSerializer<LinkedHashMap<*, *>> {
                    override fun write(obj: LinkedHashMap<*, *>, buffer: BufferOutput<out BufferOutput<*>>, serializer: Serializer) = writeMap(obj, buffer, serializer)
                    override fun read(type: Class<LinkedHashMap<*, *>>, buffer: BufferInput<out BufferInput<*>>, serializer: Serializer) = readMap(buffer, serializer)
                }
            }
        }

        server = CopycatServer.builder(address)
                .withStateMachine(stateMachineFactory)
                .withStorage(storage)
                .withServerTransport(transport)
                .withSerializer(serializer)
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
                .withSerializer(serializer)
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


    override fun commit(states: List<StateRef>, txId: SecureHash, callerIdentity: Party, unspendableStates: List<StateRef>) {
        // Check for conflicts using all states but don't commit the unspendable states.
        val allStates = states + unspendableStates
        val entries = allStates.mapIndexed { i, stateRef ->
            stateRef to UniquenessProvider.ConsumingTx(txId, i, callerIdentity)
        }.filter { it.first !in unspendableStates }

        log.debug("Attempting to commit input states: ${states.joinToString()}")
        val commitCommand = DistributedImmutableMap.Commands.PutAll(encode(entries))
        val conflicts = client.submit(commitCommand).get()

        if (conflicts.isNotEmpty()) {
            val conflictingStates = decode(conflicts).mapValues { StateConsumptionDetails(it.value.id.sha256()) }
            val error = NotaryError.Conflict(txId, conflictingStates)
            throw NotaryInternalException(error)
        }
        log.debug("All input states of transaction $txId have been committed")
    }

    /**
     * Copycat uses its own serialization framework so we convert and store entries as String -> ByteArray
     * here to avoid having to define additional serializers for our custom types.
     */
    private fun encode(items: List<Pair<StateRef, UniquenessProvider.ConsumingTx>>): Map<String, ByteArray> {
        fun StateRef.encoded() = "$txhash:$index"
        return items.map { it.first.encoded() to it.second.serialize().bytes }.toMap()
    }

    private fun decode(items: Map<String, ByteArray>): Map<StateRef, UniquenessProvider.ConsumingTx> {
        fun String.toStateRef() = split(":").let { StateRef(SecureHash.parse(it[0]), it[1].toInt()) }
        return items.map { it.key.toStateRef() to it.value.deserialize<UniquenessProvider.ConsumingTx>() }.toMap()
    }
}

private fun writeMap(map: Map<*, *>, buffer: BufferOutput<out BufferOutput<*>>, serializer: Serializer) = with(map) {
    buffer.writeInt(size)
    forEach {
        with(serializer) {
            writeObject(it.key, buffer)
            writeObject(it.value, buffer)
        }
    }
}

private fun readMap(buffer: BufferInput<out BufferInput<*>>, serializer: Serializer): LinkedHashMap<Any, Any> {
    return LinkedHashMap<Any, Any>().apply {
        repeat(buffer.readInt()) {
            put(serializer.readObject(buffer), serializer.readObject(buffer))
        }
    }
}
