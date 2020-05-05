package net.corda.node.services.api

import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.services.IdentityService
import java.security.InvalidAlgorithmParameterException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException

interface IdentityServiceInternal : IdentityService {
    /**
     * Lighter version of [verifyAndRegisterIdentity] for newly registered confidential identity.
     * The identity will only be accessible from certain lookups by public key.
     */
    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    fun verifyAndRegisterFreshIdentity(identity: PartyAndCertificate)

    /**
     * Extended version of [verifyAndRegisterIdentity] to register legal identity from NodeInfo.
     * The identity will be available from lookups by X500 name.
     */
    @Throws(CertificateExpiredException::class, CertificateNotYetValidException::class, InvalidAlgorithmParameterException::class)
    fun verifyAndRegisterLegalIdentity(identity: PartyAndCertificate)
}