package net.corda.node.services.keys

import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.IdentityService
import net.corda.core.node.services.KeyManagementService
import org.bouncycastle.operator.ContentSigner
import java.security.PublicKey
import java.util.*

interface KeyManagementServiceInternal : KeyManagementService {

    val identityService: IdentityService

    fun start(initialKeysAndAliases: List<Pair<PublicKey, String>>)

    fun freshKeyInternal(externalId: UUID?): PublicKey

    fun getSigner(publicKey: PublicKey): ContentSigner

    // Unlike initial keys, freshkey() is related confidential keys and it utilises platform's software key generation
    // thus, without using [cryptoService]).
    override fun freshKey(): PublicKey {
        return freshKeyInternal(null)
    }

    override fun freshKey(externalId: UUID): PublicKey {
        return freshKeyInternal(externalId)
    }

    override fun freshKeyAndCert(identity: PartyAndCertificate, revocationEnabled: Boolean): PartyAndCertificate {
        return freshCertificate(identityService, freshKeyInternal(null), identity, getSigner(identity.owningKey))
    }

    override fun freshKeyAndCert(identity: PartyAndCertificate, revocationEnabled: Boolean, externalId: UUID): PartyAndCertificate {
        return freshCertificate(identityService, freshKeyInternal(externalId), identity, getSigner(identity.owningKey))
    }
}

