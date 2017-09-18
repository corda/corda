package net.corda.bank.api

import net.corda.core.identity.CordaX500Name

interface PaymentRequest {
    val amount: Long
    val currency: String
    val payToPartyName: CordaX500Name
}

data class PaymentRequestParams(override val amount: Long,
                                override val currency: String,
                                override val payToPartyName: CordaX500Name) : PaymentRequest