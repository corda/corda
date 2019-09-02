package net.corda.djvm.serialization

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.djvm.serialization.SandboxType.*
import org.apache.qpid.proton.amqp.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeProtonJTest : TestBase(KOTLIN) {
    @Test
    fun `test deserializing unsigned long`() {
        val protonJ = HasUnsignedLong(UnsignedLong.valueOf(12345678))
        val data = protonJ.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxProtonJ = data.deserializeFor(classLoader)

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowUnsignedLong::class.java).newInstance(),
                sandboxProtonJ
            ) ?: fail("Result cannot be null")

            assertEquals(protonJ.number.toString(), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowUnsignedLong : Function<HasUnsignedLong, String> {
        override fun apply(data: HasUnsignedLong): String {
            return data.number.toString()
        }
    }

    @Test
    fun `test deserializing unsigned integer`() {
        val protonJ = HasUnsignedInteger(UnsignedInteger.valueOf(123456))
        val data = protonJ.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxProtonJ = data.deserializeFor(classLoader)

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowUnsignedInteger::class.java).newInstance(),
                sandboxProtonJ
            ) ?: fail("Result cannot be null")

            assertEquals(protonJ.number.toString(), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowUnsignedInteger : Function<HasUnsignedInteger, String> {
        override fun apply(data: HasUnsignedInteger): String {
            return data.number.toString()
        }
    }

    @Test
    fun `test deserializing unsigned short`() {
        val protonJ = HasUnsignedShort(UnsignedShort.valueOf(12345))
        val data = protonJ.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxProtonJ = data.deserializeFor(classLoader)

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowUnsignedShort::class.java).newInstance(),
                sandboxProtonJ
            ) ?: fail("Result cannot be null")

            assertEquals(protonJ.number.toString(), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowUnsignedShort : Function<HasUnsignedShort, String> {
        override fun apply(data: HasUnsignedShort): String {
            return data.number.toString()
        }
    }

    @Test
    fun `test deserializing unsigned byte`() {
        val protonJ = HasUnsignedByte(UnsignedByte.valueOf(0x8f.toByte()))
        val data = protonJ.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxProtonJ = data.deserializeFor(classLoader)

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowUnsignedByte::class.java).newInstance(),
                sandboxProtonJ
            ) ?: fail("Result cannot be null")

            assertEquals(protonJ.number.toString(), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowUnsignedByte : Function<HasUnsignedByte, String> {
        override fun apply(data: HasUnsignedByte): String {
            return data.number.toString()
        }
    }

    @Test
    fun `test deserializing 128 bit decimal`() {
        val protonJ = HasDecimal128(Decimal128(12345678, 98765432))
        val data = protonJ.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxProtonJ = data.deserializeFor(classLoader)

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowDecimal128::class.java).newInstance(),
                sandboxProtonJ
            ) ?: fail("Result cannot be null")

            assertThat(result)
                .isEqualTo(protonJ.number.let { longArrayOf(it.mostSignificantBits, it.leastSignificantBits) })
        }
    }

    class ShowDecimal128 : Function<HasDecimal128, LongArray> {
        override fun apply(data: HasDecimal128): LongArray {
            return data.number.let { longArrayOf(it.mostSignificantBits, it.leastSignificantBits) }
        }
    }

    @Test
    fun `test deserializing 64 bit decimal`() {
        val protonJ = HasDecimal64(Decimal64(98765432))
        val data = protonJ.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxProtonJ = data.deserializeFor(classLoader)

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowDecimal64::class.java).newInstance(),
                sandboxProtonJ
            ) ?: fail("Result cannot be null")

            assertEquals(protonJ.number.bits.toString(), result.toString())
        }
    }

    class ShowDecimal64 : Function<HasDecimal64, Long> {
        override fun apply(data: HasDecimal64): Long {
            return data.number.bits
        }
    }

    @Test
    fun `test deserializing 32 bit decimal`() {
        val protonJ = HasDecimal32(Decimal32(123456))
        val data = protonJ.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxProtonJ = data.deserializeFor(classLoader)

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowDecimal32::class.java).newInstance(),
                sandboxProtonJ
            ) ?: fail("Result cannot be null")

            assertEquals(protonJ.number.bits.toString(), result.toString())
        }
    }

    class ShowDecimal32 : Function<HasDecimal32, Int> {
        override fun apply(data: HasDecimal32): Int {
            return data.number.bits
        }
    }

    @Test
    fun `test deserializing symbol`() {
        val protonJ = HasSymbol(Symbol.valueOf("-my-symbol-value-"))
        val data = protonJ.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxProtonJ = data.deserializeFor(classLoader)

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowSymbol::class.java).newInstance(),
                sandboxProtonJ
            ) ?: fail("Result cannot be null")

            assertEquals(protonJ.symbol.toString(), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowSymbol : Function<HasSymbol, String> {
        override fun apply(data: HasSymbol): String {
            return data.symbol.toString()
        }
    }
}

@CordaSerializable
class HasUnsignedLong(val number: UnsignedLong)

@CordaSerializable
class HasUnsignedInteger(val number: UnsignedInteger)

@CordaSerializable
class HasUnsignedShort(val number: UnsignedShort)

@CordaSerializable
class HasUnsignedByte(val number: UnsignedByte)

@CordaSerializable
class HasDecimal32(val number: Decimal32)

@CordaSerializable
class HasDecimal64(val number: Decimal64)

@CordaSerializable
class HasDecimal128(val number: Decimal128)

@CordaSerializable
class HasSymbol(val symbol: Symbol)
