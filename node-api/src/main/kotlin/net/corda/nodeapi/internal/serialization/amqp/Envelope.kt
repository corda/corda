/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.serialization.amqp

import org.apache.qpid.proton.amqp.DescribedType
import org.apache.qpid.proton.codec.Data
import org.apache.qpid.proton.codec.DescribedTypeConstructor

import java.io.NotSerializableException

/**
 * This class wraps all serialized data, so that the schema can be carried along with it.  We will provide various
 * internal utilities to decompose and recompose with/without schema etc so that e.g. we can store objects with a
 * (relationally) normalised out schema to avoid excessive duplication.
 */
// TODO: make the schema parsing lazy since mostly schemas will have been seen before and we only need it if we
// TODO: don't recognise a type descriptor.
data class Envelope(val obj: Any?, val schema: Schema, val transformsSchema: TransformsSchema) : DescribedType {
    companion object : DescribedTypeConstructor<Envelope> {
        val DESCRIPTOR = AMQPDescriptorRegistry.ENVELOPE.amqpDescriptor
        val DESCRIPTOR_OBJECT = Descriptor(null, DESCRIPTOR)

        // described list should either be two or three elements long
        private const val ENVELOPE_WITHOUT_TRANSFORMS = 2
        private const val ENVELOPE_WITH_TRANSFORMS = 3

        private const val BLOB_IDX = 0
        private const val SCHEMA_IDX = 1
        private const val TRANSFORMS_SCHEMA_IDX = 2

        fun get(data: Data): Envelope {
            val describedType = data.`object` as DescribedType
            if (describedType.descriptor != DESCRIPTOR) {
                throw NotSerializableException("Unexpected descriptor ${describedType.descriptor}, should be $DESCRIPTOR.")
            }
            val list = describedType.described as List<*>

            // We need to cope with objects serialised without the transforms header element in the
            // envelope
            val transformSchema: Any? = when (list.size) {
                ENVELOPE_WITHOUT_TRANSFORMS -> null
                ENVELOPE_WITH_TRANSFORMS -> list[TRANSFORMS_SCHEMA_IDX]
                else -> throw NotSerializableException("Malformed list, bad length of ${list.size} (should be 2 or 3)")
            }

            return newInstance(listOf(list[BLOB_IDX], Schema.get(list[SCHEMA_IDX]!!),
                    TransformsSchema.newInstance(transformSchema)))
        }

        // This separation of functions is needed as this will be the entry point for the default
        // AMQP decoder if one is used (see the unit tests).
        override fun newInstance(described: Any?): Envelope {
            val list = described as? List<*> ?: throw IllegalStateException("Was expecting a list")

            // We need to cope with objects serialised without the transforms header element in the
            // envelope
            val transformSchema = when (list.size) {
                ENVELOPE_WITHOUT_TRANSFORMS -> TransformsSchema.newInstance(null)
                ENVELOPE_WITH_TRANSFORMS -> list[TRANSFORMS_SCHEMA_IDX] as TransformsSchema
                else -> throw NotSerializableException("Malformed list, bad length of ${list.size} (should be 2 or 3)")
            }

            return Envelope(list[BLOB_IDX], list[SCHEMA_IDX] as Schema, transformSchema)
        }

        override fun getTypeClass(): Class<*> = Envelope::class.java
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(obj, schema, transformsSchema)
}
