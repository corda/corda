package net.corda.serialization.internal.amqp

import net.corda.core.serialization.SerializedBytes
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.amqp.testutils.*
import net.corda.serialization.internal.carpenter.ClassCarpenterImpl
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import java.io.NotSerializableException
import kotlin.test.*

class EvolutionSerializerFactoryTests {

    private val nonStrictFactory = SerializerFactoryBuilder.build(
            AllWhitelist,
            ClassCarpenterImpl(AllWhitelist, ClassLoader.getSystemClassLoader()),
            descriptorBasedSerializerRegistry = DefaultDescriptorBasedSerializerRegistry(),
            mustPreserveDataWhenEvolving = false
    )

    private val strictFactory = SerializerFactoryBuilder.build(
            AllWhitelist,
            ClassCarpenterImpl(AllWhitelist, ClassLoader.getSystemClassLoader()),
            descriptorBasedSerializerRegistry = DefaultDescriptorBasedSerializerRegistry(),
            mustPreserveDataWhenEvolving = true
    )

    // Version of the class as it was serialised
    //
    // data class C(val a: Int, val b: Int?)
    //
    // Version of the class as it's used in the test
    data class C(val a: Int)

    @Test(timeout=300_000)
	fun preservesDataWhenFlagSet() {
        val resource = "${javaClass.simpleName}.${testName()}"

        val withNullResource = "${resource}_with_null"
        val withoutNullResource = "${resource}_without_null"

        // Uncomment to re-generate test files
        // val withNullOriginal = C(1, null)
        // val withoutNullOriginal = C(1, 1)
        // File(URI("$localPath/$withNullResource")).writeBytes(
        //         SerializationOutput(factory).serialize(withNullOriginal).bytes)
        // File(URI("$localPath/$withoutNullResource")).writeBytes(
        //         SerializationOutput(factory).serialize(withoutNullOriginal).bytes)

        val withoutNullUrl = javaClass.getResource(withoutNullResource)
        val withNullUrl = javaClass.getResource(withNullResource)

        // We can deserialize the evolved instance where the original value of 'b' is null.
        val withNullTarget = DeserializationInput(strictFactory).deserialize(SerializedBytes<C>(withNullUrl.readBytes()))
        assertEquals(1, withNullTarget.a)

        // The non-strict factory will discard the non-null original value of 'b'.
        val withNonNullTarget = DeserializationInput(nonStrictFactory).deserialize(SerializedBytes<C>(withoutNullUrl.readBytes()))
        assertEquals(1, withNonNullTarget.a)

        // The strict factory cannot deserialize the evolved instance where the original value of 'b' is non-null.
        val e = assertThrows<NotSerializableException> {
            DeserializationInput(strictFactory).deserialize(SerializedBytes<C>(withoutNullUrl.readBytes()))
        }
        assertTrue(e.message!!.contains("Non-null value 1 provided for property b, which is not supported in this version"))
    }
}