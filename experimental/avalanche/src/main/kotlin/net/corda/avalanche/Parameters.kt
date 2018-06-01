package net.corda.avalanche

import picocli.CommandLine

class Parameters {
    @CommandLine.Option(names = ["-n", "--num-transactions"], description = ["How many transactions to generate (default: 20)"])
    var nrTransactions: Int = 20

    @CommandLine.Option(names = ["-d", "--double-spend-ratio"], description = ["The double spend ratio (default: 0.02)"])
    var doubleSpendRatio: Double = 0.02

    @CommandLine.Option(names = ["-a", "--alpha"], description = ["The alpha parameter (default: 0.8)"])
    var alpha = 0.8

    @CommandLine.Option(names = ["--num-nodes"], description = ["The number of nodes (default: 50)"])
    var nrNodes = 50

    @CommandLine.Option(names = ["-k", "--sample-size"], description = ["The sample size (default `1 + nrNodes / 10`)"])
    var k = 1 + nrNodes / 10

    @CommandLine.Option(names = ["--beta1"], description = ["The beta1 parameter (default: 5)"])
    var beta1 = 5

    @CommandLine.Option(names = ["--beta2"], description = ["The beta1 parameter (default: 5)"])
    var beta2 = 5

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false

    @CommandLine.Option(names = ["--seed"], description = ["The RNG seed (default: 23)"])
    var seed = 23L

    @CommandLine.Option(names = ["--dump-dags"], description = ["Dump DAGs in dot format (default: false)"])
    var dumpDags = false

    @CommandLine.Option(names = ["-v", "--verbose"], description=["Verbose mode (default: false)"])
    var verbose = false
}
