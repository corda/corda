package net.corda.vega

class SimmValuationTest: IntegrationTestCategory {

    @Test fun `runs SIMM valuation demo`() {
        driver(isDebug = true) {
            val controller = startNode("Controller", setOf(ServiceInfo(SimpleNotaryService.type))).getOrThrow()
            val nodeA = startNode("Bank A").getOrThrow()
            val nodeB = startNode("Bank B").getOrThrow()
            assert(createTrade())
            assert(runValuations())
        }
    }

    // TODO: create, verify, run, verify or determine a better test structure.
    private fun createTrade(): Boolean {

    }

    private fun runValuations(): Boolean {


    }
}