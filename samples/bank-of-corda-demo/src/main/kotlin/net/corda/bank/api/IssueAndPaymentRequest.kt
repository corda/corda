package net.corda.bank.api

import net.corda.core.identity.CordaX500Name

interface IssueAndPaymentRequest : IssueRequest, PaymentRequest

data class IssueAndPaymentRequestParams(override val amount: Long,
                                        override val currency: String,
                                        override val payToPartyName: CordaX500Name,
                                        override val issuerBankPartyRef: String,
                                        override val issuerBankName: CordaX500Name,
                                        override val notaryName: CordaX500Name) : IssueAndPaymentRequest