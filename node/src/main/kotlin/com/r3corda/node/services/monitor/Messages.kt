package com.r3corda.node.services.monitor

import com.r3corda.core.contracts.ClientToServiceCommand
import com.r3corda.core.contracts.ContractState
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.protocols.DirectRequestMessage

data class RegisterRequest(override val replyToRecipient: SingleMessageRecipient,
                           override val sessionID: Long) : DirectRequestMessage

data class RegisterResponse(val success: Boolean)
// TODO: This should have a shared secret the monitor was sent in the registration response, for security
data class DeregisterRequest(override val replyToRecipient: SingleMessageRecipient,
                             override val sessionID: Long) : DirectRequestMessage

data class DeregisterResponse(val success: Boolean)
data class StateSnapshotMessage(val contractStates: Collection<ContractState>, val protocolStates: Collection<String>)

data class ClientToServiceCommandMessage(override val sessionID: Long, override val replyToRecipient: SingleMessageRecipient, val command: ClientToServiceCommand) : DirectRequestMessage
