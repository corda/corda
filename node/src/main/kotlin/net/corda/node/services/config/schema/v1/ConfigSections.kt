@file:Suppress("DEPRECATION")

package net.corda.node.services.config.schema.v1

import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.common.configuration.parsing.internal.listOrEmpty
import net.corda.common.configuration.parsing.internal.map
import net.corda.common.configuration.parsing.internal.mapValid
import net.corda.common.configuration.parsing.internal.nested
import net.corda.common.configuration.parsing.internal.withOptions
import net.corda.common.validation.internal.Validated.Companion.invalid
import net.corda.common.validation.internal.Validated.Companion.valid
import net.corda.core.context.AuthServiceId
import net.corda.core.internal.notary.NotaryServiceFlow
import net.corda.node.services.config.AuthDataSourceType
import net.corda.node.services.config.CertChainPolicyConfig
import net.corda.node.services.config.CertChainPolicyType
import net.corda.node.services.config.DJVMOptions
import net.corda.node.services.config.DevModeOptions
import net.corda.node.services.config.FlowOverride
import net.corda.node.services.config.FlowOverrideConfig
import net.corda.node.services.config.FlowTimeoutConfiguration
import net.corda.node.services.config.NetworkParameterAcceptanceSettings
import net.corda.node.services.config.NetworkServicesConfig
import net.corda.node.services.config.NodeH2Settings
import net.corda.node.services.config.NodeRpcSettings
import net.corda.node.services.config.NotaryConfig
import net.corda.node.services.config.PasswordEncryption
import net.corda.node.services.config.SecurityConfiguration
import net.corda.node.services.config.SecurityConfiguration.AuthService.Companion.defaultAuthServiceId
import net.corda.node.services.config.TelemetryConfiguration
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
import net.corda.node.services.config.shell.SSHDConfiguration
import net.corda.nodeapi.BrokerRpcSslOptions
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.TransactionIsolationLevel
import net.corda.notary.experimental.bftsmart.BFTSmartConfig
import net.corda.notary.experimental.raft.RaftConfig
import java.util.Properties

internal object UserSpec : Configuration.Specification<User>("User") {
    private val username by string().optional()
    private val user by string().optional()
    private val password by string(sensitive = true)
    private val permissions by string().listOrEmpty()

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<User> {
        val config = configuration.withOptions(options)
        val username = config[username] ?: config[user]
        return when (username) {
            null -> invalid(Configuration.Validation.Error.MissingValue.forKey("username"))
            else -> valid(User(username, config[password], config[permissions].toSet()))
        }
    }
}

internal object SecurityConfigurationSpec : Configuration.Specification<SecurityConfiguration>("SecurityConfiguration") {
    private object AuthServiceSpec : Configuration.Specification<SecurityConfiguration.AuthService>("AuthService") {
        private object DataSourceSpec : Configuration.Specification<SecurityConfiguration.AuthService.DataSource>("DataSource") {
            fun Properties.enablePasswordMasking(): Properties {
                class PwMasking : Properties() {
                    fun maskPassword(): Properties {
                        if (!containsKey("password")) return this
                        val propsNoPassword = Properties()
                        // if the properties are passed in to the constructor as defaults
                        // they don't get printed so adding all keys explicitly
                        propsNoPassword.putAll(this)
                        propsNoPassword.setProperty("password", "***")
                        return propsNoPassword
                    }

                    override fun toString(): String {
                        val props = maskPassword()
                        return props.toString()
                    }
                }

                val masker = PwMasking()
                masker.putAll(this)
                return masker
            }

            private val type by enum(AuthDataSourceType::class)
            private val passwordEncryption by enum(PasswordEncryption::class).optional().withDefaultValue(SecurityConfiguration.AuthService.DataSource.Defaults.passwordEncryption)
            private val connection by nestedObject(sensitive = true).map{ toProperties(it).enablePasswordMasking() }.optional()
            private val users by nested(UserSpec).list().optional()

