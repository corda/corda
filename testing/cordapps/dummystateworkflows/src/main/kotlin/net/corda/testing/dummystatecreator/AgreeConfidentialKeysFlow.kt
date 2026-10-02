package net.corda.testing.dummystatecreator

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.verify
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.services.KeyManagementService
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.unwrap
import net.corda.testing.core.singleIdentity
import java.security.PublicKey

/**
 * This is a simplified mechanism to agree Confidential Identities for a test scenario using the newer mechanism not issuing a certificate
 * for every key. This class is not safe for production use cases - use the confidential identities SDK if you need this in your CorDapp.
 */
class AgreeConfidentialKeysFlow(val counterPartySession: FlowSession) : FlowLogic<Map<Party, AnonymousParty>>() {

    companion object {
        @CordaSerializable
        data class SignedKey(val keyBytes: OpaqueBytes, val signature: DigitalSignature)

        fun SignedKey.checkSigAndGetKey(signingKey: PublicKey): PublicKey {
            require(signingKey.verify(this.keyBytes.bytes, this.signature)) { "The keybytes for the new public key are not signed by the expected party" }
            return this.keyBytes.deserialize()
        }

        fun signKey(newKey: PublicKey, signingKey: PublicKey, keyManagementService: KeyManagementService): SignedKey {
            val serializedKey = newKey.serialize()
            val sig = keyManagementService.sign(serializedKey.bytes, signingKey)
            return SignedKey(serializedKey, sig)
        }
    }

    @Suspendable
    override fun call(): Map<Party, AnonymousParty> {
        val myParty = serviceHub.myInfo.singleIdentity()
        val counterParty = counterPartySession.counterparty

        val myNewKey = serviceHub.keyManagementService.freshKey()

        val counterPartyKey = counterPartySession.sendAndReceive<SignedKey>(
                signKey(myNewKey, myParty.owningKey, serviceHub.keyManagementService))
                .unwrap { it.checkSigAndGetKey(counterParty.owningKey) }

        serviceHub.identityService.registerKey(counterPartyKey, counterParty)

        return mapOf(myParty to AnonymousParty(myNewKey), counterParty to AnonymousParty(counterPartyKey))
    }
}

