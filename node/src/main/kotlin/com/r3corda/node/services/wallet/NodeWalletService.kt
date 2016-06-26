package com.r3corda.node.services.wallet

import com.r3corda.core.node.ServiceHub
import com.r3corda.core.testing.InMemoryWalletService

/**
 * Currently, the node wallet service is just the in-memory wallet service until we have finished evaluating and
 * selecting a persistence layer (probably an ORM over a SQL DB).
 */
class NodeWalletService(services: ServiceHub) : InMemoryWalletService(services)