package net.corda.bank.api

import org.bouncycastle.asn1.x500.X500Name

interface IssueAndPaymentRequest : IssueRequest, PaymentRequest

data class IssueAndPaymentRequestParams(override val amount: Long,
                                        override val currency: String,
                                        override val payToPartyName: X500Name,
                                        override val issuerBankPartyRef: String,
                                        override val issuerBankName: X500Name,
                                        override val notaryName: X500Name,
                                        override val anonymous: Boolean) : IssueAndPaymentRequest