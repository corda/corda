package net.corda.tools.testing.pinger

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.exists
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.addShutdownHook
import java.nio.file.Paths

class Pinger()

const val confInfo = """
Typical pinger.conf values:
    parallelFlows = 10
    waitDoneTime = 60
    nodeConnection = "localhost:10130"
    rpcUser = "user"
    rpcPassword = "password"
    targetPeers = ["O=Bank B, L=London, C=GB"]
    notary = "O=Notary, L=London, C=GB"
"""

const val generalInfo = """
This tool is intended to be configured via a pinger.conf file, rather than command line options
The intended operation of this tool is that it is run to push a set of parallel flows to one or more remote nodes.
Testing should then commence in which the peer to peer communication is interrupted e.g kill bridge.
The flow processing will become blocked during the interuption, but should restore once full connectivity re-establishes
At this point the tester should issue a Ctrl-C command. No new flows will be submitted and a 60 second grace period is given for the flows to halt.
Any flow still pending at the end of this will be logged by flow id.
In its current implementation we do not handle RPC disconnects, or audit the resulting vault.
"""

fun main(args: Array<String>) {
    val log = loggerFor<Pinger>()
    if (!args.isEmpty()) {
        log.info(generalInfo)
        System.exit(-1)
    }
    val configPath = Paths.get("pinger.conf")
    if (!configPath.exists()) {
        log.error("pinger.conf file must exist!")
        log.error(confInfo)
        System.exit(-1)
    }
    Thread.setDefaultUncaughtExceptionHandler({ t, e ->
        if (e is ConfigException) {
            log.error("Config parsing error")
            log.error(confInfo)
            System.exit(-1)
        }
    })
    val fileConfig = ConfigFactory.parseFile(Paths.get("pinger.conf").toFile())
    val baseConfig = ConfigFactory.defaultReference(Pinger::class.java.classLoader)
    val conf = fileConfig.withFallback(baseConfig).resolve()
    log.info("Config:\n${conf.root().render(ConfigRenderOptions.defaults())}")
    val nodeConnection = conf.getString("nodeConnection")
    val rpcUser = conf.getString("rpcUser")
    val rpcPassword = conf.getString("rpcPassword")
    val parallelFlows = conf.getInt("parallelFlows")
    val waitDoneTime = conf.getLong("waitDoneTime")
    val peers = conf.getStringList("targetPeers")
    val notaryName = CordaX500Name.parse(conf.getString("notary"))
    val client = CordaRPCClient(NetworkHostAndPort.parse(nodeConnection))
    val conn = try {
        client.start(rpcUser, rpcPassword)
    } catch (ex: java.lang.Exception) {
        log.error("failed to connect RPC to $nodeConnection", ex)
        System.exit(-1)
        null
    }
    val shutdownFuture = openFuture<Boolean>()
    val flowRunners = mutableListOf<FlowRunner>()
    addShutdownHook {
        var valid = true
        flowRunners.forEach {
            try {
                valid = valid && it.stop()
            } catch (ex: Exception) {
                log.error("Error stopping flowrunner", ex)
            }
        }
        shutdownFuture.set(true)
        if (!valid) {
            System.exit(-1)
        }
    }
    conn?.use {
        val notary = conn.proxy.wellKnownPartyFromX500Name(notaryName)!!
        val parties = peers.map { conn.proxy.wellKnownPartyFromX500Name(CordaX500Name.parse(it))!! }
        for (party in parties) {
            flowRunners += FlowRunner(conn.proxy, party, notary, parallelFlows, waitDoneTime)
        }
        flowRunners.forEach { it.start() }

        val valid = if (System.getProperties().containsKey("WAIT_KEY_FOR_EXIT")) {
            System.`in`.read() // Inside IntelliJ we can't forward CTRL-C, so debugging shutdown is a nightmare. So allow -DWAIT_KEY_FOR_EXIT flag for key based quit.
            var valid = true
            flowRunners.forEach {
                try {
                    valid = valid && it.stop()
                } catch (ex: Exception) {
                    log.error("Error stopping flowrunner", ex)
                }
            }
            valid
        } else {
            shutdownFuture.get()
            true
        }
        if (!valid) {
            System.exit(-1)
        }
    }
    System.exit(0)
}