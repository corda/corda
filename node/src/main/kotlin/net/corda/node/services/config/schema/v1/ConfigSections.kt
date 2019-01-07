@file:Suppress("DEPRECATION")

package net.corda.node.services.config.schema.v1

import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.common.configuration.parsing.internal.get
import net.corda.common.configuration.parsing.internal.listOrEmpty
import net.corda.common.configuration.parsing.internal.map
import net.corda.common.configuration.parsing.internal.mapValid
import net.corda.common.configuration.parsing.internal.nested
import net.corda.common.validation.internal.Validated.Companion.invalid
import net.corda.common.validation.internal.Validated.Companion.valid
import net.corda.core.context.AuthServiceId
import net.corda.core.internal.notary.NotaryServiceFlow
import net.corda.node.services.config.AuthDataSourceType
import net.corda.node.services.config.CertChainPolicyConfig
import net.corda.node.services.config.CertChainPolicyType
import net.corda.node.services.config.DevModeOptions
import net.corda.node.services.config.FlowOverride
import net.corda.node.services.config.FlowOverrideConfig
import net.corda.node.services.config.FlowTimeoutConfiguration
import net.corda.node.services.config.NetworkServicesConfig
import net.corda.node.services.config.NodeH2Settings
import net.corda.node.services.config.NodeRpcSettings
import net.corda.node.services.config.NotaryConfig
import net.corda.node.services.config.PasswordEncryption
import net.corda.node.services.config.SecurityConfiguration
import net.corda.node.services.config.SecurityConfiguration.AuthService.Companion.defaultAuthServiceId
import net.corda.node.services.config.Valid
import net.corda.node.services.config.schema.parsers.attempt
import net.corda.node.services.config.schema.parsers.badValue
import net.corda.node.services.config.schema.parsers.toCordaX500Name
import net.corda.node.services.config.schema.parsers.toNetworkHostAndPort
import net.corda.node.services.config.schema.parsers.toPath
import net.corda.node.services.config.schema.parsers.toProperties
import net.corda.node.services.config.schema.parsers.toURL
import net.corda.node.services.config.schema.parsers.toUUID
import net.corda.node.services.config.schema.parsers.validValue
import net.corda.nodeapi.BrokerRpcSslOptions
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.TransactionIsolationLevel
import net.corda.nodeapi.internal.persistence.SchemaInitializationType
import net.corda.notary.experimental.bftsmart.BFTSmartConfig
import net.corda.notary.experimental.raft.RaftConfig
import net.corda.tools.shell.SSHDConfiguration

internal object UserSpec : Configuration.Specification<User>("User") {
    private val username by string().optional()
    private val user by string().optional()
    private val password by string(sensitive = true)
    private val permissions by string().listOrEmpty()

    override fun parseValid(configuration: Config): Valid<User> {
        val username = configuration[username] ?: configuration[user]
        return when (username) {
            null -> invalid(Configuration.Validation.Error.MissingValue.forKey("username"))
            else -> valid(User(username, configuration[password], configuration[permissions].toSet()))
        }
    }
}

internal object SecurityConfigurationSpec : Configuration.Specification<SecurityConfiguration>("SecurityConfiguration") {
    private object AuthServiceSpec : Configuration.Specification<SecurityConfiguration.AuthService>("AuthService") {
        private object DataSourceSpec : Configuration.Specification<SecurityConfiguration.AuthService.DataSource>("DataSource") {
            private val type by enum(AuthDataSourceType::class)
            private val passwordEncryption by enum(PasswordEncryption::class).optional().withDefaultValue(SecurityConfiguration.AuthService.DataSource.Defaults.passwordEncryption)
            private val connection by nestedObject(sensitive = true).map(::toProperties).optional()
            private val users by nested(UserSpec).list().optional()

            override fun parseValid(configuration: Config): Valid<SecurityConfiguration.AuthService.DataSource> {
                val type = configuration[type]
                val passwordEncryption = configuration[passwordEncryption]
                val connection = configuration[connection]
                val users = configuration[users]

                return when {
                    type == AuthDataSourceType.INMEMORY && (users == null || connection != null) -> badValue("\"INMEMORY\" data source type requires \"users\" and cannot specify \"connection\"")
                    type == AuthDataSourceType.DB && (users != null || connection == null) -> badValue("\"DB\" data source type requires \"connection\" and cannot specify \"users\"")
                    else -> valid(SecurityConfiguration.AuthService.DataSource(type, passwordEncryption, connection, users))
                }
            }
        }

        private object OptionsSpec : Configuration.Specification<SecurityConfiguration.AuthService.Options>("Options") {
            private object CacheSpec : Configuration.Specification<SecurityConfiguration.AuthService.Options.Cache>("Cache") {
                private val expireAfterSecs by long().mapValid { value -> if (value >= 0) validValue(value) else badValue("cannot be less than 0'") }
                private val maxEntries by long().mapValid { value -> if (value >= 0) validValue(value) else badValue("cannot be less than 0'") }

                override fun parseValid(configuration: Config): Valid<SecurityConfiguration.AuthService.Options.Cache> {
                    return valid(SecurityConfiguration.AuthService.Options.Cache(configuration[expireAfterSecs], configuration[maxEntries]))
                }
            }

            private val cache by nested(CacheSpec).optional()

