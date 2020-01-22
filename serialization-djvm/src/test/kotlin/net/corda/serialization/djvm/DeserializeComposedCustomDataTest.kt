package net.corda.serialization.djvm

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.fail
import java.io.NotSerializableException
import java.util.function.Function

class DeserializeComposedCustomDataTest: TestBase(KOTLIN) {
    companion object {
        const val MESSAGE = "Hello Sandbox!"
        const val BIG_NUMBER = 23823L

        @Suppress("unused")
        @BeforeAll
        @JvmStatic
        fun checkData() {
            assertNotCordaSerializable<LongAtom>()
            assertNotCordaSerializable<StringAtom>()
        }
    }

    @RegisterExtension
    @JvmField
    val serialization = LocalSerialization(setOf(StringAtomSerializer(), LongAtomSerializer()), emptySet())

    @Test
	fun `test deserializing composed object`() {
        val composed = ComposedData(StringAtom(MESSAGE), LongAtom(BIG_NUMBER))
        val data = composed.serialize()

        sandbox {
            val customSerializers = setOf(
                StringAtomSerializer::class.java.name,
                LongAtomSerializer::class.java.name
            )
            _contextSerializationEnv.set(createSandboxSerializationEnv(
                classLoader = classLoader,
                customSerializerClassNames = customSerializers,
                serializationWhitelistNames = emptySet()
            ))

            val sandboxComplex = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showComplex = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowComposedData::class.java)
            val result = showComplex.apply(sandboxComplex) ?: fail("Result cannot be null")
            assertEquals(SANDBOX_STRING, result::class.java.name)
            assertEquals(ShowComposedData().apply(composed), result.toString())
        }
    }

    @Test
	fun `test deserialization needs custom serializer`() {
        val composed = ComposedData(StringAtom(MESSAGE), LongAtom(BIG_NUMBER))
        val data = composed.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
            assertThrows<NotSerializableException> { data.deserializeFor(classLoader) }
        }
    }

    class ShowComposedData : Function<ComposedData, String> {
        private fun show(atom: Atom<*>): String = atom.toString()

        override fun apply(composed: ComposedData): String {
            return "Composed: message=${show(composed.message)} and value=${show(composed.value)}"
        }
    }

    class StringAtomSerializer : SerializationCustomSerializer<StringAtom, StringAtomSerializer.Proxy> {
        data class Proxy(val value: String)

        override fun fromProxy(proxy: Proxy): StringAtom = StringAtom(proxy.value)
        override fun toProxy(obj: StringAtom): Proxy = Proxy(obj.atom)
    }

    class LongAtomSerializer : SerializationCustomSerializer<LongAtom, LongAtomSerializer.Proxy> {
        data class Proxy(val value: Long?)

        override fun fromProxy(proxy: Proxy): LongAtom = LongAtom(proxy.value)
        override fun toProxy(obj: LongAtom): Proxy = Proxy(obj.atom)
    }
}

@CordaSerializable
class ComposedData(
    val message: StringAtom,
    val value: LongAtom
)

interface Atom<T> {
    val atom: T
}

abstract class AbstractAtom<T>(initialValue: T) : Atom<T> {
    override val atom: T = initialValue

    override fun toString(): String {
        return "[$atom]"
    }
}

/**
 * These classes REQUIRE custom serializers because their
 * constructor parameters cannot be mapped to properties
 * automatically. THIS IS DELIBERATE!
 */
class StringAtom(initialValue: String) : AbstractAtom<String>(initialValue)
class LongAtom(initialValue: Long?) : AbstractAtom<Long?>(initialValue)
