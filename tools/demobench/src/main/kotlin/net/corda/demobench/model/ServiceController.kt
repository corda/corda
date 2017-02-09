package net.corda.demobench.model

import tornadofx.Controller
import java.io.InputStreamReader
import java.net.URL
import java.util.*

class ServiceController : Controller() {

    val services: List<String> = loadConf(javaClass.classLoader.getResource("services.conf"))

    val notaries: List<String> = services.filter { it.startsWith("corda.notary.") }.toList()

    /*
     * Load our list of known extra Corda services.
     */
    private fun loadConf(url: URL?): List<String> {
        if (url == null) {
            return emptyList()
        } else {
            val set = TreeSet<String>()
            InputStreamReader(url.openStream()).useLines {
                sq -> sq.forEach {
                    val service = it.trim()
                    set.add(service)

                    log.info("Supports: $service")
                }
            }
            return set.toList()
        }
    }

}
