@file:JvmName("PackageWithDefaultParameters")
@file:Suppress("UNUSED")
package net.corda.gradle.jarfilter

/**
 * Example package functions, one with default parameter values and one without.
 * We will rewrite this class's metadata so that it expects both functions to
 * have default parameter values, and then ask the [MetaFixerTask] to fix it.
 */
fun hasDefaultParameters(intData: Int=0, message: String=DEFAULT_MESSAGE): String = "$message: intData=$intData"

fun hasMandatoryParameters(longData: Long, message: String): String = "$message: longData=$longData"
