package net.corda.core.node

import net.corda.core.CordaRuntimeException
import net.corda.core.KeepForDJVM
import net.corda.core.identity.Party
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.DeprecatedConstructorForDeserialization
import net.corda.core.utilities.days
import java.security.PublicKey
import java.time.Duration
import java.time.Instant

/**
 * Network parameters are a set of values that every node participating in the zone needs to agree on and use to
 * correctly interoperate with each other.
 * @property minimumPlatformVersion Minimum version of Corda platform that is required for nodes in the network.
 * @property notaries List of well known and trusted notary identities with information on validation type.
 * @property maxMessageSize This is currently ignored. However, it will be wired up in a future release.
 * @property maxTransactionSize Maximum permitted transaction size in bytes.
 * @property modifiedTime Last modification time of network parameters set.
 * @property epoch Version number of the network parameters. Starting from 1, this will always increment on each new set
 * of parameters.
 * @property whitelistedContractImplementations List of whitelisted jars containing contract code for each contract class.
 *  This will be used by [net.corda.core.contracts.WhitelistedByZoneAttachmentConstraint]. [You can learn more about contract constraints here](https://docs.corda.net/api-contract-constraints.html).
 * @property packageOwnership List of the network-wide java packages that were successfully claimed by their owners. Any CorDapp JAR that offers contracts and states in any of these packages must be signed by the owner.
 * @property eventHorizon Time after which nodes will be removed from the network map if they have not been seen
 * during this period
 */
@KeepForDJVM
@CordaSerializable
class NetworkParameters(
        val minimumPlatformVersion: Int,
        val notaries: List<NotaryInfo>,
        val maxMessageSize: Int,
        val maxTransactionSize: Int,
        modifiedTime: Instant,
        epoch: Int,
        whitelistedContractImplementations: Map<String, List<AttachmentId>>,
        val eventHorizon: Duration,
        packageOwnership: Map<JavaPackageName, PublicKey>) {
    // The autoAcceptParameters is a wrapper object around any class variables that might change. Having this wrapper
    // allows us to restrict the swapping logic to internally within the class.
    private var autoAcceptParameters: AutoAcceptParameters = AutoAcceptParameters(modifiedTime, epoch, whitelistedContractImplementations, packageOwnership)
    val modifiedTime: Instant get() = autoAcceptParameters.modifiedTime
    val epoch: Int get() = autoAcceptParameters.epoch
    val whitelistedContractImplementations: Map<String, List<AttachmentId>> get() = autoAcceptParameters.whitelistedContractImplementations
    val packageOwnership: Map<JavaPackageName, PublicKey> get() = autoAcceptParameters.packageOwnership

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

    init {
        require(minimumPlatformVersion > 0) { "minimumPlatformVersion must be at least 1" }
        require(notaries.distinctBy { it.identity } == notaries) { "Duplicate notary identities" }
        require(epoch > 0) { "epoch must be at least 1" }
        require(maxMessageSize > 0) { "maxMessageSize must be at least 1" }
        require(maxTransactionSize > 0) { "maxTransactionSize must be at least 1" }
        require(maxTransactionSize <= maxMessageSize) { "maxTransactionSize cannot be bigger than maxMessageSize" }
        require(!eventHorizon.isNegative) { "eventHorizon must be positive value" }
        require(noOverlap(packageOwnership.keys)) { "multiple packages added to the packageOwnership overlap." }
    }

    // TODO: revisit - we can remove the data class annotation to get constructor as we want but then need copy
    fun copy(minimumPlatformVersion: Int = this.minimumPlatformVersion,
             notaries: List<NotaryInfo> = this.notaries,
             maxMessageSize: Int = this.maxMessageSize,
             maxTransactionSize: Int = this.maxTransactionSize,
             modifiedTime: Instant = this.modifiedTime,
             epoch: Int = this.epoch,
             whitelistedContractImplementations: Map<String, List<AttachmentId>> = this.whitelistedContractImplementations,
             eventHorizon: Duration = this.eventHorizon,
             packageOwnership: Map<JavaPackageName, PublicKey> = this.packageOwnership): NetworkParameters {
        return NetworkParameters(
                minimumPlatformVersion,
                notaries,
                maxMessageSize,
                maxTransactionSize,
                modifiedTime,
                epoch,
                whitelistedContractImplementations,
                eventHorizon,
                packageOwnership
        )
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
        ${packageOwnership.keys.joinToString()}
      }
  }"""
    }

    /**
     * Returns the public key of the package owner of the [contractClassName], or null if not owned.
     */
    fun getOwnerOf(contractClassName: String): PublicKey? = this.packageOwnership.filterKeys { it.owns(contractClassName) }.values.singleOrNull()

    /**
     * Atomically (?) swaps the modifiedTime, epoch, whitelistedContractImplementations and packageOwnership within the
     * NetworkParameters object.
     */
    fun hotSwap(modifiedTime: Instant,
                epoch: Int,
                whitelistedContractImplementations: Map<String, List<AttachmentId>>,
                packageOwnership: Map<JavaPackageName, PublicKey>) {
        autoAcceptParameters = AutoAcceptParameters(modifiedTime, epoch, whitelistedContractImplementations, packageOwnership)
    }

    // TODO: revisit - we can remove the data class annotation to get constructor as we want but then need equals and hash code
    override fun equals(other: Any?): Boolean {
        if (other is NetworkParameters) {
            return (this.minimumPlatformVersion == other.minimumPlatformVersion) &&
            (this.notaries == other.notaries) &&
            (this.maxMessageSize == other.maxMessageSize) &&
            (this.maxTransactionSize == other.maxTransactionSize) &&
            (this.modifiedTime == other.modifiedTime) &&
            (this.epoch == other.epoch) &&
            (this.whitelistedContractImplementations == other.whitelistedContractImplementations) &&
            (this.eventHorizon == other.eventHorizon) &&
            (this.packageOwnership == other.packageOwnership)
        }
        return false
    }

    private data class AutoAcceptParameters(val modifiedTime: Instant,
                                            val epoch: Int,
                                            val whitelistedContractImplementations: Map<String, List<AttachmentId>>,
                                            val packageOwnership: Map<JavaPackageName, PublicKey>)
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

/**
 * A wrapper for a legal java package. Used by the network parameters to store package ownership.
 */
@CordaSerializable
data class JavaPackageName(val name: String) {
    init {
        require(isPackageValid(name)) { "Attempting to whitelist illegal java package: $name" }
    }

    /**
     * Returns true if the [fullClassName] is in a subpackage of the current package.
     * E.g.: "com.megacorp" owns "com.megacorp.tokens.MegaToken"
     *
     * Note: The ownership check is ignoring case to prevent people from just releasing a jar with: "com.megaCorp.megatoken" and pretend they are MegaCorp.
     * By making the check case insensitive, the node will require that the jar is signed by MegaCorp, so the attack fails.
     */
    fun owns(fullClassName: String) = fullClassName.startsWith("${name}.", ignoreCase = true)
}

// Check if a string is a legal Java package name.
private fun isPackageValid(packageName: String): Boolean = packageName.isNotEmpty() && !packageName.endsWith(".") && packageName.split(".").all { token ->
    Character.isJavaIdentifierStart(token[0]) && token.toCharArray().drop(1).all { Character.isJavaIdentifierPart(it) }
}

// Make sure that packages don't overlap so that ownership is clear.
private fun noOverlap(packages: Collection<JavaPackageName>) = packages.all { currentPackage ->
    packages.none { otherPackage -> otherPackage != currentPackage && otherPackage.name.startsWith("${currentPackage.name}.") }
}
