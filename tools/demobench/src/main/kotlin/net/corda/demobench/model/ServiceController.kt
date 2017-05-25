package net.corda.demobench.model

import tornadofx.*
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.util.logging.Level

class ServiceController(resourceName: String = "/services.conf") : Controller() {

    val services: List<String> = loadConf(resources.url(resourceName))

    val notaries: List<String> = services.filter { it.startsWith("corda.notary.") }.toList()

    /*
     * Load our list of known extra Corda services.
     */
    private fun loadConf(url: URL?): List<String> {
        return if (url == null) {
            emptyList()
        } else {
            try {
                val set = sortedSetOf<String>()
                InputStreamReader(url.openStream()).useLines { sq ->
                    sq.forEach { line ->
                        val service = line.trim()
                        set.add(service)

                        log.info("Supports: $service")
                    }
                }
                set.toList()
            } catch (e: IOException) {
                log.log(Level.SEVERE, "Failed to load $url: ${e.message}", e)
                emptyList<String>()
            }
        }
    }
}
