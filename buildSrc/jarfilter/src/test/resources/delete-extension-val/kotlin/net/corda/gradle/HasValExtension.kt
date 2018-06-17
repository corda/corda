@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe
import net.corda.gradle.unwanted.HasUnwantedVal

class HasValExtension(override val unwantedVal: String) : HasUnwantedVal {
    @DeleteMe
    val List<String>.unwantedVal: String get() = this[0]
}
