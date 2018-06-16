@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe
import net.corda.gradle.jarfilter.StubMeOut

abstract class AbstractFunctions {
    @DeleteMe
    abstract fun toDelete(value: Long): Long

    @StubMeOut
    abstract fun toStubOut(value: Long): Long
}
