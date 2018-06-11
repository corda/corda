@file:JvmName("PrimaryConstructorsToStub")
@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.StubMeOut
import net.corda.gradle.unwanted.HasInt
import net.corda.gradle.unwanted.HasLong
import net.corda.gradle.unwanted.HasString

class PrimaryIntConstructorToStub @StubMeOut constructor(private val value: Int) : HasInt {
    override fun intData() = value
}

class PrimaryLongConstructorToStub @StubMeOut constructor(private val value: Long) : HasLong {
    override fun longData() = value
}

class PrimaryStringConstructorToStub @StubMeOut constructor(private val value: String) : HasString {
    override fun stringData() = value
}

