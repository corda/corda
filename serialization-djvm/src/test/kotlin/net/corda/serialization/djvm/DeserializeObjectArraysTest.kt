package net.corda.serialization.djvm

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.util.UUID
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeObjectArraysTest : TestBase(KOTLIN) {
    @Test
	fun `test deserializing string array`() {
        val stringArray = HasStringArray(arrayOf("Hello", "World", "!"))
        val data = stringArray.serialize()
        assertEquals("Hello, World, !", ShowStringArray().apply(stringArray))

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxArray = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showStringArray = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowStringArray::class.java)
            val result = showStringArray.apply(sandboxArray) ?: fail("Result cannot be null")

            assertEquals(SANDBOX_STRING, result::class.java.name)
            assertEquals("Hello, World, !", result.toString())
        }
    }

    class ShowStringArray : Function<HasStringArray, String> {
        override fun apply(data: HasStringArray): String {
            return data.lines.joinToString()
        }
    }

    @Test
	fun `test deserializing character array`() {
        val charArray = HasCharacterArray(arrayOf('H', 'e', 'l', 'l', 'o', '!'))
        val data = charArray.serialize()
        assertEquals("Hello!", ShowCharacterArray().apply(charArray))

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxArray = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showCharacterArray = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowCharacterArray::class.java)
            val result = showCharacterArray.apply(sandboxArray) ?: fail("Result cannot be null")

            assertEquals(SANDBOX_STRING, result::class.java.name)
            assertEquals("Hello!", result.toString())
        }
    }

    class ShowCharacterArray : Function<HasCharacterArray, String> {
        override fun apply(data: HasCharacterArray): String {
            return data.letters.joinTo(StringBuilder(), separator = "").toString()
        }
    }

    @Test
	fun `test deserializing long array`() {
        val longArray = HasLongArray(arrayOf(1000, 2000, 3000, 4000, 5000))
        val data = longArray.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxArray = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showLongArray = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowLongArray::class.java)
            val result = showLongArray.apply(sandboxArray) ?: fail("Result cannot be null")

            assertEquals("sandbox.java.lang.Long", result::class.java.name)
            assertEquals("15000", result.toString())
        }
    }

    class ShowLongArray : Function<HasLongArray, Long> {
        override fun apply(data: HasLongArray): Long {
            return data.longs.sum()
        }
    }

    @Test
	fun `test deserializing integer array`() {
        val integerArray = HasIntegerArray(arrayOf(100, 200, 300, 400, 500))
        val data = integerArray.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxArray = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showIntegerArray = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowIntegerArray::class.java)
            val result = showIntegerArray.apply(sandboxArray) ?: fail("Result cannot be null")

            assertEquals("sandbox.java.lang.Integer", result::class.java.name)
            assertEquals("1500", result.toString())
        }
    }

    class ShowIntegerArray : Function<HasIntegerArray, Int> {
        override fun apply(data: HasIntegerArray): Int {
            return data.integers.sum()
        }
    }

    @Test
	fun `test deserializing short array`() {
        val shortArray = HasShortArray(arrayOf(100, 200, 300, 400, 500))
        val data = shortArray.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxArray = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showShortArray = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowShortArray::class.java)
            val result = showShortArray.apply(sandboxArray) ?: fail("Result cannot be null")

            assertEquals("sandbox.java.lang.Integer", result::class.java.name)
            assertEquals("1500", result.toString())
        }
    }

    class ShowShortArray : Function<HasShortArray, Int> {
        override fun apply(data: HasShortArray): Int {
            return data.shorts.sum()
        }
    }

    @Test
	fun `test deserializing byte array`() {
        val byteArray = HasByteArray(arrayOf(10, 20, 30, 40, 50))
        val data = byteArray.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxArray = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showByteArray = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowByteArray::class.java)
            val result = showByteArray.apply(sandboxArray) ?: fail("Result cannot be null")

            assertEquals("sandbox.java.lang.Integer", result::class.java.name)
            assertEquals("150", result.toString())
        }
    }

    class ShowByteArray : Function<HasByteArray, Int> {
        override fun apply(data: HasByteArray): Int {
            return data.bytes.sum()
        }
    }

    @Test
	fun `test deserializing double array`() {
        val doubleArray = HasDoubleArray(arrayOf(1000.0, 2000.0, 3000.0, 4000.0, 5000.0))
        val data = doubleArray.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxArray = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showDoubleArray = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowDoubleArray::class.java)
            val result = showDoubleArray.apply(sandboxArray) ?: fail("Result cannot be null")

            assertEquals("sandbox.java.lang.Double", result::class.java.name)
            assertEquals("15000.0", result.toString())
        }
    }

    class ShowDoubleArray : Function<HasDoubleArray, Double> {
        override fun apply(data: HasDoubleArray): Double {
            return data.doubles.sum()
        }
    }

    @Test
	fun `test deserializing float array`() {
        val floatArray = HasFloatArray(arrayOf(10.0f, 20.0f, 30.0f, 40.0f, 50.0f))
        val data = floatArray.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxArray = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showFloatArray = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowFloatArray::class.java)
            val result = showFloatArray.apply(sandboxArray) ?: fail("Result cannot be null")

            assertEquals("sandbox.java.lang.Float", result::class.java.name)
            assertEquals("150.0", result.toString())
        }
    }

    class ShowFloatArray : Function<HasFloatArray, Float> {
        override fun apply(data: HasFloatArray): Float {
            return data.floats.sum()
        }
    }

    @Test
	fun `test deserializing boolean array`() {
        val booleanArray = HasBooleanArray(arrayOf(true, true, true))
        val data = booleanArray.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxArray = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showBooleanArray = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowBooleanArray::class.java)
            val result = showBooleanArray.apply(sandboxArray) ?: fail("Result cannot be null")

            assertEquals("sandbox.java.lang.Boolean", result::class.java.name)
            assertEquals("true", result.toString())
        }
    }

    class ShowBooleanArray : Function<HasBooleanArray, Boolean> {
        override fun apply(data: HasBooleanArray): Boolean {
            return data.bools.all { it }
        }
    }

    @Test
	fun `test deserializing uuid array`() {
        val uuid = UUID.randomUUID()
        val uuidArray = HasUUIDArray(arrayOf(uuid))
        val data = uuidArray.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxArray = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showUUIDArray = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowUUIDArray::class.java)
            val result = showUUIDArray.apply(sandboxArray) ?: fail("Result cannot be null")

            assertEquals(SANDBOX_STRING, result::class.java.name)
            assertEquals(uuid.toString(), result.toString())
        }
    }

    class ShowUUIDArray : Function<HasUUIDArray, String> {
        override fun apply(data: HasUUIDArray): String {
            return data.uuids.joinTo(StringBuilder()).toString()
        }
    }
}

@CordaSerializable
class HasStringArray(val lines: Array<String>)

@CordaSerializable
class HasCharacterArray(val letters: Array<Char>)

@CordaSerializable
class HasLongArray(val longs: Array<Long>)

@CordaSerializable
class HasIntegerArray(val integers: Array<Int>)

@CordaSerializable
class HasShortArray(val shorts: Array<Short>)

@CordaSerializable
class HasByteArray(val bytes: Array<Byte>)

@CordaSerializable
class HasDoubleArray(val doubles: Array<Double>)

@CordaSerializable
class HasFloatArray(val floats: Array<Float>)

@CordaSerializable
class HasBooleanArray(val bools: Array<Boolean>)

@CordaSerializable
class HasUUIDArray(val uuids: Array<UUID>)
