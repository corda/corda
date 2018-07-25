package net.corda.groups.services

import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken

@CordaService
class GroupService : SingletonSerializeAsToken()