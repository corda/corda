@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe

sealed class SealedClass {
    class Wanted
    @DeleteMe class Unwanted
}
