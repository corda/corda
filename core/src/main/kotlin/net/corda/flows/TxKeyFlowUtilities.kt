package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.unwrap
import java.security.PublicKey
import java.security.cert.Certificate

object TxKeyFlowUtilities {
    /**
     * Receive a key from a counterparty. This would normally be triggered by a flow as part of a transaction assembly
     * process.
     */
    @Suspendable
    fun receiveKey(flow: FlowLogic<*>, otherSide: Party): Pair<PublicKey, Certificate?> {
        val untrustedKey = flow.receive<ProvidedTransactionKey>(otherSide)
        return untrustedKey.unwrap {
            // TODO: Verify the certificate connects the given key to the counterparty, once we have certificates
            Pair(it.key, it.certificate)
        }
    }

    /**
     * Generates a new key and then returns it to the counterparty and as the result from the function. Note that this
     * is an expensive operation, and should only be called once the calling flow has confirmed it wants to be part of
     * a transaction with the counterparty, in order to avoid a DoS risk.
     */
    @Suspendable
    fun provideKey(flow: FlowLogic<*>, otherSide: Party): PublicKey {
        val key = flow.serviceHub.keyManagementService.freshKey()
        // TODO: Generate and sign certificate for the key, once we have signing support for composite keys
        //       (in this case the legal identity key)
        flow.send(otherSide, ProvidedTransactionKey(key, null))
        return key
    }

    @CordaSerializable
    data class ProvidedTransactionKey(val key: PublicKey, val certificate: Certificate?)
}
