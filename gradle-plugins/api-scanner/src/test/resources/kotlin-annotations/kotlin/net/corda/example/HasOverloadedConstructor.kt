@file:Suppress("UNUSED")
package net.corda.example

class HasOverloadedConstructor @JvmOverloads constructor (
    val notNullable: String = "defaultName",
    val nullable: String? = null,
    val number: Int = 0
)