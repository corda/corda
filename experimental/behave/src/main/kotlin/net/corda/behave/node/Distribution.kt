package net.corda.behave.node

import net.corda.behave.file.div
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
        val url: URL? = null

) {

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
     * Ensure that the distribution is available on disk.
     */
    fun ensureAvailable() {
        if (!jarFile.exists()) {
            if (url != null) {
                try {
                    FileUtils.forceMkdirParent(jarFile)
                    FileUtils.copyURLToFile(url, jarFile)
                } catch (e: Exception) {
                    throw Exception("Invalid Corda version $version", e)
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

        private val distributions = mutableListOf<Distribution>()

        private val directory = File(System.getProperty("user.dir"))

        private val nodePrefix = directory / "deps/corda"

        /**
         * Corda Open Source, version 3.0.0
         */
        val V3 = fromJarFile("3.0.0")

        val LATEST_MASTER = V3

        /**
         * Get representation of an open source distribution based on its version string.
         * @param version The version of the open source Corda distribution.
         */
        fun fromOpenSourceVersion(version: String): Distribution {
            val url = URL("https://dl.bintray.com/r3/corda/net/corda/corda/$version/corda-$version.jar")
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
         * Get registered representation of a Corda distribution based on its version string.
         * @param version The version of the Corda distribution
         */
        fun fromVersionString(version: String): Distribution? = when (version.toLowerCase()) {
            "master" -> LATEST_MASTER
            else -> distributions.firstOrNull { it.version == version }
        }

    }

}
