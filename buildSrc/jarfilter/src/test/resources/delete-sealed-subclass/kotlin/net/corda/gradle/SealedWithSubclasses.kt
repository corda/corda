@file:JvmName("SealedWithSubclasses")
@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe

sealed class SealedBaseClass

@DeleteMe
class UnwantedSubclass : SealedBaseClass()

class WantedSubclass : SealedBaseClass()
