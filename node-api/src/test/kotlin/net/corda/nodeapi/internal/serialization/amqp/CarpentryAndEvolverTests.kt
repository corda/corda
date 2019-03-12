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
import java.io.NotSerializableException
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

        private val testEnvironment : (String) -> String? = {
            s : String -> if (s == "DISABLE-CORDA-2704") "true" else System.getenv(s)
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



    class FactoryWithoutCorda2709 (
            carpenterException: Exception
    ): SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader(), environment = testEnvironment) {
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

    @Test
    fun ignoresSeveralErrors() {
        val file = File(URI("$localPath/${javaClass.simpleName}.${testName()}"))
        val factory = NoCarpentryFactory(carpenterException)

        // Classes as they were serialised, simulates a message coming from a newer node where ToBeCarpented
        // has been added to ToBeEvolved
        //
        //class ToBeCarpented1(val a: String)
        //class ToBeCarpented2(val a: Int)
        //class ToBeEvolved(val tbc2: ToBeCarpented2, val a: String, val tbc1: List<ToBeCarpented1>)

        //file.writeBytes(SerializationOutput(factory).serialize(
        //        ToBeEvolved(
        //                ToBeCarpented2(10101),
        //                "evolve me",
        //                listOf(ToBeCarpented1("Carpent me"))
        //        )
        //).bytes)

        // Class as it exited then (from the perspective that this is replicating an older node receiving
        // a newer version of the class
        class ToBeEvolved(val a: String)

        val tbe = DeserializationInput(factory).deserialize(SerializedBytes<ToBeEvolved>(file.readBytes()))

        assertEquals("evolve me", tbe.a)
    }

    // Make sure that the list of uncarpentable elements is stored at the start of the byte stream
    // to ensure we definitely skip it properly when reading the remaining parameters
    @Test
    fun failstoCarpentFirstSortedElementIsGeneric() {
        val file = File(URI("$localPath/${javaClass.simpleName}.${testName()}"))
        val factory = NoCarpentryFactory(carpenterException)

        // Classes as they were serialised, simulates a message coming from a newer node where ToBeCarpented
        // has been added to ToBeEvolved
        //
        // class ToBeCarpented(val a: Int)
        // class ToBeEvolved(val a: List<ToBeCarpented>, val b: String)

        // Uncomment to regenerate test file
        // file.writeBytes(SerializationOutput(factory).serialize(
        //        ToBeEvolved(
        //                listOf(ToBeCarpented(10101), ToBeCarpented(20202)),
        //                "evolve me"
        //    )
        // ).bytes)

        // Class as it exited then (from the perspective that this is replicating an older node receiving
        // a newer version of the class
        class ToBeEvolved(val b: String)

        val tbe = DeserializationInput(factory).deserialize(SerializedBytes<ToBeEvolved>(file.readBytes()))

        assertEquals("evolve me", tbe.b)
    }

    @Test
    fun ensureTopLevelCarpentryStillErrorsOnFailure() {
        val file = File(URI("$localPath/${javaClass.simpleName}.${testName()}"))
        val factory = NoCarpentryFactory(carpenterException)

        abstract class TBC (open val a: String)

        // Class as it were serialised
        //
        //class ToBeCarpented(override val a: String, val b: Int) : TBC (a)


        // Uncomment to re-generate test files
        //file.writeBytes(SerializationOutput(factory).serialize(ToBeCarpented("evolve me",10)).bytes)


        // Class as it exists "then"
        class ToBeCarpented2(override val a: String, val b: Int) : TBC (a)


        assertFailsWith(NotSerializableException::class) {
            DeserializationInput(factory).deserialize(SerializedBytes<TBC>(file.readBytes()))
        }
    }


    @Test
    fun disabledByEnv() {
        val file = File(URI("$localPath/${javaClass.simpleName}.${testName()}"))
        val factory = FactoryWithoutCorda2709(carpenterException)

        // Classes as they were serialised, simulates a message coming from a newer node where ToBeCarpented
        // has been added to ToBeEvolved
        //
        //class ToBeCarpented(val a: String, val b: Int)
        //class ToBeEvolved(val a: String, val tbc: ToBeCarpented)

        // Class as it exited then (from the perspective that this is replicating an older node receiving
        // a newer version of the class
        class ToBeEvolved(val a: String)

        // Uncomment to re-generate test files
        //file.writeBytes(SerializationOutput(factory).serialize(ToBeEvolved("evolve me", ToBeCarpented("Carpent me", 10))).bytes)

        assertFailsWith(NotSerializableException::class) {
            DeserializationInput(factory).deserialize(SerializedBytes<ToBeEvolved>(file.readBytes()))
        }
    }

}