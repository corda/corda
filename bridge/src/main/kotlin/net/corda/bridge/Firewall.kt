@file:JvmName("Firewall")

package net.corda.bridge

import net.corda.bridge.internal.FirewallStartup
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(if (FirewallStartup(args).run()) 0 else 1)
}
