@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe
import net.corda.gradle.unwanted.HasInt

class HasInnerLambda(private val bytes: ByteArray) : HasInt {
    @DeleteMe
    constructor(size: Int) : this(ZeroArray { size }.bytes)

    override fun intData() = bytes.size
}

/**
 * Do NOT inline this lambda!
 */
class ZeroArray(initialSize: () -> Int) {
    val bytes: ByteArray = ByteArray(initialSize()) { 0 }
}
