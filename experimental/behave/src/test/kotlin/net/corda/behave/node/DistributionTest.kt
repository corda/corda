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
        val distribution = Distribution.fromArtifactory(Distribution.Type.CORDA_OS, "3.1-corda")
        assertNotNull(distribution.artifactUrlMap)
        assertThat(distribution.artifactUrlMap!!.size).isGreaterThanOrEqualTo(3)
        // -DSTAGING_ROOT=${STAGING_ROOT}
        distribution.ensureAvailable()
        println("Check contents of ${distribution.path}")
    }

    @Test
    fun `resolve Enterprise distribution from Artifactory`() {
        val distribution = Distribution.fromArtifactory(Distribution.Type.CORDA_ENTERPRISE, "3.0.0-RC01")
        assertNotNull(distribution.artifactUrlMap)
        assertThat(distribution.artifactUrlMap!!.size).isGreaterThanOrEqualTo(5)
        // -DSTAGING_ROOT=${STAGING_ROOT}
        distribution.ensureAvailable()
        println("Check contents of ${distribution.path}")
    }
}