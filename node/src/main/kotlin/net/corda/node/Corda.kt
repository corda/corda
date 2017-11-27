// This class is used by the smoke tests as a check that the node module isn't on their classpath
@file:JvmName("Corda")

package net.corda.node

import com.google.inject.Guice
import net.corda.node.internal.NodeStartup
import net.corda.node.internal.NodeStartupArgumentsModule
import net.corda.node.internal.VersionInfoModule

fun main(args: Array<String>) {
    // Pass the arguments to the Node factory. In the Enterprise edition, this line is modified to point to a subclass.
    // It will exit the process in case of startup failure and is not intended to be used by embedders. If you want
    // to embed Node in your own container, instantiate it directly and set up the configuration objects yourself.
    val modules = listOf(NodeStartupArgumentsModule(args), VersionInfoModule())

    val injector = Guice.createInjector(modules)

    val nodeStartup = injector.getInstance(NodeStartup::class.java)

    nodeStartup.run()
}


