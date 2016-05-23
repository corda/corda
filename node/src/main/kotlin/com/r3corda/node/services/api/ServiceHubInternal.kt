package com.r3corda.node.services.api

import com.r3corda.core.node.ServiceHub

interface ServiceHubInternal : ServiceHub {
    val monitoringService: MonitoringService
}