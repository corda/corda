package net.corda.serialization.internal

import java.io.NotSerializableException

@Suppress("FunctionNaming")
fun NotSerializableException(message: String?, cause: Throwable?): NotSerializableException {
    return NotSerializableException(message).apply { initCause(cause) }
}
