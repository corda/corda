package freightertests

import freighter.deployments.DeploymentContext
import freighter.machine.DeploymentMachineProvider
import freighter.machine.DockerMachineProvider
import freighter.testing.DockerTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@DockerTest
class DockerNetworkMembershipActivationTest: BaseNetworkMembershipActivationFreighterTests() {

    override val machineProvider: DeploymentMachineProvider = DockerMachineProvider()

    @Test
    fun test() {
        val numberOfParticipants = 2
        val deploymentContext = DeploymentContext(machineProvider, nms, artifactoryUsername, artifactoryPassword)
        val benchmark = runNetworkMembershipActivationBenchmark(deploymentContext, numberOfParticipants)
        System.err.println("Membership Active Time BenchMark ${benchmark.membershipActiveTime}")
        System.err.println("Membership Update Time BenchMark ${benchmark.membershipUpdateTime}")
        System.err.println("Membership Suspend Time BenchMark ${benchmark.membershipSuspendTime}")
        assertTrue(benchmark.membershipActiveTime <= 300000)
        assertTrue(benchmark.membershipSuspendTime <= 300000)
        assertTrue(benchmark.membershipUpdateTime <= 300000)


    }
}