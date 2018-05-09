package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.serialization.SerializationContext
import net.corda.nodeapi.internal.serialization.CordaSerializationMagic
import net.corda.nodeapi.internal.serialization.AMQP_P2P_CONTEXT
import org.apache.qpid.proton.codec.Data
import org.assertj.core.api.Assertions
import org.junit.Test
import java.lang.reflect.Type
import java.security.PublicKey

class OverridePKSerializerTest {
    class SerializerTestException(message: String) : Exception(message)

    class TestPublicKeySerializer : CustomSerializer.Implements<PublicKey>(PublicKey::class.java) {
        override fun writeDescribedObject(obj: PublicKey, data: Data, type: Type, output: SerializationOutput,
                                          context: SerializationContext
        ) {
            throw SerializerTestException("Custom write call")
        }

        override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput,
                                context: SerializationContext
        ) : PublicKey {
            throw SerializerTestException("Custom read call")
        }

        override val schemaForDocumentation: Schema
            get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
    }

    class AMQPTestSerializationScheme : AbstractAMQPSerializationScheme(emptyList()) {
        override fun rpcServerSerializerFactory(context: SerializationContext): SerializerFactory {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase) = true
        override fun rpcClientSerializerFactory(context: SerializationContext): SerializerFactory {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override val publicKeySerializer = TestPublicKeySerializer()
    }

    class TestPublicKey : PublicKey {
        override fun getAlgorithm(): String {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getEncoded(): ByteArray {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getFormat(): String {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }

    @Test
    fun `test publicKeySerializer is overridden`() {
        val scheme = AMQPTestSerializationScheme()
        val key = TestPublicKey()

        Assertions
                .assertThatThrownBy { scheme.serialize(key, AMQP_P2P_CONTEXT) }
                .hasMessageMatching("Custom write call")
    }
}