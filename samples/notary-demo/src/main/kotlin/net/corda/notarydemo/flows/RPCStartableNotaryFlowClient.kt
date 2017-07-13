package net.corda.notarydemo.flows

import net.corda.core.flows.NotaryFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction

@StartableByRPC
class RPCStartableNotaryFlowClient(stx: SignedTransaction) : NotaryFlow.Client(stx)
