/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.bridge.services.api

import net.corda.nodeapi.internal.protonwrapper.messages.ReceivedMessage
import java.net.InetSocketAddress

/**
 * This service provides centralised facilities for recording business critical events in the bridge.
 * Currently the simple implementation just records events to log file, but future implementations may need to post
 * security data to an enterprise service.
 */
interface BridgeAuditService : ServiceLifecycleSupport {
    fun successfulConnectionEvent(inbound: Boolean, sourceIP: InetSocketAddress, certificateSubject: String, msg: String)
    fun failedConnectionEvent(inbound: Boolean, sourceIP: InetSocketAddress?, certificateSubject: String?, msg: String)
    fun packetDropEvent(packet: ReceivedMessage?, msg: String)
    fun packetAcceptedEvent(packet: ReceivedMessage)
    fun statusChangeEvent(msg: String)
}