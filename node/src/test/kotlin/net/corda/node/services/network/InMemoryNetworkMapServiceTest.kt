package net.corda.node.services.network

import net.corda.testing.node.MockNetwork

class InMemoryNetworkMapServiceTest : AbstractNetworkMapServiceTest<InMemoryNetworkMapService>() {
    override val nodeFactory get() = MockNetwork.DefaultFactory
    override val networkMapService: InMemoryNetworkMapService get() = mapServiceNode.inNodeNetworkMapService as InMemoryNetworkMapService
    override fun swizzle() = Unit
}
