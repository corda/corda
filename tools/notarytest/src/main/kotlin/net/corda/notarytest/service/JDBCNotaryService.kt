/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.notarytest.service

import com.codahale.metrics.MetricFilter
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.graphite.GraphiteReporter
import com.codahale.metrics.graphite.PickledGraphite
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.StartableByRPC
import net.corda.core.internal.notary.AsyncCFTNotaryService
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.MySQLConfiguration
import net.corda.node.services.transactions.MySQLUniquenessProvider
import net.corda.node.services.transactions.NonValidatingNotaryFlow
import net.corda.nodeapi.internal.config.parseAs
import net.corda.notarytest.flows.AsyncLoadTestFlow
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Paths
import java.security.PublicKey
import java.util.concurrent.TimeUnit

@CordaService
class JDBCNotaryService(override val services: AppServiceHub, override val notaryIdentityKey: PublicKey) : AsyncCFTNotaryService() {
    private val appConfig = ConfigHelper.loadConfig(Paths.get(".")).getConfig("custom")

    override val asyncUniquenessProvider: MySQLUniquenessProvider = createUniquenessProvider()

    override fun createServiceFlow(otherPartySession: FlowSession): FlowLogic<Void?> = NonValidatingNotaryFlow(otherPartySession, this)

    override fun start() {
        asyncUniquenessProvider.createTable()
    }

    override fun stop() {
        asyncUniquenessProvider.stop()
    }

    private fun createMetricsRegistry(): MetricRegistry {
        val graphiteAddress = appConfig.getString("graphiteAddress").let { NetworkHostAndPort.parse(it) }
        val hostName = InetAddress.getLocalHost().hostName.replace(".", "_")
        val nodeName = services.myInfo.legalIdentities.first().name.organisation
                .toLowerCase()
                .replace(" ", "_")
                .replace(".", "_")
        val pickledGraphite = PickledGraphite(
                InetSocketAddress(graphiteAddress.host, graphiteAddress.port)
        )
        val metrics = MetricRegistry()
        GraphiteReporter.forRegistry(metrics)
                .prefixedWith("corda.$hostName.$nodeName")
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(MetricFilter.ALL)
                .build(pickledGraphite)
                .start(10, TimeUnit.SECONDS)
        return metrics
    }

    private fun createUniquenessProvider(): MySQLUniquenessProvider {
        val mysqlConfig = appConfig.getConfig("mysql").parseAs<MySQLConfiguration>()
        return MySQLUniquenessProvider(createMetricsRegistry(), services.clock, mysqlConfig)
    }
}

@StartableByRPC
class JDBCLoadTestFlow(transactionCount: Int,
                       batchSize: Int,
                       inputStateCount: Int?
) : AsyncLoadTestFlow<JDBCNotaryService>(JDBCNotaryService::class.java, transactionCount, batchSize, inputStateCount)