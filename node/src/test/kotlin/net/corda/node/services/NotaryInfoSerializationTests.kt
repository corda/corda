package net.corda.node.services

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NotaryInfo
import net.corda.core.serialization.SerializedBytes
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.testing.common.internal.ProjectStructure.projectRootDir
import net.corda.testing.core.TestIdentity
import org.junit.Test
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NotaryInfoSerializationTests {
    private val notaryName = CordaX500Name("Notary", "Zurich", "CH")
    private val notaryParty: Party = TestIdentity(notaryName, 20L).party

    // When regenerating the test files this needs to be set to the file system location of the resource files
    @Suppress("UNUSED")
    var localPath: URI = projectRootDir.toUri().resolve("node/src/test/resources/net/corda/node/services/")

    // Read in a serialized NotaryInfo from 4.12 (or earlier)
    // `protocol` did not exist as a field when serializing
    @Test(timeout = 300_000)
    fun deserializeNotaryInfoWithoutProtocol() {

        val resource = "NotaryInfoTest.notaryInfoWithoutProtocol"
        val sf = testDefaultFactory()

        val expectedNotaryInfo = NotaryInfo(notaryParty, false)

        // uncomment to recreate the data
        // This has to be run on a version of Corda that does not have the `protocol` field in NotaryInfo
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(expectedNotaryInfo, testSerializationContext).bytes)

        val url = NotaryInfoSerializationTests::class.java.getResource(resource)!!
        val sc2 = url.readBytes()
        val deserializedNotaryInfo = DeserializationInput(sf)
                .deserialize(SerializedBytes<NotaryInfo>(sc2), testSerializationContext)

        assertEquals(expectedNotaryInfo, deserializedNotaryInfo)
    }

    // Read in a serialized NotaryInfo from 4.13+, with protocol
    // populated from https://github.com/corda/corda/pull/7991
    @Test(timeout = 300_000)
    fun deserializeNotaryInfoWithProtocol(){
        val resource = "NotaryInfoTest.notaryInfoWithProtocol"
        val sf = testDefaultFactory()

        // uncomment to recreate the data
        // This has to be run on a version of Corda that has the `protocol` field in NotaryInfo
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf)
        //        .serialize(NotaryInfo(notaryParty, false, "TEST_PROTOCOL"), testSerializationContext).bytes)

        val url = NotaryInfoSerializationTests::class.java.getResource(resource)!!
        val sc2 = url.readBytes()
        val deserializedNotaryInfo = DeserializationInput(sf)
                .deserialize(SerializedBytes<NotaryInfo>(sc2), testSerializationContext)

        assertEquals(notaryParty, deserializedNotaryInfo.identity)
        assertFalse(deserializedNotaryInfo.validating)
    }

}
