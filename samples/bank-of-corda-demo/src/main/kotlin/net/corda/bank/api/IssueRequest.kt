package net.corda.bank.api

import org.bouncycastle.asn1.x500.X500Name

interface IssueRequest {
    val amount: Long
    val currency: String
    val issuerBankPartyRef: String
    val issuerBankName: X500Name
    val notaryName: X500Name
}

data class IssueRequestParams(override val amount: Long,
                              override val currency: String,
                              override val issuerBankPartyRef: String,
                              override val issuerBankName: X500Name,
                              override val notaryName: X500Name) : IssueRequest