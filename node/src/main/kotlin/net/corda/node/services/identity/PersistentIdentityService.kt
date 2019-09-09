package net.corda.node.services.identity

import net.corda.core.crypto.toStringShort
import net.corda.core.identity.*
import net.corda.core.internal.CertRole
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.internal.hash
import net.corda.core.internal.toSet
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.UnknownAnonymousPartyException
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.MAX_HASH_HEX_SIZE
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.x509Certificates
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import org.hibernate.internal.util.collections.ArrayHelper.EMPTY_BYTE_ARRAY
import java.security.InvalidAlgorithmParameterException
import java.security.PublicKey
import java.security.cert.*
import javax.annotation.concurrent.ThreadSafe
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Lob
import kotlin.streams.toList

/**
 * An identity service that stores parties and their identities to a key value tables in the database. The entries are
 * cached for efficient lookup.
 */
@ThreadSafe
class PersistentIdentityService(cacheFactory: NamedCacheFactory) : SingletonSerializeAsToken(), IdentityService {

    companion object {
        private val log = contextLogger()

        const val HASH_TO_IDENTITY_TABLE_NAME = "${NODE_DATABASE_PREFIX}identities"
        const val NAME_TO_HASH_TABLE_NAME = "${NODE_DATABASE_PREFIX}named_identities"
        const val KEY_TO_NAME_TABLE_NAME = "${NODE_DATABASE_PREFIX}identities_no_cert"
        const val PK_HASH_COLUMN_NAME = "pk_hash"
        const val IDENTITY_COLUMN_NAME = "identity_value"
        const val NAME_COLUMN_NAME = "name"

        fun createKeyToPartyAndCertMap(cacheFactory: NamedCacheFactory): AppendOnlyPersistentMap<String, PartyAndCertificate, PersistentPublicKeyHashToCertificate, String> {
            return AppendOnlyPersistentMap(
                    cacheFactory = cacheFactory,
                    name = "PersistentIdentityService_keyToPartyAndCert",
                    toPersistentEntityKey = { it },
                    fromPersistentEntity = {
                        Pair(
                                it.publicKeyHash,
                                PartyAndCertificate(X509CertificateFactory().delegate.generateCertPath(it.identity.inputStream()))
                        )
                    },
                    toPersistentEntity = { key: String, value: PartyAndCertificate ->
                        PersistentPublicKeyHashToCertificate(key, value.certPath.encoded)
                    },
                    persistentEntityClass = PersistentPublicKeyHashToCertificate::class.java
            )
        }

        fun createX500ToKeyMap(cacheFactory: NamedCacheFactory): AppendOnlyPersistentMap<CordaX500Name, String, PersistentPartyToPublicKeyHash, String> {
            return AppendOnlyPersistentMap(
                    cacheFactory = cacheFactory,
                    name = "PersistentIdentityService_nameToKey",
                    toPersistentEntityKey = { it.toString() },
                    fromPersistentEntity = {
                        Pair(CordaX500Name.parse(it.name), it.publicKeyHash)
                    },
                    toPersistentEntity = { key: CordaX500Name, value: String ->
                        PersistentPartyToPublicKeyHash(key.toString(), value)
                    },
                    persistentEntityClass = PersistentPartyToPublicKeyHash::class.java
            )
        }


        fun createKeyToX500Map(cacheFactory: NamedCacheFactory): AppendOnlyPersistentMap<String, CordaX500Name, PersistentPublicKeyHashToParty, String> {
            return AppendOnlyPersistentMap(
                    cacheFactory = cacheFactory,
                    name = "PersistentIdentityService_keyToName",
                    toPersistentEntityKey = { it },
                    fromPersistentEntity = {
                        Pair(
                                it.publicKeyHash,
                                CordaX500Name.parse(it.name)
                        )
                    },
                    toPersistentEntity = { key: String, value: CordaX500Name ->
                        PersistentPublicKeyHashToParty(key, value.toString())
                    },
                    persistentEntityClass = PersistentPublicKeyHashToParty::class.java)
        }

        private fun mapToKey(party: PartyAndCertificate) = party.owningKey.toStringShort()
    }

    @Entity
    @javax.persistence.Table(name = HASH_TO_IDENTITY_TABLE_NAME)
    class PersistentPublicKeyHashToCertificate(
            @Id
            @Column(name = PK_HASH_COLUMN_NAME, length = MAX_HASH_HEX_SIZE, nullable = false)
            var publicKeyHash: String = "",

            @Lob
            @Column(name = IDENTITY_COLUMN_NAME, nullable = false)
            var identity: ByteArray = EMPTY_BYTE_ARRAY
    )

