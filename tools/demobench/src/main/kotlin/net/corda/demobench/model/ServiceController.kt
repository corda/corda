package net.corda.demobench.model

import tornadofx.Controller
import java.io.InputStreamReader
import java.net.URL
import java.util.*

class ServiceController : Controller() {

    private var serviceSet: List<String>
    val services: List<String> get() = serviceSet

    init {
        /*
         * Load our list of known extra Corda services.
         */
        val serviceConf = javaClass.classLoader.getResource("services.conf")
        serviceSet = if (serviceConf == null) {
            emptyList<String>()
        } else {
            loadConf(serviceConf)
        }
    }

    private fun loadConf(url: URL): List<String> {
        val set = TreeSet<String>()
        InputStreamReader(url.openStream()).useLines {
            sq -> sq.forEach {
                val service = it.trim()
                set.add(service)

                log.info("Supports: " + service)
            }
        }
        return set.toList()
    }

}