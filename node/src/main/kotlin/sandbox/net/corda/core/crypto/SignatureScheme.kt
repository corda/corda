package sandbox.net.corda.core.crypto

import sandbox.java.lang.String
import sandbox.java.lang.Integer
import sandbox.java.lang.Object
import sandbox.java.security.spec.AlgorithmParameterSpec
import sandbox.java.util.List
import sandbox.org.bouncycastle.asn1.x509.AlgorithmIdentifier

/**
 * This is a dummy class that implements just enough of [net.corda.core.crypto.SignatureScheme]
 * to allow us to compile [sandbox.net.corda.core.crypto.Crypto].
 */
@Suppress("unused")
class SignatureScheme(
    val schemeNumberID: Int,
    val schemeCodeName: String,
    val signatureOID: AlgorithmIdentifier,
    val alternativeOIDs: List<AlgorithmIdentifier>,
    val providerName: String,
    val algorithmName: String,
    val signatureName: String,
    val algSpec: AlgorithmParameterSpec?,
    val keySize: Integer?,
    val desc: String
) : Object()
