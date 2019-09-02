package net.corda.djvm.serialization

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.djvm.serialization.SandboxType.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.util.*
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

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowStringArray::class.java).newInstance(),
                sandboxArray
            ) ?: fail("Result cannot be null")

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

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowCharacterArray::class.java).newInstance(),
                sandboxArray
            ) ?: fail("Result cannot be null")

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

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowLongArray::class.java).newInstance(),
                sandboxArray
            ) ?: fail("Result cannot be null")

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

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowIntegerArray::class.java).newInstance(),
                sandboxArray
            ) ?: fail("Result cannot be null")

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

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowShortArray::class.java).newInstance(),
                sandboxArray
            ) ?: fail("Result cannot be null")

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

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowByteArray::class.java).newInstance(),
                sandboxArray
            ) ?: fail("Result cannot be null")

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

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowDoubleArray::class.java).newInstance(),
                sandboxArray
            ) ?: fail("Result cannot be null")

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

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowFloatArray::class.java).newInstance(),
                sandboxArray
            ) ?: fail("Result cannot be null")

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

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                    classLoader.loadClassForSandbox(ShowBooleanArray::class.java).newInstance(),
                    sandboxArray
            ) ?: fail("Result cannot be null")

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

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowUUIDArray::class.java).newInstance(),
                sandboxArray
            ) ?: fail("Result cannot be null")

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
