package net.corda.nodeapi.internal.serialization

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.serialize
import net.corda.testing.TestDependencyInjectionBase
import net.i2p.crypto.eddsa.KeyPairGenerator
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.security.PrivateKey
import java.security.SecureRandom
import kotlin.test.assertTrue

class PrivateKeySerializationTest : TestDependencyInjectionBase() {

    private val privateKey: PrivateKey

    init {
        val generator = KeyPairGenerator()
        generator.initialize(256, SecureRandom())
        privateKey = generator.generateKeyPair().private
    }

    @Test
    fun `passed with expected UseCases`() {
        assertTrue { privateKey.serialize(context = SerializationDefaults.STORAGE_CONTEXT).bytes.isNotEmpty() }
        assertTrue { privateKey.serialize(context = SerializationDefaults.CHECKPOINT_CONTEXT).bytes.isNotEmpty() }
    }

    @Test
    fun `failed with wrong UseCase`() {
        assertThatThrownBy { privateKey.serialize(context = SerializationDefaults.RPC_CLIENT_CONTEXT) }
                .hasMessageContaining("UseCase '${SerializationContext.UseCase.RPCClient}' is not within")

    }
}