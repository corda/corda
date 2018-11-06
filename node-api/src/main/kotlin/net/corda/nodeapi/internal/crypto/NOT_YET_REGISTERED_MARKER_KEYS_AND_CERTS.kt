package net.corda.nodeapi.internal.crypto

import net.corda.core.crypto.Crypto
import net.corda.nodeapi.internal.crypto.X509Utilities.createSelfSignedCACertificate
import java.math.BigInteger
import javax.security.auth.x500.X500Principal

/**
 * Dummy keys and certificates mainly required when we need to store dummy entries to KeyStores, i.e., as progress
 * indicators in node registration. */
object NOT_YET_REGISTERED_MARKER_KEYS_AND_CERTS {
    val ECDSAR1_KEYPAIR by lazy { Crypto.deriveKeyPairFromEntropy(Crypto.ECDSA_SECP256R1_SHA256, BigInteger.valueOf(0)) }
    val ECDSAR1_CERT by lazy { createSelfSignedCACertificate(X500Principal("CN=DUMMY"), ECDSAR1_KEYPAIR) }
}
