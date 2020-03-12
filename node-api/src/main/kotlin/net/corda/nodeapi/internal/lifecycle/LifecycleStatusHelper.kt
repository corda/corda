package net.corda.nodeapi.internal.lifecycle

import java.util.concurrent.ConcurrentHashMap
import javax.json.Json

object LifecycleStatusHelper {
    private val serviceStatusMap = ConcurrentHashMap<String, Boolean>()

    fun setServiceStatus(serviceName: String, active: Boolean) {
        serviceStatusMap[serviceName] = active
    }

    fun getServiceStatus(serviceName: String) = serviceStatusMap.getOrDefault(serviceName, false)

    /**
     * Return a string copy of a JSON object containing the status of each service,
     * and whether this bridge is the master.
     */
    fun getServicesStatusReport(isMaster: Boolean): String {
        return Json.createObjectBuilder().apply {
            val statusList = Json.createArrayBuilder().apply {
                serviceStatusMap.forEach { name: String, status: Boolean ->
                    add(Json.createObjectBuilder().add(name, status).build())
                }
            }
            add("master", isMaster)
            add("services", statusList)
        }.build().toString()
    }
}