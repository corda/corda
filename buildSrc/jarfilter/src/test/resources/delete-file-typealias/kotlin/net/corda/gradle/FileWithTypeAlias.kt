@file:JvmName("FileWithTypeAlias")
@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe

typealias FileWantedType = Long

@DeleteMe
typealias FileUnwantedType = (String) -> Boolean

val Any.FileUnwantedType: String get() = "<value>"
