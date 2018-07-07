package net.corda.bootstrapper.cli

import com.microsoft.azure.management.resources.fluentcore.arm.Region
import net.corda.bootstrapper.Constants
import net.corda.bootstrapper.backends.Backend
import picocli.CommandLine
import picocli.CommandLine.Option
import java.io.File

open class CliParser {
    @Option(names = ["-n", "--network-name"], description = ["The resource grouping to use"])
    var name: String? = null

    @Option(names = ["-g", "--gui"], description = ["Run the graphical user interface"])
    var gui = false

    @Option(names = ["-d", "--nodes-directory"], description = ["The directory to search for nodes in"])
    var baseDirectory = File(System.getProperty("user.dir"))

    @Option(names = ["-b", "--backend"], description = ["The backend to use when instantiating nodes"])
    var backendType: Backend.BackendType = Backend.BackendType.LOCAL_DOCKER


    @Option(names = ["--add"], split = ":", description = ["The node to add. Format is <Name>:<X500>. Eg; \"Node1:O=Bank A, L=New York, C=US, OU=Org Unit, CN=Service Name\""])
    var nodesToAdd: MutableMap<String, String> = hashMapOf()

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

    @Option(names = ["-r", "--region"], description = ["The azure region to use"], converter = [RegionConverter::class])
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