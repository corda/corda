package net.corda.groups.services

import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken

/**
 * Not currently used but will be used to persist which transactions have been added to a group.
 */
@CordaService
class GroupService(val services: AppServiceHub) : SingletonSerializeAsToken()