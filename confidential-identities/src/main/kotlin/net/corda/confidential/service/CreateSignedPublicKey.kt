package net.corda.confidential.service

import net.corda.core.crypto.DigitalSignature
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.serialize
import java.security.PublicKey
import java.util.*

/**
 * TODO
 */
fun createSignedPublicKey(serviceHub: ServiceHub, uuid: UUID): SignedPublicKey {
    val key = serviceHub.keyManagementService.freshKey(uuid)
    val map = mapOf(key to serviceHub.myInfo.legalIdentities.first())
    val sig = serviceHub.keyManagementService.sign(map.serialize().hash.bytes, key)
    return SignedPublicKey(map, sig)
}

@CordaSerializable
data class SignedPublicKey(
        val publicKeyToPartyMap: Map<PublicKey, Party>,
        val signature: DigitalSignature.WithKey
)
