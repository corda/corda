@file:JvmName("StaticVarToDelete")
@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe

@DeleteMe
var stringVar: String = "<default-value>"

@DeleteMe
var longVar: Long = 123456789L

@DeleteMe
var intVar: Int = 123456

@DeleteMe
var <T: Any> T.memberVar: T
    get() = this
    set(value) { }
