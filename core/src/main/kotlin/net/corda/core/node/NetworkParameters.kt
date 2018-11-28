package net.corda.core.node

import net.corda.core.CordaRuntimeException
import net.corda.core.KeepForDJVM
import net.corda.core.contracts.ContractClassName
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.Party
import net.corda.core.internal.requirePackageValid
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.DeprecatedConstructorForDeserialization
import net.corda.core.utilities.days
import java.lang.reflect.Method
import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaGetter

// DOCSTART 1
/**
 * Network parameters are a set of values that every node participating in the zone needs to agree on and use to
 * correctly interoperate with each other.
 * @property minimumPlatformVersion Minimum version of Corda platform that is required for nodes in the network.
 * @property notaries List of well known and trusted notary identities with information on validation type.
 * @property maxMessageSize This is currently ignored. However, it will be wired up in a future release.
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
 * during this period
 */
@KeepForDJVM
@CordaSerializable
data class NetworkParameters(
        val minimumPlatformVersion: Int,
        val notaries: List<NotaryInfo>,
        val maxMessageSize: Int,
        val maxTransactionSize: Int,
        @AutoAcceptable val modifiedTime: Instant,
        @AutoAcceptable val epoch: Int,
        @AutoAcceptable val whitelistedContractImplementations: Map<String, List<AttachmentId>>,
        val eventHorizon: Duration,
        @AutoAcceptable val packageOwnership: Map<String, PublicKey>
) {
    // DOCEND 1
    @DeprecatedConstructorForDeserialization(1)
    constructor (minimumPlatformVersion: Int,
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

    @DeprecatedConstructorForDeserialization(2)
    constructor (minimumPlatformVersion: Int,
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

    companion object {
        private val memberPropertyPartition = NetworkParameters::class.declaredMemberProperties.asSequence()
                .partition { it.isAutoAcceptable() }
        private val autoAcceptableNamesAndGetters = memberPropertyPartition.first.associateBy({ it.name }, { it.javaGetter })
        private val nonAutoAcceptableGetters = memberPropertyPartition.second.map { it.javaGetter }
        val autoAcceptablePropertyNames = autoAcceptableNamesAndGetters.keys

        /**
         * Returns true if the [fullClassName] is in a subpackage of [packageName].
         * E.g.: "com.megacorp" owns "com.megacorp.tokens.MegaToken"
         *
         * Note: The ownership check is ignoring case to prevent people from just releasing a jar with: "com.megaCorp.megatoken" and pretend they are MegaCorp.
         * By making the check case insensitive, the node will require that the jar is signed by MegaCorp, so the attack fails.
         */
        private fun owns(packageName: String, fullClassName: String) = fullClassName.startsWith("$packageName.", ignoreCase = true)

        // Make sure that packages don't overlap so that ownership is clear.
        fun noOverlap(packages: Collection<String>) = packages.all { currentPackage ->
            packages.none { otherPackage -> otherPackage != currentPackage && otherPackage.startsWith("$currentPackage.") }
        }

        private fun KProperty1<out NetworkParameters, Any?>.isAutoAcceptable(): Boolean {
            return this.findAnnotation<AutoAcceptable>() != null
        }
    }

    init {
        require(minimumPlatformVersion > 0) { "Minimum platform level must be at least 1" }
        require(notaries.distinctBy { it.identity } == notaries) { "Duplicate notary identities" }
        require(epoch > 0) { "Epoch must be at least 1" }
        require(maxMessageSize > 0) { "Maximum message size must be at least 1" }
        require(maxTransactionSize > 0) { "Maximum transaction size must be at least 1" }
        require(!eventHorizon.isNegative) { "Event Horizon must be a positive value" }
        packageOwnership.keys.forEach(::requirePackageValid)
        require(noOverlap(packageOwnership.keys)) { "Multiple packages added to the packageOwnership overlap." }
    }

    fun copy(minimumPlatformVersion: Int,
             notaries: List<NotaryInfo>,
             maxMessageSize: Int,
             maxTransactionSize: Int,
             modifiedTime: Instant,
             epoch: Int,
             whitelistedContractImplementations: Map<String, List<AttachmentId>>
    ): NetworkParameters {
        return copy(minimumPlatformVersion = minimumPlatformVersion,
                notaries = notaries,
                maxMessageSize = maxMessageSize,
                maxTransactionSize = maxTransactionSize,
                modifiedTime = modifiedTime,
                epoch = epoch,
                whitelistedContractImplementations = whitelistedContractImplementations,
                eventHorizon = eventHorizon)
    }

    fun copy(minimumPlatformVersion: Int,
             notaries: List<NotaryInfo>,
             maxMessageSize: Int,
             maxTransactionSize: Int,
             modifiedTime: Instant,
             epoch: Int,
             whitelistedContractImplementations: Map<String, List<AttachmentId>>,
             eventHorizon: Duration
    ): NetworkParameters {
        return copy(minimumPlatformVersion = minimumPlatformVersion,
                notaries = notaries,
                maxMessageSize = maxMessageSize,
                maxTransactionSize = maxTransactionSize,
                modifiedTime = modifiedTime,
                epoch = epoch,
                whitelistedContractImplementations = whitelistedContractImplementations,
                eventHorizon = eventHorizon)
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
      modifiedTime=$modifiedTime
      epoch=$epoch,
      packageOwnership= {
        ${packageOwnership.entries.joinToString("\n    ") { "$it.key -> ${it.value.toStringShort()}" }}
      }
  }"""
    }

    /**
     * Returns the public key of the package owner of the [contractClassName], or null if not owned.
     */
    fun getOwnerOf(contractClassName: ContractClassName): PublicKey? = this.packageOwnership.filterKeys { packageName -> owns(packageName, contractClassName) }.values.singleOrNull()

    /**
     * Returns the public key of the package owner if any of [contractClassName] match, or null if not owned.
     */
    fun getOwnerOf(contractClassNames: Set<ContractClassName>): PublicKey? {
        val ownerKeys = contractClassNames.map { getOwnerOf(it) }
        return ownerKeys.find { it != null }
    }

    /**
     * Returns true if the only properties changed in [newNetworkParameters] are [AutoAcceptable] and not
     * included in the [excludedParameterNames]
     */
    fun canAutoAccept(newNetworkParameters: NetworkParameters, excludedParameterNames: Set<String>): Boolean {
        return nonAutoAcceptableGetters.none { valueChanged(newNetworkParameters, it) } &&
                autoAcceptableNamesAndGetters.none { excludedParameterNames.contains(it.key) && valueChanged(newNetworkParameters, it.value) }
    }

    private fun valueChanged(newNetworkParameters: NetworkParameters, getter: Method?): Boolean {
        val propertyValue = getter?.invoke(this)
        val newPropertyValue = getter?.invoke(newNetworkParameters)
        return propertyValue != newPropertyValue
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

private fun KProperty1<out NetworkParameters, Any?>.isAutoAcceptable(): Boolean {
    return this.findAnnotation<AutoAcceptable>() != null
}