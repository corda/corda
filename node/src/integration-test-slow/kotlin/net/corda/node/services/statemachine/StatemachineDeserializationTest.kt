package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.HospitalizeFlowException
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StateMachineRunId
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.OutOfProcess
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.junit.Ignore
import org.junit.Test
import java.lang.IllegalStateException
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class StatemachineDeserializationTest {

    @Test(timeout = 300_000)
    fun `Flows are marked as compatable = false if checkpoint deserialization fails`() {
        testDeserialization(BlobMuddlingFlow.Column.CHECKPOINT)
    }

    @Test(timeout = 300_000)
    fun `Flows are marked as compatable = false if flowSate deserialization fails`() {
        testDeserialization(BlobMuddlingFlow.Column.FLOW_STATE)
    }

    fun testDeserialization(column: BlobMuddlingFlow.Column) {
        driver(DriverParameters(inMemoryDB = false)) {
            //We first start up a Node and run a flow which throws an exception causing the flow to end up in the hospital
            //this causes the flow to persist a checkpoint.
            val aliceUser = User("user", "foo", setOf(Permissions.all()))
            val aliceNode = startNode(providedName = ALICE_NAME, rpcUsers = listOf(aliceUser)).getOrThrow()
            val hospitalFlowId = aliceNode.rpc.startFlow(::HospitalizingFlow).id

            //Next we run a flow which destroys the serialized flow data of the hospitalized flow in the database.
            val muddlingFlow = aliceNode.rpc.startFlow(::BlobMuddlingFlow, hospitalFlowId, column)
            muddlingFlow.returnValue.getOrThrow()
            val timeStamp = aliceNode.rpc.startFlow(::GetTimeStampFlow, hospitalFlowId).returnValue.getOrThrow()

            //We then stop the node and restart it.
            aliceNode.stop()
            val terminated = (aliceNode as OutOfProcess).process.waitFor(30, TimeUnit.SECONDS)
            if (terminated) {
                aliceNode.stop()
            } else {
                throw IllegalStateException("Out of process node is still up and running!")
            }
            val restartedAlice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(aliceUser)).getOrThrow()

            //This causes the previously hospitalised flow to run again but it can no longer be deserailised.
            //We check that the database has been updated marking the checkpoint as Compatable = False and re-timestamping.
            val flow = restartedAlice.rpc.startFlow(::CheckCompatableFlow, hospitalFlowId)
            assertEquals(false, flow.returnValue.getOrThrow())
            val timeStampAfterRestart = restartedAlice.rpc.startFlow(::GetTimeStampFlow, hospitalFlowId).returnValue.getOrThrow()
            assertNotEquals(timeStampAfterRestart, timeStamp)

            //We then stop and restart the node for a second time.
            restartedAlice.stop()
            val terminatedAgain = (restartedAlice as OutOfProcess).process.waitFor(30, TimeUnit.SECONDS)
            if (terminatedAgain) {
                restartedAlice.stop()
            } else {
                throw IllegalStateException("Out of process node is still up and running!")
            }
            val restartedAgainAlice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(aliceUser)).getOrThrow()

            //This time the checkpoint should not be updated as the StateMachine should not try and run a checkpoint with Compatable = False
            val timeStampAfterSecondRestart = restartedAgainAlice.rpc.startFlow(::GetTimeStampFlow, hospitalFlowId).returnValue.getOrThrow()
            assertEquals(timeStampAfterRestart, timeStampAfterSecondRestart)
            restartedAgainAlice.stop()
        }
    }

    @StartableByRPC
    class HospitalizingFlow : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            throw HospitalizeFlowException("Flow went wrong!")
        }
    }

    @StartableByRPC
    class BlobMuddlingFlow(private val flowId: StateMachineRunId, private val column: Column) : FlowLogic<Unit>() {
        // This flow messes up the FlowState. Such that the checkpoint cannot be deserialized anymore.
        @CordaSerializable
        enum class Column(val sqlName: String) {
            CHECKPOINT("checkpoint_value"),
            FLOW_STATE("flow_state")
        }

        @Suspendable
        override fun call() {
            val selectBlobSqlStatement = "select checkpoint_blob_id from node_checkpoints where flow_id = '${flowId.uuid}'"
            logger.info(selectBlobSqlStatement)
            val blobId = serviceHub.jdbcSession().prepareStatement(selectBlobSqlStatement).use {
                it.executeQuery().use { query ->
                    query.next()
                    query.getLong(1)
                }
            }
            val muddleBlobSqlStatement = "update node_checkpoint_blobs set ${column.sqlName} = 'DEADBEEF' where id = $blobId"
            logger.info(muddleBlobSqlStatement)
            serviceHub.jdbcSession().prepareStatement(muddleBlobSqlStatement).executeUpdate()
        }
    }

    @StartableByRPC
    class CheckCompatableFlow(private val flowId: StateMachineRunId) : FlowLogic<Boolean>() {
        @Suspendable
        override fun call() : Boolean {
            val sqlStatement = "select compatible from node_checkpoints where flow_id = '${flowId.uuid}'"
            return serviceHub.jdbcSession().prepareStatement(sqlStatement).use {
                it.executeQuery().use {query ->
                    query.next()
                    query.getBoolean(1)
                }
            }
        }
    }

    @StartableByRPC
    class GetTimeStampFlow(private  val flowId: StateMachineRunId) : FlowLogic<String>() {
        @Suspendable
        override fun call() : String {
            val sqlStatement = "select timestamp from node_checkpoints where flow_id = '${flowId.uuid}'"
            return serviceHub.jdbcSession().prepareStatement(sqlStatement).use {
                it.executeQuery().use {query ->
                    query.next()
                    query.getString(1)
                }
            }
        }
    }

}