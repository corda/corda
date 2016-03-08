/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node

import contracts.*
import core.*
import core.crypto.SecureHash
import core.crypto.generateKeyPair
import core.messaging.*
import core.node.services.*
import core.serialization.deserialize
import core.serialization.serialize
import org.slf4j.Logger
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.util.*
import java.util.concurrent.Executors

/**
 * A base node implementation that can be customised either for production (with real implementations that do real
 * I/O), or a mock implementation suitable for unit test environments.
 */
abstract class AbstractNode(val dir: Path, val configuration: NodeConfiguration, val timestamperAddress: LegallyIdentifiableNode?) {
    companion object {
        val PRIVATE_KEY_FILE_NAME = "identity-private-key"
        val PUBLIC_IDENTITY_FILE_NAME = "identity-public"
    }

    protected abstract val log: Logger

    // We will run as much stuff in this thread as possible to keep the risk of thread safety bugs low during the
    // low-performance prototyping period.
    protected open val serverThread = Executors.newSingleThreadExecutor()

    val services = object : ServiceHub {
        override val networkService: MessagingService get() = net
        override val networkMapService: NetworkMap = MockNetworkMap()
        override val storageService: StorageService get() = storage
        override val walletService: WalletService get() = wallet
        override val keyManagementService: KeyManagementService get() = keyManagement
        override val identityService: IdentityService get() = identity
    }

    val legallyIdentifableAddress: LegallyIdentifiableNode get() = LegallyIdentifiableNode(net.myAddress, storage.myLegalIdentity)

    // TODO: This will be obsoleted by "PLT-12: Basic module/sandbox system for contracts"
    protected val contractFactory = object : ContractFactory {
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

    lateinit var storage: StorageService
    lateinit var smm: StateMachineManager
    lateinit var wallet: WalletService
    lateinit var keyManagement: E2ETestKeyManagementService
    var inNodeTimestampingService: TimestamperNodeService? = null
    lateinit var identity: IdentityService
    lateinit var net: MessagingService

    open fun start(): AbstractNode {
        log.info("Node starting up ...")
        storage = initialiseStorageService(dir)
        net = makeMessagingService()
        smm = StateMachineManager(services, serverThread)
        wallet = NodeWalletService(services)
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

        identity = makeIdentityService()

        // This object doesn't need to be referenced from this class because it registers handlers on the network
        // service and so that keeps it from being collected.
        DataVendingService(net, storage)

        return this
    }

    protected open fun makeIdentityService(): IdentityService {
        // We don't have any identity infrastructure right now, so we just throw together the only two identities we
        // know about: our own, and the identity of the remote timestamper node (if any).
        val knownIdentities = if (timestamperAddress != null)
            listOf(storage.myLegalIdentity, timestamperAddress.identity)
        else
            listOf(storage.myLegalIdentity)
        return FixedIdentityService(knownIdentities)
    }

    open fun stop() {
        net.stop()
        serverThread.shutdownNow()
    }

    protected abstract fun makeMessagingService(): MessagingService

    protected open fun initialiseStorageService(dir: Path): StorageService {
        val attachments = makeAttachmentStorage(dir)
        val (identity, keypair) = obtainKeyPair(dir)
        return constructStorageService(attachments, identity, keypair)
    }

    protected open fun constructStorageService(attachments: NodeAttachmentStorage, identity: Party, keypair: KeyPair) =
            StorageServiceImpl(attachments, identity, keypair)

    open inner class StorageServiceImpl(attachments: NodeAttachmentStorage, identity: Party, keypair: KeyPair) : StorageService {
        protected val tables = HashMap<String, MutableMap<Any, Any>>()

        @Suppress("UNCHECKED_CAST")
        override fun <K, V> getMap(tableName: String): MutableMap<K, V> {
            // TODO: This should become a database.
            synchronized(tables) {
                return tables.getOrPut(tableName) { Collections.synchronizedMap(HashMap<Any, Any>()) } as MutableMap<K, V>
            }
        }

        override val validatedTransactions: MutableMap<SecureHash, SignedTransaction>
            get() = getMap("validated-transactions")

        override val attachments: AttachmentStorage = attachments
        override val contractPrograms = contractFactory
        override val myLegalIdentity = identity
        override val myLegalIdentityKey = keypair
    }

    private fun obtainKeyPair(dir: Path): Pair<Party, KeyPair> {
        // Load the private identity key, creating it if necessary. The identity key is a long term well known key that
        // is distributed to other peers and we use it (or a key signed by it) when we need to do something
        // "permissioned". The identity file is what gets distributed and contains the node's legal name along with
        // the public key. Obviously in a real system this would need to be a certificate chain of some kind to ensure
        // the legal name is actually validated in some way.
        val privKeyFile = dir.resolve(PRIVATE_KEY_FILE_NAME)
        val pubIdentityFile = dir.resolve(PUBLIC_IDENTITY_FILE_NAME)

        return if (!Files.exists(privKeyFile)) {
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
    }

    private fun makeAttachmentStorage(dir: Path): NodeAttachmentStorage {
        val attachmentsDir = dir.resolve("attachments")
        try {
            Files.createDirectory(attachmentsDir)
        } catch (e: FileAlreadyExistsException) {
        }
        val attachments = NodeAttachmentStorage(attachmentsDir)
        return attachments
    }
}