package net.corda.demobench.model

import tornadofx.*
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.util.logging.Level

class ServiceController(resourceName: String = "/services.conf") : Controller() {

    val services: HashMap<String, String> = loadConf(resources.url(resourceName))

    val notaries: Map<String, String> = services.filter { it.value.startsWith("corda.notary.") }

    val issuers: Map<String, String> = services.filter { it.value.startsWith("corda.issuer.") }

    /*
     * Load our list of known extra Corda services.
     */
    private fun loadConf(url: URL?): HashMap<String, String> {
        return if (url == null) {
            HashMap()
        } else {
            try {
                val map = HashMap<String, String>()
                InputStreamReader(url.openStream()).useLines { sq ->
                    sq.forEach { line ->
                        val service = line.split(":").map { it.trim() }
                        map[service[1]] = service[0]
                        log.info("Supports: $service")
                    }
                    map
                }
            } catch (e: IOException) {
                log.log(Level.SEVERE, "Failed to load $url: ${e.message}", e)
                HashMap<String, String>()
            }
        }
    }
}
