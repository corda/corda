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

        private const val HASH_TO_IDENTITY_TABLE_NAME = "${NODE_DATABASE_PREFIX}identities"
        private const val KEY_TO_NAME_TABLE_NAME = "${NODE_DATABASE_PREFIX}identities_no_cert"
        private const val PK_HASH_COLUMN_NAME = "pk_hash"
        private const val IDENTITY_COLUMN_NAME = "identity_value"
        private const val NAME_COLUMN_NAME = "name"

        private fun createKeyToPartyAndCertMap(cacheFactory: NamedCacheFactory): AppendOnlyPersistentMap<String, PartyAndCertificate,
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

        private fun createKeyToPartyMap(cacheFactory: NamedCacheFactory): AppendOnlyPersistentMap<PublicKey, Party,
                PersistentPublicKeyHashToParty, String> {
            return AppendOnlyPersistentMap(
                    cacheFactory = cacheFactory,
                    name = "PersistentIdentityService_keyToParty",
                    toPersistentEntityKey = { it.toStringShort() },
                    fromPersistentEntity = {
                        Pair(
                                Crypto.decodePublicKey(it.publicKey),
                                Party(CordaX500Name.parse(it.name), Crypto.decodePublicKey(it.partyPublicKey))
                        )
                    },
                    toPersistentEntity = { key: PublicKey, value: Party ->
                        PersistentPublicKeyHashToParty(key.toStringShort(), value.toString(), key.encoded, value.owningKey.encoded)
                    },
                    persistentEntityClass = PersistentPublicKeyHashToParty::class.java)
        }

        private fun createNameToPartyMap(cacheFactory: NamedCacheFactory): NonInvalidatingCache<CordaX500Name, Optional<Party>> {
            return NonInvalidatingCache(
                    cacheFactory = cacheFactory,
                    name = "PersistentIdentityService_nameToParty",
                    loadFunction = {
                        val result = currentDBSession().find(NodeInfoSchemaV1.DBPartyAndCertificate::class.java, it.toString())
                        Optional.ofNullable(result?.toLegalIdentityAndCert()?.party)
                    }
            )
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
            var publicKey: ByteArray = EMPTY_BYTE_ARRAY,

            @Type(type = "corda-blob")
            @Column(name = "party_public_key", nullable = false)
            var partyPublicKey: ByteArray = EMPTY_BYTE_ARRAY
    )

    private lateinit var _caCertStore: CertStore
    override val caCertStore: CertStore get() = _caCertStore

    private lateinit var _trustRoot: X509Certificate
    override val trustRoot: X509Certificate get() = _trustRoot

    private lateinit var _trustAnchor: TrustAnchor
    override val trustAnchor: TrustAnchor get() = _trustAnchor

    private lateinit var ourParty: Party

    /** Stores notary identities obtained from the network parameters, for which we don't need to perform a database lookup. */
    @Volatile
    private var notaryIdentityCache = HashSet<Party>()

    // CordaPersistence is not a c'tor parameter to work around the cyclic dependency
    lateinit var database: CordaPersistence

    private lateinit var _pkToIdCache: WritablePublicKeyToOwningIdentityCache

    private val keyToPartyAndCert = createKeyToPartyAndCertMap(cacheFactory)
    private val keyToParty = createKeyToPartyMap(cacheFactory)
    private val nameToParty = createNameToPartyMap(cacheFactory)

    fun start(
            trustRoot: X509Certificate,
            ourIdentity: PartyAndCertificate,
            notaryIdentities: List<Party> = emptyList(),
            pkToIdCache: WritablePublicKeyToOwningIdentityCache
    ) {
        _trustRoot = trustRoot
        _trustAnchor = TrustAnchor(trustRoot, null)
        // Extract Node CA certificate from node identity certificate path
        val certificates = setOf(ourIdentity.certificate, ourIdentity.certPath.certificates[1], trustRoot)
        _caCertStore = CertStore.getInstance("Collection", CollectionCertStoreParameters(certificates))
        _pkToIdCache = pkToIdCache
        ourParty = ourIdentity.party
        notaryIdentityCache.addAll(notaryIdentities)
    }

    fun loadIdentities(identities: Collection<PartyAndCertificate>) {
        identities.forEach {
            val key = mapToKey(it)
            keyToPartyAndCert.addWithDuplicatesAllowed(key, it, false)
            keyToParty.addWithDuplicatesAllowed(it.owningKey, it.party, false)
            nameToParty.asMap()[it.name] = Optional.of(it.party)
        }
        log.debug("Identities loaded")
    }

    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    override fun verifyAndRegisterIdentity(identity: PartyAndCertificate): PartyAndCertificate? {
        return verifyAndRegisterIdentity(trustAnchor, identity)
    }

    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    override fun verifyAndRegisterIdentity(identity: PartyAndCertificate, isNewRandomIdentity: Boolean, isWellKnownIdentity: Boolean) {
        verifyAndRegisterIdentity(trustAnchor, identity, isNewRandomIdentity, isWellKnownIdentity)
    }

    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    private fun verifyAndRegisterIdentity(trustAnchor: TrustAnchor,
                                          identity: PartyAndCertificate,
                                          isNewRandomIdentity: Boolean = false,
                                          isWellKnownIdentity: Boolean = false
    ): PartyAndCertificate? {
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
        return registerIdentity(identity, isNewRandomIdentity, isWellKnownIdentity)
    }

    private fun registerIdentity(identity: PartyAndCertificate,
                                 isNewRandomIdentity: Boolean,
                                 isWellKnownIdentity: Boolean
    ): PartyAndCertificate? {
        log.debug { "Registering identity $identity" }
        val identityCertChain = identity.certPath.x509Certificates
        val key = mapToKey(identity)

        return database.transaction {
            if (isNewRandomIdentity) {
                // Because this is supposed to be new and random, there's no way we have it in the database already, so skip the this check
                keyToPartyAndCert[key] = identity
                // keyToParty is already registered via KMS freshKeyInternal()
            } else {
                keyToPartyAndCert.addWithDuplicatesAllowed(key, identity, false)
                keyToParty.addWithDuplicatesAllowed(identity.owningKey, identity.party, false)
                if (isWellKnownIdentity) {
                    nameToParty.invalidate(identity.name)
                }
            }
            val parentId = identityCertChain[1].publicKey.toStringShort()
            keyToPartyAndCert[parentId]
        }
    }

    override fun certificateFromKey(owningKey: PublicKey): PartyAndCertificate? = database.transaction {
        keyToPartyAndCert[owningKey.toStringShort()]
    }

    override fun partyFromKey(key: PublicKey): Party? = database.transaction {
        keyToParty[key]
    }

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
                        // Party doesn't match existing well-known party: check that the key is registered, otherwise return null.
                        require(party.name == candidate.name) { "Candidate party $candidate does not match expected $party" }
                        keyToParty[party.owningKey]?.let { candidate }
                    } else {
                        // Party is a well-known party or well-known party doesn't exist: skip checks.
                        candidate
                    }
                } else {
                    keyToParty[party.owningKey]?.let {
                        // Rotated (inactive) well-known party should be converted to the actual well-known party.
                        wellKnownPartyFromX500Name(it.name)
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
                if (party.name != existingEntryForKey.name) {
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
            keyToParty[publicKey] = party
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