package net.corda.serialization.djvm

import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.internal.hash
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.security.PublicKey
import java.util.function.Function
import java.util.stream.Stream

@ExtendWith(LocalSerialization::class)
class DeserializePublicKeyTest : TestBase(KOTLIN) {
    class SignatureSchemeProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return Crypto.supportedSignatureSchemes().stream()
                .filter { it != Crypto.COMPOSITE_KEY }
                .map { Arguments.of(it) }
        }
    }

    @ArgumentsSource(SignatureSchemeProvider::class)
    @ParameterizedTest(name = "{index} => {0}")
    fun `test deserializing public key`(signatureScheme: SignatureScheme) {
        val keyPair = Crypto.generateKeyPair(signatureScheme)
        val publicKey = PublicKeyData(keyPair.public)
        val data = publicKey.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxKey = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showPublicKey = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowPublicKey::class.java)
            val result = showPublicKey.apply(sandboxKey) ?: fail("Result cannot be null")

            assertEquals(ShowPublicKey().apply(publicKey), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    @Test
	fun `test composite public key`() {
        val key1 = Crypto.generateKeyPair(Crypto.ECDSA_SECP256K1_SHA256).public
        val key2 = Crypto.generateKeyPair(Crypto.ECDSA_SECP256R1_SHA256).public
        val key3 = Crypto.generateKeyPair(Crypto.EDDSA_ED25519_SHA512).public

        val compositeKey = CompositeKey.Builder()
            .addKey(key1, weight = 1)
            .addKey(key2, weight = 1)
            .addKey(key3, weight = 1)
            .build(2)
        val compositeData = PublicKeyData(compositeKey)
        val data = compositeData.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxData = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory().compose(classLoader.createSandboxFunction())
            val showPublicKey = taskFactory.apply(ShowPublicKey::class.java)
            val result = showPublicKey.apply(sandboxData) ?: fail("Result cannot be null")

            assertEquals(ShowPublicKey().apply(compositeData), result.toString())

            val sandboxKey = taskFactory.apply(GetPublicKey::class.java)
                .apply(sandboxData) ?: fail("PublicKey cannot be null")
            assertThat(sandboxKey::class.java.name)
                .isEqualTo("sandbox." + CompositeKey::class.java.name)
        }
    }

    class ShowPublicKey : Function<PublicKeyData, String> {
        override fun apply(data: PublicKeyData): String {
            return with(data) {
                "PublicKey: algorithm='${key.algorithm}', format='${key.format}', hash=${key.hash}"
            }
        }
    }

    class GetPublicKey : Function<PublicKeyData, PublicKey> {
        override fun apply(data: PublicKeyData): PublicKey {
            return data.key
        }
    }
}

@CordaSerializable
data class PublicKeyData(val key: PublicKey)
