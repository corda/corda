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