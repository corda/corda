@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe
import net.corda.gradle.jarfilter.StubMeOut

interface InterfaceFunctions {
    @DeleteMe
    fun toDelete(value: Long): Long

    @StubMeOut
    fun toStubOut(value: Long): Long
}
