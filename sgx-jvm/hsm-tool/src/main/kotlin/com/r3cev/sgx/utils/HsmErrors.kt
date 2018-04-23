/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3cev.sgx.utils

import java.util.*

// TODO this code (incl. the hsm_errors file) is duplicated with the Network-Management module
object HsmErrors {
    val errors: Map<Int, String> by lazy(HsmErrors::load)

    fun load(): Map<Int, String> {
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
