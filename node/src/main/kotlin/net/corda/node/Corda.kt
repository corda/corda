// This class is used by the smoke tests as a check that the node module isn't on their classpath
@file:JvmName("Corda")

package net.corda.node

import net.corda.core.crypto.Crypto
import net.corda.node.internal.NodeStartup
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // Register all cryptography [Provider]s first thing on boot.
    Crypto.registerProviders()
    // Pass the arguments to the Node factory. In the Enterprise edition, this line is modified to point to a subclass.
    // It will exit the process in case of startup failure and is not intended to be used by embedders. If you want
    // to embed Node in your own container, instantiate it directly and set up the configuration objects yourself.
    exitProcess(if (NodeStartup(args).run()) 0 else 1)
}