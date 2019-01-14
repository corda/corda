package net.corda.core.contracts

import net.corda.core.KeepForDJVM
import net.corda.core.internal.cordapp.CordappImpl.Companion.DEFAULT_CORDAPP_VERSION
import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey

/**
 * Wrap an attachment in this if it is to be used as an executable contract attachment
 *
 * @property attachment The attachment representing the contract JAR
 * @property contract The contract name contained within the JAR. A Contract attachment has to contain at least 1 contract.
 * @property additionalContracts Additional contract names contained within the JAR.
 */
@KeepForDJVM
@CordaSerializable
class ContractAttachment @JvmOverloads constructor(
        val attachment: Attachment,
        val contract: ContractClassName,
        val additionalContracts: Set<ContractClassName> = emptySet(),
        val uploader: String? = null) : Attachment by attachment {
    constructor(
            attachment: Attachment,
            contract: ContractClassName,
            additionalContracts: Set<ContractClassName> = emptySet(),
            uploader: String? = null,
            signerKeys: List<PublicKey> = emptyList(),
            version: Int = DEFAULT_CORDAPP_VERSION) : this(attachment, contract, additionalContracts, uploader) {
        this.signerKeys = signerKeys
        this.version = version
    }

    override var signerKeys: List<PublicKey> = emptyList()
        private set

    var version: Int = DEFAULT_CORDAPP_VERSION
        private set

    val allContracts: Set<ContractClassName> get() = additionalContracts + contract

    val isSigned: Boolean get() = signerKeys.isNotEmpty()

    override fun toString(): String {
        return "ContractAttachment(attachment=${attachment.id}, contracts='$allContracts', uploader='$uploader', signed='$isSigned', version='$version')"
    }
}