            override fun parseValid(configuration: Config, options: Configuration.Options): Valid<SecurityConfiguration.AuthService.DataSource> {
                val config = configuration.withOptions(options)
                val type = config[type]
                val passwordEncryption = config[passwordEncryption]
                val connection = config[connection]
                val users = config[users]

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

                override fun parseValid(configuration: Config, options: Configuration.Options): Valid<SecurityConfiguration.AuthService.Options.Cache> {
                    val config = configuration.withOptions(options)
                    return valid(SecurityConfiguration.AuthService.Options.Cache(config[expireAfterSecs], config[maxEntries]))
                }
            }

            private val cache by nested(CacheSpec).optional()

            override fun parseValid(configuration: Config, options: Configuration.Options): Valid<SecurityConfiguration.AuthService.Options> {
                val config = configuration.withOptions(options)
                return valid(SecurityConfiguration.AuthService.Options(config[cache]))
            }
        }

        private val dataSource by nested(DataSourceSpec)
        private val id by string().map(::AuthServiceId).optional()
        val options by nested(OptionsSpec).optional()

        override fun parseValid(configuration: Config, options: Configuration.Options): Valid<SecurityConfiguration.AuthService> {
            val config = configuration.withOptions(options)
            val dataSource = config[dataSource]
            val id = config[id] ?: defaultAuthServiceId(dataSource.type)
            val authServiceOptions = config[this.options]
            return when {
                dataSource.type == AuthDataSourceType.INMEMORY && authServiceOptions?.cache != null -> badValue("no cache supported for \"INMEMORY\" data provider")
                else -> valid(SecurityConfiguration.AuthService(dataSource, id, authServiceOptions))
            }
        }
    }

    private val authService by nested(AuthServiceSpec)

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<SecurityConfiguration> {
        val config = configuration.withOptions(options)
        return valid(SecurityConfiguration(config[authService]))
    }
}

internal object DevModeOptionsSpec : Configuration.Specification<DevModeOptions>("DevModeOptions") {
    private val disableCheckpointChecker by boolean().optional().withDefaultValue(DevModeOptions.Defaults.disableCheckpointChecker)
    private val allowCompatibilityZone by boolean().optional().withDefaultValue(DevModeOptions.Defaults.allowCompatibilityZone)
    private val djvm by nested(DJVMOptionsSpec).optional()

    private object DJVMOptionsSpec : Configuration.Specification<DJVMOptions>("DJVMOptions") {
        private val bootstrapSource by string().optional()
        private val cordaSource by string().list()

        override fun parseValid(configuration: Config, options: Configuration.Options): Valid<DJVMOptions> {
            val config = configuration.withOptions(options)
            return valid(DJVMOptions(config[bootstrapSource], config[cordaSource]))
        }
    }

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<DevModeOptions> {
        val config = configuration.withOptions(options)
        return valid(DevModeOptions(config[disableCheckpointChecker], config[allowCompatibilityZone], config[djvm]))
    }
}

internal object NetworkServicesConfigSpec : Configuration.Specification<NetworkServicesConfig>("NetworkServicesConfig") {
    private val doormanURL by string().mapValid(::toURL)
    private val networkMapURL by string().mapValid(::toURL)
    private val pnm by string().mapValid(::toUUID).optional()
    private val inferred by boolean().optional().withDefaultValue(false)
    private val csrToken by string(sensitive = true).optional()

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<NetworkServicesConfig> {
        val config = configuration.withOptions(options)
        return valid(NetworkServicesConfig(config[doormanURL], config[networkMapURL], config[pnm], config[inferred], config[csrToken]))
    }
}

internal object NetworkParameterAcceptanceSettingsSpec :
        Configuration.Specification<NetworkParameterAcceptanceSettings>("NetworkParameterAcceptanceSettings") {
    private val autoAcceptEnabled by boolean().optional().withDefaultValue(true)
    private val excludedAutoAcceptableParameters by string().listOrEmpty()
    override fun parseValid(configuration: Config, options: Configuration.Options):
            Valid<NetworkParameterAcceptanceSettings> {
        val config = configuration.withOptions(options)
        return valid(NetworkParameterAcceptanceSettings(config[autoAcceptEnabled],
                config[excludedAutoAcceptableParameters].toSet())
        )
    }
}

