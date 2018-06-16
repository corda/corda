@file:JvmName("StaticFunctionsToDelete")
@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe

@DeleteMe
fun unwantedStringToDelete(value: String): String = value

@DeleteMe
fun unwantedIntToDelete(value: Int): Int = value

@DeleteMe
fun unwantedLongToDelete(value: Long): Long = value
