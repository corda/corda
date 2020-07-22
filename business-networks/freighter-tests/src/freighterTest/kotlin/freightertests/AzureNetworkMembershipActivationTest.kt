package freightertests

import freighter.deployments.DeploymentContext
import freighter.machine.AzureMachineProvider
import freighter.machine.DeploymentMachineProvider
import freighter.testing.AzureTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.spi.ExtendedLogger

@AzureTest
class AzureNetworkMembershipActivationTest: BaseNetworkMembershipActivationFreighterTests() {

    override val machineProvider: DeploymentMachineProvider = AzureMachineProvider()

    companion object {
        val logger: ExtendedLogger = LogManager.getContext().getLogger(AzureNetworkMembershipActivationTest::class.java.name)
    }

    @Test
    fun testMembershipActivationWith5Participants() {
        val numberOfParticipants = 5
        val benchmark = getBenchMarkResult(numberOfParticipants)
        Assertions.assertTrue(benchmark.membershipActiveTime <= 300000)
        Assertions.assertTrue(benchmark.membershipSuspendTime <= 300000)
        Assertions.assertTrue(benchmark.membershipUpdateTime <= 300000)
    }

    @Test
    fun testMembershipActivationWith10Participants() {
        val numberOfParticipants = 10
        val benchmark = getBenchMarkResult(numberOfParticipants)
        Assertions.assertTrue(benchmark.membershipActiveTime <= 300000)
        Assertions.assertTrue(benchmark.membershipSuspendTime <= 300000)
        Assertions.assertTrue(benchmark.membershipUpdateTime <= 300000)
    }

    @Test
    fun testMembershipActivationWith15Participants() {
        val numberOfParticipants = 15
        val benchmark = getBenchMarkResult(numberOfParticipants)
        Assertions.assertTrue(benchmark.membershipActiveTime <= 300000)
        Assertions.assertTrue(benchmark.membershipSuspendTime <= 300000)
        Assertions.assertTrue(benchmark.membershipUpdateTime <= 300000)
    }

    @Test
    fun testMembershipActivationWith20Participants() {
        val numberOfParticipants = 20
        val benchmark = getBenchMarkResult(numberOfParticipants)
        Assertions.assertTrue(benchmark.membershipActiveTime <= 300000)
        Assertions.assertTrue(benchmark.membershipSuspendTime <= 300000)
        Assertions.assertTrue(benchmark.membershipUpdateTime <= 300000)
    }

    @Test
    fun testMembershipActivationWith40Participants() {
        val numberOfParticipants = 40
        val benchmark = getBenchMarkResult(numberOfParticipants)
        Assertions.assertTrue(benchmark.membershipActiveTime <= 300000)
        Assertions.assertTrue(benchmark.membershipSuspendTime <= 300000)
        Assertions.assertTrue(benchmark.membershipUpdateTime <= 300000)
    }

    private fun getBenchMarkResult(numberOfParticipants: Int): BenchMarkResult {
        val deploymentContext = DeploymentContext(machineProvider, nms, artifactoryUsername, artifactoryPassword)
        val benchmark = runNetworkMembershipActivationBenchmark(deploymentContext, numberOfParticipants)
        logger.info("Membership Active Time BenchMark ${benchmark.membershipActiveTime}")
        logger.info("Membership Update Time BenchMark ${benchmark.membershipUpdateTime}")
        logger.info("Membership Suspend Time BenchMark ${benchmark.membershipSuspendTime}")
        return benchmark
    }
}