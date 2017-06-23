package net.corda.flows

import net.corda.core.identity.AnonymousParty
import net.corda.core.serialization.CordaSerializable
import org.bouncycastle.cert.X509CertificateHolder
import java.security.cert.CertPath

@CordaSerializable
data class AnonymisedIdentity(
        val certPath: CertPath,
        val certificate: X509CertificateHolder,
        val identity: AnonymousParty) {
    constructor(myIdentity: Pair<X509CertificateHolder, CertPath>) : this(myIdentity.second,
            myIdentity.first,
            AnonymousParty(myIdentity.second.certificates.last().publicKey))
}