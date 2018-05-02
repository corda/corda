package net.corda.demobench

import javafx.scene.image.Image
import net.corda.client.rpc.internal.serialization.kryo.KryoClientSerializationScheme
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.demobench.views.DemoBenchView
import net.corda.nodeapi.internal.serialization.AMQP_P2P_CONTEXT
import net.corda.nodeapi.internal.serialization.SerializationFactoryImpl
import net.corda.nodeapi.internal.serialization.amqp.AMQPClientSerializationScheme
import tornadofx.*
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets.UTF_8

/**
 * README!
 *
 *
 * This tool is intended to become a sales and educational tool for Corda. It is a standalone desktop app that
 * comes bundled with an appropriate JVM, and which runs nodes in a local network. It has the following features:
 *
 * - New nodes can be added at the click of a button. Clicking "Add node" creates new tab that lets you edit the
 *   most important configuration properties of the node before launch, like the name and what apps will be loaded.
 *
 * - Each tab contains a terminal emulator, attached to the pty of the node. This lets you see console output and
 *   (soon) interact with the command shell of the node. See the mike-crshell branch in github.
 *
 * - An Explorer instance for the node can be launched at the click of a button. Credentials are handed to the
 *   Explorer so it starts out logged in already.
 *
 * - Some basic statistics are shown about each node, informed via the RPC connection.
 *
 * - Another button launches a database viewer (like the H2 web site) for the node. For instance, in an embedded
 *   WebView, or the system browser.
 *
 * - It can also run a Jetty instance that can load WARs that come with the bundled CorDapps (eventually).
 *
 * The app is nicely themed using the Corda branding. It is easy enough to use for non-developers to successfully
 * demonstrate some example cordapps and why people should get excited about the platform. There is no setup
 * overhead as everything is included: just double click the icon and start going through the script. There are no
 * dependencies on external servers or network connections, so flaky conference room wifi should not be an issue.
 */

class DemoBench : App(DemoBenchView::class) {
    /*
     * This entry point is needed by JavaPackager, as
     * otherwise the packaged application cannot run.
     */
    companion object {
        @JvmStatic
        fun main(args: Array<String>) = launch(DemoBench::class.java, *args)
    }

    init {
        addStageIcon(Image("cordalogo.png"))
        initialiseSerialization()
    }

    private fun initialiseSerialization() {
        nodeSerializationEnv = SerializationEnvironmentImpl(
                SerializationFactoryImpl().apply {
                    registerScheme(KryoClientSerializationScheme())
                    registerScheme(AMQPClientSerializationScheme())
                },
                AMQP_P2P_CONTEXT)
    }
}

fun Process.readErrorLines(): List<String> = InputStreamReader(this.errorStream, UTF_8).readLines()
