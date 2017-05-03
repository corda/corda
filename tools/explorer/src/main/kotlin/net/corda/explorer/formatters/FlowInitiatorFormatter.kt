package net.corda.explorer.formatters

import net.corda.core.flows.FlowInitiator

object FlowInitiatorFormatter : Formatter<FlowInitiator> {
    override fun format(value: FlowInitiator): String {
        return when (value) {
            is FlowInitiator.Scheduled -> "Started by scheduled state:: " + value.scheduledState.ref.toString() // TODO format that
            is FlowInitiator.Shell -> "Started via shell"
            is FlowInitiator.Peer -> "Peer legal name: " + value.party.name //TODO format that
            is FlowInitiator.RPC -> "Rpc username: " + value.username
        }
    }
}
