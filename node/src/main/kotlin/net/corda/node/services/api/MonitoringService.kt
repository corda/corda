/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.api

import com.codahale.metrics.MetricRegistry
import net.corda.core.serialization.SingletonSerializeAsToken


/**
 * Provides access to various metrics and ways to notify monitoring services of things, for sysadmin purposes.
 * This is not an interface because it is too lightweight to bother mocking out.
 */
class MonitoringService(val metrics: MetricRegistry) : SingletonSerializeAsToken()
