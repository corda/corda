@file:JvmName("PackageWithDefaultParameters")
@file:Suppress("UNUSED")
package net.corda.gradle.jarfilter.template

import net.corda.gradle.jarfilter.DEFAULT_MESSAGE

fun hasDefaultParameters(intData: Int=0, message: String=DEFAULT_MESSAGE): String = "$message: intData=$intData"

fun hasMandatoryParameters(longData: Long=0, message: String=DEFAULT_MESSAGE): String = "$message: longData=$longData"
