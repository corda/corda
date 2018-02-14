package net.corda.core.contracts

import net.corda.core.DoNotImplement
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.GlobalProperties
import net.corda.core.serialization.CordaSerializable

/** Constrain which contract-code-containing attachment can be used with a [ContractState]. */
@CordaSerializable
@DoNotImplement
interface AttachmentConstraint {
    /** Returns whether the given contract attachment can be used with the [ContractState] associated with this constraint object. */
    fun isSatisfiedBy(attachment: Attachment): Boolean
}

/** An [AttachmentConstraint] where [isSatisfiedBy] always returns true. */
object AlwaysAcceptAttachmentConstraint : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment) = true
}

/** An [AttachmentConstraint] that verifies by hash */
data class HashAttachmentConstraint(val attachmentId: SecureHash) : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment) = attachment.id == attachmentId
}

/**
 * An [AttachmentConstraint] that verifies that the hash of the attachment is in the network parameters whitelist.
 * See: [net.corda.core.node.NetworkParameters.whitelistedContractImplementations]
 * It allows for centralized control over the cordapps that can be used.
 */
object WhitelistedByZoneAttachmentConstraint : AttachmentConstraint {
    /**
     * This sequence can be used for test/demos
     */
    val whitelistAllContractsForTest get() = mapOf("*" to listOf(SecureHash.zeroHash, SecureHash.allOnesHash))

    override fun isSatisfiedBy(attachment: Attachment): Boolean {
        return GlobalProperties.networkParameters.whitelistedContractImplementations.let { whitelist ->
            when {
                whitelist == whitelistAllContractsForTest -> true
                attachment is ConstraintAttachment -> attachment.id in (whitelist[attachment.stateContract]
                        ?: emptyList())
                else -> false
            }
        }
    }
}

/**
 * This [AttachmentConstraint] is a convenience class that will be automatically resolved to a [HashAttachmentConstraint].
 * The resolution occurs in [TransactionBuilder.toWireTransaction] and uses the [TransactionState.contract] value
 * to find a corresponding loaded [Cordapp] that contains such a contract, and then uses that [Cordapp] as the
 * [Attachment].
 *
 * If, for any reason, this class is not automatically resolved the default implementation is to fail, because the
 * intent of this class is that it should be replaced by a correct [HashAttachmentConstraint] and verify against an
 * actual [Attachment].
 */
object AutomaticHashConstraint : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment): Boolean {
        throw UnsupportedOperationException("Contracts cannot be satisfied by an AutomaticHashConstraint placeholder")
    }
}

/**
 * Used only for passing to the Attachment constraint verification.
 * Encapsulates a [ContractAttachment] and the state contract
 */
class ConstraintAttachment(val contractAttachment: ContractAttachment, val stateContract: ContractClassName) : Attachment by contractAttachment {
    init {
        require(stateContract in contractAttachment.allContracts) {
            "This ConstraintAttachment was not initialised properly"
        }
    }
}
