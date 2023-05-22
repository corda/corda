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
// TODO JDK17: Move this
val jdk17SunEcKeys = listOf(
        "Alg.Alias.AlgorithmParameters.OID.1.2.840.10045.2.1",
        "Alg.Alias.KeyAgreement.1.3.101.110",
        "Alg.Alias.KeyAgreement.1.3.101.111",
        "Alg.Alias.KeyAgreement.OID.1.3.101.110",
        "Alg.Alias.KeyAgreement.OID.1.3.101.111",
        "Alg.Alias.KeyFactory.1.3.101.110",
        "Alg.Alias.KeyFactory.1.3.101.111",
        "Alg.Alias.KeyFactory.1.3.101.112",
        "Alg.Alias.KeyFactory.1.3.101.113",
        "Alg.Alias.KeyFactory.OID.1.3.101.110",
        "Alg.Alias.KeyFactory.OID.1.3.101.111",
        "Alg.Alias.KeyFactory.OID.1.3.101.112",
        "Alg.Alias.KeyFactory.OID.1.3.101.113",
        "Alg.Alias.KeyPairGenerator.1.3.101.110",
        "Alg.Alias.KeyPairGenerator.1.3.101.111",
        "Alg.Alias.KeyPairGenerator.1.3.101.112",
        "Alg.Alias.KeyPairGenerator.1.3.101.113",
        "Alg.Alias.KeyPairGenerator.OID.1.3.101.110",
        "Alg.Alias.KeyPairGenerator.OID.1.3.101.111",
        "Alg.Alias.KeyPairGenerator.OID.1.3.101.112",
        "Alg.Alias.KeyPairGenerator.OID.1.3.101.113",
        "Alg.Alias.Signature.1.3.101.112",
        "Alg.Alias.Signature.1.3.101.113",
        "Alg.Alias.Signature.2.16.840.1.101.3.4.3.10",
        "Alg.Alias.Signature.2.16.840.1.101.3.4.3.11",
        "Alg.Alias.Signature.2.16.840.1.101.3.4.3.12",
        "Alg.Alias.Signature.2.16.840.1.101.3.4.3.9",
        "Alg.Alias.Signature.OID.1.3.101.112",
        "Alg.Alias.Signature.OID.1.3.101.113",
        "Alg.Alias.Signature.OID.2.16.840.1.101.3.4.3.10",
        "Alg.Alias.Signature.OID.2.16.840.1.101.3.4.3.11",
        "Alg.Alias.Signature.OID.2.16.840.1.101.3.4.3.12",
        "Alg.Alias.Signature.OID.2.16.840.1.101.3.4.3.9",
        "AlgorithmParameters.EC SupportedKeyClasses",
        "KeyAgreement.ECDH KeySize",
        "KeyAgreement.X25519",
        "KeyAgreement.X25519 ImplementedIn",
        "KeyAgreement.X448",
        "KeyAgreement.X448 ImplementedIn",
        "KeyAgreement.XDH",
        "KeyAgreement.XDH ImplementedIn",
        "KeyFactory.EC KeySize",
        "KeyFactory.EC SupportedKeyClasses",
        "KeyFactory.Ed25519",
        "KeyFactory.Ed25519 ImplementedIn",
        "KeyFactory.Ed448",
        "KeyFactory.Ed448 ImplementedIn",
        "KeyFactory.EdDSA",
        "KeyFactory.EdDSA ImplementedIn",
        "KeyFactory.X25519",
        "KeyFactory.X25519 ImplementedIn",
        "KeyFactory.X448",
        "KeyFactory.X448 ImplementedIn",
        "KeyFactory.XDH",
        "KeyFactory.XDH ImplementedIn",
        "KeyPairGenerator.EC SupportedKeyClasses",
        "KeyPairGenerator.Ed25519",
        "KeyPairGenerator.Ed25519 ImplementedIn",
        "KeyPairGenerator.Ed448",
        "KeyPairGenerator.Ed448 ImplementedIn",
        "KeyPairGenerator.EdDSA",
        "KeyPairGenerator.EdDSA ImplementedIn",
        "KeyPairGenerator.X25519",
        "KeyPairGenerator.X25519 ImplementedIn",
        "KeyPairGenerator.X448",
        "KeyPairGenerator.X448 ImplementedIn",
        "KeyPairGenerator.XDH",
        "KeyPairGenerator.XDH ImplementedIn",
        "Signature.Ed25519",
        "Signature.Ed25519 ImplementedIn",
        "Signature.Ed448",
        "Signature.Ed448 ImplementedIn",
        "Signature.EdDSA",
        "Signature.EdDSA ImplementedIn",
        "Signature.NONEwithECDSA KeySize",
        "Signature.NONEwithECDSAinP1363Format",
        "Signature.SHA1withECDSAinP1363Format",
        "Signature.SHA224withECDSA KeySize",
        "Signature.SHA224withECDSAinP1363Format",
        "Signature.SHA256withECDSA KeySize",
        "Signature.SHA256withECDSAinP1363Format",
        "Signature.SHA3-224withECDSA",
        "Signature.SHA3-224withECDSA ImplementedIn",
        "Signature.SHA3-224withECDSA KeySize",
        "Signature.SHA3-224withECDSA SupportedKeyClasses",
        "Signature.SHA3-224withECDSAinP1363Format",
        "Signature.SHA3-256withECDSA",
        "Signature.SHA3-256withECDSA ImplementedIn",
        "Signature.SHA3-256withECDSA KeySize",
        "Signature.SHA3-256withECDSA SupportedKeyClasses",
        "Signature.SHA3-256withECDSAinP1363Format",
        "Signature.SHA3-384withECDSA",
        "Signature.SHA3-384withECDSA ImplementedIn",
        "Signature.SHA3-384withECDSA KeySize",
        "Signature.SHA3-384withECDSA SupportedKeyClasses",
        "Signature.SHA3-384withECDSAinP1363Format",
        "Signature.SHA3-512withECDSA",
        "Signature.SHA3-512withECDSA ImplementedIn",
        "Signature.SHA3-512withECDSA KeySize",
        "Signature.SHA3-512withECDSA SupportedKeyClasses",
        "Signature.SHA3-512withECDSAinP1363Format",
        "Signature.SHA384withECDSA KeySize",
        "Signature.SHA384withECDSAinP1363Format",
        "Signature.SHA512withECDSA KeySize",
        "Signature.SHA512withECDSA SupportedKeyClasses",
        "Signature.SHA512withECDSAinP1363Format\n")
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

    // JDK 17: Add SunEC provider as lowest priority, as we use Bouncycastle for EDDSA
    val sunEC = Security.getProvider("SunEC")
    if (sunEC != null) {
        Security.removeProvider("SunEC")
        Security.addProvider(sunEC)

        for(alg in jdk17SunEcKeys) {
            sunEC.remove(alg)
        }
    }
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
