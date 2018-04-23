/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.hsm.utils

import CryptoServerAPI.CryptoServerException
import java.util.*

/**
 * CryptoServer error translator object.
 * It holds mapping between CryptoServer error code to its human readable description.
 */
// TODO this code (incl. the hsm_errors file) is duplicated with the SGX module
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
    return if (crypto != null) {
        Exception("(CryptoServer) ${HsmErrors.errors[crypto.ErrorCode]}", exception)
    } else {
        exception
    }
}