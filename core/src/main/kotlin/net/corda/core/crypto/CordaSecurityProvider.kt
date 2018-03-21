/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.crypto

import net.corda.core.crypto.CordaObjectIdentifier.COMPOSITE_KEY
import net.corda.core.crypto.CordaObjectIdentifier.COMPOSITE_SIGNATURE
import net.corda.core.internal.VisibleForTesting
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import io.netty.util.concurrent.FastThreadLocal
import java.security.Provider
import java.security.SecureRandom
import java.security.SecureRandomSpi

internal const val CORDA_SECURE_RANDOM_ALGORITHM = "CordaPRNG"

class CordaSecurityProvider : Provider(PROVIDER_NAME, 0.1, "$PROVIDER_NAME security provider wrapper") {
    companion object {
        const val PROVIDER_NAME = "Corda"
    }

    init {
        put("KeyFactory.${CompositeKey.KEY_ALGORITHM}", CompositeKeyFactory::class.java.name)
        put("Signature.${CompositeSignature.SIGNATURE_ALGORITHM}", CompositeSignature::class.java.name)
        put("Alg.Alias.KeyFactory.$COMPOSITE_KEY", CompositeKey.KEY_ALGORITHM)
        put("Alg.Alias.KeyFactory.OID.$COMPOSITE_KEY", CompositeKey.KEY_ALGORITHM)
        put("Alg.Alias.Signature.$COMPOSITE_SIGNATURE", CompositeSignature.SIGNATURE_ALGORITHM)
        put("Alg.Alias.Signature.OID.$COMPOSITE_SIGNATURE", CompositeSignature.SIGNATURE_ALGORITHM)
        setSecureRandomService()
    }

    private fun setSecureRandomService() {
        // Assuming this Provider is the first SecureRandom Provider, this algorithm is the SecureRandom default:
        putService(DelegatingSecureRandomService(this))
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

// Unlike all the NativePRNG algorithms, this doesn't use a global lock:
private class SunSecureRandom : SecureRandom(sun.security.provider.SecureRandom(), null)

private class DelegatingSecureRandomService(provider: CordaSecurityProvider) : Provider.Service(
        provider, type, CORDA_SECURE_RANDOM_ALGORITHM, DelegatingSecureRandomSpi::class.java.name, null, null) {
    private companion object {
        private const val type = "SecureRandom"
    }

    internal val instance = DelegatingSecureRandomSpi(::SunSecureRandom)
    override fun newInstance(constructorParameter: Any?) = instance
}

internal class DelegatingSecureRandomSpi internal constructor(secureRandomFactory: () -> SecureRandom) : SecureRandomSpi() {
    private val threadLocalSecureRandom = object : FastThreadLocal<SecureRandom>() {
        override fun initialValue() = secureRandomFactory()
    }

    override fun engineSetSeed(seed: ByteArray) = threadLocalSecureRandom.get().setSeed(seed)
    override fun engineNextBytes(bytes: ByteArray) = threadLocalSecureRandom.get().nextBytes(bytes)
    override fun engineGenerateSeed(numBytes: Int): ByteArray? = threadLocalSecureRandom.get().generateSeed(numBytes)
    @VisibleForTesting
    internal fun currentThreadSecureRandom() = threadLocalSecureRandom.get()
}
