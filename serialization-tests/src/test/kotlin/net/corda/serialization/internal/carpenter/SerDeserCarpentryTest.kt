package net.corda.serialization.internal.carpenter

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.deserialize
import net.corda.serialization.internal.amqp.testutils.readTestResource
import net.corda.testing.core.SerializationEnvironmentRule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Rule
import org.junit.Test

class SerDeserCarpentryTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Test
    fun implementingGenericInterface() {
        // Original class that was serialised
//        data class GenericData(val a: Int) : GenericInterface<String>
//        writeTestResource(GenericData(123).serialize())

        val data = readTestResource().deserialize<GenericInterface<*>>()
        assertThat(data.javaClass.getMethod("getA").invoke(data)).isEqualTo(123)
    }

    @Test
    fun lenientCarpenter() {
        // Original class that was serialised
//        data class Data(val b: Int) : AInterface {
//            override val a: Int get() = b
//        }
//        writeTestResource(Data(123).serialize())

        val data = readTestResource().deserialize<AInterface>(context = SerializationFactory.defaultFactory.defaultContext.withLenientCarpenter())
        assertThat(data.javaClass.getMethod("getB").invoke(data)).isEqualTo(123)
        assertThatExceptionOfType(AbstractMethodError::class.java).isThrownBy { data.a }
    }

    @Suppress("unused")
    @CordaSerializable
    interface GenericInterface<T>

    @CordaSerializable
    interface AInterface {
        val a: Int
    }
}
