package net.corda.node.services.api

import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.IdentityService
import java.security.InvalidAlgorithmParameterException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException

interface IdentityServiceInternal : IdentityService {
    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    fun verifyAndRegisterNewRandomIdentity(identity: PartyAndCertificate)
}