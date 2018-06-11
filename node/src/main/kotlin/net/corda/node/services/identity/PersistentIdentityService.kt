/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.identity

import net.corda.core.contracts.PartyAndReference
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.*
import net.corda.core.internal.CertRole
import net.corda.core.internal.hash
import net.corda.core.node.services.UnknownAnonymousPartyException
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.MAX_HASH_HEX_SIZE
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.x509Certificates
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import org.apache.commons.lang.ArrayUtils.EMPTY_BYTE_ARRAY
import java.io.Serializable
import java.security.InvalidAlgorithmParameterException
import java.security.PublicKey
import java.security.cert.*
import javax.annotation.concurrent.ThreadSafe
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Lob

/**
 * An identity service that stores parties and their identities to a key value tables in the database. The entries are
 * cached for efficient lookup.
 *
 * @param trustRoot certificate from the zone operator for identity on the network.
 * @param caCertificates list of additional certificates.
 */
// TODO There is duplicated logic between this and InMemoryIdentityService
@ThreadSafe
class PersistentIdentityService(override val trustRoot: X509Certificate,
                                private val database: CordaPersistence,
                                caCertificates: List<X509Certificate> = emptyList()) : SingletonSerializeAsToken(), IdentityServiceInternal {

    companion object {
        private val log = contextLogger()

        fun createPKMap(): AppendOnlyPersistentMap<SecureHash, PartyAndCertificate, PersistentIdentity, String> {
            return AppendOnlyPersistentMap(
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

        fun createX500Map(): AppendOnlyPersistentMap<CordaX500Name, SecureHash, PersistentIdentityNames, String> {
            return AppendOnlyPersistentMap(
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
    ) : Serializable

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}named_identities")
    class PersistentIdentityNames(
            @Id
            @Column(name = "name", length = 128, nullable = false)
            var name: String = "",

            @Column(name = "pk_hash", length = MAX_HASH_HEX_SIZE, nullable = true)
            var publicKeyHash: String? = ""
    ) : Serializable

    override val caCertStore: CertStore
    override val trustAnchor: TrustAnchor = TrustAnchor(trustRoot, null)

    private val keyToParties = createPKMap()
    private val principalToParties = createX500Map()

    init {
        val caCertificatesWithRoot: Set<X509Certificate> = caCertificates.toSet() + trustRoot
        caCertStore = CertStore.getInstance("Collection", CollectionCertStoreParameters(caCertificatesWithRoot))
    }

    /** Requires a database transaction. */
    fun loadIdentities(identities: Iterable<PartyAndCertificate> = emptySet(), confidentialIdentities: Iterable<PartyAndCertificate> = emptySet()) {
        database.transaction {
            identities.forEach {
                val key = mapToKey(it)
                keyToParties.addWithDuplicatesAllowed(key, it, false)
                principalToParties.addWithDuplicatesAllowed(it.name, key, false)
            }
            confidentialIdentities.forEach {
                principalToParties.addWithDuplicatesAllowed(it.name, mapToKey(it), false)
            }
            log.debug("Identities loaded")
        }
    }

    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    override fun verifyAndRegisterIdentity(identity: PartyAndCertificate): PartyAndCertificate? {
        return database.transaction {

            // Validate the chain first, before we do anything clever with it
            val identityCertChain = identity.certPath.x509Certificates
            try {
                identity.verify(trustAnchor)
            } catch (e: CertPathValidatorException) {
                log.warn(e.localizedMessage)
                log.warn("Path = ")
                identityCertChain.reversed().forEach {
                    log.warn(it.subjectX500Principal.toString())
                }
                throw e
            }

            // Ensure we record the first identity of the same name, first
            val wellKnownCert = identityCertChain.single { CertRole.extract(it)?.isWellKnown ?: false }
            if (wellKnownCert != identity.certificate) {
                val idx = identityCertChain.lastIndexOf(wellKnownCert)
                val firstPath = X509Utilities.buildCertPath(identityCertChain.slice(idx until identityCertChain.size))
                verifyAndRegisterIdentity(PartyAndCertificate(firstPath))
            }

            log.debug { "Registering identity $identity" }
            val key = mapToKey(identity)
            keyToParties.addWithDuplicatesAllowed(key, identity, false)
            // Always keep the first party we registered, as that's the well known identity
            principalToParties.addWithDuplicatesAllowed(identity.name, key, false)
            val parentId = mapToKey(identityCertChain[1].publicKey)
            keyToParties[parentId]
        }
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
    override fun getAllIdentities(): Iterable<PartyAndCertificate> = database.transaction { keyToParties.allPersisted().map { it.second }.asIterable() }

    override fun partyFromKey(key: PublicKey): Party? = certificateFromKey(key)?.party
    override fun wellKnownPartyFromX500Name(name: CordaX500Name): Party? = certificateFromCordaX500Name(name)?.party
    override fun wellKnownPartyFromAnonymous(party: AbstractParty): Party? {
        return database.transaction {
            // The original version of this would return the party as-is if it was a Party (rather than AnonymousParty),
            // however that means that we don't verify that we know who owns the key. As such as now enforce turning the key
            // into a party, and from there figure out the well known party.
            val candidate = partyFromKey(party.owningKey)
            // TODO: This should be done via the network map cache, which is the authoritative source of well known identities
            if (candidate != null) {
                wellKnownPartyFromX500Name(candidate.name)
            } else {
                null
            }
        }
    }

    override fun wellKnownPartyFromAnonymous(partyRef: PartyAndReference) = wellKnownPartyFromAnonymous(partyRef.party)
    override fun requireWellKnownPartyFromAnonymous(party: AbstractParty): Party {
        return wellKnownPartyFromAnonymous(party) ?: throw IllegalStateException("Could not deanonymise party ${party.owningKey.toStringShort()}")
    }

    override fun partiesFromName(query: String, exactMatch: Boolean): Set<Party> {
        return database.transaction {
            val results = LinkedHashSet<Party>()
            for ((x500name, partyId) in principalToParties.allPersisted()) {
                partiesFromName(query, exactMatch, x500name, results, keyToParties[partyId]!!.party)
            }
            results
        }
    }

    @Throws(UnknownAnonymousPartyException::class)
    override fun assertOwnership(party: Party, anonymousParty: AnonymousParty) {
        database.transaction {
            val anonymousIdentity = certificateFromKey(anonymousParty.owningKey) ?: throw UnknownAnonymousPartyException("Unknown $anonymousParty")
            val issuingCert = anonymousIdentity.certPath.certificates[1]
            require(issuingCert.publicKey == party.owningKey) {
                "Issuing certificate's public key must match the party key ${party.owningKey.toStringShort()}."
            }
        }
    }
}
