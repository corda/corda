package net.corda.node.internal

import net.corda.core.serialization.CustomSerializationScheme
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationSchemeContext
import net.corda.core.utilities.ByteSequence
import net.corda.node.internal.classloading.scanForCustomSerializationScheme
import org.junit.Test
import org.mockito.Mockito
import kotlin.test.assertFailsWith

class CustomSerializationSchemeScanningTest {

    class NonSerializationScheme

    open class DummySerializationScheme : CustomSerializationScheme {
        override fun getSchemeId(): Int {
            return 7
        }

        override fun <T : Any> deserialize(bytes: ByteSequence, clazz: Class<T>, context: SerializationSchemeContext): T {
            throw DummySerializationSchemeException("We should never get here.")
        }

        override fun <T : Any> serialize(obj: T, context: SerializationSchemeContext): ByteSequence {
            throw DummySerializationSchemeException("Tried to serialize with DummySerializationScheme")
        }
    }

    class DummySerializationSchemeException(override val message: String) : RuntimeException(message)

    class DummySerializationSchemeWithoutNoArgConstructor(val myArgument: String) : DummySerializationScheme()

    @Test(timeout = 300_000)
    fun `Can scan for custom serialization scheme and build a serialization scheme`() {
        val scheme = scanForCustomSerializationScheme(DummySerializationScheme::class.java.name, this::class.java.classLoader)
        val mockContext = Mockito.mock(SerializationContext::class.java)
        assertFailsWith<DummySerializationSchemeException>("Tried to serialize with DummySerializationScheme")  {
            scheme.serialize(Any::class.java, mockContext)
        }
    }

    @Test(timeout = 300_000)
    fun `verification fails with a helpful error if the class is not found in the classloader`() {
        val missingClassName = "org.testing.DoesNotExist"
        assertFailsWith<ConfigurationException>("$missingClassName was declared as a custom serialization scheme but could not " +
                "be found.") {
            scanForCustomSerializationScheme(missingClassName, this::class.java.classLoader)
        }
    }

    @Test(timeout = 300_000)
    fun `verification fails with a helpful error if the class is not a custom serialization scheme`() {
        val schemeName = NonSerializationScheme::class.java.name
        assertFailsWith<ConfigurationException>("$schemeName was declared as a custom serialization scheme but does not " +
                "implement CustomSerializationScheme.") {
            scanForCustomSerializationScheme(schemeName, this::class.java.classLoader)
        }
    }

    @Test(timeout = 300_000)
    fun `verification fails with a helpful error if the class does not have a no arg constructor`() {
        val schemeName = DummySerializationSchemeWithoutNoArgConstructor::class.java.name
        assertFailsWith<ConfigurationException>("$schemeName was declared as a custom serialization scheme but does not " +
                "have a no argument constructor.") {
            scanForCustomSerializationScheme(schemeName, this::class.java.classLoader)
        }
    }
}
