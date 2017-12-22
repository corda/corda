package net.corda.attestation.challenger

import net.corda.attestation.message.ias.QuoteStatus
import java.security.interfaces.ECPublicKey
import java.time.LocalDateTime

class AttestationResult(
    val reportID: String,
    val quoteStatus: QuoteStatus,
    val peerPublicKey: ECPublicKey,
    val platformInfo: ByteArray?,
    val timestamp: LocalDateTime
)
