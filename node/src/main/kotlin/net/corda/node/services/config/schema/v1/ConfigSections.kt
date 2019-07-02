@file:Suppress("DEPRECATION")

package net.corda.node.services.config.schema.v1

import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import net.corda.common.configuration.parsing.internal.*
import net.corda.common.validation.internal.Validated.Companion.invalid
import net.corda.common.validation.internal.Validated.Companion.valid
import net.corda.core.context.AuthServiceId
import net.corda.core.internal.notary.NotaryServiceFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.config.*
import net.corda.node.services.config.SecurityConfiguration.AuthService.Companion.defaultAuthServiceId
import net.corda.node.services.config.Valid
import net.corda.node.services.config.schema.parsers.*
import net.corda.nodeapi.BrokerRpcSslOptions
import net.corda.nodeapi.internal.config.MessagingServerConnectionConfiguration
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.SchemaInitializationType
import net.corda.nodeapi.internal.persistence.TransactionIsolationLevel
import net.corda.notary.experimental.bftsmart.BFTSmartConfig
import net.corda.notary.experimental.raft.RaftConfig
import net.corda.notary.mysql.MySQLNotaryConfig
import net.corda.tools.shell.SSHDConfiguration
import java.net.Proxy

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
            private val type by enum(AuthDataSourceType::class)
            private val passwordEncryption by enum(PasswordEncryption::class).optional().withDefaultValue(SecurityConfiguration.AuthService.DataSource.Defaults.passwordEncryption)
            private val connection by nestedObject(sensitive = true).map(::toProperties).optional()
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

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<DevModeOptions> {
        val config = configuration.withOptions(options)
        return valid(DevModeOptions(config[disableCheckpointChecker], config[allowCompatibilityZone]))
    }
}

