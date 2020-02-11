package net.corda.serialization.djvm

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.util.EnumMap
import java.util.NavigableMap
import java.util.SortedMap
import java.util.TreeMap
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeMapsTest : TestBase(KOTLIN) {
    @Test
	fun `test deserializing map`() {
        val stringMap = StringMap(mapOf("Open" to "Hello World", "Close" to "Goodbye, Cruel World"))
        val data = stringMap.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxMap = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showStringMap = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowStringMap::class.java)
            val result = showStringMap.apply(sandboxMap) ?: fail("Result cannot be null")

            assertEquals(stringMap.values.entries.joinToString(), result.toString())
            assertEquals("Open=Hello World, Close=Goodbye, Cruel World", result.toString())
        }
    }

    class ShowStringMap : Function<StringMap, String> {
        override fun apply(data: StringMap): String {
            return data.values.entries.joinToString()
        }
    }

    @Test
	fun `test deserializing sorted map`() {
        val sortedMap = StringSortedMap(sortedMapOf(
            100 to "Goodbye, Cruel World",
            10 to "Hello World",
            50 to "Having Fun!"
        ))
        val data = sortedMap.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxMap = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showStringSortedMap = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowStringSortedMap::class.java)
            val result = showStringSortedMap.apply(sandboxMap) ?: fail("Result cannot be null")

            assertEquals(sortedMap.values.entries.joinToString(), result.toString())
            assertEquals("10=Hello World, 50=Having Fun!, 100=Goodbye, Cruel World", result.toString())
        }
    }

    class ShowStringSortedMap : Function<StringSortedMap, String> {
        override fun apply(data: StringSortedMap): String {
            return data.values.entries.joinToString()
        }
    }

    @Test
	fun `test deserializing navigable map`() {
        val navigableMap = StringNavigableMap(mapOf(
            10000L to "Goodbye, Cruel World",
            1000L to "Hello World",
            5000L to "Having Fun!"
        ).toMap(TreeMap()))
        val data = navigableMap.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxMap = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showStringNavigableMap = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowStringNavigableMap::class.java)
            val result = showStringNavigableMap.apply(sandboxMap) ?: fail("Result cannot be null")

            assertEquals(navigableMap.values.entries.joinToString(), result.toString())
            assertEquals("1000=Hello World, 5000=Having Fun!, 10000=Goodbye, Cruel World", result.toString())
        }
    }

    class ShowStringNavigableMap : Function<StringNavigableMap, String> {
        override fun apply(data: StringNavigableMap): String {
            return data.values.entries.joinToString()
        }
    }

    @Test
	fun `test deserializing linked hash map`() {
        val linkedHashMap = StringLinkedHashMap(linkedMapOf(
            "Close" to "Goodbye, Cruel World",
            "Open" to "Hello World",
            "During" to "Having Fun!"
        ))
        val data = linkedHashMap.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxMap = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val sandboxFunction = classLoader.createSandboxFunction()
            val showStringLinkedHashMap = taskFactory.compose(sandboxFunction).apply(ShowStringLinkedHashMap::class.java)
            val result = showStringLinkedHashMap.apply(sandboxMap) ?: fail("Result cannot be null")

            assertEquals(linkedHashMap.values.entries.joinToString(), result.toString())
            assertEquals("Close=Goodbye, Cruel World, Open=Hello World, During=Having Fun!", result.toString())
        }
    }

    class ShowStringLinkedHashMap : Function<StringLinkedHashMap, String> {
        override fun apply(data: StringLinkedHashMap): String {
            return data.values.entries.joinToString()
        }
    }

    @Test
	fun `test deserializing tree map`() {
        val treeMap = StringTreeMap(mapOf(
            10000 to "Goodbye, Cruel World",
            1000 to "Hello World",
            5000 to "Having Fun!"
        ).toMap(TreeMap()))
        val data = treeMap.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxMap = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showStringTreeMap = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowStringTreeMap::class.java)
            val result = showStringTreeMap.apply(sandboxMap) ?: fail("Result cannot be null")

            assertEquals(treeMap.values.entries.joinToString(), result.toString())
            assertEquals("1000=Hello World, 5000=Having Fun!, 10000=Goodbye, Cruel World", result.toString())
        }
    }

    class ShowStringTreeMap : Function<StringTreeMap, String> {
        override fun apply(data: StringTreeMap): String {
            return data.values.entries.joinToString()
        }
    }

    @Test
	fun `test deserializing enum map`() {
        val enumMap = EnumMap(mapOf(
            ExampleEnum.ONE to "One!",
            ExampleEnum.TWO to "Two!"
        ))
        val data = enumMap.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxMap = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showEnumMap = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowEnumMap::class.java)
            val result = showEnumMap.apply(sandboxMap) ?: fail("Result cannot be null")

            assertEquals(enumMap.toString(), result.toString())
            assertEquals("{ONE=One!, TWO=Two!}", result.toString())
        }
    }

    class ShowEnumMap : Function<EnumMap<*, String>, String> {
        override fun apply(data: EnumMap<*, String>): String {
            return data.toString()
        }
    }
}

@CordaSerializable
class StringMap(val values: Map<String, String>)

@CordaSerializable
class StringSortedMap(val values: SortedMap<Int, String>)

@CordaSerializable
class StringNavigableMap(val values: NavigableMap<Long, String>)

@CordaSerializable
class StringLinkedHashMap(val values: LinkedHashMap<String, String>)

@CordaSerializable
class StringTreeMap(val values: TreeMap<Int, String>)
