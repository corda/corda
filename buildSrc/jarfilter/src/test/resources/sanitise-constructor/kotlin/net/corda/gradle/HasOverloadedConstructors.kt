@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe
import net.corda.gradle.unwanted.*

private const val DEFAULT_MESSAGE = "<default-value>"

class HasOverloadedStringConstructor @JvmOverloads @DeleteMe constructor(private val message: String = DEFAULT_MESSAGE) : HasString {
    override fun stringData(): String = message
}

class HasOverloadedLongConstructor @JvmOverloads @DeleteMe constructor(private val data: Long = 0) : HasLong {
    override fun longData(): Long = data
}

class HasOverloadedIntConstructor @JvmOverloads @DeleteMe constructor(private val data: Int = 0) : HasInt {
    override fun intData(): Int = data
}

class HasMultipleStringConstructors(private val message: String) : HasString {
    @DeleteMe constructor() : this(DEFAULT_MESSAGE)
    override fun stringData(): String = message
}

class HasMultipleLongConstructors(private val data: Long) : HasLong {
    @DeleteMe constructor() : this(0)
    override fun longData(): Long = data
}

class HasMultipleIntConstructors(private val data: Int) : HasInt {
    @DeleteMe constructor() : this(0)
    override fun intData(): Int = data
}
