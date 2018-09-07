// This class is used by the smoke tests as a check that the node module isn't on their classpath
@file:JvmName("Corda")

package net.corda.node

import net.corda.cliutils.start
import net.corda.node.internal.NodeStartup

fun main(args: Array<String>) {
    // Pass the arguments to the Node factory. In the Enterprise edition, this line is modified to point to a subclass.
    // It will exit the process in case of startup failure and is not intended to be used by embedders. If you want
    // to embed Node in your own container, instantiate it directly and set up the configuration objects yourself.
    NodeStartup().start(args)
}