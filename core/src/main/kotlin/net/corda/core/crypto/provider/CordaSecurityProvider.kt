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

        put("Alg.Alias.KeyFactory.${CompositeSignature.SIGNATURE_ALGORITHM_IDENTIFIER.algorithm.id}", CompositeKey.KEY_ALGORITHM)
        put("Alg.Alias.KeyFactory.OID.${CompositeSignature.SIGNATURE_ALGORITHM_IDENTIFIER.algorithm.id}", CompositeKey.KEY_ALGORITHM)

        put("Alg.Alias.Signature.${CompositeSignature.SIGNATURE_ALGORITHM_IDENTIFIER.algorithm.id}", CompositeSignature.SIGNATURE_ALGORITHM)
        put("Alg.Alias.Signature.OID.${CompositeSignature.SIGNATURE_ALGORITHM_IDENTIFIER.algorithm.id}", CompositeSignature.SIGNATURE_ALGORITHM)
    }

}
