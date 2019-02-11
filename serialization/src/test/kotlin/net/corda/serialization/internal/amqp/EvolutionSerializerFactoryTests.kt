package net.corda.serialization.internal.amqp

import com.natpryce.hamkrest.should.shouldMatch
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.serialize
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.amqp.GenericsTests.Companion.localPath
import net.corda.serialization.internal.amqp.testutils.*
import net.corda.serialization.internal.carpenter.ClassCarpenterImpl
import net.corda.serialization.internal.model.RemoteTypeInformation
import net.corda.serialization.internal.model.TypeIdentifier
import org.junit.Test
import java.io.File
import java.io.NotSerializableException
import java.net.URI
import kotlin.test.*

class EvolutionSerializerFactoryTests {

    private val factory = SerializerFactoryBuilder.build(
            AllWhitelist,
            ClassCarpenterImpl(AllWhitelist, ClassLoader.getSystemClassLoader()),
            descriptorBasedSerializerRegistry = DefaultDescriptorBasedSerializerRegistry(),
            mustPreserveDataWhenEvolving = true)

    // Version of the class as it was serialised
    //
    // data class C(val a: Int, val b: Int?)
    //
    // Version of the class as it's used in the test
    data class C(val a: Int)

    @Test
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
        val withNullTarget = DeserializationInput(factory).deserialize(SerializedBytes<C>(withNullUrl.readBytes()))
        assertEquals(1, withNullTarget.a)

        // We cannot deserialize the evolved instance where the original value of 'b' is non-null.
        try {
            DeserializationInput(factory).deserialize(SerializedBytes<C>(withoutNullUrl.readBytes()))
            fail("Expected deserialisation of object with non-null value for 'b' to fail")
        } catch (e: NotSerializableException) {
            assertTrue(e.message!!.contains(
                    "Non-null value 1 provided for property b, which is not supported in this version"))
        }
    }

}