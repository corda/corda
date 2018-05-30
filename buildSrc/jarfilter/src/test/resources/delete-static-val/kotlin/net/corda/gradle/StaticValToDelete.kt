@file:JvmName("StaticValToDelete")
@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe

@DeleteMe
val stringVal: String = "<default-value>"

@DeleteMe
val longVal: Long = 123456789L

@DeleteMe
val intVal: Int = 123456
