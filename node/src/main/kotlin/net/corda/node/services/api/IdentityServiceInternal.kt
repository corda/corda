package net.corda.node.services.api

import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.IdentityService
import net.corda.core.utilities.contextLogger
import java.security.InvalidAlgorithmParameterException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException

interface IdentityServiceInternal : IdentityService {
    private companion object {
        val log = contextLogger()
    }

    /** TODO: deprecate */
    /** This method exists so it can be mocked with doNothing, rather than having to make up a possibly invalid return value. */
    fun justVerifyAndRegisterIdentity(identity: PartyAndCertificate, isNewRandomIdentity: Boolean = false) {
        verifyAndRegisterIdentity(identity, isNewRandomIdentity)
    }

    /** TODO: deprecate */
    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    fun verifyAndRegisterIdentity(identity: PartyAndCertificate, isNewRandomIdentity: Boolean): PartyAndCertificate?
}