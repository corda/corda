/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.testing.node.internal

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.CordaException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.FlowStateMachine
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.times
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.millis
import net.corda.core.utilities.seconds
import net.corda.node.services.api.StartedNodeServices
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.configOf
import net.corda.node.services.config.parseToDbSchemaFriendlyName
import net.corda.node.services.messaging.Message
import net.corda.node.services.messaging.MessagingService
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.TransactionIsolationLevel
import net.corda.testing.database.DatabaseConstants
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.User
import net.corda.testing.node.testContext
import org.slf4j.LoggerFactory
import java.net.Socket
import java.net.SocketException
import java.time.Duration
import java.util.*
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private val log = LoggerFactory.getLogger("net.corda.testing.internal.InternalTestUtils")

/**
 * @throws ListenProcessDeathException if [listenProcess] dies before the check succeeds, i.e. the check can't succeed as intended.
 */
fun addressMustBeBound(executorService: ScheduledExecutorService, hostAndPort: NetworkHostAndPort, listenProcess: Process? = null) {
    addressMustBeBoundFuture(executorService, hostAndPort, listenProcess).getOrThrow()
}

fun addressMustBeBoundFuture(executorService: ScheduledExecutorService, hostAndPort: NetworkHostAndPort, listenProcess: Process? = null): CordaFuture<Unit> {
    return poll(executorService, "address $hostAndPort to bind") {
        if (listenProcess != null && !listenProcess.isAlive) {
            throw ListenProcessDeathException(hostAndPort, listenProcess)
        }
        try {
            Socket(hostAndPort.host, hostAndPort.port).close()
            Unit
        } catch (_exception: SocketException) {
            null
        }
    }
}

/*
 * The default timeout value of 40 seconds have been chosen based on previous node shutdown time estimate.
 * It's been observed that nodes can take up to 30 seconds to shut down, so just to stay on the safe side the 60 seconds
 * timeout has been chosen.
 */
fun addressMustNotBeBound(executorService: ScheduledExecutorService, hostAndPort: NetworkHostAndPort, timeout: Duration = 40.seconds) {
    addressMustNotBeBoundFuture(executorService, hostAndPort).getOrThrow(timeout)
}

fun addressMustNotBeBoundFuture(executorService: ScheduledExecutorService, hostAndPort: NetworkHostAndPort): CordaFuture<Unit> {
    return poll(executorService, "address $hostAndPort to unbind") {
        try {
            Socket(hostAndPort.host, hostAndPort.port).close()
            null
        } catch (_exception: SocketException) {
            Unit
        }
    }
}

fun <A> poll(
        executorService: ScheduledExecutorService,
        pollName: String,
        pollInterval: Duration = 500.millis,
        warnCount: Int = 120,
        check: () -> A?
): CordaFuture<A> {
    val resultFuture = openFuture<A>()
    val task = object : Runnable {
        var counter = -1
        override fun run() {
            if (resultFuture.isCancelled) return // Give up, caller can no longer get the result.
            if (++counter == warnCount) {
                log.warn("Been polling $pollName for ${(pollInterval * warnCount.toLong()).seconds} seconds...")
            }
            try {
                val checkResult = check()
                if (checkResult != null) {
                    resultFuture.set(checkResult)
                } else {
                    executorService.schedule(this, pollInterval.toMillis(), TimeUnit.MILLISECONDS)
                }
            } catch (t: Throwable) {
                resultFuture.setException(t)
            }
        }
    }
    executorService.submit(task) // The check may be expensive, so always run it in the background even the first time.
    return resultFuture
}

class ListenProcessDeathException(hostAndPort: NetworkHostAndPort, listenProcess: Process) :
        CordaException("The process that was expected to listen on $hostAndPort has died with status: ${listenProcess.exitValue()}")

fun <T> StartedNodeServices.startFlow(logic: FlowLogic<T>): FlowStateMachine<T> = startFlow(logic, newContext()).getOrThrow()

fun StartedNodeServices.newContext(): InvocationContext = testContext(myInfo.chooseIdentity().name)

fun InMemoryMessagingNetwork.MessageTransfer.getMessage(): Message = message

internal interface InternalMockMessagingService : MessagingService {
    fun pumpReceive(block: Boolean): InMemoryMessagingNetwork.MessageTransfer?

    fun stop()
}

/**
 * Make properties appropriate for creating a DataSource for unit tests.
 * Defaults configuration of in-memory H2 instance. If 'databaseProvider' system property is set then creates
 * a config from the relevant config file is present in resources folder (used to parametrize test to run against a remote database).
 * Properties retrieval can be parameterized by [configSupplier] and/or [fallBackConfigSupplier] methods
 * which are run with [nodeName] and [nodeNameExtension] parameters.
 *
 * @param nodeName Reflects the "instance" of the in-memory database or database username/schema.
 * Defaults to a random string. Passed to [configSupplier] and [fallBackConfigSupplier] methods.
 * @param nodeNameExtension Provides additional name extension for [configSupplier] and [fallBackConfigSupplier].
 * @param configSupplier Returns [Config] with dataSourceProperties, invoked with [nodeName] and [nodeNameExtension] parameters.
 * Defaults to configuration created when 'databaseProvider' system property is set.
 * @param fallBackConfigSupplier Returns [Config] with dataSourceProperties, invoked with [nodeName] and [nodeNameExtension] parameters.
 * Defaults to configuration of in-memory H2 instance.
 */
