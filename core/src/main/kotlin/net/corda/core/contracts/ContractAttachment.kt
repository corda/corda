package net.corda.core.contracts

import net.corda.core.KeepForDJVM
import net.corda.core.serialization.CordaSerializable
import net.corda.core.cordapp.DEFAULT_CORDAPP_VERSION
import java.security.PublicKey
import java.util.jar.Attributes

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
        val uploader: String? = null,
        override val signerKeys: List<PublicKey> = emptyList(),
        val version: Int = DEFAULT_CORDAPP_VERSION) : Attachment by attachment {

    val allContracts: Set<ContractClassName> get() = additionalContracts + contract

    val isSigned: Boolean get() = signerKeys.isNotEmpty()

    override fun toString(): String {
        return "ContractAttachment(attachment=${attachment.id}, contracts='$allContracts', uploader='$uploader', signed='$isSigned', version='$version')"
    }

    /**
     * Contract version.
     */
    open val version: String get() = extractVersion(attachment)

    companion object {
        private fun extractVersion(attachment: Attachment) =
            try { attachment.openAsJAR().manifest?.mainAttributes?.getValue(Attributes.Name.IMPLEMENTATION_VERSION) ?: "0" } catch (e: Exception) { "0" }

        fun getContractVersion(attachment: Attachment) : Version {
            val version = if (attachment is ContractAttachment) {
                attachment.version
            } else {
                extractVersion(attachment)
            }
            return Integer.parseInt(version)
        }
    }
}