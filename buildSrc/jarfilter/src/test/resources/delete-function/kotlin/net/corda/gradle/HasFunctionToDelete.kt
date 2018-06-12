@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe
import net.corda.gradle.unwanted.HasUnwantedFun

class HasFunctionToDelete : HasUnwantedFun {
    @DeleteMe
    override fun unwantedFun(str: String): String {
        return str
    }
}
