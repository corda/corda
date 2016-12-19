package net.corda.node.services.transactions

import com.google.common.net.HostAndPort
import io.atomix.catalyst.transport.Address
import io.atomix.catalyst.transport.Transport
import io.atomix.catalyst.transport.netty.NettyTransport
import io.atomix.catalyst.transport.netty.SslProtocol
import io.atomix.copycat.client.ConnectionStrategies
import io.atomix.copycat.client.CopycatClient
import io.atomix.copycat.server.CopycatServer
import io.atomix.copycat.server.storage.Storage
import io.atomix.copycat.server.storage.StorageLevel
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.UniquenessException
import net.corda.core.node.services.UniquenessProvider
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.loggerFor
import net.corda.node.services.config.NodeSSLConfiguration
import org.jetbrains.exposed.sql.Database
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import javax.annotation.concurrent.ThreadSafe

/**
 * A uniqueness provider that records committed input states in a distributed collection replicated and
 * persisted in a Raft cluster, using the Copycat framework (http://atomix.io/copycat/).
 *
 * The uniqueness provider maintains both a Copycat cluster node (server) and a client through which it can submit
 * requests to the cluster. In Copycat, a client request is first sent to the server it's connected to and then redirected
 * to the cluster leader to be actioned.
 *
 * @param storagePath Directory storing the Raft log and state machine snapshots
 * @param myAddress Address of the Copycat node run by this Corda node
 * @param clusterAddresses List of node addresses in the existing Copycat cluster. At least one active node must be
 * provided to join the cluster. If empty, a new cluster will be bootstrapped.
 * @param db The database to store the state machine state in
 * @param config SSL configuration
 */
@ThreadSafe
class RaftUniquenessProvider(storagePath: Path, myAddress: HostAndPort, clusterAddresses: List<HostAndPort>,
                             db: Database, config: NodeSSLConfiguration) : UniquenessProvider, SingletonSerializeAsToken() {
    companion object {
        private val log = loggerFor<RaftUniquenessProvider>()
        private val DB_TABLE_NAME = "notary_committed_states"
    }

    private val _clientFuture: CompletableFuture<CopycatClient>
    private val _serverFuture: CompletableFuture<CopycatServer>
    /**
     * Copycat clients are responsible for connecting to the cluster and submitting commands and queries that operate
     * on the cluster's replicated state machine.
     */
    private val client: CopycatClient
        get() = _clientFuture.get()

    private val server: CopycatServer
        get() = _serverFuture.get()

    init {
        log.info("Creating Copycat server, log stored in: ${storagePath.toFile()}")
        val stateMachineFactory = { DistributedImmutableMap<String, ByteArray>(db, DB_TABLE_NAME) }
        val address = Address(myAddress.hostText, myAddress.port)
        val storage = buildStorage(storagePath)
        val transport = buildTransport(config)

        val server = CopycatServer.builder(address)
                .withStateMachine(stateMachineFactory)
                .withStorage(storage)
                .withServerTransport(transport)
                .build()

        _serverFuture = if (clusterAddresses.isNotEmpty()) {
            log.info("Joining an existing Copycat cluster at $clusterAddresses")
            val cluster = clusterAddresses.map { Address(it.hostText, it.port) }
            server.join(cluster)
        } else {
            log.info("Bootstrapping a Copycat cluster at $address")
            server.bootstrap()
        }

        val client = CopycatClient.builder(address)
                .withTransport(transport) // TODO: use local transport for client-server communications
                .withConnectionStrategy(ConnectionStrategies.EXPONENTIAL_BACKOFF)
                .build()
        _clientFuture = _serverFuture.thenCompose { client.connect(address) }
    }

    private fun buildStorage(storagePath: Path): Storage? {
        return Storage.builder()
                .withDirectory(storagePath.toFile())
                .withStorageLevel(StorageLevel.DISK)
                .build()
    }

    private fun buildTransport(config: NodeSSLConfiguration): Transport? {
        return NettyTransport.builder()
                .withSsl()
                .withSslProtocol(SslProtocol.TLSv1_2)
                .withKeyStorePath(config.keyStorePath.toString())
                .withKeyStorePassword(config.keyStorePassword)
                .withTrustStorePath(config.trustStorePath.toString())
                .withTrustStorePassword(config.trustStorePassword)
                .build()
    }

    override fun commit(states: List<StateRef>, txId: SecureHash, callerIdentity: Party) {
        val entries = states.mapIndexed { i, stateRef -> stateRef to UniquenessProvider.ConsumingTx(txId, i, callerIdentity) }

        log.debug("Attempting to commit input states: ${states.joinToString()}")
        val commitCommand = DistributedImmutableMap.Commands.PutAll(encode(entries))
        val conflicts = client.submit(commitCommand).get()

        if (conflicts.isNotEmpty()) throw UniquenessException(UniquenessProvider.Conflict(decode(conflicts)))
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

    /** Disconnect the Copycat client and shut down the replica (without leaving the cluster) */
    fun stop(): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()
        client.close().whenComplete({ clientResult, clientError ->
            server.shutdown().whenComplete({ serverResult, serverError ->
                if (clientError != null) {
                    future.completeExceptionally(clientError)
                } else if (serverError != null) {
                    future.completeExceptionally(serverError)
                } else {
                    future.complete(null)
                }
            })
        })

        return future
    }
}