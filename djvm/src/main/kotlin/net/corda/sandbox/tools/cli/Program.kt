@file:JvmName("Program")

package net.corda.sandbox.tools.cli

import kotlin.system.exitProcess

/**
 * The entry point of the deterministic sandbox tool.
 */
fun main(args: Array<String>) {
    exitProcess(Commands().run(args))
}
