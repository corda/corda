package net.corda.core.node

import net.corda.core.CordaRuntimeException
import net.corda.core.KeepForDJVM
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.Party
import net.corda.core.internal.noPackageOverlap
import net.corda.core.internal.requirePackageValid
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.DeprecatedConstructorForDeserialization
import net.corda.core.utilities.days
import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import java.util.Collections.unmodifiableList
import java.util.Collections.unmodifiableMap

// DOCSTART 1
/**
 * Network parameters are a set of values that every node participating in the zone needs to agree on and use to
 * correctly interoperate with each other.
 *
 * @property minimumPlatformVersion Minimum version of Corda platform that is required for nodes in the network.
 * @property notaries List of well known and trusted notary identities with information on validation type.
 * @property maxMessageSize Maximum allowed size in bytes of an individual message sent over the wire.
 * @property maxTransactionSize Maximum permitted transaction size in bytes.
 * @property modifiedTime ([AutoAcceptable]) Last modification time of network parameters set.
 * @property epoch ([AutoAcceptable]) Version number of the network parameters. Starting from 1, this will always increment on each new set
 * of parameters.
 * @property whitelistedContractImplementations ([AutoAcceptable]) List of whitelisted jars containing contract code for each contract class.
 *  This will be used by [net.corda.core.contracts.WhitelistedByZoneAttachmentConstraint].
 *  [You can learn more about contract constraints here](https://docs.corda.net/api-contract-constraints.html).
 * @property packageOwnership ([AutoAcceptable]) List of the network-wide java packages that were successfully claimed by their owners.
 * Any CorDapp JAR that offers contracts and states in any of these packages must be signed by the owner.
 * @property eventHorizon Time after which nodes will be removed from the network map if they have not been seen
 * during this period.
 * @property recoveryMaximumBackupInterval A default value, that will be used by the Ledger Recovery flows to set how far back in time to
 * consider for recovery. The expectation is that a node will restore to a database backup that is no older than this, by default, when
 * attempting a recovery. This value can be overridden by specifying an override to the flow. It can also be overridden if the same parameter
 * is specified, per-node in the node configuration. An override to the flow takes priority in terms of overrides. It is optional in both
 * the network parameters and the node configuration however if no values are set then it needs to be specified in the flow.
 * @property confidentialIdentityMinimumBackupInterval A default value for the  minimum age of a generated confidential identity key before
 * it can be used. This can be overridden in the node configuration or if a more recent database backup is indicated via RPC / shell. It is
 * optional in both the network parameters and the node configuration and if no value is set for either then it is assumed to be zero.
 */
