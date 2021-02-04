package net.corda.node.internal

import com.nhaarman.mockito_kotlin.whenever
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
            return 7;
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
        val classLoader = Mockito.mock(ClassLoader::class.java)
        whenever(classLoader.loadClass(DummySerializationScheme::class.java.canonicalName)).thenAnswer { DummySerializationScheme::class.java }
        val scheme = scanForCustomSerializationScheme(DummySerializationScheme::class.java.canonicalName, classLoader)
        val mockContext = Mockito.mock(SerializationContext::class.java)
        assertFailsWith<DummySerializationSchemeException>("Tried to serialize with DummySerializationScheme")  {
            scheme.serialize(Any::class.java, mockContext)
        }
    }

    @Test(timeout = 300_000)
    fun `verification fails with a helpful error if the class is not found in the classloader`() {
        val classLoader = Mockito.mock(ClassLoader::class.java)
        val missingClassName = DummySerializationScheme::class.java.canonicalName
        whenever(classLoader.loadClass(missingClassName)).thenAnswer { throw ClassNotFoundException()}
        assertFailsWith<ConfigurationException>("$missingClassName was declared as a custom serialization scheme but could not " +
                "be found.") {
            scanForCustomSerializationScheme(missingClassName, classLoader)
        }
    }

    @Test(timeout = 300_000)
    fun `verification fails with a helpful error if the class is not a custom serialization scheme`() {
        val canonicalName = NonSerializationScheme::class.java.canonicalName
        val classLoader = Mockito.mock(ClassLoader::class.java)
        whenever(classLoader.loadClass(canonicalName)).thenAnswer { NonSerializationScheme::class.java }
        assertFailsWith<ConfigurationException>("$canonicalName was declared as a custom serialization scheme but does not " +
                "implement CustomSerializationScheme.") {
            scanForCustomSerializationScheme(canonicalName, classLoader)
        }
    }

    @Test(timeout = 300_000)
    fun `verification fails with a helpful error if the class does not have a no arg constructor`() {
        val classLoader = Mockito.mock(ClassLoader::class.java)
        val canonicalName = DummySerializationSchemeWithoutNoArgConstructor::class.java.canonicalName
        whenever(classLoader.loadClass(canonicalName)).thenAnswer { DummySerializationSchemeWithoutNoArgConstructor::class.java }
        assertFailsWith<ConfigurationException>("$canonicalName was declared as a custom serialization scheme but does not " +
                "have a no argument constructor.") {
            scanForCustomSerializationScheme(canonicalName, classLoader)
        }
    }
}