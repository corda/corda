/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi

import java.time.Duration

/**
 * Ideas borrowed from "io.kotlintest" with some improvements made
 * This is meant for use from Kotlin code use only mainly due to it's inline/reified nature
 */
inline fun <reified E : Throwable, R> eventually(duration: Duration, f: () -> R): R {
    val end = System.nanoTime() + duration.toNanos()
    var times = 0
    while (System.nanoTime() < end) {
        try {
            return f()
        } catch (e: Throwable) {
            when (e) {
                is E -> {
                }// ignore and continue
                else -> throw e // unexpected exception type - rethrow
            }
        }
        times++
    }
    throw AssertionError("Test failed after $duration; attempted $times times")
}