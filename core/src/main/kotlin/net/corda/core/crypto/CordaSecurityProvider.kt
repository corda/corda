package net.corda.core.crypto

import net.corda.core.crypto.CordaObjectIdentifier.COMPOSITE_KEY
import net.corda.core.crypto.CordaObjectIdentifier.COMPOSITE_SIGNATURE
import net.corda.core.crypto.internal.PlatformSecureRandomService
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import java.security.Provider
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Suppress("DEPRECATION")    // JDK11: should replace with Provider(String name, double version, String info) (since 9)
class CordaSecurityProvider : Provider(PROVIDER_NAME, 0.1, "$PROVIDER_NAME security provider wrapper") {
    companion object {
        const val PROVIDER_NAME = "Corda"
    }

    private val services = ConcurrentHashMap<Pair<String, String>, Optional<Service>>()

    init {
        put("KeyFactory.${CompositeKey.KEY_ALGORITHM}", CompositeKeyFactory::class.java.name)
        put("Alg.Alias.KeyFactory.$COMPOSITE_KEY", CompositeKey.KEY_ALGORITHM)
        put("Alg.Alias.KeyFactory.OID.$COMPOSITE_KEY", CompositeKey.KEY_ALGORITHM)
        put("Signature.${CompositeSignature.SIGNATURE_ALGORITHM}", CompositeSignature::class.java.name)
        put("Alg.Alias.Signature.$COMPOSITE_SIGNATURE", CompositeSignature.SIGNATURE_ALGORITHM)
        put("Alg.Alias.Signature.OID.$COMPOSITE_SIGNATURE", CompositeSignature.SIGNATURE_ALGORITHM)
        putPlatformSecureRandomService()
    }

    private fun putPlatformSecureRandomService() {
        putService(PlatformSecureRandomService(this))
    }

    override fun getService(type: String, algorithm: String): Service? {
        return services.getOrPut(Pair(type, algorithm)) {
            Optional.ofNullable(superGetService(type, algorithm))
        }.orElse(null)
    }

    private fun superGetService(type: String, algorithm: String): Service? = super.getService(type, algorithm)
}

object CordaObjectIdentifier {
    // UUID-based OID
    // TODO define and use an official Corda OID in [CordaOID]. We didn't do yet for backwards compatibility purposes,
    //      because key.encoded (serialised version of keys) and [PublicKey.hash] for already stored [CompositeKey]s
    //      will not match.
    @JvmField
    val COMPOSITE_KEY = ASN1ObjectIdentifier("2.25.30086077608615255153862931087626791002")
    @JvmField
    val COMPOSITE_SIGNATURE = ASN1ObjectIdentifier("2.25.30086077608615255153862931087626791003")
}
