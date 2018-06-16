@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.StubMeOut
import net.corda.gradle.unwanted.HasUnwantedFun
import javax.annotation.Resource

class HasFunctionToStub : HasUnwantedFun {
    @StubMeOut
    @Resource
    override fun unwantedFun(@Parameter str: String): String {
        return str
    }
}
