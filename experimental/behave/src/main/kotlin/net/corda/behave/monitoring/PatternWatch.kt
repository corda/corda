/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.monitoring

class PatternWatch(
        pattern: String,
        ignoreCase: Boolean = false
) : Watch() {

    private val regularExpression = if (ignoreCase) {
        Regex("^.*$pattern.*$", RegexOption.IGNORE_CASE)
    } else {
        Regex("^.*$pattern.*$")
    }

    override fun match(data: String) = regularExpression.matches(data.trim())

    companion object {

        val EMPTY = PatternWatch("")

    }

}