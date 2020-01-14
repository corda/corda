package net.corda.core.serialization.internal

import net.corda.core.KeepForDJVM
import java.io.NotSerializableException

/**
 * Thrown by the serialization framework, probably indicating that a custom serializer
 * needs to be included in a transaction.
 */
@KeepForDJVM
open class MissingSerializerException private constructor(
    message: String,
    val typeDescriptor: String?,
    val typeNames: List<String>
) : NotSerializableException(message) {
    constructor(message: String, typeDescriptor: String) : this(message, typeDescriptor, emptyList())
    constructor(message: String, typeNames: List<String>) : this(message, null, typeNames)

    /**
     * This constructor allows instances of this exception to escape the DJVM sandbox.
     */
    @Suppress("unused")
    private constructor(message: String) : this(message, null, emptyList())
}
