@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.StubMeOut
import net.corda.gradle.unwanted.HasUnwantedVal

class HasValPropertyForStub(@get:StubMeOut override val unwantedVal: String) : HasUnwantedVal