@Suppress("DEPRECATION")
internal object CertChainPolicyConfigSpec : Configuration.Specification<CertChainPolicyConfig>("CertChainPolicyConfig") {
    private val role by string()
    private val policy by enum(CertChainPolicyType::class)
    private val trustedAliases by string().listOrEmpty()

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<CertChainPolicyConfig> {
        val config = configuration.withOptions(options)
        return valid(CertChainPolicyConfig(config[role], config[policy], config[trustedAliases].toSet()))
    }
}

internal object FlowTimeoutConfigurationSpec : Configuration.Specification<FlowTimeoutConfiguration>("FlowTimeoutConfiguration") {
    private val timeout by duration()
    private val maxRestartCount by int()
    private val backoffBase by double()

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<FlowTimeoutConfiguration> {
        val config = configuration.withOptions(options)
        return valid(FlowTimeoutConfiguration(config[timeout], config[maxRestartCount], config[backoffBase]))
    }
}

internal object TelemetryConfigurationSpec : Configuration.Specification<TelemetryConfiguration>("TelemetryConfiguration") {
    private val openTelemetryEnabled by boolean()
    private val simpleLogTelemetryEnabled by boolean()
    private val spanStartEndEventsEnabled by boolean()
    private val copyBaggageToTags by boolean()

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<TelemetryConfiguration> {
        val config = configuration.withOptions(options)
        return valid(TelemetryConfiguration(config[openTelemetryEnabled], config[simpleLogTelemetryEnabled], config[spanStartEndEventsEnabled], config[copyBaggageToTags]))
    }
}

internal object NotaryConfigSpec : Configuration.Specification<NotaryConfig>("NotaryConfig") {
    private val validating by boolean()
    private val serviceLegalName by string().mapValid(::toCordaX500Name).optional()
    private val className by string().optional()
    private val etaMessageThresholdSeconds by int().optional().withDefaultValue(NotaryServiceFlow.defaultEstimatedWaitTime.seconds.toInt())
    private val extraConfig by nestedObject(sensitive = true).map(ConfigObject::toConfig).optional()
    private val raft by nested(RaftConfigSpec).optional()
    private val bftSMaRt by nested(BFTSmartConfigSpec).optional()
    private val enableOverridableFlows by boolean().optional()

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<NotaryConfig> {
        val config = configuration.withOptions(options)
        return valid(
                NotaryConfig(
                        config[validating],
                        config[serviceLegalName],
                        config[className],
                        config[etaMessageThresholdSeconds],
                        config[extraConfig],
                        config[raft],
                        config[bftSMaRt],
                        config[enableOverridableFlows]
                )
        )
    }
}

internal object RaftConfigSpec : Configuration.Specification<RaftConfig>("RaftConfig") {
    private val nodeAddress by string().mapValid(::toNetworkHostAndPort)
    private val clusterAddresses by string().mapValid(::toNetworkHostAndPort).listOrEmpty()

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<RaftConfig> {
        val config = configuration.withOptions(options)
        return valid(RaftConfig(config[nodeAddress], config[clusterAddresses]))
    }
}

internal object BFTSmartConfigSpec : Configuration.Specification<BFTSmartConfig>("BFTSmartConfig") {
    private val replicaId by int()
    private val clusterAddresses by string().mapValid(::toNetworkHostAndPort).listOrEmpty()
    private val debug by boolean().optional().withDefaultValue(false)
    private val exposeRaces by boolean().optional().withDefaultValue(false)

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<BFTSmartConfig> {
        val config = configuration.withOptions(options)
        return valid(BFTSmartConfig(config[replicaId], config[clusterAddresses], config[debug], config[exposeRaces]))
    }
}

internal object NodeRpcSettingsSpec : Configuration.Specification<NodeRpcSettings>("NodeRpcSettings") {
    internal object BrokerRpcSslOptionsSpec : Configuration.Specification<BrokerRpcSslOptions>("BrokerRpcSslOptions") {
        private val keyStorePath by string().mapValid(::toPath)
        private val keyStorePassword by string(sensitive = true)

        override fun parseValid(configuration: Config, options: Configuration.Options): Valid<BrokerRpcSslOptions> {
            val config = configuration.withOptions(options)
            return valid(BrokerRpcSslOptions(config[keyStorePath], config[keyStorePassword]))
        }
    }