            override fun parseValid(configuration: Config): Valid<SecurityConfiguration.AuthService.Options> {
                return valid(SecurityConfiguration.AuthService.Options(configuration[cache]))
            }
        }

        private val dataSource by nested(DataSourceSpec)
        private val id by string().map(::AuthServiceId).optional()
        val options by nested(OptionsSpec).optional()

        override fun parseValid(configuration: Config): Valid<SecurityConfiguration.AuthService> {
            val dataSource = configuration[dataSource]
            val id = configuration[id] ?: defaultAuthServiceId(dataSource.type)
            val options = configuration[options]
            return when {
                dataSource.type == AuthDataSourceType.INMEMORY && options?.cache != null -> badValue("no cache supported for \"INMEMORY\" data provider")
                else -> valid(SecurityConfiguration.AuthService(dataSource, id, options))
            }
        }
    }

    private val authService by nested(AuthServiceSpec)

    override fun parseValid(configuration: Config): Valid<SecurityConfiguration> {
        return valid(SecurityConfiguration(configuration[authService]))
    }
}

internal object DevModeOptionsSpec : Configuration.Specification<DevModeOptions>("DevModeOptions") {
    private val disableCheckpointChecker by boolean().optional().withDefaultValue(DevModeOptions.Defaults.disableCheckpointChecker)
    private val allowCompatibilityZone by boolean().optional().withDefaultValue(DevModeOptions.Defaults.allowCompatibilityZone)

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
    private val className by string().optional()
    private val etaMessageThresholdSeconds by int().optional().withDefaultValue(NotaryServiceFlow.defaultEstimatedWaitTime.seconds.toInt())
    private val extraConfig by nestedObject().map(ConfigObject::toConfig).optional()
    private val raft by nested(RaftConfigSpec).optional()
    private val bftSMaRt by nested(BFTSmartConfigSpec).optional()

    override fun parseValid(configuration: Config): Valid<NotaryConfig> {
        return valid(NotaryConfig(configuration[validating], configuration[serviceLegalName], configuration[className], configuration[etaMessageThresholdSeconds], configuration[extraConfig], configuration[raft], configuration[bftSMaRt]))
    }
}

internal object RaftConfigSpec : Configuration.Specification<RaftConfig>("RaftConfig") {
    private val nodeAddress by string().mapValid(::toNetworkHostAndPort)
    private val clusterAddresses by string().mapValid(::toNetworkHostAndPort).listOrEmpty()

    override fun parseValid(configuration: Config): Valid<RaftConfig> {
        return valid(RaftConfig(configuration[nodeAddress], configuration[clusterAddresses]))
    }
}

internal object BFTSmartConfigSpec : Configuration.Specification<BFTSmartConfig>("BFTSmartConfig") {
    private val replicaId by int()
    private val clusterAddresses by string().mapValid(::toNetworkHostAndPort).listOrEmpty()
    private val debug by boolean().optional().withDefaultValue(false)
    private val exposeRaces by boolean().optional().withDefaultValue(false)

    override fun parseValid(configuration: Config): Valid<BFTSmartConfig> {
        return valid(BFTSmartConfig(configuration[replicaId], configuration[clusterAddresses], configuration[debug], configuration[exposeRaces]))
    }
}


internal object NodeRpcSettingsSpec : Configuration.Specification<NodeRpcSettings>("NodeRpcSettings") {
    internal object BrokerRpcSslOptionsSpec : Configuration.Specification<BrokerRpcSslOptions>("BrokerRpcSslOptions") {
        private val keyStorePath by string().mapValid(::toPath)
        private val keyStorePassword by string(sensitive = true)

        override fun parseValid(configuration: Config): Valid<BrokerRpcSslOptions> {
            return valid(BrokerRpcSslOptions(configuration[keyStorePath], configuration[keyStorePassword]))
        }
    }

    private val address by string().mapValid(::toNetworkHostAndPort).optional()
    private val adminAddress by string().mapValid(::toNetworkHostAndPort).optional()
    private val standAloneBroker by boolean().optional().withDefaultValue(NodeRpcSettings.Defaults.standAloneBroker)
    private val useSsl by boolean().optional().withDefaultValue(NodeRpcSettings.Defaults.useSsl)
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
    private val initialiseSchema by boolean().optional().withDefaultValue(DatabaseConfig.Defaults.initialiseSchema)
    private val initialiseAppSchema by enum(SchemaInitializationType::class).optional().withDefaultValue(DatabaseConfig.Defaults.initialiseAppSchema)
    private val transactionIsolationLevel by enum(TransactionIsolationLevel::class).optional().withDefaultValue(DatabaseConfig.Defaults.transactionIsolationLevel)
    private val exportHibernateJMXStatistics by boolean().optional().withDefaultValue(DatabaseConfig.Defaults.exportHibernateJMXStatistics)
    private val mappedSchemaCacheSize by long().optional().withDefaultValue(DatabaseConfig.Defaults.mappedSchemaCacheSize)

    override fun parseValid(configuration: Config): Valid<DatabaseConfig> {
        return valid(DatabaseConfig(configuration[initialiseSchema], configuration[initialiseAppSchema], configuration[transactionIsolationLevel], configuration[exportHibernateJMXStatistics], configuration[mappedSchemaCacheSize]))
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