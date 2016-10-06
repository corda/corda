package com.r3corda.demos

import com.google.common.net.HostAndPort
import com.r3corda.contracts.InterestRateSwap
import com.r3corda.contracts.asset.Cash
import com.r3corda.core.contracts.*
import com.r3corda.core.logElapsedTime
import com.r3corda.core.node.services.ServiceInfo
import com.r3corda.core.utilities.Emoji
import com.r3corda.core.utilities.LogHelper
import com.r3corda.demos.api.NodeInterestRates
import com.r3corda.node.internal.Node
import com.r3corda.node.services.config.ConfigHelper
import com.r3corda.node.services.config.FullNodeConfiguration
import com.r3corda.node.services.messaging.NodeMessagingClient
import com.r3corda.protocols.RatesFixProtocol
import joptsimple.OptionParser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.nio.file.Paths
import kotlin.system.exitProcess

private val log: Logger = LoggerFactory.getLogger("RatesFixDemo")

/**
 * Creates a dummy transaction that requires a rate fix within a certain range, and gets it signed by an oracle
 * service.
 */
fun main(args: Array<String>) {
    val parser = OptionParser()
    val networkAddressArg = parser.accepts("network-address").withRequiredArg().required()
    val dirArg = parser.accepts("directory").withRequiredArg().defaultsTo("rate-fix-demo-data")
    val networkMapAddrArg = parser.accepts("network-map").withRequiredArg().required()

    val fixOfArg = parser.accepts("fix-of").withRequiredArg().defaultsTo("ICE LIBOR 2016-03-16 1M")
    val expectedRateArg = parser.accepts("expected-rate").withRequiredArg().defaultsTo("0.67")
    val rateToleranceArg = parser.accepts("rate-tolerance").withRequiredArg().defaultsTo("0.1")

    val options = try {
        parser.parse(*args)
    } catch (e: Exception) {
        log.error(e.message)
        exitProcess(1)
    }

    // Suppress the Artemis MQ noise, and activate the demo logging.
    LogHelper.setLevel("+RatesFixDemo", "-org.apache.activemq")

    val dir = Paths.get(options.valueOf(dirArg))
    val networkMapAddr = NodeMessagingClient.makeNetworkMapAddress(HostAndPort.fromString(options.valueOf(networkMapAddrArg)))

    val fixOf: FixOf = NodeInterestRates.parseFixOf(options.valueOf(fixOfArg))
    val expectedRate = BigDecimal(options.valueOf(expectedRateArg))
    val rateTolerance = BigDecimal(options.valueOf(rateToleranceArg))

    // Bring up node.
    val advertisedServices: Set<ServiceInfo> = emptySet()
    val myNetAddr = HostAndPort.fromString(options.valueOf(networkAddressArg))

    val apiAddr = HostAndPort.fromParts(myNetAddr.hostText, myNetAddr.port + 1)

    val config = ConfigHelper.loadConfig(
            baseDirectoryPath = dir,
            allowMissingConfig = true,
            configOverrides = mapOf(
                    "myLegalName" to "Rate fix demo node",
                    "basedir" to dir.normalize().toString(),
                    "artemisAddress" to myNetAddr.toString(),
                    "webAddress" to apiAddr.toString()
            )
    )

    val nodeConfiguration = FullNodeConfiguration(config)

    val node = logElapsedTime("Node startup") {
        Node(nodeConfiguration, networkMapAddr, advertisedServices, DemoClock()).setup().start()
    }
    node.networkMapRegistrationFuture.get()
    val notaryNode = node.services.networkMapCache.notaryNodes[0]
    val rateOracle = node.services.networkMapCache.get(InterestRateSwap.OracleType).first()

    // Make a garbage transaction that includes a rate fix.
    val tx = TransactionType.General.Builder(notaryNode.identity)
    tx.addOutputState(TransactionState(Cash.State(1500.DOLLARS `issued by` node.storage.myLegalIdentity.ref(1), node.storage.myLegalIdentityKey.public), notaryNode.identity))
    val protocol = RatesFixProtocol(tx, rateOracle.identity, fixOf, expectedRate, rateTolerance)
    node.services.startProtocol("demo.ratefix", protocol).get()
    node.stop()

    // Show the user the output.
    log.info("Got rate fix\n")
    print(Emoji.renderIfSupported(tx.toWireTransaction()))
    println(tx.toSignedTransaction().sigs.toString())
}
