package net.corda.bootstrapper.cli

import com.microsoft.azure.management.resources.fluentcore.arm.Region
import net.corda.bootstrapper.Constants
import net.corda.bootstrapper.backends.Backend
import picocli.CommandLine
import picocli.CommandLine.Option
import java.io.File

open class CliParser {

    @Option(names = arrayOf("-n", "--network-name"), description = arrayOf("The resource grouping to use"))
    var name: String? = null

    @Option(names = arrayOf("-g", "--gui"), description = arrayOf("Run the graphical user interface"))
    var gui = false

    @Option(names = arrayOf("-d", "--nodes-directory"), description = arrayOf("The directory to search for nodes in"))
    var baseDirectory = File(System.getProperty("user.dir"))

    @Option(names = arrayOf("-b", "--backend"), description = arrayOf("The backend to use when instantiating nodes"))
    var backendType: Backend.BackendType = Backend.BackendType.LOCAL_DOCKER

    @Option(names = arrayOf("--add"), split = ":", description = arrayOf("The node to add. Format is <Name>:<X500>. Eg; \"Node1:O=Bank A, L=New York, C=US, OU=Org Unit, CN=Service Name\""))
    var nodesToAdd: MutableMap<String, String> = hashMapOf()

    @Option(names = arrayOf("-h", "--help"), usageHelp = true, description = arrayOf("display a help message"))
    var helpRequested = false

    fun isNew(): Boolean {
        return nodesToAdd.isEmpty()
    }

    open fun backendOptions(): Map<String, String> {
        return emptyMap()
    }
}

class AzureParser : CliParser() {
    companion object {
        val regions = Region.values().map { it.name() to it }.toMap()
    }

    @Option(names = arrayOf("-r", "--region"), description = arrayOf("The azure region to use"), converter = arrayOf(RegionConverter::class))
    var region: Region = Region.EUROPE_WEST

    class RegionConverter : CommandLine.ITypeConverter<Region> {
        override fun convert(value: String): Region {
            return regions[value] ?: throw Error("Unknown azure region: $value")
        }
    }

    override fun backendOptions(): Map<String, String> {
        return mapOf(Constants.REGION_ARG_NAME to region.name())
    }
}