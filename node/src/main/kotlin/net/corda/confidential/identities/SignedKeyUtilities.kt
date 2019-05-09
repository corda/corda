package net.corda.confidential.identities

import net.corda.core.CordaInternal
import net.corda.core.identity.KeyToPartyMapping
import net.corda.core.identity.SignedKeyToPartyMapping
import net.corda.core.internal.VisibleForTesting
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.serialize
import java.security.SignatureException
import java.util.*

@CordaInternal
@VisibleForTesting
fun createSignedPublicKey(serviceHub: ServiceHub, uuid: UUID): SignedKeyToPartyMapping {
    val nodeParty = serviceHub.myInfo.legalIdentities.first()
    val newKey = serviceHub.keyManagementService.freshKey(uuid)
    val keyToPartyMapping = KeyToPartyMapping(newKey, nodeParty)
    val sig = serviceHub.keyManagementService.sign(keyToPartyMapping.serialize().hash.bytes, nodeParty.owningKey)
    return SignedKeyToPartyMapping(keyToPartyMapping, sig)
}

@CordaInternal
@VisibleForTesting
fun validateSignature(signedKeyMapping: SignedKeyToPartyMapping) {
    try {
        signedKeyMapping.signature.verify(signedKeyMapping.mapping.serialize().hash.bytes)
    } catch (ex: SignatureException) {
        throw SignatureException("The signature does not match the expected.", ex)
    }
}

@CordaSerializable
class CreateKeyForAccount(private val _uuid: UUID) {
    val uuid: UUID
        get() = _uuid
}