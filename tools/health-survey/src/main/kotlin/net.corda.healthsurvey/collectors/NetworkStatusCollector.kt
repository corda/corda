package net.corda.healthsurvey.collectors

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.healthsurvey.cli.Console.yellow
import net.corda.healthsurvey.output.Report
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

class NetworkStatusCollector(private val nodeConfigurationFile: Path) : TrackedCollector("Collecting general network information ...") {

    private lateinit var file: Report.ReportFile

    override fun collect(report: Report) {
        try {
            file = report.addFile("network-information.txt")
            attempt("Resolving public host ...", "Failed to resolve public host, continuing ...", file) {
                val host = "corda.network"
                val address = InetAddress.getByName(host)
                val hostAddress = address.hostAddress
                step("Resolved public host ${yellow(host)} to ${yellow(hostAddress)}")
                file.withContent("Resolved public host $host to $hostAddress\n")
            }
            attempt("Resolving local host ...", "Failed to resolve local host, continuing ...", file) {
                val host = "localhost"
                val address = InetAddress.getByName(host)
                val hostAddress = address.hostAddress
                step("Resolved local host to ${yellow(hostAddress)}")
                file.withContent("Resolved $host to $hostAddress\n")
            }
            attempt("Resolving database host ...", "Failed to resolve database host, continuing ...", file) {
                if (Files.exists(nodeConfigurationFile)) {
                    val config = ConfigFactory.parseReader(Files.newBufferedReader(nodeConfigurationFile))
                    val rawDatabaseUrl = config.getOptionalString("dataSourceProperties.dataSource.url")
                    val host = (URI((rawDatabaseUrl ?: "jdbc:").substring(5)).host ?: "")
                    if (host.isNotBlank()) {
                        val address = InetAddress.getByName(host)
                        val hostAddress = address.hostAddress
                        step("Resolved database host ${yellow(host)} to ${yellow(hostAddress)}")
                        file.withContent("Resolved database host $host to $hostAddress\n")
                    } else {
                        step("No database host specified")
                    }
                } else {
                    fail("No configuration file found")
                }
            }
            attempt("Resolving doorman host ...", "Failed to resolve doorman host, continuing ...", file) {
                if (Files.exists(nodeConfigurationFile)) {
                    val config = ConfigFactory.parseReader(Files.newBufferedReader(nodeConfigurationFile))
                    val rawCompatibilityZoneUrl = config.getOptionalString("compatibilityZoneURL")
                    val rawDoormanUrl = config.getOptionalString("networkServices.doormanURL")
                    val host = URL(rawDoormanUrl ?: rawCompatibilityZoneUrl ?: "http://").host
                    if (host.isNotBlank()) {
                        val address = InetAddress.getByName(host)
                        val hostAddress = address.hostAddress
                        step("Resolved doorman host ${yellow(host)} to ${yellow(hostAddress)}")
                        file.withContent("Resolved doorman host $host to $hostAddress\n")
                    } else {
                        step("No doorman host specified")
                    }
                } else {
                    fail("No configuration file found")
                }
            }
            attempt("Resolving network map host ...", "Failed to resolve network map host, continuing ...", file) {
                if (Files.exists(nodeConfigurationFile)) {
                    val config = ConfigFactory.parseReader(Files.newBufferedReader(nodeConfigurationFile))
                    val rawCompatibilityZoneUrl = config.getOptionalString("compatibilityZoneURL")
                    val rawNetworkMapUrl = config.getOptionalString("networkServices.networkMapURL")
                    val host = URL(rawNetworkMapUrl ?: rawCompatibilityZoneUrl ?: "http://").host
                    if (host.isNotBlank()) {
                        val address = InetAddress.getByName(host)
                        val hostAddress = address.hostAddress
                        step("Resolved network map host ${yellow(host)} to ${yellow(hostAddress)}")
                        file.withContent("Resolved network map host $host to $hostAddress\n")
                    } else {
                        step("No network map host specified")
                    }
                } else {
                    fail("No configuration file found")
                }
            }
        } finally {
            if (errors > 0) {
                fail("Collected general network information, but one or more steps failed")
            } else {
                complete("Collected general network information")
            }
        }
    }

    private fun Config.getOptionalString(path: String) = if (hasPath(path)) { getString(path) } else { null }

}
