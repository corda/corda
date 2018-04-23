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

import net.corda.nodeapi.internal.ArtemisMessagingClient

/**
 * This provides a service to manage connection to the local broker as defined in the [BridgeConfiguration.outboundConfig] section.
 * Once started the service will repeatedly attempt to connect to the bus, signalling success by changing to the [active] state.
 */
interface BridgeArtemisConnectionService : ServiceLifecycleSupport {
    /**
     * When the service becomes [active] this will be non-null and provides access to Artemis management objects.
     */
    val started: ArtemisMessagingClient.Started?
}