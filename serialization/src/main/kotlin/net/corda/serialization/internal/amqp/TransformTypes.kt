package net.corda.serialization.internal.amqp

import net.corda.core.KeepForDJVM
import net.corda.core.serialization.CordaSerializationTransformEnumDefault
import net.corda.core.serialization.CordaSerializationTransformEnumDefaults
import net.corda.core.serialization.CordaSerializationTransformRename
import net.corda.serialization.internal.NotSerializableWithReasonException
import org.apache.qpid.proton.amqp.DescribedType
import org.apache.qpid.proton.codec.DescribedTypeConstructor

/**
 * Enumerated type that represents each transform that can be applied to a class. Used as the key type in
 * the [TransformsSchema] map for each class.
 *
 * @property build should be a function that takes a transform [Annotation] (currently one of
 * [CordaSerializationTransformRename] or [CordaSerializationTransformEnumDefaults])
 * and constructs an instance of the corresponding [Transform] type
 *
 * DO NOT REORDER THE CONSTANTS!!! Please append any new entries to the end
 */
// TODO:  it would be awesome to auto build this list by scanning for transform annotations themselves
// TODO: annotated with some annotation
@KeepForDJVM
enum class TransformTypes(val build: (Annotation) -> Transform) : DescribedType {
    /**
     * Placeholder entry for future transforms where a node receives a transform we've subsequently
     * added and thus the de-serialising node doesn't know about that transform.
     */
    Unknown({ UnknownTransform() }) {
        override fun getDescriptor(): Any = DESCRIPTOR
        override fun getDescribed(): Any = ordinal
    },
    EnumDefault({ a -> EnumDefaultSchemaTransform((a as CordaSerializationTransformEnumDefault).old, a.new) }) {
        override fun getDescriptor(): Any = DESCRIPTOR
        override fun getDescribed(): Any = ordinal
    },
    Rename({ a -> RenameSchemaTransform((a as CordaSerializationTransformRename).from, a.to) }) {
        override fun getDescriptor(): Any = DESCRIPTOR
        override fun getDescribed(): Any = ordinal
    }
    // Transform used to test the unknown handler, leave this at as the final constant, uncomment
    // when regenerating test cases - if Java had a pre-processor this would be much neater
    //
    //,UnknownTest({ a -> UnknownTestTransform((a as UnknownTransformAnnotation).a, a.b, a.c)}) {
    //    override fun getDescriptor(): Any = DESCRIPTOR
    //    override fun getDescribed(): Any = ordinal
    //}
    ;

    companion object : DescribedTypeConstructor<TransformTypes> {
        val DESCRIPTOR = AMQPDescriptorRegistry.TRANSFORM_ELEMENT_KEY.amqpDescriptor

        /**
         * Used to construct an instance of the object from the serialised bytes
         *
         * @param obj the serialised byte object from the AMQP serialised stream
         */
        override fun newInstance(obj: Any?): TransformTypes {
            val describedType = obj as DescribedType

            if (describedType.descriptor != DESCRIPTOR) {
                throw NotSerializableWithReasonException("Unexpected descriptor ${describedType.descriptor}.")
            }

            return try {
                values()[describedType.described as Int]
            } catch (e: IndexOutOfBoundsException) {
                values()[0]
            }
        }

        override fun getTypeClass(): Class<*> = TransformTypes::class.java
    }
}
