package net.corda.core.identity

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import java.security.PublicKey
import java.security.cert.CertPath

/**
 * A full party plus the X.509 certificate and path linking the party back to a trust root.
 */
class PartyAndCertificate(name: X500Name, owningKey: PublicKey,
                          val certificate: X509CertificateHolder,
                          val certPath: CertPath) : Party(name, owningKey)