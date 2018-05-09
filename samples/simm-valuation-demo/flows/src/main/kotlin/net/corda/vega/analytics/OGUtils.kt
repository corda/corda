/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.vega.analytics

fun compareIMTriples(a: InitialMarginTriple, b: InitialMarginTriple): Boolean {
    return withinTolerance(a.first, b.first) && withinTolerance(a.second, b.second) && withinTolerance(a.third, b.third)
}

// TODO: Do this correctly
private fun <A, B> withinTolerance(first: A, second: B): Boolean {
    if (first is Double && second is Double) {
        val q = Math.abs(first - second)
        if (q < 0.001) return true
    }
    return false
}
