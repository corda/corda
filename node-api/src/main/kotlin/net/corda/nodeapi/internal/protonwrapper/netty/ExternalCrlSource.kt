package net.corda.nodeapi.internal.protonwrapper.netty

import java.security.cert.X509CRL
import java.security.cert.X509Certificate

interface ExternalCrlSource {

    /**
     * Given certificate provides a set of CRLs, potentially performing remote communication.
     */
    fun fetch(certificate: X509Certificate) : Set<X509CRL>
}