    @Entity
    @javax.persistence.Table(name = NAME_TO_HASH_TABLE_NAME)
    class PersistentPartyToPublicKeyHash(
            @Id
            @Column(name = NAME_COLUMN_NAME, length = 128, nullable = false)
            var name: String = "",

            @Column(name = PK_HASH_COLUMN_NAME, length = MAX_HASH_HEX_SIZE, nullable = false)
            var publicKeyHash: String = ""
    )

    @Entity
    @javax.persistence.Table(name = KEY_TO_NAME_TABLE_NAME)
    class PersistentPublicKeyHashToParty(
            @Id
            @Column(name = PK_HASH_COLUMN_NAME, length = MAX_HASH_HEX_SIZE, nullable = false)
            var publicKeyHash: String = "",

            @Column(name = NAME_COLUMN_NAME, length = 128, nullable = false)
            var name: String = ""
    )

    private lateinit var _caCertStore: CertStore
    override val caCertStore: CertStore get() = _caCertStore

    private lateinit var _trustRoot: X509Certificate
    override val trustRoot: X509Certificate get() = _trustRoot

    private lateinit var _trustAnchor: TrustAnchor
    override val trustAnchor: TrustAnchor get() = _trustAnchor

    /** Stores notary identities obtained from the network parameters, for which we don't need to perform a database lookup. */
    private val notaryIdentityCache = HashSet<Party>()

    // CordaPersistence is not a c'tor parameter to work around the cyclic dependency
    lateinit var database: CordaPersistence

    private val keyToPartyAndCert = createKeyToPartyAndCertMap(cacheFactory)
    private val nameToKey = createX500ToKeyMap(cacheFactory)
    private val keyToName = createKeyToX500Map(cacheFactory)

    fun start(trustRoot: X509Certificate, caCertificates: List<X509Certificate> = emptyList(), notaryIdentities: List<Party> = emptyList()) {
        _trustRoot = trustRoot
        _trustAnchor = TrustAnchor(trustRoot, null)
        _caCertStore = CertStore.getInstance("Collection", CollectionCertStoreParameters(caCertificates.toSet() + trustRoot))
        notaryIdentityCache.addAll(notaryIdentities)
    }

    fun loadIdentities(identities: Collection<PartyAndCertificate> = emptySet(), confidentialIdentities: Collection<PartyAndCertificate> = emptySet()) {
        identities.forEach {
            val key = mapToKey(it)
            keyToPartyAndCert.addWithDuplicatesAllowed(key, it, false)
            nameToKey.addWithDuplicatesAllowed(it.name, key, false)
            keyToName.addWithDuplicatesAllowed(mapToKey(it), it.name, false)
        }
        confidentialIdentities.forEach {
            keyToName.addWithDuplicatesAllowed(mapToKey(it), it.name, false)
        }
        log.debug("Identities loaded")
    }

    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    override fun verifyAndRegisterIdentity(identity: PartyAndCertificate): PartyAndCertificate? {
        return verifyAndRegisterIdentity(trustAnchor, identity)
    }

    /**
     * Verifies that an identity is valid. If it is valid, it gets registered in the database and the [PartyAndCertificate] is returned.
     *
     * @param trustAnchor The trust anchor that will verify the identity's validity
     * @param identity The identity to verify
     * @param isNewRandomIdentity true if the identity will not have been registered before (e.g. because it is randomly generated by ourselves).
     */
    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    private fun verifyAndRegisterIdentity(trustAnchor: TrustAnchor, identity: PartyAndCertificate): PartyAndCertificate? {
        // Validate the chain first, before we do anything clever with it
        val identityCertChain = identity.certPath.x509Certificates
        try {
            identity.verify(trustAnchor)
        } catch (e: CertPathValidatorException) {
            log.warn("Certificate validation failed for ${identity.name} against trusted root ${trustAnchor.trustedCert.subjectX500Principal}.")
            log.warn("Certificate path :")
            identityCertChain.reversed().forEachIndexed { index, certificate ->
                val space = (0 until index).joinToString("") { "   " }
                log.warn("$space${certificate.subjectX500Principal}")
            }
            throw e
        }
        // Ensure we record the first identity of the same name, first
        val wellKnownCert = identityCertChain.single { CertRole.extract(it)?.isWellKnown ?: false }
        if (wellKnownCert != identity.certificate) {
            val idx = identityCertChain.lastIndexOf(wellKnownCert)
            val firstPath = X509Utilities.buildCertPath(identityCertChain.slice(idx until identityCertChain.size))
            verifyAndRegisterIdentity(trustAnchor, PartyAndCertificate(firstPath))
        }
        return registerIdentity(identity)
    }

