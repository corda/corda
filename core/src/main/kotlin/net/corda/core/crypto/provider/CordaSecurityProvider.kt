package net.corda.core.crypto.provider

import net.corda.core.crypto.composite.CompositeKey
import net.corda.core.crypto.composite.CompositeSignature
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import java.security.AccessController
import java.security.PrivilegedAction
import java.security.Provider

class CordaSecurityProvider : Provider(PROVIDER_NAME, 0.1, "$PROVIDER_NAME security provider wrapper") {
    companion object {
        val PROVIDER_NAME = "Corda"
    }

    init {
        AccessController.doPrivileged(PrivilegedAction<Unit> { setup() })
    }

    private fun setup() {
        put("KeyFactory.${CompositeKey.KEY_ALGORITHM}", "net.corda.core.crypto.composite.KeyFactory")
        put("Signature.${CompositeSignature.SIGNATURE_ALGORITHM}", "net.corda.core.crypto.composite.CompositeSignature")

        val compositeKeyOID = CordaObjectIdentifier.compositeKey.id
        put("Alg.Alias.KeyFactory.$compositeKeyOID", CompositeKey.KEY_ALGORITHM)
        put("Alg.Alias.KeyFactory.OID.$compositeKeyOID", CompositeKey.KEY_ALGORITHM)
        put("Alg.Alias.Signature.$compositeKeyOID", CompositeSignature.SIGNATURE_ALGORITHM)
        put("Alg.Alias.Signature.OID.$compositeKeyOID", CompositeSignature.SIGNATURE_ALGORITHM)
    }
}

object CordaObjectIdentifier {
    // UUID-based OID
    // TODO: Register for an OID space and issue our own shorter OID
    val compositeKey = ASN1ObjectIdentifier("2.25.30086077608615255153862931087626791002")
    val compositeSignature = ASN1ObjectIdentifier("2.25.30086077608615255153862931087626791003")
}
