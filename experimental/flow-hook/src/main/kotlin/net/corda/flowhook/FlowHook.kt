package net.corda.flowhook

import java.lang.instrument.Instrumentation

@Suppress("UNUSED", "UNUSED_PARAMETER")
class FlowHookAgent {
    companion object {
        @JvmStatic
        fun premain(argumentsString: String?, instrumentation: Instrumentation) {
            FiberMonitor.start()
            instrumentation.addTransformer(Hooker(FlowHookContainer))
        }
    }
}
