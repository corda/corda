@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe
import net.corda.gradle.unwanted.HasUnwantedVal

class HasValPropertyForDelete(@DeleteMe override val unwantedVal: String) : HasUnwantedVal

class HasValGetterForDelete(@get:DeleteMe override val unwantedVal: String): HasUnwantedVal

class HasValJvmFieldForDelete(@DeleteMe @JvmField val unwantedVal: String)