package net.corda.confidential.service

import net.corda.core.crypto.DigitalSignature
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import java.security.PublicKey
import java.util.*

/**
 * TODO
 */
fun createSignedPublicKey(serviceHub: ServiceHub, uuid: UUID): SignedPublicKey {
    val key = serviceHub.keyManagementService.freshKey(uuid)
    val sig = serviceHub.keyManagementService.sign(key.encoded, key)
    return SignedPublicKey(mapOf(key to serviceHub.myInfo.legalIdentities.first()), sig)
}

data class SignedPublicKey(
        val publicKeyToPartyMap: Map<PublicKey, Party>,
        val signature: DigitalSignature.WithKey
)
