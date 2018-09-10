package net.corda.node.utilities.profiling

import net.corda.core.internal.profiling.CacheTracing
import net.corda.node.services.config.EnterpriseConfiguration
import java.nio.charset.Charset

val longHash = com.google.common.hash.Hashing.sipHash24()

private fun convertObject(key: Any?): Long {
    if (key == null) {
        return 0
    }
    return longHash.hashString(key.toString(), Charset.defaultCharset()).asLong()
}


fun EnterpriseConfiguration.getTracingConfig(): CacheTracing.CacheTracingConfig {
    return CacheTracing.CacheTracingConfig(this.enableCacheTracing, this.traceTargetDirectory, { convertObject(it) })
}