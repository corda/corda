package net.corda.node.services.config.schema.v1

import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import net.corda.common.configuration.parsing.internal.*
import net.corda.common.validation.internal.Validated.Companion.valid
import net.corda.node.services.config.*
import net.corda.node.services.config.schema.parsers.*
import net.corda.nodeapi.BrokerRpcSslOptions
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.TransactionIsolationLevel
import net.corda.tools.shell.SSHDConfiguration

internal object UserSpec : Configuration.Specification<User>("User") {
    private val username by string()
    private val password by string()
    private val permissions by string().listOrEmpty()

    override fun parseValid(configuration: Config): Valid<User> {
        return valid(User(configuration[username], configuration[password], configuration[permissions].toSet()))
    }
}

internal object SecurityConfigurationSpec : Configuration.Specification<SecurityConfiguration>("SecurityConfiguration") {
    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<SecurityConfiguration> {
        TODO("sollecitom not implemented")
    }
}

internal object DevModeOptionsSpec : Configuration.Specification<DevModeOptions>("DevModeOptions") {
    private val disableCheckpointChecker by boolean().optional().withDefaultValue(false)
    private val allowCompatibilityZone by boolean().optional().withDefaultValue(false)

    override fun parseValid(configuration: Config): Valid<DevModeOptions> {
        return valid(DevModeOptions(configuration[disableCheckpointChecker], configuration[allowCompatibilityZone]))
    }
}

internal object NetworkServicesConfigSpec : Configuration.Specification<NetworkServicesConfig>("NetworkServicesConfig") {
    private val doormanURL by string().mapValid(::toURL)
    private val networkMapURL by string().mapValid(::toURL)
    private val pnm by string().mapValid(::toUUID).optional()
    private val inferred by boolean().optional().withDefaultValue(false)

    override fun parseValid(configuration: Config): Valid<NetworkServicesConfig> {
        return valid(NetworkServicesConfig(configuration[doormanURL], configuration[networkMapURL], configuration[pnm], configuration[inferred]))
    }
}

@Suppress("DEPRECATION")
internal object CertChainPolicyConfigSpec : Configuration.Specification<CertChainPolicyConfig>("CertChainPolicyConfig") {
    private val role by string()
    private val policy by enum(CertChainPolicyType::class)
    private val trustedAliases by string().listOrEmpty()

    override fun parseValid(configuration: Config): Valid<CertChainPolicyConfig> {
        return valid(CertChainPolicyConfig(configuration[role], configuration[policy], configuration[trustedAliases].toSet()))
    }
}

internal object FlowTimeoutConfigurationSpec : Configuration.Specification<FlowTimeoutConfiguration>("FlowTimeoutConfiguration") {
    private val timeout by duration()
    private val maxRestartCount by int()
    private val backoffBase by double()

    override fun parseValid(configuration: Config): Valid<FlowTimeoutConfiguration> {
        return valid(FlowTimeoutConfiguration(configuration[timeout], configuration[maxRestartCount], configuration[backoffBase]))
    }
}

internal object NotaryConfigSpec : Configuration.Specification<NotaryConfig>("NotaryConfig") {
    private val validating by boolean()
    private val serviceLegalName by string().mapValid(::toCordaX500Name).optional()
    private val className by string().optional().withDefaultValue("net.corda.node.services.transactions.SimpleNotaryService")
    private val extraConfig by nestedObject().map(ConfigObject::toConfig).optional()

    override fun parseValid(configuration: Config): Valid<NotaryConfig> {
        return valid(NotaryConfig(configuration[validating], configuration[serviceLegalName], configuration[className], configuration[extraConfig]))
    }
}

internal object NodeRpcSettingsSpec : Configuration.Specification<NodeRpcSettings>("NodeRpcSettings") {
    internal object BrokerRpcSslOptionsSpec : Configuration.Specification<BrokerRpcSslOptions>("BrokerRpcSslOptions") {
        private val keyStorePath by string().mapValid(::toPath)
        private val keyStorePassword by string()

        override fun parseValid(configuration: Config): Valid<BrokerRpcSslOptions> {
            return valid(BrokerRpcSslOptions(configuration[keyStorePath], configuration[keyStorePassword]))
        }
    }

    private val address by string().mapValid(::toNetworkHostAndPort).optional()
    private val adminAddress by string().mapValid(::toNetworkHostAndPort).optional()
    private val standAloneBroker by boolean().optional().withDefaultValue(false)
    private val useSsl by boolean().optional().withDefaultValue(false)
    private val ssl by nested(BrokerRpcSslOptionsSpec).optional()

    override fun parseValid(configuration: Config): Valid<NodeRpcSettings> {
        return valid(NodeRpcSettings(configuration[address], configuration[adminAddress], configuration[standAloneBroker], configuration[useSsl], configuration[ssl]))
    }
}

internal object SSHDConfigurationSpec : Configuration.Specification<SSHDConfiguration>("SSHDConfiguration") {
    private val port by int()

    override fun parseValid(configuration: Config): Valid<SSHDConfiguration> = attempt<SSHDConfiguration, IllegalArgumentException> { SSHDConfiguration(configuration[port]) }
}


internal object DatabaseConfigSpec : Configuration.Specification<DatabaseConfig>("DatabaseConfig") {
    private val initialiseSchema by boolean().optional().withDefaultValue(true)
    private val transactionIsolationLevel by enum(TransactionIsolationLevel::class).optional().withDefaultValue(TransactionIsolationLevel.REPEATABLE_READ)
    private val exportHibernateJMXStatistics by boolean().optional().withDefaultValue(false)
    private val mappedSchemaCacheSize by long().optional().withDefaultValue(100)

    override fun parseValid(configuration: Config): Valid<DatabaseConfig> {
        return valid(DatabaseConfig(configuration[initialiseSchema], configuration[transactionIsolationLevel], configuration[exportHibernateJMXStatistics], configuration[mappedSchemaCacheSize]))
    }
}

internal object NodeH2SettingsSpec : Configuration.Specification<NodeH2Settings>("NodeH2Settings") {
    private val address by string().mapValid(::toNetworkHostAndPort).optional()

    override fun parseValid(configuration: Config): Valid<NodeH2Settings> {
        return valid(NodeH2Settings(configuration[address]))
    }
}

internal object FlowOverridesConfigSpec : Configuration.Specification<FlowOverrideConfig>("FlowOverrideConfig") {
    internal object SingleSpec : Configuration.Specification<FlowOverride>("FlowOverride") {
        private val initiator by string()
        private val responder by string()

        override fun parseValid(configuration: Config): Valid<FlowOverride> {
            return valid(FlowOverride(configuration[initiator], configuration[responder]))
        }
    }

    private val overrides by nested(FlowOverridesConfigSpec.SingleSpec).listOrEmpty()

    override fun parseValid(configuration: Config): Valid<FlowOverrideConfig> {
        return valid(FlowOverrideConfig(configuration[overrides]))
    }
}