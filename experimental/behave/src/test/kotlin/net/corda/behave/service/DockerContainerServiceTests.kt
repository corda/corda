package net.corda.behave.service

import net.corda.behave.network.Network
import net.corda.behave.node.Distribution
import net.corda.behave.seconds
import net.corda.behave.service.containers.DockerContainerService
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class DockerContainerServiceTests {

    private val DOORMAN_V3_SNAPSHOT = Distribution.fromDockerImage(Distribution.Type.CORDA, baseImage = "localhost:5000/r3/doorman", imageTag = "3.0-snapshot")
    private val NOTARY_V3_SNAPSHOT = Distribution.fromDockerImage(Distribution.Type.CORDA, baseImage = "localhost:5000/r3/notary", imageTag = "3.0-snapshot")
    private val CORDA_V3_SNAPSHOT = Distribution.fromDockerImage(Distribution.Type.CORDA, baseImage = "localhost:5000/r3/corda", imageTag = "3.0-snapshot")
    private val CORDAPP_HC_V3_SNAPSHOT = Distribution.fromDockerImage(Distribution.Type.CORDA, baseImage = "localhost:5000/r3/notary-healthcheck", imageTag = "3.0-snapshot")

    @Test
    fun `corda network using docker can be started and stopped`() {
        /*
         * Configure basic network from Docker registered images
         *
         * REPOSITORY                                    TAG                 IMAGE ID            CREATED             SIZE
            localhost:5000/r3/notary-healthcheck          3.0-snapshot        cceb90090b29        2 days ago          163 MB
            localhost:5000/r3/corda                       3.0-snapshot        3939785a96b3        2 days ago          139 MB
            localhost:5000/r3/doorman                     3.0-snapshot        6e98046ee9da        2 days ago          82 MB
            localhost:5000/r3/notary                      3.0-snapshot        6e98046ee9da        2 days ago          82 MB
         */
        val network = Network
                .new(CORDA_V3_SNAPSHOT)
//                .addNode("Doorman", DOORMAN_V3_SNAPSHOT)
//                .addNode("Notary", NOTARY_V3_SNAPSHOT, notaryType = NotaryType.NON_VALIDATING)
                .addNode("Corda", CORDA_V3_SNAPSHOT)
//                .addNode("Notary-healthcheck", CORDAPP_HC_V3_SNAPSHOT)
                .generate(false)
        network.use {
            it.waitUntilRunning(30.seconds)
            it.signal()
            it.keepAlive(30.seconds)
        }
    }

    @Test
    fun `docker container can be started and stopped`() {

//    DOCKER_HOST=tcp://192.168.99.100:2376
//    DOCKER_API_VERSION=1.23
//    DOCKER_TLS_VERIFY=1
//    DOCKER_CERT_PATH=/Users/josecoll/.minikube/certs

//        DockerHost.from("tcp://192.168.99.100:2376", "/Users/josecoll/.minikube/certs")

        println("DOCKER_HOST ${System.getenv("DOCKER_HOST")}")
        println("DOCKER_CERT_PATH ${System.getenv("DOCKER_CERT_PATH")}")

//    localhost:5000/r3/cora:3.0-snapshot
        val service = DockerContainerService(internalPort = 5000,
                baseImage = "localhost:5000/r3/corda",
                imageTag = "3.0-snapshot")
        val didStart = service.start()
        assertThat(didStart).isTrue()
    }
}