@file:JvmName("HasVarPropertyForDelete")
@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe
import net.corda.gradle.unwanted.HasUnwantedVar

class HasUnwantedVarPropertyForDelete(@DeleteMe override var unwantedVar: String) : HasUnwantedVar

class HasUnwantedGetForDelete(@get:DeleteMe override var unwantedVar: String) : HasUnwantedVar

class HasUnwantedSetForDelete(@set:DeleteMe override var unwantedVar: String) : HasUnwantedVar

class HasVarJvmFieldForDelete(@DeleteMe @JvmField var unwantedVar: String)
