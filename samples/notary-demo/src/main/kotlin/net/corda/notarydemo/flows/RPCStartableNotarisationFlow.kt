package net.corda.notarydemo.flows

import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.flows.NotarisationFlow

@StartableByRPC
class RPCStartableNotarisationFlow(stx: SignedTransaction) : NotarisationFlow(stx)
