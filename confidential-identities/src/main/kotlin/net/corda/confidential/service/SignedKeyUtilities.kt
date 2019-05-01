package net.corda.confidential.service

import net.corda.core.CordaInternal
import net.corda.core.crypto.DigitalSignature
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party
import net.corda.core.internal.VisibleForTesting
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.serialize
import net.corda.node.services.api.IdentityServiceInternal
import java.security.PublicKey
import java.security.SignatureException
import java.util.*

@CordaInternal
@VisibleForTesting
fun createSignedPublicKey(serviceHub: ServiceHub, uuid: UUID): SignedPublicKey {
    val nodeParty = serviceHub.myInfo.legalIdentities.first()
    val newKey = serviceHub.keyManagementService.freshKey(uuid)
    val map = mapOf(newKey to nodeParty)
    val sig = serviceHub.keyManagementService.sign(map.serialize().hash.bytes, nodeParty.owningKey)
    return SignedPublicKey(map, sig)
}

@CordaInternal
@VisibleForTesting
fun validateSignature(signedKey: SignedPublicKey): SignedPublicKey {
    try {
        signedKey.signature.verify(signedKey.publicKeyToPartyMap.serialize().hash.bytes)
    } catch (ex: SignatureException) {
        throw SignatureException("The signature does not match the expected.", ex)
    }
    return signedKey
}

@CordaInternal
@VisibleForTesting
fun registerIdentityMapping(serviceHub: ServiceHub, signedKey: SignedPublicKey, party: Party): Boolean {
    val publicKey: PublicKey? = signedKey.publicKeyToPartyMap.filter { identityName -> (identityName.value.name == party.name) }.keys.first()
    return if (publicKey != null) {
        (serviceHub.identityService as IdentityServiceInternal).registerIdentityMapping(party, publicKey)
    } else {
        false
    }
}

@CordaSerializable
data class SignedPublicKey(
        val publicKeyToPartyMap: Map<PublicKey, Party>,
        val signature: DigitalSignature.WithKey
)

@CordaSerializable
class CreateKeyForAccount(private val _uuid: UUID) {
    val uuid: UUID
        get() = _uuid
}