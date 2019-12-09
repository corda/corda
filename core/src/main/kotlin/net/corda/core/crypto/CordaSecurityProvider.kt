package net.corda.core.crypto

import net.corda.core.KeepForDJVM
import net.corda.core.StubOutForDJVM
import net.corda.core.crypto.CordaObjectIdentifier.COMPOSITE_KEY
import net.corda.core.crypto.CordaObjectIdentifier.COMPOSITE_SIGNATURE
import net.corda.core.crypto.internal.PlatformSecureRandomService
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import java.security.Provider
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@KeepForDJVM
@Suppress("DEPRECATION")    // JDK11: should replace with Provider(String name, double version, String info) (since 9)
class CordaSecurityProvider : Provider(PROVIDER_NAME, 0.1, "$PROVIDER_NAME security provider wrapper") {
    companion object {
        const val PROVIDER_NAME = "Corda"
    }

    init {
        put("KeyFactory.${CompositeKey.KEY_ALGORITHM}", CompositeKeyFactory::class.java.name)
        put("Alg.Alias.KeyFactory.$COMPOSITE_KEY", CompositeKey.KEY_ALGORITHM)
        put("Alg.Alias.KeyFactory.OID.$COMPOSITE_KEY", CompositeKey.KEY_ALGORITHM)
        put("Signature.${CompositeSignature.SIGNATURE_ALGORITHM}", CompositeSignature::class.java.name)
        put("Alg.Alias.Signature.$COMPOSITE_SIGNATURE", CompositeSignature.SIGNATURE_ALGORITHM)
        put("Alg.Alias.Signature.OID.$COMPOSITE_SIGNATURE", CompositeSignature.SIGNATURE_ALGORITHM)
        putPlatformSecureRandomService()
    }

    @StubOutForDJVM
    private fun putPlatformSecureRandomService() {
        putService(PlatformSecureRandomService(this))
    }

    override fun getService(type: String, algorithm: String): Service? = serviceFactory(type, algorithm)

    // Used to work around banning of ConcurrentHashMap in DJVM
    @Suppress("TooGenericExceptionCaught")
    private val serviceFactory: (String, String) -> Service? = try {
        // Will throw UnsupportedOperationException in DJVM
        makeCachingFactory()
    } catch (e: Exception) {
        makeFactory()
    }

    private fun superGetService(type: String, algorithm: String): Service? = super.getService(type, algorithm)

    @StubOutForDJVM
    private fun makeCachingFactory(): Function2<String, String, Service?> {
        return object : Function2<String, String, Service?> {
            private val services = ConcurrentHashMap<Pair<String, String>, Optional<Service>>()

            override fun invoke(type: String, algorithm: String): Service? {
                return services.getOrPut(Pair(type, algorithm)) {
                    Optional.ofNullable(superGetService(type, algorithm))
                }.orElse(null)
            }
        }
    }

    private fun makeFactory(): Function2<String, String, Service?> {
        return object : Function2<String, String, Service?> {
            override fun invoke(type: String, algorithm: String): Service? {
                return superGetService(type, algorithm)
            }
        }
    }
}

@KeepForDJVM
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