    private val address by string().mapValid(::toNetworkHostAndPort).optional()
    private val adminAddress by string().mapValid(::toNetworkHostAndPort).optional()
    private val standAloneBroker by boolean().optional().withDefaultValue(NodeRpcSettings.Defaults.standAloneBroker)
    private val useSsl by boolean().optional().withDefaultValue(NodeRpcSettings.Defaults.useSsl)
    private val ssl by nested(BrokerRpcSslOptionsSpec).optional()

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<NodeRpcSettings> {
        val config = configuration.withOptions(options)
        return valid(NodeRpcSettings(config[address], config[adminAddress], config[standAloneBroker], config[useSsl], config[ssl]))
    }
}

internal object SSHDConfigurationSpec : Configuration.Specification<SSHDConfiguration>("SSHDConfiguration") {
    private val port by int()

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<SSHDConfiguration> = attempt<SSHDConfiguration, IllegalArgumentException> { SSHDConfiguration(configuration.withOptions(options)[port]) }
}

enum class SchemaInitializationType{
    NONE,
    VALIDATE,
    UPDATE
}

internal object DatabaseConfigSpec : Configuration.Specification<DatabaseConfig>("DatabaseConfig") {
    private val initialiseSchema by boolean().optional()
    private val initialiseAppSchema by enum(SchemaInitializationType::class).optional()
    private val transactionIsolationLevel by enum(TransactionIsolationLevel::class).optional()
    private val exportHibernateJMXStatistics by boolean().optional().withDefaultValue(DatabaseConfig.Defaults.exportHibernateJMXStatistics)
    private val mappedSchemaCacheSize by long().optional().withDefaultValue(DatabaseConfig.Defaults.mappedSchemaCacheSize)

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<DatabaseConfig> {
        if (initialiseSchema.isSpecifiedBy(configuration)) {
            return invalid(Configuration.Validation.Error.BadPath.of(
                    "Unsupported configuration database/initialiseSchema - this option has been removed, please use the run-migration-scripts sub-command or the database management tool to modify schemas",
                    "initialiseSchema",
                    "Boolean"))
        }
        if (initialiseAppSchema.isSpecifiedBy(configuration)) {
            return invalid(Configuration.Validation.Error.BadPath.of(
                    "Unsupported configuration database/initialiseAppSchema - this option has been removed, please use the run-migration-scripts sub-command or the database management tool to modify schemas",
                    "initialiseAppSchema",
                    SchemaInitializationType::class.qualifiedName!!))
        }
        if (transactionIsolationLevel.isSpecifiedBy(configuration)) {
            return invalid(Configuration.Validation.Error.BadPath.of(
                    "Unsupported configuration database/transactionIsolationLevel - this option has been removed and cannot be changed",
                    "transactionIsolationLevel",
                    TransactionIsolationLevel::class.qualifiedName!!))
        }

        val config = configuration.withOptions(options)
        return valid(DatabaseConfig(config[exportHibernateJMXStatistics], config[mappedSchemaCacheSize]))
    }
}

internal object NodeH2SettingsSpec : Configuration.Specification<NodeH2Settings>("NodeH2Settings") {
    private val address by string().mapValid(::toNetworkHostAndPort).optional()

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<NodeH2Settings> {
        val config = configuration.withOptions(options)
        return valid(NodeH2Settings(config[address]))
    }
}

internal object FlowOverridesConfigSpec : Configuration.Specification<FlowOverrideConfig>("FlowOverrideConfig") {
    internal object SingleSpec : Configuration.Specification<FlowOverride>("FlowOverride") {
        private val initiator by string()
        private val responder by string()

        override fun parseValid(configuration: Config, options: Configuration.Options): Valid<FlowOverride> {
            val config = configuration.withOptions(options)
            return valid(FlowOverride(config[initiator], config[responder]))
        }
    }

    private val overrides by nested(FlowOverridesConfigSpec.SingleSpec).listOrEmpty()

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<FlowOverrideConfig> {
        val config = configuration.withOptions(options)
        return valid(FlowOverrideConfig(config[overrides]))
    }
}