package net.corda.core.crypto

import java.security.Provider

/**
 * A simple I2P [Provider] that supports the EdDSA signature scheme.
 */
class I2PProvider : Provider("I2P", 0.1, "I2P Security Provider v0.1, implementing I2P's EdDSA 25519.") {

    init { setup() }

    private fun setup() {
        // Key OID: 1.3.101.100; Sig OID: 1.3.101.101
        put("KeyFactory.EdDSA", "net.i2p.crypto.eddsa.KeyFactory")
        put("KeyPairGenerator.EdDSA", "net.i2p.crypto.eddsa.KeyPairGenerator")
        put("Signature.SHA512withEdDSA", "net.i2p.crypto.eddsa.EdDSAEngine")

        // without these, Certificate.verify() fails.
        put("Alg.Alias.KeyFactory.1.3.101.100", "EdDSA")
        put("Alg.Alias.KeyFactory.OID.1.3.101.100", "EdDSA")

        // Without these, keytool fails with:
        // keytool error: java.security.NoSuchAlgorithmException: unrecognized algorithm name: SHA512withEdDSA.
        put("Alg.Alias.KeyPairGenerator.1.3.101.100", "EdDSA")
        put("Alg.Alias.KeyPairGenerator.OID.1.3.101.100", "EdDSA")

        // with this setting, keytool's keygen doesn't work.
        // java.security.cert.CertificateException: Signature algorithm mismatch.
        // It must match the key setting (1.3.101.100) to work,
        // but this works fine with programmatic cert generation.
        put("Alg.Alias.Signature.1.3.101.101", "SHA512withEdDSA")
        put("Alg.Alias.Signature.OID.1.3.101.101", "SHA512withEdDSA")
    }
}
