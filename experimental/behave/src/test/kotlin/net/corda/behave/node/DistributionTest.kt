package net.corda.behave.node

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import kotlin.test.assertNotNull

class DistributionTest {

    /**
     *  NOTE: ensure you have correctly setup the system environment variables:
     *  CORDA_ARTIFACTORY_USERNAME
     *  CORDA_ARTIFACTORY_PASSWORD
     */

    @Test
    fun `resolve OS distribution from Artifactory`() {
        val distribution = Distribution.fromVersionString("OS-3.1-corda")
        assertNotNull(distribution.artifactUrlMap)
        assertThat(distribution.artifactUrlMap!!.size).isGreaterThanOrEqualTo(3)
        distribution.ensureAvailable()
        println("Check contents of ${distribution.path}")
    }

    @Test
    fun `resolve Enterprise distribution from Artifactory`() {
        val distribution = Distribution.fromVersionString("ENT-3.0")
        assertNotNull(distribution.artifactUrlMap)
        assertThat(distribution.artifactUrlMap!!.size).isGreaterThanOrEqualTo(4)
        distribution.ensureAvailable()
        println("Check contents of ${distribution.path}")
    }

    @Test
    fun `resolve OS snapshot distribution from Artifactory`() {
        val distribution = Distribution.fromVersionString("OS-4.0-SNAPSHOT")
        assertNotNull(distribution.artifactUrlMap)
        assertThat(distribution.artifactUrlMap!!.size).isGreaterThanOrEqualTo(3)
        distribution.ensureAvailable()
        println("Check contents of ${distribution.path}")
    }

    @Test
    fun `resolve Enterprise snapshot distribution from Artifactory`() {
        val distribution = Distribution.fromVersionString("ENT-4.0-SNAPSHOT")
        assertNotNull(distribution.artifactUrlMap)
        assertThat(distribution.artifactUrlMap!!.size).isGreaterThanOrEqualTo(4)
        distribution.ensureAvailable()
        println("Check contents of ${distribution.path}")
    }
}