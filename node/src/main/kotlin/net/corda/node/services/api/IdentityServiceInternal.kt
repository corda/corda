package net.corda.node.services.api

import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.IdentityService

interface IdentityServiceInternal : IdentityService {
    /** This method exists so it can be mocked with doNothing, rather than having to make up a possibly invalid return value. */
    fun justVerifyAndRegisterIdentity(identity: PartyAndCertificate) {
        verifyAndRegisterIdentity(identity)
    }
}
