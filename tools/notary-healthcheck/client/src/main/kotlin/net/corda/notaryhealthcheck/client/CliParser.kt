package net.corda.notaryhealthcheck.client

import picocli.CommandLine.Option

open class CliParser(config: Config) {
    companion object {
        enum class NotaryHealthCheckCommand{
            startAll,
            stopAll,
            start,
            stop
        }
    }

    @Option(names = ["-w", "--wait-period"], description = ["Time to wait between checks in seconds"])
    var waitPeriodSeconds: Int = config.waitPeriodSeconds

    @Option(names = ["-o", "--wait-for-outstanding-flows"], description = ["Time to wait for an outstanding response before rechecking"])
    var waitForOutstandingFlows: Int = config.waitForOutstandingFlowsSeconds

    @Option(names = ["-u", "--user"], description = ["RPC user name"])
    var user: String? = config.user

    @Option(names = ["-P", "--password"], description = ["RPC password"])
    var password: String? = config.password

    @Option(names = ["-h", "--host"], description = ["RPC target host"])
    var host: String? = config.host

    @Option(names = ["-p", "--port"], description = ["RPC target port"])
    var port: Int? = config.port

    @Option(names = ["-c", "--command"], required = true, description = ["Command to run, one of startAll, stopAll, start, stop"])
    var command: NotaryHealthCheckCommand? = null

    @Option(names = ["-t", "--target"], description = ["X500 name of the node to monitor"])
    var target: String? = null

    @Option(names = ["-n", "--notary"], description = ["X500 name of the notary for the node to monitor, will default to the target if no provided"])
    var notary: String? = null
}