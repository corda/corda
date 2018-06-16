@file:JvmName("HasDeletedInsideStubbed")
@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe
import net.corda.gradle.jarfilter.StubMeOut
import net.corda.gradle.unwanted.HasString
import net.corda.gradle.unwanted.HasUnwantedFun
import net.corda.gradle.unwanted.HasUnwantedVal
import net.corda.gradle.unwanted.HasUnwantedVar

class DeletedFunctionInsideStubbed(private val data: String): HasString, HasUnwantedFun {
    @DeleteMe
    override fun unwantedFun(str: String): String = str

    @StubMeOut
    override fun stringData(): String = unwantedFun(data)
}

class DeletedValInsideStubbed(@DeleteMe override val unwantedVal: String): HasString, HasUnwantedVal {
    @StubMeOut
    override fun stringData(): String = unwantedVal
}

class DeletedVarInsideStubbed(@DeleteMe override var unwantedVar: String) : HasString, HasUnwantedVar {
    @StubMeOut
    override fun stringData(): String = unwantedVar
}