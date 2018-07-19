@file:JvmName("HasOverloadedConstructorsToStub")
@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.StubMeOut
import net.corda.gradle.unwanted.*

private const val DEFAULT_MESSAGE = "<default-value>"

class HasOverloadedStringConstructorToStub @JvmOverloads @StubMeOut constructor(private val message: String = DEFAULT_MESSAGE) : HasString {
    override fun stringData(): String = message
}

class HasOverloadedLongConstructorToStub @JvmOverloads @StubMeOut constructor(private val data: Long = 0) : HasLong {
    override fun longData(): Long = data
}

class HasOverloadedIntConstructorToStub @JvmOverloads @StubMeOut constructor(private val data: Int = 0) : HasInt {
    override fun intData(): Int = data
}

/**
 * This case is complex because:
 *  - The primary constructor has two parameters.
 *  - The first constructor parameter has a default value.
 *  - The second constructor parameter is mandatory.
 */
class HasOverloadedComplexConstructorToStub @JvmOverloads @StubMeOut constructor(private val data: Int = 0, private val message: String)
    : HasInt, HasString
{
    override fun stringData(): String = message
    override fun intData(): Int = data
}

class HasMultipleStringConstructorsToStub(private val message: String) : HasString {
    @StubMeOut constructor() : this(DEFAULT_MESSAGE)
    override fun stringData(): String = message
}

class HasMultipleLongConstructorsToStub(private val data: Long) : HasLong {
    @StubMeOut constructor() : this(0)
    override fun longData(): Long = data
}

class HasMultipleIntConstructorsToStub(private val data: Int) : HasInt {
    @StubMeOut constructor() : this(0)
    override fun intData(): Int = data
}
