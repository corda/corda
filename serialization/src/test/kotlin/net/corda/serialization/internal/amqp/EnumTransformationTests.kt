package net.corda.serialization.internal.amqp

import net.corda.core.serialization.CordaSerializationTransformEnumDefault
import net.corda.core.serialization.CordaSerializationTransformEnumDefaults
import net.corda.core.serialization.CordaSerializationTransformRename
import org.junit.Test

class EnumTransformationTests {

    @CordaSerializationTransformEnumDefaults(
            CordaSerializationTransformEnumDefault(old = "C", new = "D"),
            CordaSerializationTransformEnumDefault(old = "D", new = "E")
    )
    @CordaSerializationTransformRename(to = "BOB", from = "E")
    enum class MultiOperations { A, B, C, D, BOB }

    @Test
    fun doubleRename() {
        TransformsAnnotationProcessor.getTransformsSchema(MultiOperations::class.java)
    }
}