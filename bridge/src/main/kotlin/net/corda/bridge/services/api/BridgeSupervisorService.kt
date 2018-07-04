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
 * This is the top level service representing the [FirewallMode.BridgeInner] service stack. The primary role of this component is to
 * create and wire up concrete implementations of the relevant services according to the [FirewallConfiguration] details.
 * The possibly proxied path to the [BridgeAMQPListenerService] is typically a constructor input
 * as that is a [FirewallMode.FloatOuter] component.
 */
interface BridgeSupervisorService : ServiceLifecycleSupport