package net.corda.nodeapi.internal.serialization

import net.corda.core.crypto.Crypto
import net.corda.core.serialization.SerializationContext.UseCase.*
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.serialize
import net.corda.testing.TestDependencyInjectionBase
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.security.PrivateKey
import kotlin.test.assertTrue

class PrivateKeySerializationTest : TestDependencyInjectionBase() {

    private val privateKeys: List<PrivateKey> = Crypto.supportedSignatureSchemes().filterNot { Crypto.COMPOSITE_KEY === it }
            .map { Crypto.generateKeyPair(it).private }

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