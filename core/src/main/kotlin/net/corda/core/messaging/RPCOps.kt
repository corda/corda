/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.messaging

import net.corda.core.DoNotImplement

/**
 * Base interface that all RPC servers must implement. Note: in Corda there's only one RPC interface. This base
 * interface is here in case we split the RPC system out into a separate library one day.
 */
@DoNotImplement
interface RPCOps {
    /** Returns the RPC protocol version. Exists since version 0 so guaranteed to be present. */
    val protocolVersion: Int
}