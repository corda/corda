package net.corda.node.services.identity

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.identity.x500Matches
import net.corda.core.internal.CertRole
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.internal.hash
import net.corda.core.node.NotaryInfo
import net.corda.core.node.services.UnknownAnonymousPartyException
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.MAX_HASH_HEX_SIZE
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.internal.schemas.NodeInfoSchemaV1
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.node.services.network.NotaryUpdateListener
import net.corda.node.services.persistence.PublicKeyHashToExternalId
import net.corda.node.services.persistence.WritablePublicKeyToOwningIdentityCache
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.node.utilities.NonInvalidatingCache
import net.corda.nodeapi.internal.KeyOwningIdentity
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.x509Certificates
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.nodeapi.internal.persistence.currentDBSession
import org.apache.commons.lang3.ArrayUtils
import org.hibernate.Session
import org.hibernate.annotations.Type
import org.hibernate.internal.util.collections.ArrayHelper.EMPTY_BYTE_ARRAY
import java.security.InvalidAlgorithmParameterException
import java.security.PublicKey
import java.security.cert.CertPathValidatorException
import java.security.cert.CertStore
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.CollectionCertStoreParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.util.*
import javax.annotation.concurrent.ThreadSafe
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import kotlin.collections.HashSet
import kotlin.streams.toList

/**
 * An identity service that stores parties and their identities to a key value tables in the database. The entries are
 * cached for efficient lookup.
 */
@ThreadSafe
@Suppress("TooManyFunctions")
class PersistentIdentityService(cacheFactory: NamedCacheFactory) : SingletonSerializeAsToken(), IdentityServiceInternal, NotaryUpdateListener {

