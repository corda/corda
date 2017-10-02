package net.corda.demobench.model

import tornadofx.*
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.util.logging.Level

class ServiceController(resourceName: String = "/services.conf") : Controller() {

    val services: Map<String, String> = loadConf(resources.url(resourceName))

    val notaries: Map<String, String> = services.filter { it.value.startsWith("corda.notary.") }

    val issuers: Map<String, String> = services.filter { it.value.startsWith("corda.issuer.") }

    /*
     * Load our list of known extra Corda services.
     */
    private fun loadConf(url: URL?): Map<String, String> {
        return if (url == null) {
            emptyMap()
        } else {
            try {
                val map = linkedMapOf<String, String>()
                InputStreamReader(url.openStream()).useLines { sq ->
                    sq.forEach { line ->
                        val service = line.split(":").map { it.trim() }
                        if (service.size != 2) {
                            log.warning("Encountered corrupted line '$line' while reading services from config: $url")
                        }
                        else {
                            map[service[1]] = service[0]
                            log.info("Supports: $service")
                        }
                    }
                    map
                }
            } catch (e: IOException) {
                log.log(Level.SEVERE, "Failed to load $url: ${e.message}", e)
                emptyMap<String, String>()
            }
        }
    }
}
