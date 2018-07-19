package net.corda.serialization.internal

import java.io.IOException
import java.io.NotSerializableException

class NotSerializableDetailedException(classname: String?, val reason: String) : NotSerializableException(classname) {
    override fun toString(): String {
        return "Unable to serialize/deserialize $message: $reason"
    }
}

// This exception is thrown when serialization isn't possible but at the point the exception
// is thrown the classname isn't known. It's caught and rethrown as a [NotSerializableDetailedException]
class NotSerializableWithReasonException(message: String?): IOException(message)