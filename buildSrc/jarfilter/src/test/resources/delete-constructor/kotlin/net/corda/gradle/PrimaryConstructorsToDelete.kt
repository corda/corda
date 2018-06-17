@file:JvmName("PrimaryConstructorsToDelete")
@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe
import net.corda.gradle.unwanted.HasInt
import net.corda.gradle.unwanted.HasLong
import net.corda.gradle.unwanted.HasString

class PrimaryIntConstructorToDelete @DeleteMe constructor(private val value: Int) : HasInt {
    override fun intData() = value
}

class PrimaryLongConstructorToDelete @DeleteMe constructor(private val value: Long) : HasLong {
    override fun longData() = value
}

class PrimaryStringConstructorToDelete @DeleteMe constructor(private val value: String) : HasString {
    override fun stringData() = value
}

