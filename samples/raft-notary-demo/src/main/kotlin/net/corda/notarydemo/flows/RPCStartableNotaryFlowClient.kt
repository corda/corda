package net.corda.notarydemo.flows

import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.flows.NotaryFlow

@StartableByRPC
class RPCStartableNotaryFlowClient(stx: SignedTransaction) : NotaryFlow.Client(stx)
