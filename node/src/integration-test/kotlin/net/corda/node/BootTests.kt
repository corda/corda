/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.CordaRuntimeException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.internal.div
import net.corda.core.internal.list
import net.corda.core.internal.readLines
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.NodeStartup
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.nodeapi.exceptions.InternalNodeException
import net.corda.testing.common.internal.ProjectStructure.projectRootDir
import net.corda.testing.core.*
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.node.User
import net.corda.testing.node.internal.startNode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.ClassRule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import kotlin.test.assertEquals

class BootTests : IntegrationTest() {
     companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME.toDatabaseSchemaName(), BOB_NAME.toDatabaseSchemaName(), DUMMY_NOTARY_NAME.toDatabaseSchemaName())
     }

    @Test
    fun `java deserialization is disabled`() {
        val user = User("u", "p", setOf(startFlow<ObjectInputStreamFlow>()))
        val params = NodeParameters(rpcUsers = listOf(user))

        fun NodeHandle.attemptJavaDeserialization() {
            CordaRPCClient(rpcAddress).use(user.username, user.password) { connection ->
                connection.proxy
                rpc.startFlow(::ObjectInputStreamFlow).returnValue.getOrThrow()
            }
        }
        driver {
            val devModeNode = startNode(params, BOB_NAME).getOrThrow()
            val node = startNode(ALICE_NAME, devMode = false, parameters = params).getOrThrow()

            assertThatThrownBy { devModeNode.attemptJavaDeserialization() }.isInstanceOf(CordaRuntimeException::class.java)
            assertThatThrownBy { node.attemptJavaDeserialization() }.isInstanceOf(InternalNodeException::class.java)
        }
    }

    @Test
    fun `double node start doesn't write into log file`() {
        val logConfigFile = projectRootDir / "config" / "dev" / "log4j2.xml"
        assertThat(logConfigFile).isRegularFile()
        driver(DriverParameters(isDebug = true, systemProperties = mapOf("log4j.configurationFile" to logConfigFile.toString()))) {
            val alice = startNode(providedName = ALICE_NAME).get()
            val logFolder = alice.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME
            val logFile = logFolder.list { it.filter { it.fileName.toString().endsWith(".log") }.findAny().get() }
            // Start second Alice, should fail
            assertThatThrownBy {
                startNode(providedName = ALICE_NAME).getOrThrow()
            }
            // We count the number of nodes that wrote into the logfile by counting "Logs can be found in"
            val numberOfNodesThatLogged = logFile.readLines { it.filter { NodeStartup.LOGS_CAN_BE_FOUND_IN_STRING in it }.count() }
            assertEquals(1, numberOfNodesThatLogged)
        }
    }
}

@StartableByRPC
class ObjectInputStreamFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val data = ByteArrayOutputStream().apply { ObjectOutputStream(this).use { it.writeObject(object : Serializable {}) } }.toByteArray()
        ObjectInputStream(data.inputStream()).use { it.readObject() }
    }
}
