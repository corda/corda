package net.corda.serialization.djvm

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializePrimitiveArraysTest : TestBase(KOTLIN) {
    @Test
	fun `test deserializing character array`() {
        val charArray = PrimitiveCharArray(charArrayOf('H', 'e', 'l', 'l', 'o', '!'))
        val data = charArray.serialize()
        assertEquals("Hello!", ShowCharArray().apply(charArray))

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxArray = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showCharArray = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowCharArray::class.java)
            val result = showCharArray.apply(sandboxArray) ?: fail("Result cannot be null")

            assertEquals(SANDBOX_STRING, result::class.java.name)
            assertEquals("Hello!", result.toString())
        }
    }

    class ShowCharArray : Function<PrimitiveCharArray, String> {
        override fun apply(data: PrimitiveCharArray): String {
            return data.letters.joinTo(StringBuilder(), separator = "").toString()
        }
    }

    @Test
	fun `test deserializing integer array`() {
        val intArray = PrimitiveIntegerArray(intArrayOf(100, 200, 300, 400, 500))
        val data = intArray.serialize()

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

    class ShowIntegerArray : Function<PrimitiveIntegerArray, Int> {
        override fun apply(data: PrimitiveIntegerArray): Int {
            return data.integers.sum()
        }
    }

    @Test
	fun `test deserializing long array`() {
        val longArray = PrimitiveLongArray(longArrayOf(1000, 2000, 3000, 4000, 5000))
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

    class ShowLongArray : Function<PrimitiveLongArray, Long> {
        override fun apply(data: PrimitiveLongArray): Long {
            return data.longs.sum()
        }
    }

    @Test
	fun `test deserializing short array`() {
        val shortArray = PrimitiveShortArray(shortArrayOf(100, 200, 300, 400, 500))
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

    class ShowShortArray : Function<PrimitiveShortArray, Int> {
        override fun apply(data: PrimitiveShortArray): Int {
            return data.shorts.sum()
        }
    }

    @Test
	fun `test deserializing byte array`() {
        val byteArray = PrimitiveByteArray(byteArrayOf(10, 20, 30, 40, 50))
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

    class ShowByteArray : Function<PrimitiveByteArray, Int> {
        override fun apply(data: PrimitiveByteArray): Int {
            return data.bytes.sum()
        }
    }

    @Test
	fun `test deserializing boolean array`() {
        val booleanArray = PrimitiveBooleanArray(booleanArrayOf(true, true))
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

    class ShowBooleanArray : Function<PrimitiveBooleanArray, Boolean> {
        override fun apply(data: PrimitiveBooleanArray): Boolean {
            return data.bools.all { it }
        }
    }

    @Test
	fun `test deserializing double array`() {
        val doubleArray = PrimitiveDoubleArray(doubleArrayOf(1000.0, 2000.0, 3000.0, 4000.0, 5000.0))
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

    class ShowDoubleArray : Function<PrimitiveDoubleArray, Double> {
        override fun apply(data: PrimitiveDoubleArray): Double {
            return data.doubles.sum()
        }
    }

    @Test
	fun `test deserializing float array`() {
        val floatArray = PrimitiveFloatArray(floatArrayOf(100.0f, 200.0f, 300.0f, 400.0f, 500.0f))
        val data = floatArray.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxArray = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showFloatArray = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowFloatArray::class.java)
            val result = showFloatArray.apply(sandboxArray) ?: fail("Result cannot be null")

            assertEquals("sandbox.java.lang.Float", result::class.java.name)
            assertEquals("1500.0", result.toString())
        }
    }

    class ShowFloatArray : Function<PrimitiveFloatArray, Float> {
        override fun apply(data: PrimitiveFloatArray): Float {
            return data.floats.sum()
        }
    }
}

@CordaSerializable
class PrimitiveCharArray(val letters: CharArray)

@CordaSerializable
class PrimitiveShortArray(val shorts: ShortArray)

@CordaSerializable
class PrimitiveIntegerArray(val integers: IntArray)

@CordaSerializable
class PrimitiveLongArray(val longs: LongArray)

@CordaSerializable
class PrimitiveByteArray(val bytes: ByteArray)

@CordaSerializable
class PrimitiveBooleanArray(val bools: BooleanArray)

@CordaSerializable
class PrimitiveDoubleArray(val doubles: DoubleArray)

@CordaSerializable
class PrimitiveFloatArray(val floats: FloatArray)
