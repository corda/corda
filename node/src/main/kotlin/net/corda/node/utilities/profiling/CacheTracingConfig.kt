/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.utilities.profiling

import net.corda.node.services.config.EnterpriseConfiguration
import java.nio.file.Path

data class CacheTracingConfig(val enabled: Boolean, val targetDir: Path)

fun EnterpriseConfiguration.getTracingConfig(): CacheTracingConfig {
    return CacheTracingConfig(this.enableCacheTracing, this.traceTargetDirectory)
}