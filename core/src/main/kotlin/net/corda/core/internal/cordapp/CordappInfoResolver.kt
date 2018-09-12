package net.corda.core.internal.cordapp

abstract class CordappInfoResolver : () -> CordappImpl.Info? {
    companion object {
        private var defaultCordappInfoResolver: CordappInfoResolver = object : CordappInfoResolver() {
            override fun invoke(): CordappImpl.Info? {
                return null
            }
        }
        private var cordappInfoResolver: CordappInfoResolver = defaultCordappInfoResolver
        private var initialized = false

        @Synchronized
        fun init(resolver: CordappInfoResolver) {
            if (!initialized) {
                defaultCordappInfoResolver = resolver
                initialized = true
            }
        }

        fun getCorDappInfo(): CordappImpl.Info? = cordappInfoResolver()

        @Synchronized
        fun withCordappInfoResolution(tempResolver: () -> CordappImpl.Info?, block: () -> Unit) {
            cordappInfoResolver = tempResolver as CordappInfoResolver
            block()
            cordappInfoResolver = defaultCordappInfoResolver
        }
    }
}

class CordappInfoResolverImpl(private val cordapps: List<CordappImpl>) : CordappInfoResolver() {
    override fun invoke(): CordappImpl.Info? {
        Exception().stackTrace.forEach { stackFrame: StackTraceElement ->
            val cordappInfo: CordappImpl.Info? = cordappInfoForClass(stackFrame.className)
            if (cordappInfo != null) {
                return cordappInfo
            }
        }
        return null
    }

    private fun cordappInfoForClass(className: String) = cordapps.find { it.cordappClasses.contains(className) }?.info
}