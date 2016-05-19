package node.services.api

import core.node.ServiceHub

interface ServiceHubInternal : ServiceHub {
    val monitoringService: MonitoringService
}