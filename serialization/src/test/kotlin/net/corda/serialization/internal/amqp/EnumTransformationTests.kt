package net.corda.serialization.internal.amqp

import net.corda.core.serialization.CordaSerializationTransformEnumDefault
import net.corda.core.serialization.CordaSerializationTransformEnumDefaults
import net.corda.core.serialization.CordaSerializationTransformRename
import net.corda.core.serialization.CordaSerializationTransformRenames
import net.corda.serialization.internal.model.EnumTransforms
import net.corda.serialization.internal.model.InvalidEnumTransformsException
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

class EnumTransformationTests {

    @CordaSerializationTransformEnumDefaults(
            CordaSerializationTransformEnumDefault(old = "C", new = "D"),
            CordaSerializationTransformEnumDefault(old = "D", new = "E")
    )
    @CordaSerializationTransformRenames(
        CordaSerializationTransformRename(to = "BOB", from = "FRED"),
        CordaSerializationTransformRename(to = "FRED", from = "E")
    )
    enum class MultiOperations { A, B, C, D, BOB }

    // See https://r3-cev.atlassian.net/browse/CORDA-1497
    @Test(timeout=300_000)
	fun defaultAndRename() {
        val transforms = EnumTransforms.build(
                TransformsAnnotationProcessor.getTransformsSchema(MultiOperations::class.java),
                MultiOperations::class.java.constants)

        assertEquals(mapOf("BOB" to "FRED", "FRED" to "E"), transforms.renames)
        assertEquals(mapOf("D" to "C", "E" to "D"), transforms.defaults)
    }

    @CordaSerializationTransformRenames(
            CordaSerializationTransformRename(from = "A", to = "C"),
            CordaSerializationTransformRename(from = "B", to = "D"),
            CordaSerializationTransformRename(from = "C", to = "E"),
            CordaSerializationTransformRename(from = "E", to = "B"),
            CordaSerializationTransformRename(from = "D", to = "A")
    )
    enum class RenameCycle { A, B, C, D, E}

    @Test(timeout=300_000)
	fun cycleDetection() {
        assertFailsWith<InvalidEnumTransformsException> {
            EnumTransforms.build(
                    TransformsAnnotationProcessor.getTransformsSchema(RenameCycle::class.java),
                    RenameCycle::class.java.constants)
        }
    }

    @CordaSerializationTransformRenames(
            CordaSerializationTransformRename(from = "P", to = "Q"),
            CordaSerializationTransformRename(from = "Q", to = "R")
    )
    enum class DanglingRenames { A, B, C }

    @Test(timeout=300_000)
	fun renameCycleDoesNotTerminateInConstant() {
        assertFailsWith<InvalidEnumTransformsException> {
            EnumTransforms.build(
                    TransformsAnnotationProcessor.getTransformsSchema(DanglingRenames::class.java),
                    DanglingRenames::class.java.constants)
        }
    }

    @CordaSerializationTransformRenames(
            CordaSerializationTransformRename(from = "P", to = "Q"),
            CordaSerializationTransformRename(from = "Q", to = "R")
    )
    enum class RenamesExisting { Q, R, S }

    @Test(timeout=300_000)
	fun renamesRenameExistingConstant() {
        assertFailsWith<InvalidEnumTransformsException> {
            EnumTransforms.build(
                    TransformsAnnotationProcessor.getTransformsSchema(RenamesExisting::class.java),
                    RenamesExisting::class.java.constants)
        }
    }

    private val Class<*>.constants: Map<String, Int> get() =
        enumConstants.asSequence().mapIndexed { index, constant -> constant.toString() to index }.toMap()
}