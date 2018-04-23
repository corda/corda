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

/**
 * This service controls when a bridge may become active and start relaying messages to/from the artemis broker.
 * The active flag is the used to gate dependent services, which should hold off connecting to the bus until this service
 * has been able to become active.
 */
interface BridgeMasterService : ServiceLifecycleSupport {
    // An echo of the active flag that can be used to make the intention of active status checks clearer.
    val isMaster: Boolean get() = active
}