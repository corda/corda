package net.corda.serialization.internal.amqp.schema

import net.corda.core.KeepForDJVM

@KeepForDJVM
data class SerializationSchemas(val schema: Schema, val transforms: TransformsSchema)