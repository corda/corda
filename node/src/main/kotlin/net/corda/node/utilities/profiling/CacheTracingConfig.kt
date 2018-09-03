package net.corda.node.utilities.profiling

import net.corda.node.services.config.EnterpriseConfiguration
import java.nio.file.Path

data class CacheTracingConfig(val enabled: Boolean, val targetDir: Path)

fun EnterpriseConfiguration.getTracingConfig(): CacheTracingConfig {
    return CacheTracingConfig(this.enableCacheTracing, this.traceTargetDirectory)
}