    companion object {
        private val log = contextLogger()

        const val HASH_TO_IDENTITY_TABLE_NAME = "${NODE_DATABASE_PREFIX}identities"
        const val KEY_TO_NAME_TABLE_NAME = "${NODE_DATABASE_PREFIX}identities_no_cert"
        const val PK_HASH_COLUMN_NAME = "pk_hash"
        const val IDENTITY_COLUMN_NAME = "identity_value"
        const val NAME_COLUMN_NAME = "name"

        fun createKeyToPartyAndCertMap(cacheFactory: NamedCacheFactory): AppendOnlyPersistentMap<String, PartyAndCertificate,
                PersistentPublicKeyHashToCertificate, String> {
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

        fun createNameToPartyMap(cacheFactory: NamedCacheFactory): NonInvalidatingCache<CordaX500Name, Optional<Party>> {
            return NonInvalidatingCache(
                    cacheFactory = cacheFactory,
                    name = "PersistentIdentityService_nameToParty",
                    loadFunction = {
                        val result = currentDBSession().find(NodeInfoSchemaV1.DBPartyAndCertificate::class.java, it.toString())
                        Optional.ofNullable(result?.toLegalIdentityAndCert()?.party)
                    }
            )
        }

        fun createKeyToPartyMap(cacheFactory: NamedCacheFactory): AppendOnlyPersistentMap<PublicKey, CordaX500Name,
                PersistentPublicKeyHashToParty, String> {
            return AppendOnlyPersistentMap(
                    cacheFactory = cacheFactory,
                    name = "PersistentIdentityService_keyToParty",
                    toPersistentEntityKey = { it.toStringShort() },
                    fromPersistentEntity = {
                        Pair(
                                Crypto.decodePublicKey(it.publicKey),
                                CordaX500Name.parse(it.name)
                        )
                    },
                    toPersistentEntity = { key: PublicKey, value: CordaX500Name ->
                        PersistentPublicKeyHashToParty(key.toStringShort(), value.toString(), key.encoded)
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

            @Type(type = "corda-blob")
            @Column(name = IDENTITY_COLUMN_NAME, nullable = false)
            var identity: ByteArray = EMPTY_BYTE_ARRAY
    )

    @Entity
    @javax.persistence.Table(name = KEY_TO_NAME_TABLE_NAME)
    class PersistentPublicKeyHashToParty(
            @Id
            @Suppress("Unused")
            @Column(name = PK_HASH_COLUMN_NAME, length = MAX_HASH_HEX_SIZE, nullable = false)
            var publicKeyHash: String = "",

            @Column(name = NAME_COLUMN_NAME, length = 128, nullable = false)
            var name: String = "",

            @Type(type = "corda-blob")
            @Column(name = "public_key", nullable = false)
            var publicKey: ByteArray = ArrayUtils.EMPTY_BYTE_ARRAY
    )

    private lateinit var _caCertStore: CertStore
    override val caCertStore: CertStore get() = _caCertStore

    private lateinit var _trustRoot: X509Certificate
    override val trustRoot: X509Certificate get() = _trustRoot

    private lateinit var _trustAnchor: TrustAnchor
    override val trustAnchor: TrustAnchor get() = _trustAnchor

    /** Stores notary identities obtained from the network parameters, for which we don't need to perform a database lookup. */
    @Volatile
    private var notaryIdentityCache = HashSet<Party>()

    // CordaPersistence is not a c'tor parameter to work around the cyclic dependency
    lateinit var database: CordaPersistence

    private lateinit var _pkToIdCache: WritablePublicKeyToOwningIdentityCache

    private val keyToPartyAndCert = createKeyToPartyAndCertMap(cacheFactory)
    private val nameToParty = createNameToPartyMap(cacheFactory)
    private val keyToParty = createKeyToPartyMap(cacheFactory)

    fun start(
            trustRoot: X509Certificate,
            caCertificates: List<X509Certificate> = emptyList(),
            notaryIdentities: List<Party> = emptyList(),
            pkToIdCache: WritablePublicKeyToOwningIdentityCache
    ) {
        _trustRoot = trustRoot
        _trustAnchor = TrustAnchor(trustRoot, null)
        _caCertStore = CertStore.getInstance("Collection", CollectionCertStoreParameters(caCertificates.toSet() + trustRoot))
        _pkToIdCache = pkToIdCache
        notaryIdentityCache.addAll(notaryIdentities)
    }

    fun loadIdentities(identities: Collection<PartyAndCertificate>) {
        identities.forEach {
            val key = mapToKey(it)
            keyToPartyAndCert.addWithDuplicatesAllowed(key, it, false)
            nameToParty.asMap()[it.name] = Optional.of(it.party)
            keyToParty.addWithDuplicatesAllowed(it.owningKey, it.name, false)
        }
        log.debug("Identities loaded")
    }

    /** TODO: deprecate except for internal use from network map cache, which should be eventually replaced with [registerKey] */
    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    override fun verifyAndRegisterIdentity(identity: PartyAndCertificate): PartyAndCertificate? {
        return verifyAndRegisterIdentity(identity, false)
    }

    /** TODO: deprecate */
    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    override fun verifyAndRegisterIdentity(identity: PartyAndCertificate, isNewRandomIdentity: Boolean): PartyAndCertificate? {
        return database.transaction {
            verifyAndRegisterIdentity(trustAnchor, identity, isNewRandomIdentity)
        }
    }

    /**
     * Verifies that an identity is valid. If it is valid, it gets registered in the database and the [PartyAndCertificate] is returned.
     *
     * @param trustAnchor The trust anchor that will verify the identity's validity
     * @param identity The identity to verify
     * @param isNewRandomIdentity true if identity will not have been registered before (e.g. because it is randomly generated by us)
     */
    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    private fun verifyAndRegisterIdentity(trustAnchor: TrustAnchor, identity: PartyAndCertificate, isNewRandomIdentity: Boolean = false):
            PartyAndCertificate? {
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
        if (wellKnownCert != identity.certificate && !isNewRandomIdentity) {
            val idx = identityCertChain.lastIndexOf(wellKnownCert)
            val firstPath = X509Utilities.buildCertPath(identityCertChain.slice(idx until identityCertChain.size))
            verifyAndRegisterIdentity(trustAnchor, PartyAndCertificate(firstPath))
        }
        return registerIdentity(identity, isNewRandomIdentity)
    }

    private fun registerIdentity(identity: PartyAndCertificate, isNewRandomIdentity: Boolean): PartyAndCertificate? {
        log.debug { "Registering identity $identity" }
        val identityCertChain = identity.certPath.x509Certificates
        val key = mapToKey(identity)

        if (isNewRandomIdentity) {
            // Because this is supposed to be new and random, there's no way we have it in the database already, so skip the this check
            keyToPartyAndCert[key] = identity
            // keyToName is already registered using KMS freshKeyInternal()
            val parentId = identityCertChain[1].publicKey.toStringShort()
            return keyToPartyAndCert[parentId]
        } else {
            return database.transaction {
                keyToPartyAndCert.addWithDuplicatesAllowed(key, identity, false)
                /** TODO: exclude confidential identity */
                nameToParty.invalidate(identity.name)
                keyToParty.addWithDuplicatesAllowed(identity.owningKey, identity.name, false)
                val parentId = identityCertChain[1].publicKey.toStringShort()
                keyToPartyAndCert[parentId]
            }
        }
    }

    /** TODO: deprecate */
    override fun certificateFromKey(owningKey: PublicKey): PartyAndCertificate? = database.transaction {
        keyToPartyAndCert[owningKey.toStringShort()]
    }

    override fun partyFromKey(key: PublicKey): Party? {
        return certificateFromKey(key)?.party ?: database.transaction {
            keyToParty[key]
        }?.let { wellKnownPartyFromX500Name(it) }
    }

    /** TODO: deprecate */
    // We give the caller a copy of the data set to avoid any locking problems
    override fun getAllIdentities(): Iterable<PartyAndCertificate> {
        return database.transaction {
            keyToPartyAndCert.allPersisted.use { it.map { it.second }.toList() }
        }
    }

    override fun wellKnownPartyFromX500Name(name: CordaX500Name): Party? = database.transaction {
        nameToParty[name]?.orElse(null)
    }

    override fun wellKnownPartyFromAnonymous(party: AbstractParty): Party? {
        // Skip database lookup if the party is a notary identity.
        // This also prevents an issue where the notary identity can't be resolved if it's not in the network map cache. The node obtains
        // a trusted list of notary identities from the network parameters automatically.
        return if (party is Party && party in notaryIdentityCache) {
            party
        } else {
            database.transaction {
                if (party is Party) {
                    val candidate = wellKnownPartyFromX500Name(party.name)
                    if (candidate != null && candidate != party) {
                        require(party.name == candidate.name) { "Candidate party $candidate does not match expected $party" }
                        // Check that key is registered, otherwise return null.
                        keyToParty[party.owningKey]?.let { candidate }
                    } else {
                        // Skip key registration check for well-known party.
                        candidate
                    }
                } else {
                    keyToParty[party.owningKey]?.let {
                        wellKnownPartyFromX500Name(it)
                    }
                }
            }
        }
    }

    private fun getAllCertificates(session: Session): List<NodeInfoSchemaV1.DBPartyAndCertificate> {
        val criteria = session.criteriaBuilder.createQuery(NodeInfoSchemaV1.DBPartyAndCertificate::class.java)
        criteria.select(criteria.from(NodeInfoSchemaV1.DBPartyAndCertificate::class.java))
        return session.createQuery(criteria).resultList
    }

    override fun partiesFromName(query: String, exactMatch: Boolean): Set<Party> {
        return database.transaction {
            getAllCertificates(session)
                    .map { it.toLegalIdentityAndCert() }
                    .filter { x500Matches(query, exactMatch, it.name) }
                    .map { it.party }.toSet()
        }
    }

    @Throws(UnknownAnonymousPartyException::class)
    override fun assertOwnership(party: Party, anonymousParty: AnonymousParty) = database.transaction { super.assertOwnership(party,
            anonymousParty) }

    /** TODO: look for better implementation */
    lateinit var ourParty: Party

    override fun registerKey(publicKey: PublicKey, party: Party, externalId: UUID?) {
        return database.transaction {
            // EVERY key should be mapped to a Party in the "keyToName" table. Therefore if there is already a record in that table for the
            // specified key then it's either our key which has been stored prior or another node's key which we have previously mapped.
            val existingEntryForKey = keyToParty[publicKey]
            if (existingEntryForKey == null) {
                // Update the three tables as necessary. We definitely store the public key and map it to a party and we optionally update
                // the public key to external ID mapping table. This block will only ever be reached when registering keys generated on
                // other because when a node generates its own keys "registerKeyToParty" is automatically called by
                // KeyManagementService.freshKey.
                registerKeyToParty(publicKey, party)
                if (externalId != null) {
                    registerKeyToExternalId(publicKey, externalId)
                }
            } else {
                val publicKeyHash = publicKey.toStringShort()
                log.info("An existing entry for $publicKeyHash already exists.")
                if (party.name != existingEntryForKey) {
                    throw IllegalStateException("The public publicKey $publicKeyHash is already assigned to a different party than the " +
                            "supplied party.")
                }
            }
        }
    }

    // Internal function used by the KMS to register a public key to a Corda Party.
    fun registerKeyToParty(publicKey: PublicKey, party: Party = ourParty) {
        return database.transaction {
            log.info("Linking: ${publicKey.hash} to ${party.name}")
            keyToParty[publicKey] = party.name
            if (party == ourParty) {
                _pkToIdCache[publicKey] = KeyOwningIdentity.UnmappedIdentity
            }
        }
    }

    // Internal function used by the KMS to register a public key to an external ID.
    fun registerKeyToExternalId(publicKey: PublicKey, externalId: UUID) {
        _pkToIdCache[publicKey] = KeyOwningIdentity.fromUUID(externalId)
    }

    override fun externalIdForPublicKey(publicKey: PublicKey): UUID? {
        return _pkToIdCache[publicKey].uuid
    }

    override fun publicKeysForExternalId(externalId: UUID): Iterable<PublicKey> {
        return database.transaction {
            val query = session.createQuery(
                    """
                        select a.publicKey
                        from ${PersistentPublicKeyHashToParty::class.java.name} a, ${PublicKeyHashToExternalId::class.java.name} b
                        where b.externalId = :uuid
                        and b.publicKeyHash = a.publicKeyHash
                    """,
                    ByteArray::class.java
            )
            query.setParameter("uuid", externalId)
            query.resultList.map { Crypto.decodePublicKey(it) }
        }
    }

    override fun onNewNotaryList(notaries: List<NotaryInfo>) {
        notaryIdentityCache = HashSet(notaries.map { it.identity })
    }
}