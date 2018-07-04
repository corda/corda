/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.bridge.services.ha

import net.corda.bridge.services.api.BridgeMasterService
import net.corda.bridge.services.api.FirewallAuditService
import net.corda.bridge.services.api.FirewallConfiguration
import net.corda.bridge.services.api.ServiceStateSupport
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.utilities.contextLogger

class SingleInstanceMasterService(val conf: FirewallConfiguration,
                                  val auditService: FirewallAuditService,
                                  private val stateHelper: ServiceStateHelper = ServiceStateHelper(log)) : BridgeMasterService, ServiceStateSupport by stateHelper {
    companion object {
        val log = contextLogger()
    }

    override fun start() {
        auditService.statusChangeEvent("Single instance master going active immediately.")
        stateHelper.active = true
    }

    override fun stop() {
        auditService.statusChangeEvent("Single instance master stopping")
        stateHelper.active = false
    }

}