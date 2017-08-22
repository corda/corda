package net.corda.testing.node

import net.corda.core.contracts.Attachment
import net.corda.core.crypto.*
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.messaging.DataFeed
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.*
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NonEmptySet
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
import net.corda.node.services.vault.HibernateVaultQueryImpl
import net.corda.node.services.vault.NodeVaultService
import net.corda.node.utilities.CordaPersistence
import net.corda.node.utilities.configureDatabase
import net.corda.schemas.CashSchemaV1
import net.corda.schemas.CommercialPaperSchemaV1
import net.corda.testing.*
import net.corda.testing.schemas.DummyLinearStateSchemaV1
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
import java.sql.Connection
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

    override fun recordTransactions(notifyVault: Boolean, txs: Iterable<SignedTransaction>) {
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

    lateinit var hibernatePersister: HibernateObserver

    fun makeVaultService(hibernateConfig: HibernateConfiguration = HibernateConfiguration( { NodeSchemaService() }, makeTestDatabaseProperties(), { identityService })): VaultService {
        val vaultService = NodeVaultService(this)
        hibernatePersister = HibernateObserver(vaultService.rawUpdates, hibernateConfig)
        return vaultService
    }

    override fun <T : SerializeAsToken> cordaService(type: Class<T>): T = throw IllegalArgumentException("${type.name} not found")

    override fun jdbcSession(): Connection = throw UnsupportedOperationException()
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

    override fun freshKeyAndCert(identity: PartyAndCertificate, revocationEnabled: Boolean): PartyAndCertificate {
        return freshCertificate(identityService, freshKey(), identity, getSigner(identity.owningKey), revocationEnabled)
    }

    private fun getSigner(publicKey: PublicKey): ContentSigner = getSigner(getSigningKeyPair(publicKey))

    private fun getSigningKeyPair(publicKey: PublicKey): KeyPair {
        val pk = publicKey.keys.first { keyStore.containsKey(it) }
        return KeyPair(pk, keyStore[pk]!!)
    }

    override fun sign(bytes: ByteArray, publicKey: PublicKey): DigitalSignature.WithKey {
        val keyPair = getSigningKeyPair(publicKey)
        return keyPair.sign(bytes)
    }

    override fun sign(signableData: SignableData, publicKey: PublicKey): TransactionSignature {
        val keyPair = getSigningKeyPair(publicKey)
        return keyPair.sign(signableData)
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

fun makeTestDatabaseProperties(): Properties {
    val props = Properties()
    props.setProperty("transactionIsolationLevel", "repeatableRead") //for other possible values see net.corda.node.utilities.CordaPeristence.parserTransactionIsolationLevel(String)
    return props
}

fun makeTestIdentityService() = InMemoryIdentityService(MOCK_IDENTITIES, trustRoot = DUMMY_CA.certificate)

fun makeTestDatabaseAndMockServices(customSchemas: Set<MappedSchema> = setOf(CommercialPaperSchemaV1, DummyLinearStateSchemaV1, CashSchemaV1),
                                    keys: List<KeyPair> = listOf(MEGA_CORP_KEY),
                                    createIdentityService: () -> IdentityService = { makeTestIdentityService() }): Pair<CordaPersistence, MockServices> {
    val dataSourceProps = makeTestDataSourceProperties()
    val databaseProperties = makeTestDatabaseProperties()
    val createSchemaService = { NodeSchemaService(customSchemas) }
    val database = configureDatabase(dataSourceProps, databaseProperties, createSchemaService, createIdentityService)
    val mockService = database.transaction {
        object : MockServices(*(keys.toTypedArray())) {
            override val vaultService: VaultService = makeVaultService(database.hibernateConfig)

            override fun recordTransactions(notifyVault: Boolean, txs: Iterable<SignedTransaction>) {
                for (stx in txs) {
                    validatedTransactions.addTransaction(stx)
                }
                // Refactored to use notifyAll() as we have no other unit test for that method with multiple transactions.
                vaultService.notifyAll(txs.map { it.tx })
            }

            override val vaultQueryService: VaultQueryService = HibernateVaultQueryImpl(database.hibernateConfig, vaultService)

            override fun jdbcSession(): Connection = database.createSession()
        }
    }
    return Pair(database, mockService)
}

val MOCK_VERSION_INFO = VersionInfo(1, "Mock release", "Mock revision", "Mock Vendor")
