/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node

import com.google.common.net.HostAndPort
import contracts.*
import core.*
import core.crypto.SecureHash
import core.crypto.generateKeyPair
import core.messaging.*
import core.serialization.deserialize
import core.serialization.serialize
import core.utilities.loggerFor
import java.io.RandomAccessFile
import java.lang.management.ManagementFactory
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.KeyPair
import java.util.*
import java.util.concurrent.Executors

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

    // TODO: This will be obsoleted by "PLT-12: Basic module/sandbox system for contracts"
    private val contractFactory = object : ContractFactory {
        private val contracts = mapOf(
                CASH_PROGRAM_ID to Cash::class.java,
                CP_PROGRAM_ID to CommercialPaper::class.java,
                CROWDFUND_PROGRAM_ID to CrowdFund::class.java,
                DUMMY_PROGRAM_ID to DummyContract::class.java
        )

        override fun <T : Contract> get(hash: SecureHash): T {
            val c = contracts[hash] ?: throw UnknownContractException()
            @Suppress("UNCHECKED_CAST")
            return c.newInstance() as T
        }
    }

    val storage: StorageService
    val smm: StateMachineManager
    val net: ArtemisMessagingService
    val wallet: WalletService
    val keyManagement: E2ETestKeyManagementService
    val inNodeTimestampingService: TimestamperNodeService?
    val identity: IdentityService

    // Avoid the lock being garbage collected. We don't really need to release it as the OS will do so for us
    // when our process shuts down, but we try in stop() anyway just to be nice.
    private var nodeFileLock: FileLock? = null

    init {
        alreadyRunningNodeCheck()

        storage = makeStorageService(dir)
        smm = StateMachineManager(services, serverThread)
        net = ArtemisMessagingService(dir, myNetAddr)
        wallet = E2ETestWalletService(services)
        keyManagement = E2ETestKeyManagementService()

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

        // This object doesn't need to be referenced from this class because it registers handlers on the network
        // service and so that keeps it from being collected.
        DataVendingService(net, storage)

        net.start()
    }

    fun stop() {
        net.stop()
        serverThread.shutdownNow()
        nodeFileLock!!.release()
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
            val keypair: KeyPair = generateKeyPair()
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

        val ss = object : StorageService {
            private val tables = HashMap<String, MutableMap<Any, Any>>()

            @Suppress("UNCHECKED_CAST")
            override fun <K, V> getMap(tableName: String): MutableMap<K, V> {
                // TODO: This should become a database.
                synchronized(tables) {
                    return tables.getOrPut(tableName) { Collections.synchronizedMap(HashMap<Any, Any>()) } as MutableMap<K, V>
                }
            }

            override val validatedTransactions: MutableMap<SecureHash, SignedTransaction>
                get() = getMap("validated-transactions")

            override val contractPrograms = contractFactory
            override val myLegalIdentity = identity
            override val myLegalIdentityKey = keypair
        }

        return ss
    }

    private fun alreadyRunningNodeCheck() {
        // Write out our process ID (which may or may not resemble a UNIX process id - to us it's just a string) to a
        // file that we'll do our best to delete on exit. But if we don't, it'll be overwritten next time. If it already
        // exists, we try to take the file lock first before replacing it and if that fails it means we're being started
        // twice with the same directory: that's a user error and we should bail out.
        val pidPath = dir.resolve("process-id")
        val file = pidPath.toFile()
        if (file.exists()) {
            val f = RandomAccessFile(file, "rw")
            val l = f.channel.tryLock()
            if (l == null) {
                println("It appears there is already a node running with the specified data directory $dir")
                println("Shut that other node down and try again. It may have process ID ${file.readText()}")
                System.exit(1)
            }
            nodeFileLock = l
        }
        val ourProcessID: String = ManagementFactory.getRuntimeMXBean().name.split("@")[0]
        Files.write(pidPath, ourProcessID.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        pidPath.toFile().deleteOnExit()
        if (nodeFileLock == null)
            nodeFileLock = RandomAccessFile(file, "rw").channel.lock()
    }

    companion object {
        val PRIVATE_KEY_FILE_NAME = "identity-private-key"
        val PUBLIC_IDENTITY_FILE_NAME = "identity-public"

        /** The port that is used by default if none is specified. As you know, 31337 is the most elite number. */
        val DEFAULT_PORT = 31337
    }
}