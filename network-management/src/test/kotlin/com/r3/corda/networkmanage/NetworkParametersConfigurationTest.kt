package com.r3.corda.networkmanage

import com.r3.corda.networkmanage.doorman.NetworkParametersConfiguration
import com.r3.corda.networkmanage.doorman.NotaryConfiguration
import com.r3.corda.networkmanage.doorman.parseNetworkParameters
import com.r3.corda.networkmanage.doorman.parseNetworkParametersFrom
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.copyTo
import net.corda.core.internal.deleteIfExists
import net.corda.core.serialization.serialize
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.createNodeInfoAndSigned
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant

class NetworkParametersConfigurationTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private fun generateNetworkParametersConfiguration() = NetworkParametersConfiguration(
            notaries = listOf(
                    NotaryConfiguration(generateNodeInfoFile("Test1"), true),
                    NotaryConfiguration(generateNodeInfoFile("Test2"), false)
            ),
            maxMessageSize = 100,
            maxTransactionSize = 100,
            minimumPlatformVersion = 1
    )

    private fun generateNodeInfoFile(organisation: String): Path {
        val (_, signedNodeInfo) = createNodeInfoAndSigned(CordaX500Name(organisation, "Madrid", "ES"))
        val path = tempFolder.newFile().toPath()
        path.deleteIfExists()
        signedNodeInfo.serialize().open().copyTo(path)
        return path
    }

    @Test
    fun `reads an existing file`() {
        val networkParameters = parseNetworkParameters(generateNetworkParametersConfiguration())
        assertThat(networkParameters.minimumPlatformVersion).isEqualTo(1)
        val notaries = networkParameters.notaries
        assertThat(notaries).hasSize(2)
        assertThat(notaries[0].validating).isTrue()
        assertThat(notaries[1].validating).isFalse()
        assertThat(networkParameters.maxMessageSize).isEqualTo(100)
        assertThat(networkParameters.maxTransactionSize).isEqualTo(100)
        // This is rather weak, though making this an exact test will require mocking a clock.
        assertThat(networkParameters.modifiedTime).isBefore(Instant.now())
        assertThat(networkParameters.epoch).isEqualTo(1)
    }

    @Test
    fun `throws on a non-existing file`() {
        assertThatThrownBy {
            parseNetworkParametersFrom(Paths.get("notHere"))
        }.isInstanceOf(IllegalStateException::class.java)
    }
}