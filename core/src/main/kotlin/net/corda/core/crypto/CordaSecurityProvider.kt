package net.corda.core.crypto

import net.corda.core.KeepForDJVM
import net.corda.core.crypto.CordaObjectIdentifier.COMPOSITE_KEY
import net.corda.core.crypto.CordaObjectIdentifier.COMPOSITE_SIGNATURE
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import net.corda.core.StubOutForDJVM
import java.security.Provider

@KeepForDJVM
class CordaSecurityProvider : Provider(PROVIDER_NAME, 0.1, "$PROVIDER_NAME security provider wrapper") {
    companion object {
        const val PROVIDER_NAME = "Corda"
    }

    init {
        provideNonDeterministic(this)
        put("Signature.${CompositeSignature.SIGNATURE_ALGORITHM}", CompositeSignature::class.java.name)
        put("Alg.Alias.Signature.$COMPOSITE_SIGNATURE", CompositeSignature.SIGNATURE_ALGORITHM)
        put("Alg.Alias.Signature.OID.$COMPOSITE_SIGNATURE", CompositeSignature.SIGNATURE_ALGORITHM)
    }
}

/**
 * The core-deterministic module is not allowed to generate keys.
 */
@StubOutForDJVM
private fun provideNonDeterministic(provider: Provider) {
    provider["KeyFactory.${CompositeKey.KEY_ALGORITHM}"] = CompositeKeyFactory::class.java.name
    provider["Alg.Alias.KeyFactory.$COMPOSITE_KEY"] = CompositeKey.KEY_ALGORITHM
    provider["Alg.Alias.KeyFactory.OID.$COMPOSITE_KEY"] = CompositeKey.KEY_ALGORITHM
}

@KeepForDJVM
object CordaObjectIdentifier {
    // UUID-based OID
    // TODO: Register for an OID space and issue our own shorter OID.
    @JvmField
    val COMPOSITE_KEY = ASN1ObjectIdentifier("2.25.30086077608615255153862931087626791002")
    @JvmField
    val COMPOSITE_SIGNATURE = ASN1ObjectIdentifier("2.25.30086077608615255153862931087626791003")
}
