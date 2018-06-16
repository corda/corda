@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.RemoveMe
import net.corda.gradle.unwanted.HasUnwantedFun
import net.corda.gradle.unwanted.HasUnwantedVal
import net.corda.gradle.unwanted.HasUnwantedVar

@RemoveMe
class HasUnwantedAnnotations @RemoveMe constructor(
   longValue: Long, message: String
) : HasUnwantedVar, HasUnwantedVal, HasUnwantedFun {
    @RemoveMe
    constructor() : this(999L, "<default-value>")

    @field:RemoveMe
    @JvmField
    val longField: Long = longValue

    @get:RemoveMe
    @property:RemoveMe
    override val unwantedVal: String = message

    @get:RemoveMe
    @set:RemoveMe
    @property:RemoveMe
    override var unwantedVar: String = message

    @RemoveMe
    override fun unwantedFun(str: String): String {
        return "[$str]"
    }
}
