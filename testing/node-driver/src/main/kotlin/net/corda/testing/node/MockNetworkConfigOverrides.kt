package net.corda.testing.node

import com.typesafe.config.Config
import net.corda.core.identity.CordaX500Name
import java.time.Duration

/**
 * This is a data class to configure overrides to the node configuration used in the mock network
 * without having to expose/use the actual (internal) node configuration interface on the API.
 * When passing one of these to [createNode] or [createUnstartedNode] functions, the bits that are
 * set will be injected into the node configuration for the node to be created.
 */
class MockNodeConfigOverrides(
        val extraDataSourceProperties: Map<String, String>? = null,
        val notary: MockNetNotaryConfig? = null,
        val flowTimeout: MockNetFlowTimeOut? = null)

/**
 * MockNetNotaryConfig can be used to configure a node to be a notary via the mock network API. Internally
 * this will be translated into a NotaryConfiguration and passed to the respective node.
 */
class MockNetNotaryConfig(
        val validating: Boolean,
        val extraConfig: Config? = null,
        val className: String? = null,
        val serviceLegalName: CordaX500Name? = null)

/**
 * MockNetFlowTimeOut can be used to configure flow time out settings for a node via the mock network API.
 */
class MockNetFlowTimeOut(
        val timeout: Duration,
        val maxRestartCount: Int,
        val backoffBase: Double)
