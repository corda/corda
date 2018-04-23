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

import rx.Observable

class PatternWatch(
        observable: Observable<String>,
        pattern: String,
        ignoreCase: Boolean = false
) : AbstractWatch<String>(observable, false) {

    private val regularExpression: Regex = if (ignoreCase) {
        Regex("^.*$pattern.*$", RegexOption.IGNORE_CASE)
    } else {
        Regex("^.*$pattern.*$")
    }

    init {
        run()
    }

    override fun match(data: String): Boolean {
        return regularExpression.matches(data.trim())
    }
}