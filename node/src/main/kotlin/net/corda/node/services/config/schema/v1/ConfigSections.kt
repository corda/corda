package net.corda.node.services.config.schema.v1

import com.typesafe.config.Config
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.node.services.config.*
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.tools.shell.SSHDConfiguration

internal object UserSpec : Configuration.Specification<User>("User") {

    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<User> {
        TODO("sollecitom not implemented")
    }
}

internal object SecurityConfigurationSpec : Configuration.Specification<SecurityConfiguration>("SecurityConfiguration") {

    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<SecurityConfiguration> {
        TODO("sollecitom not implemented")
    }
}

internal object DevModeOptionsSpec : Configuration.Specification<DevModeOptions>("DevModeOptions") {

    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<DevModeOptions> {
        TODO("sollecitom not implemented")
    }
}

internal object NetworkServicesConfigSpec : Configuration.Specification<NetworkServicesConfig>("NetworkServicesConfig") {

    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<NetworkServicesConfig> {
        TODO("sollecitom not implemented")
    }
}

@Suppress("DEPRECATION")
internal object CertChainPolicyConfigSpec : Configuration.Specification<CertChainPolicyConfig>("CertChainPolicyConfig") {

    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<CertChainPolicyConfig> {
        TODO("sollecitom not implemented")
    }
}

internal object FlowTimeoutConfigurationSpec : Configuration.Specification<FlowTimeoutConfiguration>("FlowTimeoutConfiguration") {

    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<FlowTimeoutConfiguration> {
        TODO("sollecitom not implemented")
    }
}

internal object NotaryConfigSpec : Configuration.Specification<NotaryConfig>("NotaryConfig") {

    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<NotaryConfig> {
        TODO("sollecitom not implemented")
    }
}

internal object NodeRpcSettingsSpec : Configuration.Specification<NodeRpcSettings>("NodeRpcSettings") {

    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<NodeRpcSettings> {
        TODO("sollecitom not implemented")
    }
}

internal object SSHDConfigurationSpec : Configuration.Specification<SSHDConfiguration>("SSHDConfiguration") {

    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<SSHDConfiguration> {
        TODO("sollecitom not implemented")
    }
}


internal object DatabaseConfigSpec : Configuration.Specification<DatabaseConfig>("DatabaseConfig") {

    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<DatabaseConfig> {
        TODO("sollecitom not implemented")
    }
}

internal object NodeH2SettingsSpec : Configuration.Specification<NodeH2Settings>("NodeH2Settings") {

    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<NodeH2Settings> {
        TODO("sollecitom not implemented")
    }
}

internal object FlowOverrideConfigSpec : Configuration.Specification<FlowOverrideConfig>("FlowOverrideConfig") {

    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<FlowOverrideConfig> {
        TODO("sollecitom not implemented")
    }
}