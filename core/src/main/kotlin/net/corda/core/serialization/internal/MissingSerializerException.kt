package net.corda.core.serialization.internal

import net.corda.core.KeepForDJVM
import java.io.NotSerializableException
import java.net.URL

/**
 * Thrown by the serialization framework, probably indicating that a custom serializer
 * needs to be included in a transaction.
 */
@KeepForDJVM
open class MissingSerializerException(
    message: String,
    val typeDescriptor: String,
    val serializerLocation: URL? = null
) : NotSerializableException(message)
