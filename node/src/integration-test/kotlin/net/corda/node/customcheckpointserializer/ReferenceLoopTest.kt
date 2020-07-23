package net.corda.node.customcheckpointserializer

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.serialization.CheckpointCustomSerializer
import net.corda.core.serialization.EncodingWhitelist
import net.corda.core.serialization.internal.CheckpointSerializationContext
import net.corda.core.serialization.internal.checkpointDeserialize
import net.corda.core.serialization.internal.checkpointSerialize
import net.corda.coretesting.internal.rigorousMock
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.CheckpointSerializationContextImpl
import net.corda.serialization.internal.CordaSerializationEncoding
import net.corda.testing.core.internal.CheckpointSerializationEnvironmentRule
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ReferenceLoopTest(private val compression: CordaSerializationEncoding?) {
    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun compression() = arrayOf<CordaSerializationEncoding?>(null) + CordaSerializationEncoding.values()
    }

    @get:Rule
    val serializationRule = CheckpointSerializationEnvironmentRule(inheritable = true)
    private val context: CheckpointSerializationContext = CheckpointSerializationContextImpl(
            deserializationClassLoader = javaClass.classLoader,
            whitelist = AllWhitelist,
            properties = emptyMap(),
            objectReferencesEnabled = true,
            encoding = compression,
            encodingWhitelist = rigorousMock<EncodingWhitelist>()
            .also {
                if (compression != null) doReturn(true).whenever(it)
                        .acceptEncoding(compression)
            },
            checkpointCustomSerializers = listOf(PersonSerializer()))

    @Test(timeout=300_000)
    fun `custom checkpoint serialization with reference loop`() {
        val person = Person("Test name")

        val result = person.checkpointSerialize(context).checkpointDeserialize(context)

        Assert.assertEquals("Test name", result.name)
        Assert.assertEquals("Test name", result.bestFriend.name)
        Assert.assertSame(result, result.bestFriend)
    }

    /**
     * Test class that will hold a reference to itself
     */
    class Person(val name: String, bestFriend: Person? = null) {
        val bestFriend: Person = bestFriend ?: this
    }

    /**
     * Custom serializer for the Person class
     */
    @Suppress("unused")
    class PersonSerializer : CheckpointCustomSerializer<Person, Map<String, Any>> {
        override fun toProxy(obj: Person): Map<String, Any> {
            return mapOf("name" to obj.name, "bestFriend" to obj.bestFriend)
        }

        override fun fromProxy(proxy: Map<String, Any>): Person {
            return Person(proxy["name"] as String, proxy["bestFriend"] as Person?)
        }
    }
}
