package net.corda.node.services.identity

import net.corda.core.contracts.PartyAndReference
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.toX509CertHolder
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.UnknownAnonymousPartyException
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.cert
import net.corda.core.utilities.loggerFor
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.node.utilities.NODE_DATABASE_PREFIX
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import java.io.ByteArrayInputStream
import java.security.InvalidAlgorithmParameterException
import java.security.PublicKey
import java.security.cert.*
import javax.annotation.concurrent.ThreadSafe
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Lob

@ThreadSafe
class PersistentIdentityService(identities: Iterable<PartyAndCertificate> = emptySet(),
                                confidentialIdentities: Iterable<PartyAndCertificate> = emptySet(),
                                override val trustRoot: X509Certificate,
                                vararg caCertificates: X509Certificate) : SingletonSerializeAsToken(), IdentityService {
    constructor(wellKnownIdentities: Iterable<PartyAndCertificate> = emptySet(),
                confidentialIdentities: Iterable<PartyAndCertificate> = emptySet(),
                trustRoot: X509CertificateHolder) : this(wellKnownIdentities, confidentialIdentities, trustRoot.cert)

    companion object {
        private val log = loggerFor<PersistentIdentityService>()
        private val certFactory: CertificateFactory = CertificateFactory.getInstance("X.509")

        fun createPKMap(): AppendOnlyPersistentMap<SecureHash, PartyAndCertificate, PersistentIdentity, String> {
            return AppendOnlyPersistentMap(
                    toPersistentEntityKey = { it.toString() },
                    fromPersistentEntity = {
                        Pair(SecureHash.parse(it.publicKeyHash),
                                PartyAndCertificate(ByteArrayInputStream(it.identity).use {
                                    certFactory.generateCertPath(it)
                                }))
                    },
                    toPersistentEntity = { key: SecureHash, value: PartyAndCertificate ->
                        PersistentIdentity(key.toString(), value.certPath.encoded)
                    },
                    persistentEntityClass = PersistentIdentity::class.java
            )
        }

        fun createX500Map(): AppendOnlyPersistentMap<X500Name, SecureHash, PersistentIdentityNames, String> {
            return AppendOnlyPersistentMap(
                    toPersistentEntityKey = { it.toString() },
                    fromPersistentEntity = { Pair(X500Name(it.name), SecureHash.parse(it.publicKeyHash)) },
                    toPersistentEntity = { key: X500Name, value: SecureHash ->
                        PersistentIdentityNames(key.toString(), value.toString())
                    },
                    persistentEntityClass = PersistentIdentityNames::class.java
            )
        }

        private fun mapToKey(owningKey: PublicKey) = SecureHash.sha256(owningKey.encoded)
        private fun mapToKey(party: PartyAndCertificate) = mapToKey(party.owningKey)
    }

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}identities")
    class PersistentIdentity(
            @Id
            @Column(name = "pk_hash", length = 64)
            var publicKeyHash: String = "",

            @Lob
            @Column
            var identity: ByteArray = ByteArray(0)
    )

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}named_identities")
    class PersistentIdentityNames(
            @Id
            @Column(name = "name", length = 128)
            var name: String = "",

            @Column(name = "pk_hash", length = 64)
            var publicKeyHash: String = ""
    )

    override val caCertStore: CertStore
    override val trustRootHolder = trustRoot.toX509CertHolder()
    override val trustAnchor: TrustAnchor = TrustAnchor(trustRoot, null)

    private val keyToParties = createPKMap()
    private val principalToParties = createX500Map()

    init {
        val caCertificatesWithRoot: Set<X509Certificate> = caCertificates.toSet() + trustRoot
        caCertStore = CertStore.getInstance("Collection", CollectionCertStoreParameters(caCertificatesWithRoot))
        identities.forEach {
            val key = mapToKey(it)
            keyToParties.addWithDuplicatesAllowed(key, it)
            principalToParties.addWithDuplicatesAllowed(it.name, key)
        }
        confidentialIdentities.forEach {
            principalToParties.addWithDuplicatesAllowed(it.name, mapToKey(it))
        }
    }

    override fun registerIdentity(party: PartyAndCertificate) {
        verifyAndRegisterIdentity(party)
    }

    // TODO: Check the certificate validation logic
    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    override fun verifyAndRegisterIdentity(identity: PartyAndCertificate): PartyAndCertificate? {
        // Validate the chain first, before we do anything clever with it
        identity.verify(trustAnchor)

        log.info("Registering identity $identity")
        val key = mapToKey(identity)
        keyToParties.addWithDuplicatesAllowed(key, identity)
        // Always keep the first party we registered, as that's the well known identity
        principalToParties.addWithDuplicatesAllowed(identity.name, key)
        val parentId = mapToKey(identity.certPath.certificates[1].publicKey)
        return keyToParties[parentId]
    }

    override fun certificateFromKey(owningKey: PublicKey): PartyAndCertificate? = keyToParties[mapToKey(owningKey)]
    private fun certificateFromX500Name(name: X500Name): PartyAndCertificate? {
        val partyId = principalToParties[name]
        return if (partyId != null) {
            keyToParties[partyId]
        } else null
    }

    override fun certificateFromParty(party: Party): PartyAndCertificate = certificateFromX500Name(party.name) ?: throw IllegalArgumentException("Unknown identity ${party.name}")

    // We give the caller a copy of the data set to avoid any locking problems
    override fun getAllIdentities(): Iterable<PartyAndCertificate> = keyToParties.allPersisted().map { it.second }.asIterable()

    override fun partyFromKey(key: PublicKey): Party? = certificateFromKey(key)?.party
    override fun partyFromX500Name(principal: X500Name): Party? = certificateFromX500Name(principal)?.party
    override fun partyFromAnonymous(party: AbstractParty): Party? {
        // Expand the anonymous party to a full party (i.e. has a name) if possible
        val candidate = party as? Party ?: partyFromKey(party.owningKey)
        // TODO: This should be done via the network map cache, which is the authoritative source of well known identities
        // Look up the well known identity for that name
        return if (candidate != null) {
            // If we have a well known identity by that name, use it in preference to the candidate. Otherwise default
            // back to the candidate.
            val res = partyFromX500Name(candidate.name) ?: candidate
            res
        } else {
            null
        }
    }

    override fun partyFromAnonymous(partyRef: PartyAndReference) = partyFromAnonymous(partyRef.party)
    override fun requirePartyFromAnonymous(party: AbstractParty): Party {
        return partyFromAnonymous(party) ?: throw IllegalStateException("Could not deanonymise party ${party.owningKey.toStringShort()}")
    }

    override fun partiesFromName(query: String, exactMatch: Boolean): Set<Party> {
        val results = LinkedHashSet<Party>()
        for ((x500name, partyId) in principalToParties.allPersisted()) {
            val party = keyToParties[partyId]!!.party
            for (rdn in x500name.rdNs) {
                val component = rdn.first.value.toString()
                if (exactMatch && component == query) {
                    results += party
                } else if (!exactMatch) {
                    // We can imagine this being a query over a lucene index in future.
                    //
                    // Kostas says: We can easily use the Jaro-Winkler distance metric as it is best suited for short
                    // strings such as entity/company names, and to detect small typos. We can also apply it for city
                    // or any keyword related search in lists of records (not raw text - for raw text we need indexing)
                    // and we can return results in hierarchical order (based on normalised String similarity 0.0-1.0).
                    if (component.contains(query, ignoreCase = true))
                        results += party
                }
            }
        }
        return results
    }

    @Throws(UnknownAnonymousPartyException::class)
    override fun assertOwnership(party: Party, anonymousParty: AnonymousParty) {
        val anonymousIdentity = certificateFromKey(anonymousParty.owningKey) ?:
                throw UnknownAnonymousPartyException("Unknown $anonymousParty")
        val issuingCert = anonymousIdentity.certPath.certificates[1]
        require(issuingCert.publicKey == party.owningKey) {
            "Issuing certificate's public key must match the party key ${party.owningKey.toStringShort()}."
        }
    }
}