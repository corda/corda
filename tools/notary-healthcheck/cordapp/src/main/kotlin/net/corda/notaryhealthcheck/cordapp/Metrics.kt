package net.corda.notaryhealthcheck.cordapp

import net.corda.core.identity.Party

private val X500CleanUpRegex = Regex("[^a-zA-Z\\d]")

fun Party.metricPrefix(): String {
    return "notaryhealthcheck.${this.name.toString().replace(X500CleanUpRegex, "_")}"
}

class Metrics {
    companion object {
        fun fail(prefix: String): String = "$prefix.fail"
        fun success(prefix: String) = "$prefix.success"
        fun inflight(prefix: String) = "$prefix.inflight"
        fun checkTime(prefix: String) = "$prefix.checkTime"
        fun maxInflightTime(prefix: String) = "$prefix.maxInflightTime"
        fun reportedWaitTimeSeconds(prefix: String) = "$prefix.reportedWaitTimeSeconds"
    }
}