    private fun registerIdentity(identity: PartyAndCertificate): PartyAndCertificate? {
        log.debug { "Registering identity $identity" }
        val identityCertChain = identity.certPath.x509Certificates
        val key = mapToKey(identity)
        return database.transaction {
                keyToPartyAndCert.addWithDuplicatesAllowed(key, identity, false)
                nameToKey.addWithDuplicatesAllowed(identity.name, key, false)
                keyToName.addWithDuplicatesAllowed(key, identity.name, false)
            val parentId = identityCertChain[1].publicKey.toStringShort()
            keyToPartyAndCert[parentId]
        }
    }

    override fun certificateFromKey(owningKey: PublicKey): PartyAndCertificate? = database.transaction {
        keyToPartyAndCert[owningKey.toStringShort()]
    }

    private fun certificateFromCordaX500Name(name: CordaX500Name): PartyAndCertificate? {
        return database.transaction {
            val partyId = nameToKey[name]
            if (partyId != null) {
                keyToPartyAndCert[partyId]
            } else null
        }
    }

    // We give the caller a copy of the data set to avoid any locking problems
    override fun getAllIdentities(): Iterable<PartyAndCertificate> {
        return database.transaction {
            keyToPartyAndCert.allPersisted.use { it.map { it.second }.toList() }
        }
    }
    override fun wellKnownPartyFromX500Name(name: CordaX500Name): Party? = database.transaction {
        certificateFromCordaX500Name(name)?.party
    }

    override fun wellKnownPartyFromAnonymous(party: AbstractParty): Party? {
        // Skip database lookup if the party is a notary identity.
        // This also prevents an issue where the notary identity can't be resolved if it's not in the network map cache. The node obtains
        // a trusted list of notary identities from the network parameters automatically.
        return if (party is Party && party in notaryIdentityCache) {
            party
        } else {
            database.transaction {
                // Try and resolve the party from the table to public keys to party and certificates
                // If we cannot find it then we perform a lookup on the public key to X500 name table
                val legalIdentity = super.wellKnownPartyFromAnonymous(party)
                if (legalIdentity == null) {
                    // If there is no entry in the legal keyToPartyAndCert table then the party must be a confidential identity so we perform
                    // a lookup in the keyToName table. If an entry for that public key exists, then we attempt
                    val name = keyToName[party.owningKey.toStringShort()]
                    if (name != null) {
                        wellKnownPartyFromX500Name(name)
                    } else {
                        null
                    }
                } else {
                    legalIdentity
                }
            }
        }
    }

    override fun partiesFromName(query: String, exactMatch: Boolean): Set<Party> {
        return database.transaction {
            nameToKey.allPersisted.use {
                it.filter { x500Matches(query, exactMatch, it.first) }.map { keyToPartyAndCert[it.second]!!.party }.toSet()
            }
        }
    }

    @Throws(UnknownAnonymousPartyException::class)
    override fun assertOwnership(party: Party, anonymousParty: AnonymousParty) = database.transaction { super.assertOwnership(party, anonymousParty) }

    lateinit var ourNames: Set<CordaX500Name>

    // Allows us to eliminate keys we know belong to others by using the cache contents that might have been seen during other identity activity.
    // Concentrating activity on the identity cache works better than spreading checking across identity and key management, because we cache misses too.
    fun stripNotOurKeys(keys: Iterable<PublicKey>): Iterable<PublicKey> {
        return keys.filter { (@Suppress("DEPRECATION") certificateFromKey(it))?.name in ourNames }
    }

    override fun registerKeyToParty(key: PublicKey, party: Party) {
        return database.transaction {
            val existingEntryForKey = keyToName[key.toStringShort()]
            if (existingEntryForKey == null) {
                log.info("Linking: ${key.hash} to ${party.name}")
                keyToName[key.toStringShort()] = party.name
            } else {
                log.info("An existing entry for ${key.hash} already exists.")
                if (party.name != keyToName[key.toStringShort()]) {
                    throw IllegalArgumentException("The public key ${key.hash} is already assigned to a different party than the supplied .")
                }
            }
        }
    }
}