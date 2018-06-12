@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe
import net.corda.gradle.unwanted.HasAll

class HasConstructorToDelete(private val message: String, private val data: Long) : HasAll {
    @DeleteMe constructor(message: String) : this(message, 0)
    @DeleteMe constructor(data: Long) : this("<nothing>", data)
    constructor(data: Int) : this("<nothing>", data.toLong())

    override fun stringData(): String = message
    override fun longData(): Long = data
    override fun intData(): Int = data.toInt()
}
