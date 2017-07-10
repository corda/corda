package net.corda.core.crypto.provider

import net.corda.core.crypto.composite.CompositeKey
import net.corda.core.crypto.composite.CompositeSignature
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

        val compositeKeyOID = CompositeSignature.SIGNATURE_ALGORITHM_IDENTIFIER.algorithm.id

        put("Alg.Alias.KeyFactory.$compositeKeyOID", CompositeKey.KEY_ALGORITHM)
        put("Alg.Alias.KeyFactory.OID.$compositeKeyOID", CompositeKey.KEY_ALGORITHM)
        put("Alg.Alias.Signature.$compositeKeyOID", CompositeSignature.SIGNATURE_ALGORITHM)
        put("Alg.Alias.Signature.OID.$compositeKeyOID", CompositeSignature.SIGNATURE_ALGORITHM)
    }

}
