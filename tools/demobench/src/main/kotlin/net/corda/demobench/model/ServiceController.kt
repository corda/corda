package net.corda.demobench.model

import tornadofx.Controller
import java.io.File
import java.util.*

class ServiceController : Controller() {

    private var serviceSet : List<String>

    val services : List<String>
       get() = serviceSet

    private fun loadConf(name: String): List<String> {
        val set = HashSet<String>()
        File(name).readLines().forEach {
            val service = it.trim()
            set.add(service)

            log.info("Supports: " + service)
        }
        return set.toList()
    }

    init {
        /*
         * Load our list of known extra Corda services.
         */
        val serviceConf = javaClass.classLoader.getResource("services.conf")
        serviceSet = if (serviceConf == null) {
            emptyList<String>()
        } else {
            loadConf(serviceConf.file)
        }
    }
}