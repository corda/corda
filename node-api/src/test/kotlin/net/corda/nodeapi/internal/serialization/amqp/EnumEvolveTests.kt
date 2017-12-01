package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.serialization.CordaSerializationTransformEnumDefault
import net.corda.core.serialization.SerializedBytes
import net.corda.testing.common.internal.ProjectStructure.projectRootDir
import org.assertj.core.api.Assertions
import org.junit.Test
import java.io.File
import java.io.NotSerializableException
import java.net.URI

// NOTE: To recreate the test files used by these tests uncomment the original test classes and comment
//       the new ones out, then change each test to write out the serialized bytes rather than read
//       the file.
class EnumEvolveTests {
    var localPath = projectRootDir.toUri().resolve(
            "node-api/src/test/resources/net/corda/nodeapi/internal/serialization/amqp")

    // Version of the class as it was serialised
    //
    // @CordaSerializationTransformEnumDefault("D", "C")
    // enum class DeserializeNewerSetToUnknown { A, B, C, D }
    //
    // Version of the class as it's used in the test
    enum class DeserializeNewerSetToUnknown { A, B, C }

    @Test
    fun deserialiseNewerSetToUnknown() {
        val resource = "${this.javaClass.simpleName}.${testName()}"
        val sf = testDefaultFactory()

        data class C (val e : DeserializeNewerSetToUnknown)

        // Uncomment to re-generate test files
        // File(URI("$localPath/$resource")).writeBytes(
        //        SerializationOutput(sf).serialize(C(DeserializeNewerSetToUnknown.D)).bytes)

        Assertions.assertThatThrownBy {
            DeserializationInput(sf).deserialize(SerializedBytes<C>(
                    File(EvolvabilityTests::class.java.getResource(resource).toURI()).readBytes()))
        }.isInstanceOf(NotSerializableException::class.java)
    }
}