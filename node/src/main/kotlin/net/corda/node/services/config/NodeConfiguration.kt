package net.corda.node.services.config

import com.google.common.net.HostAndPort
import net.corda.core.div
import net.corda.core.node.VersionInfo
import net.corda.core.node.services.ServiceInfo
import net.corda.node.internal.NetworkMapInfo
import net.corda.node.internal.Node
import net.corda.node.serialization.NodeClock
import net.corda.node.services.messaging.CertificateChainCheckPolicy
import net.corda.node.services.network.NetworkMapService
import net.corda.node.utilities.TestClock
import net.corda.nodeapi.User
import net.corda.nodeapi.config.OldConfig
import net.corda.nodeapi.config.SSLConfiguration
import org.bouncycastle.asn1.x500.X500Name
import java.net.URL
import java.nio.file.Path
import java.util.*

interface NodeConfiguration : SSLConfiguration {
    val baseDirectory: Path
    override val certificatesDirectory: Path get() = baseDirectory / "certificates"
    val myLegalName: X500Name
    val networkMapService: NetworkMapInfo?
    val minimumPlatformVersion: Int
    val nearestCity: String
    val emailAddress: String
    val exportJMXto: String
    val dataSourceProperties: Properties
    val rpcUsers: List<User>
    val devMode: Boolean
    val certificateSigningService: URL
    val certificateChainCheckPolicies: List<CertChainPolicyConfig>
    val verifierType: VerifierType
}

data class FullNodeConfiguration(
        // TODO Remove this subsitution value and use baseDirectory as the subsitution instead
        @Deprecated(
                "This is a subsitution value which points to the baseDirectory and is manually added into the config before parsing",
                ReplaceWith("baseDirectory"))
        val basedir: Path,
        override val myLegalName: X500Name,
        override val nearestCity: String,
        override val emailAddress: String,
        override val keyStorePassword: String,
        override val trustStorePassword: String,
        override val dataSourceProperties: Properties,
        override val certificateSigningService: URL,
        override val networkMapService: NetworkMapInfo?,
        override val minimumPlatformVersion: Int = 1,
        override val rpcUsers: List<User>,
        override val verifierType: VerifierType,
        val useHTTPS: Boolean,
        @OldConfig("artemisAddress")
        val p2pAddress: HostAndPort,
        val rpcAddress: HostAndPort?,
        // TODO This field is slightly redundant as p2pAddress is sufficient to hold the address of the node's MQ broker.
        // Instead this should be a Boolean indicating whether that broker is an internal one started by the node or an external one
        val messagingServerAddress: HostAndPort?,
        val extraAdvertisedServiceIds: List<String>,
        val notaryNodeId: Int?,
        val notaryNodeAddress: HostAndPort?,
        val notaryClusterAddresses: List<HostAndPort>,
        override val certificateChainCheckPolicies: List<CertChainPolicyConfig>,
        override val devMode: Boolean = false,
        val useTestClock: Boolean = false
) : NodeConfiguration {
    /** This is not retrieved from the config file but rather from a command line argument. */
    @Suppress("DEPRECATION")
    override val baseDirectory: Path get() = basedir
    override val exportJMXto: String get() = "http"

    init {
        // This is a sanity feature do not remove.
        require(!useTestClock || devMode) { "Cannot use test clock outside of dev mode" }
        // TODO Move this to ArtemisMessagingServer
        rpcUsers.forEach {
            require(it.username.matches("\\w+".toRegex())) { "Username ${it.username} contains invalid characters" }
        }
    }

    fun createNode(versionInfo: VersionInfo): Node {
        val advertisedServices = extraAdvertisedServiceIds
                .filter(String::isNotBlank)
                .map { ServiceInfo.parse(it) }
                .toMutableSet()
        if (networkMapService == null) advertisedServices += ServiceInfo(NetworkMapService.type)

        return Node(this, advertisedServices, versionInfo, if (useTestClock) TestClock() else NodeClock())
    }
}

enum class VerifierType {
    InMemory,
    OutOfProcess
}

enum class CertChainPolicyType {
    Any,
    RootMustMatch,
    LeafMustMatch,
    MustContainOneOf
}

data class CertChainPolicyConfig(val role: String, val policy: CertChainPolicyType, val trustedAliases: Set<String>) {
    val certificateChainCheckPolicy: CertificateChainCheckPolicy get() {
        return when (policy) {
            CertChainPolicyType.Any -> CertificateChainCheckPolicy.Any
            CertChainPolicyType.RootMustMatch -> CertificateChainCheckPolicy.RootMustMatch
            CertChainPolicyType.LeafMustMatch -> CertificateChainCheckPolicy.LeafMustMatch
            CertChainPolicyType.MustContainOneOf -> CertificateChainCheckPolicy.MustContainOneOf(trustedAliases)
        }
    }
}