internal object NetworkServicesConfigSpec : Configuration.Specification<NetworkServicesConfig>("NetworkServicesConfig") {
    private val doormanURL by string().mapValid(::toURL)
    private val networkMapURL by string().mapValid(::toURL)
    private val pnm by string().mapValid(::toUUID).optional()
    private val inferred by boolean().optional().withDefaultValue(false)
    private val proxyType by enum(Proxy.Type::class).optional().withDefaultValue(Proxy.Type.DIRECT)
    private val proxyAddress by string().mapValid(::toNetworkHostAndPort).optional()
    private val proxyUser by string().optional()
    private val proxyPassword by string(sensitive = true).optional()

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<NetworkServicesConfig> {
        val config = configuration.withOptions(options)
        return valid(NetworkServicesConfig(config[doormanURL], config[networkMapURL], config[pnm], config[inferred], config[proxyType], config[proxyAddress], config[proxyUser], config[proxyPassword]))
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

internal object NotaryConfigSpec : Configuration.Specification<NotaryConfig>("NotaryConfig") {
    private val validating by boolean()
    private val serviceLegalName by string().mapValid(::toCordaX500Name).optional()
    private val className by string().optional()
    private val etaMessageThresholdSeconds by int().optional().withDefaultValue(NotaryServiceFlow.defaultEstimatedWaitTime.seconds.toInt())
    private val extraConfig by nestedObject().map(ConfigObject::toConfig).optional()
    private val raft by nested(RaftConfigSpec).optional()
    private val bftSMaRt by nested(BFTSmartConfigSpec).optional()
    private val mysql by nested(MySQLNotaryConfigSpec).optional()

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<NotaryConfig> {
        val config = configuration.withOptions(options)
        return valid(NotaryConfig(config[validating], config[serviceLegalName], config[className], config[etaMessageThresholdSeconds], config[extraConfig], config[raft], config[bftSMaRt], config[mysql]))
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

internal object MySQLNotaryConfigSpec: Configuration.Specification<MySQLNotaryConfig>("MySQLNotaryConfig") {
    private val dataSource by nestedObject(sensitive = true).map(::toProperties)
    private val connectionRetries by int().optional().withDefaultValue(MySQLNotaryConfig.Defaults.connectionRetries)
    private val backOffIncrement by int().optional().withDefaultValue(MySQLNotaryConfig.Defaults.backOffIncrement)
    private val backOffBase by double().optional().withDefaultValue(MySQLNotaryConfig.Defaults.backOffBase)
    private val maxBatchSize by int().optional().withDefaultValue(MySQLNotaryConfig.Defaults.maxBatchSize)
    private val maxBatchInputStates by int().optional().withDefaultValue(MySQLNotaryConfig.Defaults.maxBatchInputStates)
    private val batchTimeoutMs by long().optional().withDefaultValue(MySQLNotaryConfig.Defaults.batchTimeoutMs)
    private val maxQueueSize by int().optional().withDefaultValue(MySQLNotaryConfig.Defaults.maxQueueSize)

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<MySQLNotaryConfig> {
        val config = configuration.withOptions(options)
        return valid(MySQLNotaryConfig(config[dataSource], config[connectionRetries], config[backOffIncrement], config[backOffBase], config[maxBatchSize], config[maxBatchInputStates], config[batchTimeoutMs], config[maxQueueSize]))
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


internal object DatabaseConfigSpec : Configuration.Specification<DatabaseConfig>("DatabaseConfig") {
    private val initialiseSchema by boolean().optional().withDefaultValue(DatabaseConfig.Defaults.initialiseSchema)
    private val initialiseAppSchema by enum(SchemaInitializationType::class).optional().withDefaultValue(DatabaseConfig.Defaults.initialiseAppSchema)
    private val transactionIsolationLevel by enum(TransactionIsolationLevel::class).optional().withDefaultValue(DatabaseConfig.Defaults.transactionIsolationLevel)
    private val exportHibernateJMXStatistics by boolean().optional().withDefaultValue(DatabaseConfig.Defaults.exportHibernateJMXStatistics)
    private val mappedSchemaCacheSize by long().optional().withDefaultValue(DatabaseConfig.Defaults.mappedSchemaCacheSize)
    private val runMigration by boolean().optional().withDefaultValue(DatabaseConfig.Defaults.runMigration)
    private val hibernateDialect by string().optional()
    private val schema by string().optional()

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<DatabaseConfig> {
        val config = configuration.withOptions(options)
        return valid(DatabaseConfig(config[runMigration], config[initialiseSchema], config[initialiseAppSchema], config[transactionIsolationLevel], config[schema], config[exportHibernateJMXStatistics], config[hibernateDialect], config[mappedSchemaCacheSize]))
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

internal object RelayConfigurationSpec : Configuration.Specification<RelayConfiguration>("RelayConfiguration") {
    private val relayHost by string()
    private val remoteInboundPort by int()
    private val username by string()
    private val privateKeyFile by string().mapValid(::toPath)
    private val publicKeyFile by string().mapValid(::toPath)
    private val sshPort by int().optional().withDefaultValue(RelayConfiguration.Defaults.sshPort)

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<RelayConfiguration> {
        val config = configuration.withOptions(options)
        return valid(RelayConfiguration(config[relayHost], config[remoteInboundPort], config[username], config[privateKeyFile], config[publicKeyFile], config[sshPort]))
    }
}

internal object GraphiteOptionsSpec : Configuration.Specification<GraphiteOptions>("GraphiteOptions") {
    private val server by string()
    private val port by int()
    private val prefix by string().optional()
    // TODO fix this typo, even if it means breaking the config schema
    private val sampleInvervallSeconds by long().optional().withDefaultValue(GraphiteOptions.Defaults.sampleInvervallSeconds)

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<GraphiteOptions> {
        val config = configuration.withOptions(options)
        return valid(GraphiteOptions(config[server], config[port], config[prefix], config[sampleInvervallSeconds]))
    }
}

internal object EnterpriseConfigurationSpec : Configuration.Specification<EnterpriseConfiguration>("EnterpriseConfiguration") {
    private val mutualExclusionConfiguration by nested(MutualExclusionConfigurationSpec)
    private val messagingServerConnectionConfiguration by enum(MessagingServerConnectionConfiguration::class).optional().withDefaultValue(EnterpriseConfiguration.Defaults.messagingServerConnectionConfiguration)
    private val messagingServerBackupAddresses by string().mapValid(::toNetworkHostAndPort).list().optional().withDefaultValue(EnterpriseConfiguration.Defaults.messagingServerBackupAddresses)
    private val useMultiThreadedSMM by boolean().optional().withDefaultValue(EnterpriseConfiguration.Defaults.useMultiThreadedSMM)
    private val tuning by nested(PerformanceTuningSpec).optional().withDefaultValue(EnterpriseConfiguration.Defaults.tuning)
    private val externalBridge by boolean().optional()
    private val enableCacheTracing by boolean().optional().withDefaultValue(EnterpriseConfiguration.Defaults.enableCacheTracing)
    private val traceTargetDirectory by string().mapValid(::toPath).optional().withDefaultValue(EnterpriseConfiguration.Defaults.traceTargetDirectory)
    private val messagingServerSslConfiguration by nested(MessagingServerSslConfigurationSpec).optional()
    private val processedMessageCleanup by nested(ProcessedMessageCleanupSpec).optional()

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<EnterpriseConfiguration> {
        val config = configuration.withOptions(options)
        return valid(EnterpriseConfiguration(
                config[mutualExclusionConfiguration],
                config[messagingServerConnectionConfiguration],
                config[messagingServerBackupAddresses],
                config[messagingServerSslConfiguration],
                config[useMultiThreadedSMM],
                config[tuning],
                config[externalBridge],
                config[enableCacheTracing],
                config[traceTargetDirectory],
                config[processedMessageCleanup])
        )
    }
}

internal object MessagingServerSslConfigurationSpec : Configuration.Specification<MessagingServerSslConfiguration>("MessagingServerSslConfiguration") {
    private val sslKeystore by string().mapValid(::toPath)
    private val keyStorePassword by string(sensitive = true)
    private val trustStoreFile by string().mapValid(::toPath)
    private val trustStorePassword by string(sensitive = true)
    private val useOpenSsl by boolean().optional().withDefaultValue(MessagingServerSslConfiguration.Defaults.useOpenSsl)

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<MessagingServerSslConfiguration> {
        val config = configuration.withOptions(options)
        return valid(MessagingServerSslConfiguration(config[sslKeystore], config[keyStorePassword], config[trustStoreFile], config[trustStorePassword], config[useOpenSsl]))
    }
}

internal object MutualExclusionConfigurationSpec : Configuration.Specification<MutualExclusionConfiguration>("MutualExclusionConfiguration") {
    private val on by boolean().optional().withDefaultValue(MutualExclusionConfiguration.Defaults.on)
    private val machineName by string().optional().withDefaultValue(MutualExclusionConfiguration.Defaults.machineName)
    private val updateInterval by long()
    private val waitInterval by long()

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<MutualExclusionConfiguration> {
        val config = configuration.withOptions(options)
        return valid(MutualExclusionConfiguration(config[on], config[machineName], config[updateInterval], config[waitInterval]))
    }
}

internal object PerformanceTuningSpec : Configuration.Specification<PerformanceTuning>("PerformanceTuning") {
    private val flowThreadPoolSize by int()
    private val maximumMessagingBatchSize by int()
    private val rpcThreadPoolSize by int()
    private val p2pConfirmationWindowSize by int()
    private val brokerConnectionTtlCheckIntervalMs by long()

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<PerformanceTuning> {
        val config = configuration.withOptions(options)
        return valid(PerformanceTuning(config[flowThreadPoolSize], config[maximumMessagingBatchSize], config[rpcThreadPoolSize], config[p2pConfirmationWindowSize], config[brokerConnectionTtlCheckIntervalMs]))
    }
}

internal object ProcessedMessageCleanupSpec : Configuration.Specification<ProcessedMessageCleanup>("ProcessedMessageCleanup") {
    private val retainPerSender by int().optional()
    private val retainForDays by int().optional()

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<ProcessedMessageCleanup> {
        val config = configuration.withOptions(options)
        return valid(ProcessedMessageCleanup(config[retainPerSender], config[retainForDays]))
    }
}