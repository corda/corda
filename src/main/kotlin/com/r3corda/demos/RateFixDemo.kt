package com.r3corda.demos

import com.google.common.net.HostAndPort
import com.r3corda.contracts.asset.Cash
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.logElapsedTime
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.ServiceType
import com.r3corda.testing.node.makeTestDataSourceProperties
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.utilities.Emoji
import com.r3corda.core.utilities.LogHelper
import com.r3corda.node.internal.Node
import com.r3corda.node.services.clientapi.NodeInterestRates
import com.r3corda.node.services.config.NodeConfiguration
import com.r3corda.node.services.messaging.ArtemisMessagingClient
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.services.transactions.NotaryService
import com.r3corda.protocols.RatesFixProtocol
import joptsimple.OptionParser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
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
    val networkMapIdentityArg = parser.accepts("network-map-identity-file").withRequiredArg().required()

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
    val networkMapAddr = ArtemisMessagingClient.makeNetworkMapAddress(HostAndPort.fromString(options.valueOf(networkMapAddrArg)))
    val networkMapIdentity = Files.readAllBytes(Paths.get(options.valueOf(networkMapIdentityArg))).deserialize<Party>()
    val networkMapAddress = NodeInfo(networkMapAddr, networkMapIdentity, setOf(NetworkMapService.Type, NotaryService.Type))

    val fixOf: FixOf = NodeInterestRates.parseFixOf(options.valueOf(fixOfArg))
    val expectedRate = BigDecimal(options.valueOf(expectedRateArg))
    val rateTolerance = BigDecimal(options.valueOf(rateToleranceArg))

    // Bring up node.
    val advertisedServices: Set<ServiceType> = emptySet()
    val myNetAddr = HostAndPort.fromString(options.valueOf(networkAddressArg))
    val config = object : NodeConfiguration {
        override val myLegalName: String = "Rate fix demo node"
        override val exportJMXto: String = "http"
        override val nearestCity: String = "Atlantis"
        override val keyStorePassword: String = "cordacadevpass"
        override val trustStorePassword: String = "trustpass"
        override val dataSourceProperties: Properties = makeTestDataSourceProperties()
    }

    val apiAddr = HostAndPort.fromParts(myNetAddr.hostText, myNetAddr.port + 1)

    val node = logElapsedTime("Node startup") { Node(dir, myNetAddr, apiAddr, config, networkMapAddress,
            advertisedServices, DemoClock()).setup().start() }
    node.networkMapRegistrationFuture.get()
    val notaryNode = node.services.networkMapCache.notaryNodes[0]
    val rateOracle = node.services.networkMapCache.ratesOracleNodes[0]

    // Make a garbage transaction that includes a rate fix.
    val tx = TransactionType.General.Builder(notaryNode.identity)
    tx.addOutputState(TransactionState(Cash.State(1500.DOLLARS `issued by` node.storage.myLegalIdentity.ref(1), node.keyManagement.freshKey().public), notaryNode.identity))
    val protocol = RatesFixProtocol(tx, rateOracle.identity, fixOf, expectedRate, rateTolerance)
    node.services.startProtocol("demo.ratefix", protocol).get()
    node.stop()

    // Show the user the output.
    log.info("Got rate fix\n")
    print(Emoji.renderIfSupported(tx.toWireTransaction()))
    println(tx.toSignedTransaction().sigs.toString())
}
