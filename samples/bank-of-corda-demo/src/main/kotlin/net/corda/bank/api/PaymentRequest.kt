package net.corda.bank.api

import org.bouncycastle.asn1.x500.X500Name

interface PaymentRequest {
    val amount: Long
    val currency: String
    val payToPartyName: X500Name
    val anonymous: Boolean
}

data class PaymentRequestParams(override val amount: Long,
                                override val currency: String,
                                override val payToPartyName: X500Name,
                                override val anonymous: Boolean) : PaymentRequest