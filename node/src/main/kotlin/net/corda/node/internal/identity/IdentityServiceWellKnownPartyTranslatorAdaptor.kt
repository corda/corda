package net.corda.node.internal.identity

import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.IdentityService

/**
 * Since `IdentityService` cannot implement internal interface of `WellKnownPartyTranslator` this adaptor is there to fill the gap.
 */
class IdentityServiceWellKnownPartyTranslatorAdaptor(private val identityService: IdentityService) : WellKnownPartyTranslator {
    override fun wellKnownPartyFromX500Name(name: CordaX500Name): Party? {
        return identityService.wellKnownPartyFromX500Name(name)
    }

    override fun wellKnownPartyFromAnonymous(party: AbstractParty): Party? {
        return identityService.wellKnownPartyFromAnonymous(party)
    }
}