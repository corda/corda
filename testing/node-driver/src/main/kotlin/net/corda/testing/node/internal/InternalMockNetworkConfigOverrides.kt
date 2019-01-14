package net.corda.testing.node.internal

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.node.services.config.FlowTimeoutConfiguration
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.NotaryConfig
import net.corda.testing.node.MockNetNotaryConfig
import net.corda.testing.node.MockNodeConfigOverrides

fun MockNetNotaryConfig.toNotaryConfig(): NotaryConfig {
    return NotaryConfig(validating = this.validating, extraConfig = this.extraConfig, serviceLegalName = this.serviceLegalName, className = this.className)
}

fun MockNodeConfigOverrides.applyMockNodeOverrides(config: NodeConfiguration) {
    config.also {
        this.notary?.also { n -> doReturn(n.toNotaryConfig()).whenever(it).notary }
        this.extraDataSourceProperties?.forEach { k, v -> it.dataSourceProperties.put(k, v) }
        this.flowTimeout?.also { fto -> doReturn(FlowTimeoutConfiguration(fto.timeout, fto.maxRestartCount, fto.backoffBase)).whenever(config).flowTimeout }
    }
}