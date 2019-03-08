package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.serialization.SerializedBytes
import net.corda.nodeapi.internal.serialization.AllWhitelist
import net.corda.nodeapi.internal.serialization.amqp.testutils.TerribleCarpenter
import net.corda.nodeapi.internal.serialization.amqp.testutils.testName
import net.corda.nodeapi.internal.serialization.carpenter.InterfaceMismatchException
import net.corda.nodeapi.internal.serialization.carpenter.UncarpentableException
import net.corda.testing.common.internal.ProjectStructure
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.net.URI
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class CarpentryAndEvolverTests(private val carpenterException : Exception) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun parameters(): Collection<Exception> {
            return listOf (
                    UncarpentableException("", "", ""),
                    InterfaceMismatchException("")
            )
        }
    }

    private var localPath = ProjectStructure.projectRootDir.toUri().resolve(
            "node-api/src/test/resources/net/corda/nodeapi/internal/serialization/amqp")

    class NoCarpentryFactory(
            carpenterException: Exception
    ) : SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader()) {
        override val classCarpenter = TerribleCarpenter(
                ClassLoader.getSystemClassLoader(),
                AllWhitelist,
                carpenterException)
    }

    @Test
    fun newTypeWeCantCarpentIsIgnoredWhenRemovedByEvolver() {
        val file = File(URI("$localPath/${javaClass.simpleName}.${testName()}"))
        val factory = NoCarpentryFactory(carpenterException)

        // Classes as they were serialised, simulates a message coming from a newer node where ToBeCarpented
        // has been added to ToBeEvolved
        //
        // class ToBeCarpented(val a: String, val b: Int)
        // class ToBeEvolved(val a: String, val tbc: ToBeCarpented)

        // Class as it exited then (from the perspective that this is replicating an older node receiving
        // a newer version of the class
        class ToBeEvolved(val a: String)

        // Uncomment to re-generate test files
        //file.writeBytes(SerializationOutput(factory).serialize(ToBeEvolved("evolve me", ToBeCarpented("Carpent me", 10))).bytes)

        val tbe = DeserializationInput(factory).deserialize(SerializedBytes<ToBeEvolved>(file.readBytes()))

        assertEquals("evolve me", tbe.a)
    }
}