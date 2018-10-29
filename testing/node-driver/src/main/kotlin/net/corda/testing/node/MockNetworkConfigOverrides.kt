package net.corda.testing.node

import com.typesafe.config.Config
import net.corda.core.identity.CordaX500Name
import java.time.Duration


// These are data classes aping bits of configuration that we want to be able to override via the public API
// without exposing the actual config publicly.

data class MockNetNotaryConfig(
        val validating: Boolean,
        val extraConfig: Config? = null,
        val className: String? = null,
        val serviceLegalName: CordaX500Name? = null)

data class MockNetFlowTimeOut(
        val timeout: Duration,
        val maxRestartCount: Int,
        val backoffBase: Double
)

data class MockNodeConfigOverides(
        val extraDataSourceProperties: Map<String, String>? = null,
        val notary: MockNetNotaryConfig? = null,
        val flowTimeout: MockNetFlowTimeOut? = null)
