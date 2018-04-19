package net.corda.behave.node

import net.corda.behave.file.div
import net.corda.behave.file.stagingRoot
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
    val cordaJar: File = path / "corda.jar"

    /**
     * The path to available Cordapps for this distribution.
     */
    val cordappDirectory: File = path / "apps"

    /**
     * The path to network bootstrapping tool.
     */
    val networkBootstrapper: File = path / "network-bootstrapper.jar"

    /**
     * Ensure that the distribution is available on disk.
     */
    fun ensureAvailable() {
        if (!cordaJar.exists()) {
            if (url != null) {
                try {
                    FileUtils.forceMkdirParent(cordaJar)
                    FileUtils.copyURLToFile(url, cordaJar)
                } catch (e: Exception) {
                    if (e.message!!.contains("HTTP response code: 401")) {
                        log.warn("CORDA_ARTIFACTORY_USERNAME ${System.getenv("CORDA_ARTIFACTORY_USERNAME")}")
                        log.warn("CORDA_ARTIFACTORY_PASSWORD ${System.getenv("CORDA_ARTIFACTORY_PASSWORD")}")
                        throw Exception("Incorrect Artifactory permission. Please set CORDA_ARTIFACTORY_USERNAME and CORDA_ARTIFACTORY_PASSWORD environment variables correctly.")
                    }
                    else throw Exception("Invalid Corda version $version", e)
                }
            } else {
                throw Exception("File not found $cordaJar")
            }
        }
    }

    /**
     * Human-readable representation of the distribution.
     */
    override fun toString() = "Corda(version = $version, path = $cordaJar)"

    companion object {

        protected val log = getLogger<Service>()

        private val distributions = mutableListOf<Distribution>()

        private val nodePrefix = stagingRoot / "deps/corda"

        val MASTER = fromJarFile("corda-master")

        /**
         * Get representation of a Corda distribution from Artifactory based on its version string.
         * @param version The version of the Corda distribution.
         */
        fun fromArtifactory(version: String): Distribution {
            val url = URL("https://ci-artifactory.corda.r3cev.com/artifactory/corda-releases/net/corda/corda/$version/corda-$version.jar")
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

        /**
         * Get Corda distribution from a Docker image file.
         * @param baseImage The name (eg. corda) of the Corda distribution.
         * @param imageTag The version (github commit id or corda version) of the Corda distribution.
         */
        fun fromDockerImage(baseImage: String, imageTag: String): Distribution {
            val distribution = Distribution(version = imageTag, baseImage = baseImage)
            distributions.add(distribution)
            return distribution
        }

        /**
         * Get registered representation of a Corda distribution based on its version string.
         * @param version The version of the Corda distribution
         */
        fun fromVersionString(version: String): Distribution = when (version) {
            "master"  -> MASTER
            "corda-3.0" -> fromArtifactory(version)
            else -> fromJarFile(version)
        }
    }
}
