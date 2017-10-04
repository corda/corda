package net.corda.nodeapi.internal.serialization

import net.corda.core.serialization.SerializationContext.UseCase.*
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.serialize
import net.corda.testing.TestDependencyInjectionBase
import net.i2p.crypto.eddsa.KeyPairGenerator
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.jcajce.provider.asymmetric.ec.KeyPairGeneratorSpi
import org.junit.Test
import java.security.PrivateKey
import java.security.SecureRandom
import kotlin.test.assertTrue

class PrivateKeySerializationTest : TestDependencyInjectionBase() {

    private val privateKeys: List<PrivateKey>

    init {
        val generator = KeyPairGenerator()
        generator.initialize(256, SecureRandom())

        val ec = KeyPairGeneratorSpi.EC()
        ec.initialize(256)

        privateKeys = listOf(generator.generateKeyPair().private, ec.generateKeyPair().private)
    }

    @Test
    fun `passed with expected UseCases`() {
        assertTrue { privateKeys.serialize(context = SerializationDefaults.STORAGE_CONTEXT).bytes.isNotEmpty() }
        assertTrue { privateKeys.serialize(context = SerializationDefaults.CHECKPOINT_CONTEXT).bytes.isNotEmpty() }
    }

    @Test
    fun `failed with wrong UseCase`() {
        assertThatThrownBy { privateKeys.serialize(context = SerializationDefaults.P2P_CONTEXT) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("UseCase '${P2P}' is not within")

    }
}