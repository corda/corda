@file:JvmName("HasVarPropertyForStub")
@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.StubMeOut
import net.corda.gradle.unwanted.HasUnwantedVar

class HasUnwantedGetForStub(@get:StubMeOut override var unwantedVar: String) : HasUnwantedVar

class HasUnwantedSetForStub(@set:StubMeOut override var unwantedVar: String) : HasUnwantedVar