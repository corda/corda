package net.corda.serialization.djvm

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.apache.qpid.proton.amqp.Decimal128
import org.apache.qpid.proton.amqp.Decimal32
import org.apache.qpid.proton.amqp.Decimal64
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.amqp.UnsignedByte
import org.apache.qpid.proton.amqp.UnsignedInteger
import org.apache.qpid.proton.amqp.UnsignedLong
import org.apache.qpid.proton.amqp.UnsignedShort
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

            val taskFactory = classLoader.createRawTaskFactory()
            val showUnsignedLong = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowUnsignedLong::class.java)
            val result = showUnsignedLong.apply(sandboxProtonJ) ?: fail("Result cannot be null")

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

            val taskFactory = classLoader.createRawTaskFactory()
            val showUnsignedInteger = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowUnsignedInteger::class.java)
            val result = showUnsignedInteger.apply(sandboxProtonJ) ?: fail("Result cannot be null")

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

            val taskFactory = classLoader.createRawTaskFactory()
            val showUnsignedShort = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowUnsignedShort::class.java)
            val result = showUnsignedShort.apply(sandboxProtonJ) ?: fail("Result cannot be null")

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

            val taskFactory = classLoader.createRawTaskFactory()
            val showUnsignedByte = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowUnsignedByte::class.java)
            val result = showUnsignedByte.apply(sandboxProtonJ) ?: fail("Result cannot be null")

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

            val taskFactory = classLoader.createRawTaskFactory()
            val showDecimal128 = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowDecimal128::class.java)
            val result = showDecimal128.apply(sandboxProtonJ) ?: fail("Result cannot be null")

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

            val taskFactory = classLoader.createRawTaskFactory()
            val showDecimal64 = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowDecimal64::class.java)
            val result = showDecimal64.apply(sandboxProtonJ) ?: fail("Result cannot be null")

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

            val taskFactory = classLoader.createRawTaskFactory()
            val showDecimal32 = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowDecimal32::class.java)
            val result = showDecimal32.apply(sandboxProtonJ) ?: fail("Result cannot be null")

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

            val taskFactory = classLoader.createRawTaskFactory()
            val showSymbol = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowSymbol::class.java)
            val result = showSymbol.apply(sandboxProtonJ) ?: fail("Result cannot be null")

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
