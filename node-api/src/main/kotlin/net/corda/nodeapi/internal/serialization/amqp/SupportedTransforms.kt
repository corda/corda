package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.serialization.CordaSerializationTransformEnumDefaults
import net.corda.core.serialization.CordaSerializationTransformEnumDefault
import net.corda.core.serialization.CordaSerializationTransformRenames
import net.corda.core.serialization.CordaSerializationTransformRename

/**
 * Utility class that defines an instance of a transform we support
 *
 * @property type The transform annotation
 * @property enum Maps the annotaiton onto a transform type, we expect there are multiple annotations that
 * would map to a single transform type
 * @property f Anonymous function that should return a list of Annotations encapsualted by the parent annotation
 * that reference the transform. Notionally this allows the code that extracts transforms to work on single instances
 * of a transform or a meta list of them
 */
data class SupportedTransform(
        val type: Class<out Annotation>,
        val enum: TransformTypes,
        val getAnnotations: (Annotation) -> List<Annotation>)

/**
 * Extract from an annotated class the list of annotations that refer to a particular
 * transformation type when that class has multiple transforms wrapped in an
 * outer annotation
 */
@Suppress("UNCHECKED_CAST")
private val wrapperExtract = { x: Annotation ->
    (x::class.java.getDeclaredMethod("value").invoke(x) as Array<Annotation>).toList()
}

/**
 * Extract from an annotated class the list of annotations that refer to a particular
 * transformation type when that class has a single decorator applied
 */
private val singleExtract = { x: Annotation -> listOf(x) }

/**
 * Utility list of all transforms we support that simplifies our generator
 *
 * NOTE: We have to support single instances of the transform annotations as well as the wrapping annotation
 * when many instances are repeated
 */
val supportedTransforms = listOf(
        SupportedTransform(
                CordaSerializationTransformEnumDefaults::class.java,
                TransformTypes.EnumDefault,
                wrapperExtract
        ),
        SupportedTransform(
                CordaSerializationTransformEnumDefault::class.java,
                TransformTypes.EnumDefault,
                singleExtract
        ),
        SupportedTransform(
                CordaSerializationTransformRenames::class.java,
                TransformTypes.Rename,
                wrapperExtract
        ),
        SupportedTransform(
                CordaSerializationTransformRename::class.java,
                TransformTypes.Rename,
                singleExtract
        )
)
