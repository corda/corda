package net.corda.core.transactions

import net.corda.core.KeepForDJVM
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionState
import net.corda.core.flows.FlowException
import net.corda.core.internal.Version
import net.corda.core.serialization.CordaSerializable

/**
 * A contract attachment was missing when trying to automatically attach all known contract attachments
 *
 * @property states States which have contracts that do not have corresponding attachments in the attachment store.
 */
@CordaSerializable
@KeepForDJVM
class MissingContractAttachments
@JvmOverloads
constructor(val states: List<TransactionState<ContractState>>, contractsClassName: String? = null, minimumRequiredContractClassVersion: Version? = null) : FlowException(
        "Cannot find contract attachments for " +
        "${contractsClassName ?: states.map { it.contract }.distinct()}${minimumRequiredContractClassVersion?.let { ", minimum required contract class version $minimumRequiredContractClassVersion"}}.")
