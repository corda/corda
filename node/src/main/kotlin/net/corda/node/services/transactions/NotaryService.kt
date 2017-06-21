package net.corda.node.services.transactions

import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party

interface NotaryService {

    /**
     * Factory for producing notary service flows which have the corresponding sends and receives as NotaryFlow.Client.
     * The first parameter is the client [Party] making the request and the second is the platform version
     * of the client's node. Use this version parameter to provide backwards compatibility if the notary flow protocol
     * changes.
     */
    val serviceFlowFactory: (Party, Int) -> FlowLogic<Void?>
}
