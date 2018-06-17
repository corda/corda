@file:JvmName("DeletePackageWithStubbed")
@file:Suppress("UNUSED")
@file:DeleteMe
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe
import net.corda.gradle.jarfilter.StubMeOut

fun bracket(str: String): String = "[$str]"

@StubMeOut
fun stubbed(str: String): String = bracket(str)
