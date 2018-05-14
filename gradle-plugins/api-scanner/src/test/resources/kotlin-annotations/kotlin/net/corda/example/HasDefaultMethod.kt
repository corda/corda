@file:Suppress("UNUSED")
package net.corda.example

interface HasDefaultMethod {
    // This annotation is an experimental feature of Kotlin 1.2.40
    // and IntelliJ is probably complaining about it. But it will
    // still build successfully in Gradle.
    @JvmDefault
    fun doSomething(message: String) {
        println(message)
    }
}
