package net.corda.bank.api

import net.corda.core.identity.CordaX500Name

interface IssueRequest {
    val amount: Long
    val currency: String
    val issuerBankPartyRef: String
    val issuerBankName: CordaX500Name
    val notaryName: CordaX500Name
}

data class IssueRequestParams(override val amount: Long,
                              override val currency: String,
                              override val issuerBankPartyRef: String,
                              override val issuerBankName: CordaX500Name,
                              override val notaryName: CordaX500Name) : IssueRequest