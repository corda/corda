@file:JvmName("StaticFunctionsToStub")
@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.StubMeOut

@StubMeOut
fun unwantedStringToStub(value: String): String = value

@StubMeOut
fun unwantedIntToStub(value: Int): Int = value

@StubMeOut
fun unwantedLongToStub(value: Long): Long = value

private var seed: Int = 0
val staticSeed: Int get() = seed

@StubMeOut
fun unwantedVoidToStub() {
    ++seed
}
