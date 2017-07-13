package net.corda.testing.node

import net.corda.core.contracts.Attachment
import net.corda.core.crypto.*
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.messaging.DataFeed
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.*
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NonEmptySet
import net.corda.flows.AnonymisedIdentity
import net.corda.node.VersionInfo
import net.corda.node.services.api.StateMachineRecordedTransactionMappingStorage
import net.corda.node.services.api.WritableTransactionStorage
import net.corda.node.services.database.HibernateConfiguration
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.node.services.keys.freshCertificate
import net.corda.node.services.keys.getSigner
import net.corda.node.services.persistence.InMemoryStateMachineRecordedTransactionMappingStorage
import net.corda.node.services.schema.HibernateObserver
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.transactions.InMemoryTransactionVerifierService
import net.corda.node.services.vault.NodeVaultService
import net.corda.testing.DUMMY_CA
import net.corda.testing.MEGA_CORP
import net.corda.testing.MOCK_IDENTITIES
import net.corda.testing.getTestPartyAndCertificate
import org.bouncycastle.operator.ContentSigner
import rx.Observable
import rx.subjects.PublishSubject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.time.Clock
import java.util.*
import java.util.jar.JarInputStream

// TODO: We need a single, rationalised unit testing environment that is usable for everything. Fix this!
// That means it probably shouldn't be in the 'core' module, which lacks enough code to create a realistic test env.

/**
 * A singleton utility that only provides a mock identity, key and storage service. However, this is sufficient for
 * building chains of transactions and verifying them. It isn't sufficient for testing flows however.
 */
open class MockServices(vararg val keys: KeyPair) : ServiceHub {
    constructor() : this(generateKeyPair())

    val key: KeyPair get() = keys.first()

    override fun recordTransactions(txs: Iterable<SignedTransaction>) {
        txs.forEach {
            stateMachineRecordedTransactionMapping.addMapping(StateMachineRunId.createRandom(), it.id)
        }
        for (stx in txs) {
            validatedTransactions.addTransaction(stx)
        }
    }

    override val attachments: AttachmentStorage = MockAttachmentStorage()
    override val validatedTransactions: WritableTransactionStorage = MockTransactionStorage()
    val stateMachineRecordedTransactionMapping: StateMachineRecordedTransactionMappingStorage = MockStateMachineRecordedTransactionMappingStorage()
    override final val identityService: IdentityService = InMemoryIdentityService(MOCK_IDENTITIES, trustRoot = DUMMY_CA.certificate)
    override val keyManagementService: KeyManagementService = MockKeyManagementService(identityService, *keys)

    override val vaultService: VaultService get() = throw UnsupportedOperationException()
    override val vaultQueryService: VaultQueryService get() = throw UnsupportedOperationException()
    override val networkMapCache: NetworkMapCache get() = throw UnsupportedOperationException()
    override val clock: Clock get() = Clock.systemUTC()
    override val myInfo: NodeInfo get() {
        val identity = getTestPartyAndCertificate(MEGA_CORP.name, key.public)
        return NodeInfo(emptyList(), identity, NonEmptySet.of(identity), 1)
    }
    override val transactionVerifierService: TransactionVerifierService get() = InMemoryTransactionVerifierService(2)

    fun makeVaultService(dataSourceProps: Properties, hibernateConfig: HibernateConfiguration = HibernateConfiguration(NodeSchemaService())): VaultService {
        val vaultService = NodeVaultService(this, dataSourceProps)
        HibernateObserver(vaultService.rawUpdates, hibernateConfig)
        return vaultService
    }

    override fun <T : SerializeAsToken> cordaService(type: Class<T>): T = throw IllegalArgumentException("${type.name} not found")
}

