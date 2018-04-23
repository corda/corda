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

import org.apache.commons.lang.StringUtils.splitByCharacterTypeCamelCase

object FlowNameFormatter {
    val camelCase = object : Formatter<String> {
        override fun format(value: String): String {
            val flowName = value.split('.', '$').last()
            val split =  splitByCharacterTypeCamelCase(flowName).filter { it.compareTo("Flow", true) != 0 } .joinToString(" ")
            return split
        }
    }
}