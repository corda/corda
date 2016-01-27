/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node

import com.google.common.net.HostAndPort
import core.*
import core.messaging.*
import core.serialization.deserialize
import core.serialization.serialize
import core.utilities.loggerFor
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.*
import java.util.concurrent.Executors

val DEFAULT_PORT = 31337

class ConfigurationException(message: String) : Exception(message)

// TODO: Split this into a regression testing environment

/**
 * A simple wrapper around a plain old Java .properties file. The keys have the same name as in the source code.
 *
 * TODO: Replace Java properties file with a better config file format (maybe yaml).
 * We want to be able to configure via a GUI too, so an ability to round-trip whitespace, comments etc when machine
 * editing the file is a must-have.
 */
class NodeConfiguration(private val properties: Properties) {
    val myLegalName: String by properties
}

/**
 * A Node manages a standalone server that takes part in the P2P network. It creates the services found in [ServiceHub],
 * loads important data off disk and starts listening for connections.
 *
 * @param dir A [Path] to a location on disk where working files can be found or stored.
 * @param myNetAddr The host and port that this server will use. It can't find out its own external hostname, so you
 *                  have to specify that yourself.
 * @param configuration This is typically loaded from a .properties file
 * @param timestamperAddress If null, this node will become a timestamping node, otherwise, it will use that one.
 */
class Node(val dir: Path, val myNetAddr: HostAndPort, val configuration: NodeConfiguration,
           timestamperAddress: LegallyIdentifiableNode?) {
    private val log = loggerFor<Node>()

    // We will run as much stuff in this thread as possible to keep the risk of thread safety bugs low during the
    // low-performance prototyping period.
    val serverThread = Executors.newSingleThreadExecutor()

    val services = object : ServiceHub {
        override val networkService: MessagingService get() = net
        override val networkMapService: NetworkMap = MockNetworkMap()
        override val storageService: StorageService get() = storage
        override val walletService: WalletService get() = wallet
        override val keyManagementService: KeyManagementService get() = keyManagement
        override val identityService: IdentityService get() = identity
    }

    // TODO: Implement mutual exclusion so we can't start the node twice by accident.

    val storage = makeStorageService(dir)
    val smm = StateMachineManager(services, serverThread)
    val net = ArtemisMessagingService(dir, myNetAddr)
    val wallet: WalletService = E2ETestWalletService(services)
    val keyManagement = E2ETestKeyManagementService()
    val inNodeTimestampingService: TimestamperNodeService?
    val identity: IdentityService

    init {
        // Insert a network map entry for the timestamper: this is all temp scaffolding and will go away. If we are
        // given the details, the timestamping node is somewhere else. Otherwise, we do our own timestamping.
        val tsid = if (timestamperAddress != null) {
            inNodeTimestampingService = null
            timestamperAddress
        } else {
            inNodeTimestampingService = TimestamperNodeService(net, storage.myLegalIdentity, storage.myLegalIdentityKey)
            LegallyIdentifiableNode(net.myAddress, storage.myLegalIdentity)
        }
        (services.networkMapService as MockNetworkMap).timestampingNodes.add(tsid)

        // We don't have any identity infrastructure right now, so we just throw together the only two identities we
        // know about: our own, and the identity of the remote timestamper node (if any).
        val knownIdentities = if (timestamperAddress != null)
            listOf(storage.myLegalIdentity, timestamperAddress.identity)
        else
            listOf(storage.myLegalIdentity)
        identity = FixedIdentityService(knownIdentities)

        net.start()
    }

    fun stop() {
        net.stop()
        serverThread.shutdownNow()
    }

    fun makeStorageService(dir: Path): StorageService {
        // Load the private identity key, creating it if necessary. The identity key is a long term well known key that
        // is distributed to other peers and we use it (or a key signed by it) when we need to do something
        // "permissioned". The identity file is what gets distributed and contains the node's legal name along with
        // the public key. Obviously in a real system this would need to be a certificate chain of some kind to ensure
        // the legal name is actually validated in some way.
        val privKeyFile = dir.resolve(PRIVATE_KEY_FILE_NAME)
        val pubIdentityFile = dir.resolve(PUBLIC_IDENTITY_FILE_NAME)

        val (identity, keypair) = if (!Files.exists(privKeyFile)) {
            log.info("Identity key not found, generating fresh key!")
            val keypair: KeyPair = KeyPairGenerator.getInstance("EC").genKeyPair()
            keypair.serialize().writeToFile(privKeyFile)
            val myIdentity = Party(configuration.myLegalName, keypair.public)
            // We include the Party class with the file here to help catch mixups when admins provide files of the
            // wrong type by mistake.
            myIdentity.serialize(includeClassName = true).writeToFile(pubIdentityFile)
            Pair(myIdentity, keypair)
        } else {
            // Check that the identity in the config file matches the identity file we have stored to disk.
            // This is just a sanity check. It shouldn't fail unless the admin has fiddled with the files and messed
            // things up for us.
            val myIdentity = Files.readAllBytes(pubIdentityFile).deserialize<Party>(includeClassName = true)
            if (myIdentity.name != configuration.myLegalName)
                throw ConfigurationException("The legal name in the config file doesn't match the stored identity file:" +
                        "${configuration.myLegalName} vs ${myIdentity.name}")
            // Load the private key.
            val keypair = Files.readAllBytes(privKeyFile).deserialize<KeyPair>()
            Pair(myIdentity, keypair)
        }

        log.info("Node owned by ${identity.name} starting up ...")

        return object : StorageService {
            private val tables = HashMap<String, MutableMap<Any, Any>>()

            @Suppress("UNCHECKED_CAST")
            override fun <K, V> getMap(tableName: String): MutableMap<K, V> {
                // TODO: This should become a database.
                synchronized(tables) {
                    return tables.getOrPut(tableName) { Collections.synchronizedMap(HashMap<Any, Any>()) } as MutableMap<K, V>
                }
            }

            override val myLegalIdentity = identity
            override val myLegalIdentityKey = keypair
        }
    }

    companion object {
        val PRIVATE_KEY_FILE_NAME = "identity-private-key"
        val PUBLIC_IDENTITY_FILE_NAME = "identity-public"
    }
}