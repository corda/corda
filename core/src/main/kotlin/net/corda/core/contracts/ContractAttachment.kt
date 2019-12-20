package net.corda.core.contracts

import net.corda.core.CordaInternal
import net.corda.core.KeepForDJVM
import net.corda.core.internal.cordapp.CordappImpl.Companion.DEFAULT_CORDAPP_VERSION
import java.security.PublicKey

/**
 * An [Attachment] which represents a contract JAR.
 *
 * @property attachment The attachment representing the contract JAR
 * @property contract The contract name contained within the JAR. A Contract attachment has to contain at least 1 contract.
 * @property additionalContracts Additional contract names contained within the JAR.
 */
@KeepForDJVM
class ContractAttachment private constructor(
        val attachment: Attachment,
        val contract: ContractClassName,
        val additionalContracts: Set<ContractClassName>,
        val uploader: String?,
        override val signerKeys: List<PublicKey>,
        val version: Int
) : Attachment by attachment {
    @JvmOverloads
    constructor(attachment: Attachment,
                contract: ContractClassName,
                additionalContracts: Set<ContractClassName> = emptySet(),
                uploader: String? = null) : this(attachment, contract, additionalContracts, uploader, emptyList(), DEFAULT_CORDAPP_VERSION)

    companion object {
        @CordaInternal
        fun create(attachment: Attachment,
                   contract: ContractClassName,
                   additionalContracts: Set<ContractClassName> = emptySet(),
                   uploader: String? = null,
                   signerKeys: List<PublicKey> = emptyList(),
                   version: Int = DEFAULT_CORDAPP_VERSION): ContractAttachment {
            return ContractAttachment(attachment, contract, additionalContracts, uploader, signerKeys, version)
        }
    }

    val allContracts: Set<ContractClassName> get() = additionalContracts + contract

    val isSigned: Boolean get() = signerKeys.isNotEmpty()

    override fun toString(): String {
        return "ContractAttachment(attachment=${attachment.id}, contracts='$allContracts', uploader='$uploader', signed='$isSigned', version='$version')"
    }
}
