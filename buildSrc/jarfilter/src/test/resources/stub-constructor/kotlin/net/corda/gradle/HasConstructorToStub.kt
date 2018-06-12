@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.StubMeOut
import net.corda.gradle.unwanted.HasAll

class HasConstructorToStub(private val message: String, private val data: Long) : HasAll {
    @StubMeOut constructor(message: String) : this(message, 0)
    @StubMeOut constructor(data: Long) : this("<nothing>", data)
    constructor(data: Int) : this("<nothing>", data.toLong())

    override fun stringData(): String = message
    override fun longData(): Long = data
    override fun intData(): Int = data.toInt()
}