@KeepForDJVM
@CordaSerializable
@Suppress("LongParameterList")
data class NetworkParameters(
        val minimumPlatformVersion: Int,
        val notaries: List<NotaryInfo>,
        val maxMessageSize: Int,
        val maxTransactionSize: Int,
        @AutoAcceptable val modifiedTime: Instant,
        @AutoAcceptable val epoch: Int,
        @AutoAcceptable val whitelistedContractImplementations: Map<String, List<AttachmentId>>,
        val eventHorizon: Duration,
        @AutoAcceptable val packageOwnership: Map<String, PublicKey>,
        val recoveryMaximumBackupInterval: Duration? = null,
        val confidentialIdentityMinimumBackupInterval: Duration? = null
) {
    // DOCEND 1
    @DeprecatedConstructorForDeserialization(version = 1)
    constructor(minimumPlatformVersion: Int,
                notaries: List<NotaryInfo>,
                maxMessageSize: Int,
                maxTransactionSize: Int,
                modifiedTime: Instant,
                epoch: Int,
                whitelistedContractImplementations: Map<String, List<AttachmentId>>
    ) : this(minimumPlatformVersion,
            notaries,
            maxMessageSize,
            maxTransactionSize,
            modifiedTime,
            epoch,
            whitelistedContractImplementations,
            Int.MAX_VALUE.days,
            emptyMap()
    )

    @DeprecatedConstructorForDeserialization(version = 2)
    constructor(minimumPlatformVersion: Int,
                notaries: List<NotaryInfo>,
                maxMessageSize: Int,
                maxTransactionSize: Int,
                modifiedTime: Instant,
                epoch: Int,
                whitelistedContractImplementations: Map<String, List<AttachmentId>>,
                eventHorizon: Duration
    ) : this(minimumPlatformVersion,
            notaries,
            maxMessageSize,
            maxTransactionSize,
            modifiedTime,
            epoch,
            whitelistedContractImplementations,
            eventHorizon,
            emptyMap()
    )

    @DeprecatedConstructorForDeserialization(version = 3)
    constructor(minimumPlatformVersion: Int,
                notaries: List<NotaryInfo>,
                maxMessageSize: Int,
                maxTransactionSize: Int,
                modifiedTime: Instant,
                epoch: Int,
                whitelistedContractImplementations: Map<String, List<AttachmentId>>,
                eventHorizon: Duration,
                packageOwnership: Map<String, PublicKey>
    ) : this(minimumPlatformVersion,
            notaries,
            maxMessageSize,
            maxTransactionSize,
            modifiedTime,
            epoch,
            whitelistedContractImplementations,
            eventHorizon,
            packageOwnership,
            recoveryMaximumBackupInterval = null,
            confidentialIdentityMinimumBackupInterval = null
    )

    init {
        require(minimumPlatformVersion > 0) { "Minimum platform level must be at least 1" }
        require(notaries.distinctBy { it.identity } == notaries) { "Duplicate notary identities" }
        require(epoch > 0) { "Epoch must be at least 1" }
        require(maxMessageSize > 0) { "Maximum message size must be at least 1" }
        require(maxTransactionSize > 0) { "Maximum transaction size must be at least 1" }
        require(!eventHorizon.isNegative) { "Event Horizon must be a positive value" }
        packageOwnership.keys.forEach(::requirePackageValid)
        require(noPackageOverlap(packageOwnership.keys)) { "Multiple packages added to the packageOwnership overlap." }
        require(recoveryMaximumBackupInterval == null || !recoveryMaximumBackupInterval.isNegative) {
            "Recovery maximum backup interval must be a positive value"
        }
        require(confidentialIdentityMinimumBackupInterval == null || !confidentialIdentityMinimumBackupInterval.isNegative) {
            "Confidential Identities maximum backup interval must be a positive value"
        }
    }

    /**
     * This is to address backwards compatibility of the API, invariant to package ownership
     * addresses bug CORDA-2769
     */
    fun copy(minimumPlatformVersion: Int = this.minimumPlatformVersion,
             notaries: List<NotaryInfo> = this.notaries,
             maxMessageSize: Int = this.maxMessageSize,
             maxTransactionSize: Int = this.maxTransactionSize,
             modifiedTime: Instant = this.modifiedTime,
             epoch: Int = this.epoch,
             whitelistedContractImplementations: Map<String, List<AttachmentId>> = this.whitelistedContractImplementations,
             eventHorizon: Duration = this.eventHorizon,
             packageOwnership: Map<String, PublicKey> = this.packageOwnership
    ): NetworkParameters {
        return NetworkParameters(
                minimumPlatformVersion = minimumPlatformVersion,
                notaries = notaries,
                maxMessageSize = maxMessageSize,
                maxTransactionSize = maxTransactionSize,
                modifiedTime = modifiedTime,
                epoch = epoch,
                whitelistedContractImplementations = whitelistedContractImplementations,
                eventHorizon = eventHorizon,
                packageOwnership = packageOwnership,
                recoveryMaximumBackupInterval = recoveryMaximumBackupInterval,
                confidentialIdentityMinimumBackupInterval = confidentialIdentityMinimumBackupInterval
        )
    }

    /**
     * This is to address backwards compatibility of the API, invariant to package ownership
     * addresses bug CORDA-2769
     */
    fun copy(minimumPlatformVersion: Int = this.minimumPlatformVersion,
             notaries: List<NotaryInfo> = this.notaries,
             maxMessageSize: Int = this.maxMessageSize,
             maxTransactionSize: Int = this.maxTransactionSize,
             modifiedTime: Instant = this.modifiedTime,
             epoch: Int = this.epoch,
             whitelistedContractImplementations: Map<String, List<AttachmentId>> = this.whitelistedContractImplementations,
             eventHorizon: Duration = this.eventHorizon
    ): NetworkParameters {
        return NetworkParameters(
                minimumPlatformVersion = minimumPlatformVersion,
                notaries = notaries,
                maxMessageSize = maxMessageSize,
                maxTransactionSize = maxTransactionSize,
                modifiedTime = modifiedTime,
                epoch = epoch,
                whitelistedContractImplementations = whitelistedContractImplementations,
                eventHorizon = eventHorizon,
                packageOwnership = packageOwnership,
                recoveryMaximumBackupInterval = recoveryMaximumBackupInterval,
                confidentialIdentityMinimumBackupInterval = confidentialIdentityMinimumBackupInterval
        )
    }

    /**
     * This is to address backwards compatibility of the API, invariant to package ownership
     * addresses bug CORDA-2769
     */
    fun copy(minimumPlatformVersion: Int = this.minimumPlatformVersion,
             notaries: List<NotaryInfo> = this.notaries,
             maxMessageSize: Int = this.maxMessageSize,
             maxTransactionSize: Int = this.maxTransactionSize,
             modifiedTime: Instant = this.modifiedTime,
             epoch: Int = this.epoch,
             whitelistedContractImplementations: Map<String, List<AttachmentId>> = this.whitelistedContractImplementations
    ): NetworkParameters {
        return NetworkParameters(
                minimumPlatformVersion = minimumPlatformVersion,
                notaries = notaries,
                maxMessageSize = maxMessageSize,
                maxTransactionSize = maxTransactionSize,
                modifiedTime = modifiedTime,
                epoch = epoch,
                whitelistedContractImplementations = whitelistedContractImplementations,
                eventHorizon = eventHorizon,
                packageOwnership = packageOwnership,
                recoveryMaximumBackupInterval = recoveryMaximumBackupInterval,
                confidentialIdentityMinimumBackupInterval = confidentialIdentityMinimumBackupInterval
        )
    }

    override fun toString(): String {
        return """NetworkParameters {
      minimumPlatformVersion=$minimumPlatformVersion
      notaries=$notaries
      maxMessageSize=$maxMessageSize
      maxTransactionSize=$maxTransactionSize
      whitelistedContractImplementations {
        ${whitelistedContractImplementations.entries.joinToString("\n    ")}
      }
      eventHorizon=$eventHorizon
      packageOwnership {
        ${packageOwnership.entries.joinToString("\n    ") { "$it.key -> ${it.value.toStringShort()}" }}
      }
      modifiedTime=$modifiedTime
      epoch=$epoch
      transactionRecoveryPeriod=$recoveryMaximumBackupInterval
      confidentialIdentityPreGenerationPeriod=$confidentialIdentityMinimumBackupInterval
  }"""
    }

    fun toImmutable(): NetworkParameters {
        return NetworkParameters(
            minimumPlatformVersion = minimumPlatformVersion,
            notaries = unmodifiable(notaries),
            maxMessageSize = maxMessageSize,
            maxTransactionSize = maxTransactionSize,
            modifiedTime = modifiedTime,
            epoch = epoch,
            whitelistedContractImplementations = unmodifiable(whitelistedContractImplementations) { entry ->
                unmodifiableList(entry.value)
            },
            eventHorizon = eventHorizon,
            packageOwnership = unmodifiable(packageOwnership),
            recoveryMaximumBackupInterval = recoveryMaximumBackupInterval,
            confidentialIdentityMinimumBackupInterval = confidentialIdentityMinimumBackupInterval
        )
    }
}

private fun <T> unmodifiable(list: List<T>): List<T> {
    return if (list.isEmpty()) {
        emptyList()
    } else {
        unmodifiableList(list)
    }
}

private inline fun <K, V> unmodifiable(map: Map<K, V>, transform: (Map.Entry<K, V>) -> V = Map.Entry<K, V>::value): Map<K, V> {
    return if (map.isEmpty()) {
        emptyMap()
    } else {
        unmodifiableMap(map.mapValues(transform))
    }
}

/**
 * Data class storing information about notaries available in the network.
 * @property identity Identity of the notary (note that it can be an identity of the distributed node).
 * @property validating Indicates if the notary is validating.
 */
@KeepForDJVM
@CordaSerializable
data class NotaryInfo(val identity: Party, val validating: Boolean)

/**
 * When a Corda feature cannot be used due to the node's compatibility zone not enforcing a high enough minimum platform
 * version.
 */
class ZoneVersionTooLowException(message: String) : CordaRuntimeException(message)
