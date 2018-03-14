@file:JvmName("Bridge")

package net.corda.bridge

import net.corda.bridge.internal.BridgeStartup
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(if (BridgeStartup(args).run()) 0 else 1)
}
