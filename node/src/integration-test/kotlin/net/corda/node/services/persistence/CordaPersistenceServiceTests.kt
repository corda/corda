package net.corda.node.services.persistence

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.messaging.startFlow
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.getOrThrow
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.internal.enclosedCordapp
import org.junit.Test
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CordaPersistenceServiceTests {
    @Test
    fun `corda service can save many transactions from different threads`() {
        driver(DriverParameters(inMemoryDB = false, startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()))) {

            val port = incrementalPortAllocation().nextPort()
            val node = startNode(customOverrides = mapOf("h2Settings.address" to "localhost:$port")).getOrThrow()

            val sampleSize = 100
            val count = node.rpc.startFlow(::MyRpcFlow, sampleSize).returnValue.getOrThrow()
            assertEquals(sampleSize, count)

            DriverManager.getConnection("jdbc:h2:tcp://localhost:$port/node", "sa", "").use {
                val resultSet = it.createStatement().executeQuery("SELECT count(*) from ${NODE_DATABASE_PREFIX}checkpoints")
                assertTrue(resultSet.next())
                val resultSize = resultSet.getInt(1)
                assertEquals(sampleSize, resultSize)
            }
        }
    }

    @StartableByRPC
    class MyRpcFlow(private val count: Int) : FlowLogic<Int>() {
        @Suspendable
        override fun call(): Int {
            val service = serviceHub.cordaService(MultiThreadedDbLoader::class.java)
            return service.createObjects(count)
        }
    }

    @CordaService
    class MultiThreadedDbLoader(private val services: AppServiceHub) : SingletonSerializeAsToken() {
        fun createObjects(count: Int) : Int {
            (1..count).toList().parallelStream().forEach {
                services.database.transaction {
                    session.save(DBCheckpointStorage.DBCheckpoint().apply {
                        checkpointId = it.toString()
                    })
                }
            }

            return count
        }
    }
}