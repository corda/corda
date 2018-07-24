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

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.notaryhealthcheck.cordapp.StartCheckScheduleFlow
import net.corda.notaryhealthcheck.cordapp.StartAllChecksFlow
import net.corda.notaryhealthcheck.cordapp.StopAllChecksFlow
import net.corda.notaryhealthcheck.cordapp.StopCheckScheduleFlow
import net.corda.notaryhealthcheck.utils.Monitorable
import picocli.CommandLine
import java.io.File
import java.lang.System.exit

class MissingTargetException(override val message: String) : Exception(message)

fun getMonitorable(proxy: CordaRPCOps, target: String?, notary: String?): Monitorable {
    if (target == null) {
        throw MissingTargetException("'--target' cannot be null")
    }
    val targetName = try {
        CordaX500Name.parse(target)
    } catch (e: Exception) {
        throw MissingTargetException("Failed to parse X500 name $target: ${e.message}")
    }
    val targetParty = proxy.wellKnownPartyFromX500Name(targetName)
            ?: throw MissingTargetException("Failed to resolve name $target to a party")

    val notaryParty = notary?.let {
        val notaryName = try {
            CordaX500Name.parse(notary)
        } catch (e: Exception) {
            throw MissingTargetException("Failed to parse X500 name $notary: ${e.message}")
        }
        proxy.notaryPartyFromX500Name(notaryName)
                ?: throw MissingTargetException("Failed to resolve name $notary to a notary party")
    } ?: targetParty
    return Monitorable(notaryParty, targetParty)
}

fun main(args: Array<String>) {
    val parsed = CliParser(loadConfig(File(".").toPath()))
    val cli = CommandLine(parsed)
    try {
        cli.parse(*args)
    } catch (e: Exception) {
        println(e.message)
        cli.usage(System.out)
        exit(ReturnCodes.ParseFailure)
    }

    if (parsed.user == null || parsed.password == null) {
        println("User and password must be provided either in config or on the command line")
        cli.usage(System.out)
        exit(ReturnCodes.MissingCredentials)
    }

    if (parsed.host == null || parsed.port == null) {
        println("Host and port must be provided either in config or on the command line")
        cli.usage(System.out)
        exit(ReturnCodes.MissingHostOrPort)
    }

    val client = CordaRPCClient(NetworkHostAndPort(parsed.host!!, parsed.port!!))
    client.use(parsed.user!!, parsed.password!!) {
        val proxy = it.proxy

        try {
            when (parsed.command!!) {
                CliParser.Companion.NotaryHealthCheckCommand.startAll -> {
                    proxy.startFlow(::StartAllChecksFlow, parsed.waitPeriodSeconds, parsed.waitForOutstandingFlows)
                }
                CliParser.Companion.NotaryHealthCheckCommand.stopAll -> {
                    proxy.startFlow(::StopAllChecksFlow)
                }
                CliParser.Companion.NotaryHealthCheckCommand.start -> {
                    val target = getMonitorable(proxy, parsed.target, parsed.notary)
                    proxy.startFlow(::StartCheckScheduleFlow, target, parsed.waitPeriodSeconds, parsed.waitForOutstandingFlows)
                }
                CliParser.Companion.NotaryHealthCheckCommand.stop -> {
                    val target = getMonitorable(proxy, parsed.target, parsed.notary)
                    proxy.startFlow(::StopCheckScheduleFlow, target)
                }
            }
        } catch (e: MissingTargetException) {
            println("When using commands 'start' or 'stop', a target node to be monitored needs to be provided.")
            println(e.message)
            println("Use 'startAll'/'stopAll' to start/stop checking all notaries in the network map")
            cli.usage(System.out)
            exit(ReturnCodes.MissingNotaryTarget)
        }
    }
}