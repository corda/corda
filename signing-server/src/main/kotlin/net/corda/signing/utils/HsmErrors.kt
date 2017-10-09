package net.corda.signing.utils

import CryptoServerAPI.CryptoServerException
import java.util.HashMap

/**
 * CryptoServer error translator object.
 * It holds mapping between CryptoServer error code to its human readable description.
 */
object HsmErrors {
    val errors: Map<Int, String> by lazy(HsmErrors::load)

    private fun load(): Map<Int, String> {
        val errors = HashMap<Int, String>()
        val hsmErrorsStream = HsmErrors::class.java.getResourceAsStream("hsm_errors")
        hsmErrorsStream.bufferedReader().lines().reduce(null) { previous, current ->
            if (previous == null) {
                current
            } else {
                errors[java.lang.Long.decode(previous).toInt()] = current
                null
            }
        }
        return errors
    }
}

/**
 * Utility function for providing human readable error description in case of the [CryptoServerException] being thrown.
 * If the exception is of different type the method does nothing.
 */
fun mapCryptoServerException(exception: Exception): Exception {
    // Try to decode the error code
    val crypto = exception as? CryptoServerException ?: exception.cause as? CryptoServerException
    if (crypto != null) {
        return Exception("(CryptoServer) ${HsmErrors.errors[crypto.ErrorCode]}", exception)
    } else {
        return exception
    }
}