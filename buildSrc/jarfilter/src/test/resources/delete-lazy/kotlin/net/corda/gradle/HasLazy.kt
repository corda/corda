@file:JvmName("HasLazy")
@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe
import net.corda.gradle.unwanted.HasUnwantedVal

class HasLazyVal(private val message: String) : HasUnwantedVal {
    @DeleteMe
    override val unwantedVal: String by lazy {
        message
    }
}
