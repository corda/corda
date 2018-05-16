@file:Suppress("UNUSED")
package net.corda.example

class HasJvmStaticFunction {
    companion object {
        @JvmStatic
        fun doThing(message: String) = println(message)
    }
}
