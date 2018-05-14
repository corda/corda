@file:Suppress("UNUSED")
package net.corda.example

class HasDeprecatedFunctions @Deprecated("Dont use this anymore!") constructor () {
    @Deprecated("Dont use this anymore!")
    fun doSomething() = "123"
}