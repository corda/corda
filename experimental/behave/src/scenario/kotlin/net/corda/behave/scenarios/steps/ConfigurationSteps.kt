package net.corda.behave.scenarios.steps

import net.corda.behave.database.DatabaseType
import net.corda.behave.node.Distribution
import net.corda.behave.node.configuration.toNotaryType
import net.corda.behave.scenarios.ScenarioState
import net.corda.behave.scenarios.api.StepsBlock
import net.corda.core.identity.CordaX500Name

class ConfigurationSteps : StepsBlock {

    override fun initialize(state: ScenarioState) {
        fun node(name: String) = state.nodeBuilder(name)

        Given<String, String>("^a node (\\w+) of version ([^ ]+)$") { name, version ->
            node(name)
                    .withDistribution(Distribution.fromVersionString(version)
                            ?: error("Unknown version '$version'"))
        }

        Given<String, String>("^a node (\\w+) of version ([^ ]+) with proxy$") { name, version ->
            node(name).withRPCProxy(true)
                    .withDistribution(Distribution.fromVersionString(version)
                            ?: error("Unknown version '$version'"))
        }

        Given<String, String, String, String>("^a node (\\w+) in location (\\w+) and country (\\w+) of version ([^ ]+)$") { name, location, country, version ->
            node(name).withRPCProxy(true)
                    .withDistribution(Distribution.fromVersionString(version)
                            ?: error("Unknown version '$version'"))
                    .withLocation(location.replace("_", " "), country)
        }

        Given<String, String, String>("^a (\\w+) notary node (\\w+) of version ([^ ]+)$") { notaryType, name, version ->
            node(name)
                    .withDistribution(Distribution.fromVersionString(version)
                            ?: error("Unknown version '$version'"))
                    .withNotaryType(notaryType.toNotaryType()
                            ?: error("Unknown notary type '$notaryType'"))
        }

        Given<String, String, String>("^a (\\w+) notary node (\\w+) of version ([^ ]+) with proxy$") { notaryType, name, version ->
            node(name).withRPCProxy(true)
                    .withDistribution(Distribution.fromVersionString(version)
                            ?: error("Unknown version '$version'"))
                    .withNotaryType(notaryType.toNotaryType()
                            ?: error("Unknown notary type '$notaryType'"))
        }

        Given<String, String, String>("^a (\\w+) notary (\\w+) of version ([^ ]+)$") { type, name, version ->
            node(name)
                    .withDistribution(Distribution.fromVersionString(version)
                            ?: error("Unknown version '$version'"))
                    .withNotaryType(type.toNotaryType()
                            ?: error("Unknown notary type '$type'"))
        }

        Given<String, String>("^node (\\w+) uses database of type (.+)$") { name, type ->
            node(name)
                    .withDatabaseType(DatabaseType.fromName(type)
                            ?: error("Unknown database type '$type'"))
        }

        Given<String, String>("^node (\\w+) can issue currencies of denomination (.+)$") { name, currencies ->
            node(name).withIssuableCurrencies(currencies
                    .replace(" and ", ", ")
                    .split(", ")
                    .map { it.toUpperCase() })
        }

        Given<String, String, String>("^node (\\w+) is located in (\\w+), (\\w+)$") { name, location, country ->
            node(name).withLocation(location, country)
        }

        Given<String>("^node (\\w+) has the finance app installed$") { name ->
            node(name).withFinanceApp()
        }

        Given<String, String>("^node (\\w+) has app installed: (.+)$") { name, app ->
            node(name).withApp(app)
        }

        When<Int, String, String>("^a (\\d+) node (\\w+) RAFT notary cluster of version ([^ ]+)$") { clusterSize, notaryType, version ->

            val clusterName = CordaX500Name("Raft", "Zurich", "CH")
            val notaryNames = createNotaryNames(clusterSize)

            notaryNames.forEachIndexed { index, notaryName ->
                node(notaryName.organisation)
                        .withDistribution(Distribution.fromVersionString(version)
                                ?: error("Unknown version '$version'"))
                        .withNotaryType(notaryType.toNotaryType()
                                ?: error("Unknown notary type '$notaryType'"))
                        .withRaftConfig(index)
            }
        }
    }
}

private fun createNotaryNames(clusterSize: Int) = (0 until clusterSize).map { CordaX500Name("Notary Service $it", "Zurich", "CH") }
