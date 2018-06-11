package net.corda.core.crypto.internal

import net.corda.core.DeleteForDJVM
import net.corda.core.crypto.CordaSecurityProvider
import net.corda.core.crypto.Crypto.EDDSA_ED25519_SHA512
import net.corda.core.crypto.Crypto.decodePrivateKey
import net.corda.core.crypto.Crypto.decodePublicKey
import net.corda.core.internal.X509EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSASecurityProvider
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jcajce.provider.util.AsymmetricKeyInfoConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import java.security.Security

internal val cordaSecurityProvider = CordaSecurityProvider().also {
    Security.insertProviderAt(it, 1) // The position is 1-based.
}
// OID taken from https://tools.ietf.org/html/draft-ietf-curdle-pkix-00
internal val `id-Curve25519ph` = ASN1ObjectIdentifier("1.3.101.112")
internal val cordaBouncyCastleProvider = BouncyCastleProvider().apply {
    putAll(EdDSASecurityProvider())
    // Override the normal EdDSA engine with one which can handle X509 keys.
    put("Signature.${EdDSAEngine.SIGNATURE_ALGORITHM}", X509EdDSAEngine::class.java.name)
    addKeyInfoConverter(`id-Curve25519ph`, object : AsymmetricKeyInfoConverter {
        override fun generatePublic(keyInfo: SubjectPublicKeyInfo) = decodePublicKey(EDDSA_ED25519_SHA512, keyInfo.encoded)
        override fun generatePrivate(keyInfo: PrivateKeyInfo) = decodePrivateKey(EDDSA_ED25519_SHA512, keyInfo.encoded)
    })
}.also {
    // This registration is needed for reading back EdDSA key from java keystore.
    // TODO: Find a way to make JKS work with bouncy castle provider or implement our own provide so we don't have to register bouncy castle provider.
    Security.addProvider(it)
}
internal val bouncyCastlePQCProvider = BouncyCastlePQCProvider().apply {
    require(name == "BCPQC") // The constant it comes from is not final.
}.also {
    Security.addProvider(it)
}
// This map is required to defend against users that forcibly call Security.addProvider / Security.removeProvider
// that could cause unexpected and suspicious behaviour.
// i.e. if someone removes a Provider and then he/she adds a new one with the same name.
// The val is private to avoid any harmful state changes.
internal val providerMap = listOf(cordaBouncyCastleProvider, cordaSecurityProvider, bouncyCastlePQCProvider).map { it.name to it }.toMap()

@DeleteForDJVM
internal fun platformSecureRandomFactory() = platformSecureRandom() // To minimise diff of CryptoUtils against open-source.