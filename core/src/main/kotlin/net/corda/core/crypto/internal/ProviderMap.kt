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
import org.bouncycastle.jcajce.provider.asymmetric.ec.AlgorithmParametersSpi
import org.bouncycastle.jcajce.provider.util.AsymmetricKeyInfoConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.util.Collections.unmodifiableMap

val cordaSecurityProvider = CordaSecurityProvider().also {
    // Among the others, we should register [CordaSecurityProvider] as the first provider, to ensure that when invoking [SecureRandom()]
    // the [platformSecureRandom] is returned (which is registered in CordaSecurityProvider).
    // Note that internally, [SecureRandom()] will look through all registered providers.
    // Then it returns the first PRNG algorithm of the first provider that has registered a SecureRandom
    // implementation (in our case [CordaSecurityProvider]), or null if none of the registered providers supplies
    // a SecureRandom implementation.
    Security.insertProviderAt(it, 1) // The position is 1-based.
}
// OID taken from https://tools.ietf.org/html/draft-ietf-curdle-pkix-00
val `id-Curve25519ph` = ASN1ObjectIdentifier("1.3.101.112")
val cordaBouncyCastleProvider = BouncyCastleProvider().apply {
    putAll(EdDSASecurityProvider())
    // Override the normal EdDSA engine with one which can handle X509 keys.
    put("Signature.${EdDSAEngine.SIGNATURE_ALGORITHM}", X509EdDSAEngine::class.java.name)
    put("Signature.Ed25519", X509EdDSAEngine::class.java.name)
    addKeyInfoConverter(`id-Curve25519ph`, object : AsymmetricKeyInfoConverter {
        override fun generatePublic(keyInfo: SubjectPublicKeyInfo) = decodePublicKey(EDDSA_ED25519_SHA512, keyInfo.encoded)
        override fun generatePrivate(keyInfo: PrivateKeyInfo) = decodePrivateKey(EDDSA_ED25519_SHA512, keyInfo.encoded)
    })
    // Required due to [X509CRL].verify() reported issues in network-services after BC 1.60 update.
    put("AlgorithmParameters.SHA256WITHECDSA", AlgorithmParametersSpi::class.java.name)
}.also {
    // This registration is needed for reading back EdDSA key from java keystore.
    // TODO: Find a way to make JKS work with bouncy castle provider or implement our own provide so we don't have to register bouncy castle provider.
    Security.addProvider(it)
}

val bouncyCastlePQCProvider = BouncyCastlePQCProvider().apply {
    require(name == "BCPQC") { "Invalid PQCProvider name" }
}.also {
    Security.addProvider(it)
}
// This map is required to defend against users that forcibly call Security.addProvider / Security.removeProvider
// that could cause unexpected and suspicious behaviour.
// i.e. if someone removes a Provider and then he/she adds a new one with the same name.
// The val is immutable to avoid any harmful state changes.
internal val providerMap: Map<String, Provider> = unmodifiableMap(
    listOf(cordaBouncyCastleProvider, cordaSecurityProvider, bouncyCastlePQCProvider)
        .associateByTo(LinkedHashMap(), Provider::getName)
)

@DeleteForDJVM
fun platformSecureRandomFactory(): SecureRandom = platformSecureRandom() // To minimise diff of CryptoUtils against open-source.
