package net.corda.flowhook

import java.lang.instrument.Instrumentation

@Suppress("UNUSED")
class FlowHookAgent {
    companion object {
        @JvmStatic
        fun premain(argumentsString: String?, instrumentation: Instrumentation) {
            FiberMonitor.start()
            instrumentation.addTransformer(Hooker(FlowHookContainer))
        }
    }
}

