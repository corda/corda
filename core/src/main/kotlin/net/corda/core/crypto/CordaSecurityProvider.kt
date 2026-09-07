package net.corda.core.crypto

import net.corda.core.crypto.CordaObjectIdentifier.COMPOSITE_KEY
import net.corda.core.crypto.CordaObjectIdentifier.COMPOSITE_SIGNATURE
import net.corda.core.crypto.internal.PlatformSecureRandomService
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import java.security.Provider
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

class CordaSecurityProvider : Provider(PROVIDER_NAME, "0.2", "$PROVIDER_NAME security provider") {
    companion object {
        const val PROVIDER_NAME = "Corda"
    }

    private val services = ConcurrentHashMap<Pair<String, String>, Optional<Service>>()

    init {
        putService(Service(this, "KeyFactory", CompositeKey.KEY_ALGORITHM, CompositeKeyFactory::class.java.name, listOf("$COMPOSITE_KEY", "OID.$COMPOSITE_KEY"), null))
        putService(Service(this, "Signature", CompositeSignature.SIGNATURE_ALGORITHM, CompositeSignature::class.java.name, listOf("$COMPOSITE_SIGNATURE", "OID.$COMPOSITE_SIGNATURE"), null))
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