class MockKeyManagementService(val identityService: IdentityService,
                               vararg initialKeys: KeyPair) : SingletonSerializeAsToken(), KeyManagementService {
    private val keyStore: MutableMap<PublicKey, PrivateKey> = initialKeys.associateByTo(HashMap(), { it.public }, { it.private })

    override val keys: Set<PublicKey> get() = keyStore.keys

    val nextKeys = LinkedList<KeyPair>()

    override fun freshKey(): PublicKey {
        val k = nextKeys.poll() ?: generateKeyPair()
        keyStore[k.public] = k.private
        return k.public
    }

    override fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> = candidateKeys.filter { it in this.keys }

    override fun freshKeyAndCert(identity: PartyAndCertificate, revocationEnabled: Boolean): AnonymisedIdentity {
        return freshCertificate(identityService, freshKey(), identity, getSigner(identity.owningKey), revocationEnabled)
    }

    private fun getSigner(publicKey: PublicKey): ContentSigner = getSigner(getSigningKeyPair(publicKey))

    private fun getSigningKeyPair(publicKey: PublicKey): KeyPair {
        val pk = publicKey.keys.first { keyStore.containsKey(it) }
        return KeyPair(pk, keyStore[pk]!!)
    }

    override fun sign(bytes: ByteArray, publicKey: PublicKey): DigitalSignature.WithKey {
        val keyPair = getSigningKeyPair(publicKey)
        val signature = keyPair.sign(bytes)
        return signature
    }
}

class MockAttachmentStorage : AttachmentStorage, SingletonSerializeAsToken() {
    val files = HashMap<SecureHash, ByteArray>()

    override fun openAttachment(id: SecureHash): Attachment? {
        val f = files[id] ?: return null
        return object : Attachment {
            override fun open(): InputStream = ByteArrayInputStream(f)
            override val id: SecureHash = id
        }
    }

    override fun importAttachment(jar: InputStream): SecureHash {
        // JIS makes read()/readBytes() return bytes of the current file, but we want to hash the entire container here.
        require(jar !is JarInputStream)

        val bytes = run {
            val s = ByteArrayOutputStream()
            jar.copyTo(s)
            s.close()
            s.toByteArray()
        }
        val sha256 = bytes.sha256()
        if (files.containsKey(sha256))
            throw FileAlreadyExistsException(File("!! MOCK FILE NAME"))
        files[sha256] = bytes
        return sha256
    }
}

class MockStateMachineRecordedTransactionMappingStorage(
        val storage: StateMachineRecordedTransactionMappingStorage = InMemoryStateMachineRecordedTransactionMappingStorage()
) : StateMachineRecordedTransactionMappingStorage by storage

open class MockTransactionStorage : WritableTransactionStorage, SingletonSerializeAsToken() {
    override fun track(): DataFeed<List<SignedTransaction>, SignedTransaction> {
        return DataFeed(txns.values.toList(), _updatesPublisher)
    }

    private val txns = HashMap<SecureHash, SignedTransaction>()

    private val _updatesPublisher = PublishSubject.create<SignedTransaction>()

    override val updates: Observable<SignedTransaction>
        get() = _updatesPublisher

    private fun notify(transaction: SignedTransaction) = _updatesPublisher.onNext(transaction)

    override fun addTransaction(transaction: SignedTransaction): Boolean {
        val recorded = txns.putIfAbsent(transaction.id, transaction) == null
        if (recorded) {
            notify(transaction)
        }
        return recorded
    }

    override fun getTransaction(id: SecureHash): SignedTransaction? = txns[id]
}

/**
 * Make properties appropriate for creating a DataSource for unit tests.
 *
 * @param nodeName Reflects the "instance" of the in-memory database.  Defaults to a random string.
 */
// TODO: Can we use an X509 principal generator here?
fun makeTestDataSourceProperties(nodeName: String = SecureHash.randomSHA256().toString()): Properties {
    val props = Properties()
    props.setProperty("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource")
    props.setProperty("dataSource.url", "jdbc:h2:mem:${nodeName}_persistence;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE")
    props.setProperty("dataSource.user", "sa")
    props.setProperty("dataSource.password", "")
    return props
}

val MOCK_VERSION_INFO = VersionInfo(1, "Mock release", "Mock revision", "Mock Vendor")
