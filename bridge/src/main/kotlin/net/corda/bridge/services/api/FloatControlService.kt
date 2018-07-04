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
 * This service represent an AMQP socket listener that awaits a remote initiated connection from the [FirewallMode.BridgeInner].
 * Only one active connection is allowed at a time and it must match the configured requirements in the [FirewallConfiguration.bridgeInnerConfig].
 */
interface FloatControlService : ServiceLifecycleSupport