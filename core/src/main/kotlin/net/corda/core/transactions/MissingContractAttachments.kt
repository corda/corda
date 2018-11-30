package net.corda.core.transactions

import net.corda.core.KeepForDJVM
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionState
import net.corda.core.flows.FlowException
import net.corda.core.serialization.CordaSerializable
import net.corda.core.contracts.Version
/**
 * A contract attachment was missing when trying to automatically attach all known contract attachments
 *
 * @property states States which have contracts that do not have corresponding attachments in the attachment store.
 */
@CordaSerializable
@KeepForDJVM
class MissingContractAttachments(val states: List<TransactionState<ContractState>>, minimumRequiredContractClassVersion: Version? = null)
    : FlowException("Cannot find contract attachments for ${states.map { it.contract }.distinct()}, minimum required contract class version ${minimumRequiredContractClassVersion ?: "UNKNOWN"}. " +
        "See https://docs.corda.net/api-contract-constraints.html#debugging")