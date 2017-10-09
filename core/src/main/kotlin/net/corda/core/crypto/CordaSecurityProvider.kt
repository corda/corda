package net.corda.core.crypto

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import java.security.AccessController
import java.security.PrivilegedAction
import java.security.Provider

class CordaSecurityProvider : Provider(PROVIDER_NAME, 0.1, "$PROVIDER_NAME security provider wrapper") {
    companion object {
        const val PROVIDER_NAME = "Corda"
    }

    init {
        AccessController.doPrivileged(PrivilegedAction<Unit> { setup() })
    }

    private fun setup() {
        put("KeyFactory.${CompositeKey.KEY_ALGORITHM}", "net.corda.core.crypto.CompositeKeyFactory")
        put("Signature.${CompositeSignature.SIGNATURE_ALGORITHM}", "net.corda.core.crypto.CompositeSignature")

        val compositeKeyOID = CordaObjectIdentifier.COMPOSITE_KEY.id
        put("Alg.Alias.KeyFactory.$compositeKeyOID", CompositeKey.KEY_ALGORITHM)
        put("Alg.Alias.KeyFactory.OID.$compositeKeyOID", CompositeKey.KEY_ALGORITHM)

        val compositeSignatureOID = CordaObjectIdentifier.COMPOSITE_SIGNATURE.id
        put("Alg.Alias.Signature.$compositeSignatureOID", CompositeSignature.SIGNATURE_ALGORITHM)
        put("Alg.Alias.Signature.OID.$compositeSignatureOID", CompositeSignature.SIGNATURE_ALGORITHM)
    }
}

object CordaObjectIdentifier {
    // UUID-based OID
    // TODO: Register for an OID space and issue our own shorter OID.
    @JvmField
    val COMPOSITE_KEY = ASN1ObjectIdentifier("2.25.30086077608615255153862931087626791002")
    @JvmField
    val COMPOSITE_SIGNATURE = ASN1ObjectIdentifier("2.25.30086077608615255153862931087626791003")
}
