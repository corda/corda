/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.notaryhealthcheck.client

import com.typesafe.config.ConfigFactory
import java.nio.file.Path

/**
 * Configuration for the notary healthcheck command line client. Anything in here can be set in a config file so it
 * does not have to be added to the command line if it doesn't change often. Where a default is specified, this is set
 * via the default.conf file
 *
 * @param waitPeriodSeconds The time how long to wait between checks of any notary. If omitted, this defaults to 60s
 * @param waitForOutstandingFlowsSeconds The time to wait for a slow or hanging check to return before another check
 *      of the same notary node is run. This should be greater than waitPeriodSeconds to avoid filling up the queues
 *      in case of a hanging notary. Defaults to 300s.
 * @param user RPC user name. No default
 * @param password RPC password. No default
 * @param host Hostname of the node running the notary health check CordApp. No default
 * @param port Port of the node process running the notary health check CordApp. No default
 *
 */
data class Config(
        val waitPeriodSeconds: Int,
        val waitForOutstandingFlowsSeconds: Int,
        val user: String?,
        val password: String?,
        val host: String?,
        val port: Int?)

fun loadConfig(configPath: Path): Config {
    val configFile = configPath.resolve("healthcheck.conf")
    val fallback = ConfigFactory.parseURL(Config::class.java.getResource("default.conf"))
    val cfg = ConfigFactory.parseFile(configFile.toFile()).withFallback(fallback)
    return Config(
            waitPeriodSeconds = cfg.getInt("waitPeriodSeconds"),
            waitForOutstandingFlowsSeconds = cfg.getInt("waitForOutstandingFlowsSeconds"),
            user = if (cfg.hasPath("user")) cfg.getString("user") else null,
            password = if (cfg.hasPath("password")) cfg.getString("password") else null,
            host = if (cfg.hasPath("host")) cfg.getString("host") else null,
            port = if (cfg.hasPath("port")) cfg.getInt("port") else null)

}