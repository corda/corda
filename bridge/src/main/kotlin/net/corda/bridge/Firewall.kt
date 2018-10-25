@file:JvmName("Firewall")

package net.corda.bridge

import net.corda.bridge.internal.FirewallStartup
import net.corda.cliutils.start
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    FirewallStartup().start(args)
}
