package net.corda.flows

import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party

abstract class AbstractIdentityFlow(val otherSide: Party, val revocationEnabled: Boolean): FlowLogic<TransactionIdentities>() {
    fun validateIdentity(untrustedIdentity: AnonymisedIdentity): AnonymisedIdentity {
        val (certPath, theirCert, txIdentity) = untrustedIdentity
        if (theirCert.subject == otherSide.name) {
            serviceHub.identityService.registerAnonymousIdentity(txIdentity, otherSide, certPath)
            return AnonymisedIdentity(certPath, theirCert, txIdentity)
        } else
            throw IllegalStateException("Expected certificate subject to be ${otherSide.name} but found ${theirCert.subject}")
    }
}