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

import net.corda.core.crypto.SecureHash
import net.corda.core.identity.*
import net.corda.core.internal.hash
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
import org.apache.commons.lang.ArrayUtils.EMPTY_BYTE_ARRAY
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
            verifyAndRegisterIdentity(trustAnchor, identity)
        }
    }

    override fun registerIdentity(identity: PartyAndCertificate): PartyAndCertificate? {
        val identityCertChain = identity.certPath.x509Certificates
        log.debug { "Registering identity $identity" }
        val key = mapToKey(identity)
        keyToParties.addWithDuplicatesAllowed(key, identity)
        // Always keep the first party we registered, as that's the well known identity
        principalToParties.addWithDuplicatesAllowed(identity.name, key, false)
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
    override fun getAllIdentities(): Iterable<PartyAndCertificate> = database.transaction { keyToParties.allPersisted().map { it.second }.asIterable() }

    override fun wellKnownPartyFromX500Name(name: CordaX500Name): Party? = certificateFromCordaX500Name(name)?.party

    override fun wellKnownPartyFromAnonymous(party: AbstractParty): Party? = database.transaction { super.wellKnownPartyFromAnonymous(party) }

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
    override fun assertOwnership(party: Party, anonymousParty: AnonymousParty) = database.transaction { super.assertOwnership(party, anonymousParty) }

}
