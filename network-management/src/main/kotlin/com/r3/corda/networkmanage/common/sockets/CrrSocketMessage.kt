package com.r3.corda.networkmanage.common.sockets

import com.r3.corda.networkmanage.common.persistence.RequestStatus
import net.corda.core.serialization.CordaSerializable
import java.security.cert.X509CRL

@CordaSerializable
interface CrrSocketMessage

/**
 * CRL retrieval message type
 */
class CrlRetrievalMessage : CrrSocketMessage

/**
 * CRL response message type
 */
data class CrlResponseMessage(val crl: X509CRL?) : CrrSocketMessage

/**
 * CRL submission message type
 */
class CrlSubmissionMessage : CrrSocketMessage

/**
 * By status CRRs retrieval message type
 */
data class CrrsByStatusMessage(val status: RequestStatus) : CrrSocketMessage