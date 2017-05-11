package net.corda.explorer.formatters

import net.corda.core.flows.FlowInitiator

object FlowInitiatorFormatter : Formatter<FlowInitiator> {
    override fun format(value: FlowInitiator): String {
        return when (value) {
            is FlowInitiator.Scheduled -> "Started by scheduled state:: " + value.scheduledState.ref.toString() // TODO How do we want to format that?
            is FlowInitiator.Shell -> "Started via shell"
            is FlowInitiator.Peer -> "Peer legal name: " + PartyNameFormatter.short.format(value.party.name)
            is FlowInitiator.RPC -> "Rpc username: " + value.username
        }
    }
}
