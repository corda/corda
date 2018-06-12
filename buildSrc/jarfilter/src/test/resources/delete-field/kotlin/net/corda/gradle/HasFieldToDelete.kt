@file:JvmName("HasFieldToDelete")
@file:Suppress("UNUSED")
package net.corda.gradle

import net.corda.gradle.jarfilter.DeleteMe

class HasStringFieldToDelete(value: String) {
    @JvmField
    @field:DeleteMe
    val stringField: String = value
}

class HasLongFieldToDelete(value: Long) {
    @JvmField
    @field:DeleteMe
    val longField: Long = value
}

class HasIntFieldToDelete(value: Int) {
    @JvmField
    @field:DeleteMe
    val intField: Int = value
}
