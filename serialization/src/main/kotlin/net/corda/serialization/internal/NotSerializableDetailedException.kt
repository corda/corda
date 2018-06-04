package net.corda.serialization.internal

import java.io.NotSerializableException

class NotSerializableDetailedException(classname: String?, val reason: String) : NotSerializableException(classname) {
    override fun toString(): String {
        return "Unable to serialize/deserialize $message: $reason"
    }
}