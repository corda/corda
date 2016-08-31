package com.r3corda.core.testing

import com.pholser.junit.quickcheck.From
import com.pholser.junit.quickcheck.Property
import com.pholser.junit.quickcheck.generator.GenerationStatus
import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck
import com.r3corda.testing.node.MockIdentityService
import com.r3corda.node.utilities.JsonSupport
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(JUnitQuickcheck::class)
class JsonSupportTest {

    companion object {
        val mapper = JsonSupport.createDefaultMapper(MockIdentityService(mutableListOf()))
        val ed25519Curve = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.CURVE_ED25519_SHA512)
    }

    /** TODO: factor out generators into a ServiceLoader in order to remove @From annotations.
     * @See <a href="http://pholser.github.io/junit-quickcheck/site/0.6/usage/other-types.html">pholser.github.io</a>
     */
    class PrivateKeyGenerator: Generator<EdDSAPrivateKey>(EdDSAPrivateKey::class.java) {
        override fun generate(random: SourceOfRandomness, status: GenerationStatus): EdDSAPrivateKey {
            val seed = random.nextBytes(32)
            val privateKeySpec = EdDSAPrivateKeySpec(seed, ed25519Curve)
            return EdDSAPrivateKey(privateKeySpec)
        }
    }

    class PublicKeyGenerator: Generator<EdDSAPublicKey>(EdDSAPublicKey::class.java) {
        override fun generate(random: SourceOfRandomness, status: GenerationStatus): EdDSAPublicKey {
            val privateKey = PrivateKeyGenerator().generate(random, status)
            return EdDSAPublicKey(EdDSAPublicKeySpec(privateKey.a, ed25519Curve))
        }
    }

    @Property fun publicKeySerializingWorks(@From(PublicKeyGenerator::class) publicKey: EdDSAPublicKey) {
        val serialized = mapper.writeValueAsString(publicKey)
        val parsedKey = mapper.readValue(serialized, EdDSAPublicKey::class.java)
        assertEquals(publicKey, parsedKey)
    }
}
