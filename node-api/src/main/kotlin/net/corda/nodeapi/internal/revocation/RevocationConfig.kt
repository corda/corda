package net.corda.nodeapi.internal.revocation

import java.security.cert.CertStore

/**
 * Holds the configuration related to the certificate revocation.
 */
data class RevocationConfig(val crlCheckSoftFail: Boolean = true, val certStores: Set<CertStore> = emptySet())