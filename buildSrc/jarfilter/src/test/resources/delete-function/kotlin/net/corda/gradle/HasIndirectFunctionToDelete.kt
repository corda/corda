@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe
import net.corda.gradle.unwanted.HasString
import net.corda.gradle.unwanted.HasUnwantedFun

class HasIndirectFunctionToDelete(private val data: String) : HasUnwantedFun, HasString {
    @DeleteMe
    override fun unwantedFun(str: String): String = str

    override fun stringData() = unwantedFun(data)
}