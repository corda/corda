/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.explorer.formatters

import net.corda.core.contracts.Amount
import java.util.*

/**
 * A note on formatting: Currently we don't have any fancy locale/use-case-specific formatting of amounts. This is a
 * non-trivial problem that requires substantial work.
 * Libraries to evaluate: IBM ICU currency library, github.com/mfornos/humanize, JSR 354 ref. implementation
 */

object AmountFormatter {
    // TODO replace this once we settled on how we do formatting
    val boring = object : Formatter<Amount<Currency>> {
        override fun format(value: Amount<Currency>) = value.toString()
    }
}