fun makeTestDataSourceProperties(nodeName: String = SecureHash.randomSHA256().toString(),
                                 nodeNameExtension: String? = null,
                                 configSupplier: (String, String?) -> Config = ::databaseProviderDataSourceConfig,
                                 fallBackConfigSupplier: (String?, String?) -> Config = ::inMemoryH2DataSourceConfig): Properties {
    val config = configSupplier(nodeName, nodeNameExtension)
            .withFallback(fallBackConfigSupplier(nodeName, nodeNameExtension))
            .resolve()

    val props = Properties()
    props.setProperty("dataSourceClassName", config.getString("dataSourceProperties.dataSourceClassName"))
    props.setProperty("dataSource.url", config.getString("dataSourceProperties.dataSource.url"))
    props.setProperty("dataSource.user", config.getString("dataSourceProperties.dataSource.user"))
    props.setProperty("dataSource.password", config.getString("dataSourceProperties.dataSource.password"))
    props.setProperty("autoCommit", "false")
    return props
}

/**
 * Make properties appropriate for creating a Database for unit tests.
 *
 * @param nodeName Reflects the "instance" of the in-memory database or database username/schema.
 */
fun makeTestDatabaseProperties(nodeName: String? = null): DatabaseConfig {
    val config = databaseProviderDataSourceConfig(nodeName)
    val transactionIsolationLevel = if (config.hasPath(DatabaseConstants.TRANSACTION_ISOLATION_LEVEL))
        TransactionIsolationLevel.valueOf(config.getString(DatabaseConstants.TRANSACTION_ISOLATION_LEVEL))
    else TransactionIsolationLevel.READ_COMMITTED
    val schema = if (config.hasPath(DatabaseConstants.SCHEMA)) config.getString(DatabaseConstants.SCHEMA) else ""
    return DatabaseConfig(runMigration = true, transactionIsolationLevel = transactionIsolationLevel, schema = schema)
}

/**
 * Reads database and dataSource configuration from a file denoted by 'databaseProvider' system property,
 * overwritten by system properties and defaults to H2 in memory db.
 * @param nodeName Reflects the "instance" of the database username/schema, the value will be used to replace ${custom.nodeOrganizationName} placeholder
 * @param notUsed Not uses, required for API backward compatibility.
 * if the placeholder is present in config.
 */
fun databaseProviderDataSourceConfig(nodeName: String? = null, notUsed: String? = null): Config {

    val parseOptions = ConfigParseOptions.defaults()

    val keys = listOf(DatabaseConstants.DATA_SOURCE_URL, DatabaseConstants.DATA_SOURCE_CLASSNAME,
            DatabaseConstants.DATA_SOURCE_USER, DatabaseConstants.DATA_SOURCE_PASSWORD)
    //read overrides from command line (passed by Gradle as system properties)
    val systemConfigOverride = ConfigFactory.parseMap(System.getProperties()
            .filterKeys { (it as String).startsWith(ConfigHelper.CORDA_PROPERTY_PREFIX) }
            .mapKeys { (it.key as String).removePrefix(ConfigHelper.CORDA_PROPERTY_PREFIX) }
            .filterKeys { it in keys })

    //read from db vendor specific configuration file
    val databaseConfig = ConfigFactory.parseResources(System.getProperty("custom.databaseProvider") + ".conf", parseOptions.setAllowMissing(true))
    val fixedOverride = ConfigFactory.parseString("baseDirectory = \"\"")

    //implied property custom.nodeOrganizationName to fill the potential placeholders in db schema/ db user properties
    val nodeOrganizationNameConfig = if (nodeName != null) configOf("custom.nodeOrganizationName" to parseToDbSchemaFriendlyName(nodeName)) else ConfigFactory.empty()

    return systemConfigOverride.withFallback(databaseConfig)
            .withFallback(fixedOverride)
            .withFallback(nodeOrganizationNameConfig)
            .resolve()
}

/**
 * Creates data source configuration for in memory H2 as it would be specified in reference.conf 'datasource' snippet.
 * @param nodeName Reflects the "instance" of the database username/schema
 * @param postfix Additional postix added to database "instance" name to add uniqueness when running integration tests.
 */
fun inMemoryH2DataSourceConfig(nodeName: String? = null, postfix: String? = null) : Config {
    val nodeName = nodeName ?: SecureHash.randomSHA256().toString()
    val h2InstanceName = if (postfix != null) nodeName + "_" + postfix else nodeName

    return ConfigFactory.parseMap(mapOf(
            DatabaseConstants.DATA_SOURCE_CLASSNAME to "org.h2.jdbcx.JdbcDataSource",
            DatabaseConstants.DATA_SOURCE_URL to "jdbc:h2:mem:${h2InstanceName}_persistence;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
            DatabaseConstants.DATA_SOURCE_USER to "sa",
            DatabaseConstants.DATA_SOURCE_PASSWORD to ""))
}

fun CordaRPCClient.start(user: User) = start(user.username, user.password)
