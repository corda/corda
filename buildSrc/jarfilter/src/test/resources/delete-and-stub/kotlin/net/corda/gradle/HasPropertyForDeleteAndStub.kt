@file:JvmName("HasPropertyForDeleteAndStub")
@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe
import net.corda.gradle.jarfilter.StubMeOut
import net.corda.gradle.unwanted.*

class HasVarPropertyForDeleteAndStub(value: Long) : HasLongVar {
    @DeleteMe
    @get:StubMeOut
    @set:StubMeOut
    override var longVar: Long = value
}

class HasValPropertyForDeleteAndStub(str: String) : HasStringVal {
    @DeleteMe
    @get:StubMeOut
    override val stringVal: String = str
}

