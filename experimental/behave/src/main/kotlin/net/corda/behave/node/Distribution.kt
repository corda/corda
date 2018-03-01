package net.corda.behave.node

import net.corda.behave.file.div
import net.corda.behave.logging.getLogger
import net.corda.behave.service.Service
import org.apache.commons.io.FileUtils
import java.io.File
import java.net.URL

/**
 * Corda distribution.
 */
class Distribution private constructor(

        /**
         * The version string of the Corda distribution.
         */
        val version: String,

        /**
         * The path of the distribution fat JAR on disk, if available.
         */
        file: File? = null,

        /**
         * The URL of the distribution fat JAR, if available.
         */
        val url: URL? = null,

        /**
         *  The Docker image details, if available
         */
        val baseImage: String? = null
) {

    /**
     * The path to the distribution fat JAR.
     */
    val path: File = file ?: nodePrefix / "$version"

    /**
     * The path to the distribution fat JAR.
     */
    val jarFile: File = file ?: nodePrefix / "$version/corda.jar"

    /**
     * The path to available Cordapps for this distribution.
     */
    val cordappDirectory: File = nodePrefix / "$version/apps"

    /**
     * The path to network bootstrapping tool.
     */
    val networkBootstrapper: File = nodePrefix / "$version/network-bootstrapper.jar"

    /**
     * The path to the RPC proxy tool.
     */
    val rpcProxyJar: File = nodePrefix / "$version/corda-rpcProxy.jar"

    /**
     * Ensure that the distribution is available on disk.
     */
    fun ensureAvailable() {
        if (!jarFile.exists()) {
            if (url != null) {
                try {
                    FileUtils.forceMkdirParent(jarFile)
                    FileUtils.copyURLToFile(url, jarFile)
                } catch (e: Exception) {
                    if (e.message!!.contains("HTTP response code: 401")) {
                        log.warn("CORDA_ARTIFACTORY_USERNAME ${System.getenv("CORDA_ARTIFACTORY_USERNAME")}")
                        log.warn("CORDA_ARTIFACTORY_PASSWORD ${System.getenv("CORDA_ARTIFACTORY_PASSWORD")}")
                        throw Exception("Incorrect Artifactory permission. Please set CORDA_ARTIFACTORY_USERNAME and CORDA_ARTIFACTORY_PASSWORD environment variables correctly.")
                    }
                    else throw Exception("Invalid Corda version $version", e)
                }
            } else {
                throw Exception("File not found $jarFile")
            }
        }
    }

    /**
     * Human-readable representation of the distribution.
     */
    override fun toString() = "Corda(version = $version, path = $jarFile)"

    companion object {

        protected val log = getLogger<Service>()

        enum class DistributionType {
            CORDA,
            R3_CORDA
        }

        private val distributions = mutableListOf<Distribution>()

        private val directory = File(System.getProperty("user.dir"))

        private val nodePrefix = directory / "deps/corda"

        /**
         * Corda Open Source, version 3.0.0
         */
        val V3 = fromJarFile("corda-3.0")
        val MASTER = fromJarFile("corda-master")

        val R3_V3 = fromJarFile("r3corda-3.0")
        val R3_MASTER = fromJarFile("r3corda-master")

        val LATEST_MASTER = MASTER
        val LATEST_R3_MASTER = R3_MASTER

        /**
         * Get representation of a Corda distribution from Artifactory based on its version string.
         * @param version The version of the Corda distribution.
         * @param type The Corda distribution type.
         */
        fun fromArtifactory(version: String, type: DistributionType): Distribution {
            val url =
                when (type) {
                    DistributionType.CORDA -> URL("https://ci-artifactory.corda.r3cev.com/artifactory/corda-releases/net/corda/corda/$version/corda-$version.jar")
                    DistributionType.R3_CORDA -> URL("https://ci-artifactory.corda.r3cev.com/artifactory/r3-corda-releases/com/r3/corda/corda/$version/corda-$version.jar")
                }
            log.info("Artifactory URL: $url\n")
            val distribution = Distribution(version, url = url)
            distributions.add(distribution)
            return distribution
        }

        /**
         * Get representation of a Corda distribution based on its version string and fat JAR path.
         * @param version The version of the Corda distribution.
         * @param jarFile The path to the Corda fat JAR.
         */
        fun fromJarFile(version: String, jarFile: File? = null): Distribution {
            val distribution = Distribution(version, file = jarFile)
            distributions.add(distribution)
            return distribution
        }

        fun fromDockerImage(baseImage: String, imageTag: String): Distribution {
            val distribution = Distribution(version = imageTag, baseImage = baseImage)
            distributions.add(distribution)
            return distribution
        }

        /**
         * Get registered representation of a Corda distribution based on its version string.
         * @param version The version of the Corda distribution
         */
        fun fromVersionString(version: String): Distribution? = when (version) {
            "master"  -> LATEST_MASTER
            "r3-master"  -> LATEST_R3_MASTER
            "corda-3.0" -> V3
            "corda-3.0-HC02" -> fromArtifactory(version, DistributionType.CORDA)
            "corda-3.0-RC01" -> fromArtifactory(version, DistributionType.CORDA)
            "corda-3.0-pre-release-V3" -> fromJarFile("corda-3.0-pre-release-V3")
            "r3corda-3.0" -> R3_V3
//            "r3corda-3.0-DP2" -> fromArtifactory("R3.CORDA-3.0.0-DEV-PREVIEW-2", DistributionType.R3_CORDA)
            "r3corda-3.0-DP2" -> fromJarFile("R3.CORDA-3.0.0-DEV-PREVIEW-2")
            else -> distributions.firstOrNull { it.version == version }
        }
    }
}
