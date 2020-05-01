package net.corda.serialization.djvm

import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.SandboxType.KOTLIN
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.model.LocalTypeInformation
import net.corda.serialization.internal.model.LocalTypeInformation.ACollection
import net.corda.serialization.internal.model.LocalTypeInformation.AnEnum
import net.corda.serialization.internal.model.LocalTypeInformation.AMap
import net.corda.serialization.internal.model.LocalTypeInformation.Abstract
import net.corda.serialization.internal.model.LocalTypeInformation.Atomic
import net.corda.serialization.internal.model.LocalTypeInformation.Opaque
import org.apache.qpid.proton.amqp.Decimal128
import org.apache.qpid.proton.amqp.Decimal32
import org.apache.qpid.proton.amqp.Decimal64
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.amqp.UnsignedByte
import org.apache.qpid.proton.amqp.UnsignedInteger
import org.apache.qpid.proton.amqp.UnsignedLong
import org.apache.qpid.proton.amqp.UnsignedShort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Date
import java.util.EnumSet
import java.util.UUID

class LocalTypeModelTest : TestBase(KOTLIN) {
    private val serializerFactory: SerializerFactory get() {
        val factory = SerializationFactory.defaultFactory as SerializationFactoryImpl
        val scheme = factory.getRegisteredSchemes().single() as AMQPSerializationScheme
        return scheme.serializerFactory
    }

    private inline fun <reified T> sandbox(classLoader: SandboxClassLoader): Class<*> {
        return classLoader.toSandboxClass(T::class.java)
    }

    private inline fun <reified LOCAL: LocalTypeInformation> assertLocalType(type: Class<*>): LOCAL {
        return assertLocalType(LOCAL::class.java, type) as LOCAL
    }

    private fun <LOCAL: LocalTypeInformation> assertLocalType(localType: Class<LOCAL>, type: Class<*>): LocalTypeInformation {
        val typeData = serializerFactory.getTypeInformation(type)
        assertThat(typeData).isInstanceOf(localType)
        return typeData
    }

    @Test
    fun testString() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        assertLocalType<Atomic>(sandbox<String>(classLoader))
    }

    @Test
    fun testLong() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        assertLocalType<Opaque>(sandbox<Long>(classLoader))
        assertLocalType<Atomic>(Long::class.javaPrimitiveType!!)
    }

    @Test
    fun testInteger() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        assertLocalType<Opaque>(sandbox<Int>(classLoader))
        assertLocalType<Atomic>(Int::class.javaPrimitiveType!!)
    }

    @Test
    fun testShort() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        assertLocalType<Opaque>(sandbox<Short>(classLoader))
        assertLocalType<Atomic>(Short::class.javaPrimitiveType!!)
    }

    @Test
    fun testByte() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        assertLocalType<Opaque>(sandbox<Byte>(classLoader))
        assertLocalType<Atomic>(Byte::class.javaPrimitiveType!!)
    }

    @Test
    fun testDouble() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        assertLocalType<Opaque>(sandbox<Double>(classLoader))
        assertLocalType<Atomic>(Double::class.javaPrimitiveType!!)
    }

    @Test
    fun testFloat() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        assertLocalType<Opaque>(sandbox<Float>(classLoader))
        assertLocalType<Atomic>(Float::class.javaPrimitiveType!!)
    }

    @Test
    fun testChar() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        assertLocalType<Opaque>(sandbox<Char>(classLoader))
        assertLocalType<Atomic>(Char::class.javaPrimitiveType!!)
    }

    @Test
    fun testUnsignedLong() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        assertLocalType<Opaque>(sandbox<UnsignedLong>(classLoader))
    }

    @Test
    fun testUnsignedInteger() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        assertLocalType<Opaque>(sandbox<UnsignedInteger>(classLoader))
    }

    @Test
    fun testUnsignedShort() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        assertLocalType<Opaque>(sandbox<UnsignedShort>(classLoader))
    }

    @Test
    fun testUnsignedByte() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        assertLocalType<Opaque>(sandbox<UnsignedByte>(classLoader))
    }

    @Test
    fun testDecimal32() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        assertLocalType<Opaque>(sandbox<Decimal32>(classLoader))
    }

    @Test
    fun testDecimal64() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        assertLocalType<Opaque>(sandbox<Decimal64>(classLoader))
    }

    @Test
    fun testDecimal128() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        assertLocalType<Opaque>(sandbox<Decimal128>(classLoader))
    }

    @Test
    fun testSymbol() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        assertLocalType<Opaque>(sandbox<Symbol>(classLoader))
    }

    @Test
    fun testUUID() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        assertLocalType<Opaque>(sandbox<UUID>(classLoader))
    }

    @Test
    fun testDate() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        assertLocalType<Opaque>(sandbox<Date>(classLoader))
    }

    @Test
    fun testCollection() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        assertLocalType<ACollection>(sandbox<Collection<*>>(classLoader))
    }

    @Test
    fun testEnum() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        assertLocalType<AnEnum>(sandbox<ExampleEnum>(classLoader))
    }

    @Test
    fun testCustomEnum() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        val anEnum = assertLocalType<AnEnum>(sandbox<CustomEnum>(classLoader))
        assertThat(anEnum.members)
            .containsExactlyElementsOf(CustomEnum::class.java.enumConstants.map(CustomEnum::name))
    }

    @Test
    fun testEnumSet() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        assertLocalType<Abstract>(sandbox<EnumSet<*>>(classLoader))

        val exampleEnumSet = EnumSet.noneOf(ExampleEnum::class.java)
        assertLocalType<Opaque>(classLoader.toSandboxClass(exampleEnumSet::class.java))
    }

    @Test
    fun testMap() = sandbox {
        _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
        assertLocalType<AMap>(sandbox<Map<*,*>>(classLoader))
    }

    @Suppress("unused")
    enum class CustomEnum {
        ONE,
        TWO;

        override fun toString(): String {
            return "[${name.toLowerCase()}]"
        }
    }
}