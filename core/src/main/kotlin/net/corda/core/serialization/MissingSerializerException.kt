package net.corda.core.serialization

import net.corda.core.KeepForDJVM
import java.io.NotSerializableException

/**
 * Thrown by the serialization framework, probably indicating that a custom serializer
 * needs to be included in a transaction.
 */
@KeepForDJVM
@CordaSerializable
open class MissingSerializerException private constructor(
    message: String,
    val typeDescriptor: String?,
    val typeNames: List<String>
) : NotSerializableException(message) {
    constructor(message: String, typeDescriptor: String) : this(message, typeDescriptor, emptyList())
    constructor(message: String, typeNames: List<String>) : this(message, null, typeNames)
}
