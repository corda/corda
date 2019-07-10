package net.corda.node.services.identity

import net.corda.core.crypto.SecureHash
import net.corda.core.identity.*
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.internal.hash
import net.corda.core.internal.toSet
import net.corda.core.node.services.UnknownAnonymousPartyException
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.MAX_HASH_HEX_SIZE
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.crypto.x509Certificates
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import org.apache.commons.lang3.ArrayUtils.EMPTY_BYTE_ARRAY
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
class PersistentIdentityService(cacheFactory: NamedCacheFactory) : SingletonSerializeAsToken(), IdentityServiceInternal {
    companion object {
        private val log = contextLogger()

        fun createPKMap(cacheFactory: NamedCacheFactory): AppendOnlyPersistentMap<SecureHash, PartyAndCertificate, PersistentIdentity, String> {
            return AppendOnlyPersistentMap(
                    cacheFactory = cacheFactory,
                    name = "PersistentIdentityService_partyByKey",
                    toPersistentEntityKey = { it.toString() },
                    fromPersistentEntity = {
                        Pair(
                                SecureHash.parse(it.publicKeyHash),
                                PartyAndCertificate(X509CertificateFactory().delegate.generateCertPath(it.identity.inputStream()))
                        )
                    },
                    toPersistentEntity = { key: SecureHash, value: PartyAndCertificate ->
                        PersistentIdentity(key.toString(), value.certPath.encoded)
                    },
                    persistentEntityClass = PersistentIdentity::class.java
            )
        }

        fun createX500Map(cacheFactory: NamedCacheFactory): AppendOnlyPersistentMap<CordaX500Name, SecureHash, PersistentIdentityNames, String> {
            return AppendOnlyPersistentMap(
                    cacheFactory = cacheFactory,
                    name = "PersistentIdentityService_partyByName",
                    toPersistentEntityKey = { it.toString() },
                    fromPersistentEntity = { Pair(CordaX500Name.parse(it.name), SecureHash.parse(it.publicKeyHash)) },
                    toPersistentEntity = { key: CordaX500Name, value: SecureHash ->
                        PersistentIdentityNames(key.toString(), value.toString())
                    },
                    persistentEntityClass = PersistentIdentityNames::class.java
            )
        }

        private fun mapToKey(owningKey: PublicKey) = owningKey.hash
        private fun mapToKey(party: PartyAndCertificate) = mapToKey(party.owningKey)
    }

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}identities")
    class PersistentIdentity(
            @Id
            @Column(name = "pk_hash", length = MAX_HASH_HEX_SIZE, nullable = false)
            var publicKeyHash: String = "",

            @Lob
            @Column(name = "identity_value", nullable = false)
            var identity: ByteArray = EMPTY_BYTE_ARRAY
    )

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}named_identities")
    class PersistentIdentityNames(
            @Id
            @Column(name = "name", length = 128, nullable = false)
            var name: String = "",

            @Column(name = "pk_hash", length = MAX_HASH_HEX_SIZE, nullable = true)
            var publicKeyHash: String? = ""
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

    private val keyToParties = createPKMap(cacheFactory)
    private val principalToParties = createX500Map(cacheFactory)

    fun start(trustRoot: X509Certificate, caCertificates: List<X509Certificate> = emptyList(), notaryIdentities: List<Party> = emptyList()) {
        _trustRoot = trustRoot
        _trustAnchor = TrustAnchor(trustRoot, null)
        _caCertStore = CertStore.getInstance("Collection", CollectionCertStoreParameters(caCertificates.toSet() + trustRoot))
        notaryIdentityCache.addAll(notaryIdentities)
    }

    fun loadIdentities(identities: Collection<PartyAndCertificate> = emptySet(), confidentialIdentities: Collection<PartyAndCertificate> = emptySet()) {
        identities.forEach {
            val key = mapToKey(it)
            keyToParties.addWithDuplicatesAllowed(key, it, false)
            principalToParties.addWithDuplicatesAllowed(it.name, key, false)
        }
        confidentialIdentities.forEach {
            keyToParties.addWithDuplicatesAllowed(mapToKey(it), it, false)
        }
        log.debug("Identities loaded")
    }

    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    override fun verifyAndRegisterIdentity(identity: PartyAndCertificate): PartyAndCertificate? {
        return verifyAndRegisterIdentity(identity, false)
    }

    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    override fun verifyAndRegisterIdentity(identity: PartyAndCertificate, isNewRandomIdentity: Boolean): PartyAndCertificate? {
        return database.transaction {
            verifyAndRegisterIdentity(trustAnchor, identity, isNewRandomIdentity)
        }
    }

    override fun registerIdentity(identity: PartyAndCertificate, isNewRandomIdentity: Boolean): PartyAndCertificate? {
        log.debug { "Registering identity $identity" }
        val identityCertChain = identity.certPath.x509Certificates
        val key = mapToKey(identity)

        if (isNewRandomIdentity) {
            // Because this is supposed to be new and random, there's no way we have it in the database already, so skip the pessimistic check.
            keyToParties[key] = identity
        } else {
            keyToParties.addWithDuplicatesAllowed(key, identity)
            principalToParties.addWithDuplicatesAllowed(identity.name, key, false)
        }

        val parentId = mapToKey(identityCertChain[1].publicKey)
        return keyToParties[parentId]
    }

    override fun certificateFromKey(owningKey: PublicKey): PartyAndCertificate? = database.transaction { keyToParties[mapToKey(owningKey)] }

    private fun certificateFromCordaX500Name(name: CordaX500Name): PartyAndCertificate? {
        return database.transaction {
            val partyId = principalToParties[name]
            if (partyId != null) {
                keyToParties[partyId]
            } else null
        }
    }

    // We give the caller a copy of the data set to avoid any locking problems
    override fun getAllIdentities(): Iterable<PartyAndCertificate> {
        return database.transaction {
            keyToParties.allPersisted.use { it.map { it.second }.toList() }
        }
    }

    override fun wellKnownPartyFromX500Name(name: CordaX500Name): Party? = certificateFromCordaX500Name(name)?.party

    override fun wellKnownPartyFromAnonymous(party: AbstractParty): Party? {
        // Skip database lookup if the party is a notary identity.
        // This also prevents an issue where the notary identity can't be resolved if it's not in the network map cache. The node obtains
        // a trusted list of notary identities from the network parameters automatically.
        return if (party is Party && party in notaryIdentityCache) {
            party
        } else {
            database.transaction { super.wellKnownPartyFromAnonymous(party) }
        }
    }

    override fun partiesFromName(query: String, exactMatch: Boolean): Set<Party> {
        return database.transaction {
            principalToParties.allPersisted.use {
                it.filter { x500Matches(query, exactMatch, it.first) }.map { keyToParties[it.second]!!.party }.toSet()
            }
        }
    }

    @Throws(UnknownAnonymousPartyException::class)
    override fun assertOwnership(party: Party, anonymousParty: AnonymousParty) = database.transaction { super.assertOwnership(party, anonymousParty) }

    lateinit var ourNames: Set<CordaX500Name>

    // Allows us to eliminate keys we know belong to others by using the cache contents that might have been seen during other identity activity.
    // Concentrating activity on the identity cache works better than spreading checking across identity and key management, because we cache misses too.
    fun stripNotOurKeys(keys: Iterable<PublicKey>): Iterable<PublicKey> {
        return keys.filter { certificateFromKey(it)?.name in ourNames }
    }
}
