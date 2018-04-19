/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.identity

import net.corda.core.internal.CertRole
import net.corda.core.internal.uncheckedCast
import net.corda.core.internal.validate
import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey
import java.security.cert.*

/**
 * A full party plus the X.509 certificate and path linking the party back to a trust root. Equality of
 * [PartyAndCertificate] instances is based on the party only, as certificate and path are data associated with the party,
 * not part of the identifier themselves.
 */
@CordaSerializable
class PartyAndCertificate(val certPath: CertPath) {
    @Transient
    val certificate: X509Certificate

    init {
        require(certPath.type == "X.509") { "Only X.509 certificates supported" }
        val certs = certPath.certificates
        require(certs.size >= 2) { "Certificate path must at least include subject and issuing certificates" }
        certificate = certs[0] as X509Certificate
        val role = CertRole.extract(certificate)
        require(role?.isIdentity ?: false) { "Party certificate ${certificate.subjectDN} does not have a well known or confidential identity role. Found: $role" }
    }

    @Transient
    val party: Party = Party(certificate)

    val owningKey: PublicKey get() = party.owningKey
    val name: CordaX500Name get() = party.name

    operator fun component1(): Party = party
    operator fun component2(): X509Certificate = certificate

    override fun equals(other: Any?): Boolean = other === this || other is PartyAndCertificate && other.party == party
    override fun hashCode(): Int = party.hashCode()
    override fun toString(): String = party.toString()

    /** Verify the certificate path is valid. */
    fun verify(trustAnchor: TrustAnchor): PKIXCertPathValidatorResult {
        val result = certPath.validate(trustAnchor)
        // Apply Corda-specific validity rules to the chain. This only applies to chains with any roles present, so
        // an all-null chain is in theory valid.
        var parentRole: CertRole? = CertRole.extract(result.trustAnchor.trustedCert)
        val certChain: List<X509Certificate> = uncheckedCast(certPath.certificates)
        for (certIdx in (0 until certChain.size).reversed()) {
            val certificate = certChain[certIdx]
            val role = CertRole.extract(certificate)
            if (parentRole != null) {
                if (role == null) {
                    throw CertPathValidatorException("Child certificate whose issuer includes a Corda role, must also specify Corda role")
                }
                if (!role.isValidParent(parentRole)) {
                    val certificateString = certificate.subjectDN.toString()
                    throw CertPathValidatorException("The issuing certificate for $certificateString has role $parentRole, expected one of ${role.validParents}")
                }
            }
            parentRole = role
        }
        return result
    }
}
