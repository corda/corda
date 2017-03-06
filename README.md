![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Corda

Corda is a decentralised database system in which nodes trust each other as little as possible.

## Features

* A P2P network of nodes
* Smart contracts
* Flow framework
* "Notary" infrastructure to validate uniqueness of transactions
* Written as a platform for distributed apps called CorDapps
* Written in [Kotlin](https://kotlinlang.org), targeting the JVM
 
Read our full and planned feature list [here](https://docs.corda.net/inthebox.html).

## Getting started

Firstly, read the [Getting started](https://docs.corda.net/getting-set-up.html) documentation. 

Next, use the following guides to set up your dev environment:

* If you are on **Windows** [use this getting started guide](https://www.corda.net/wp-content/uploads/2017/01/Corda-Windows-Quick-start-guide-1.pdf) which also explains through how to run the sample apps.

* Alternatively if you are on **Mac/Linux**, [watch this brief Webinar](https://vimeo.com/200167665) which walks through getting Corda, installing it, building it, running nodes and opening projects in IntelliJ.

After the above, watching the following webinars will give you a great introduction to Corda:

### Webinar 1 – [Introduction to Corda](https://vimeo.com/192757743/c2ec39c1e1)
 
Richard Brown, R3 Chief Technology Officer, explains Corda's unique architecture, the only distributed ledger platform designed by and for the financial industry's unique requirements. You may want to read the [Corda non-technical whitepaper](https://www.r3.com/s/corda-introductory-whitepaper-final.pdf) as pre-reading for this session.
 
### Webinar 2 – [Corda Developers’ Tutorial](https://vimeo.com/192797322/aab499b152)
 
Roger Willis, R3 Developer Relations Lead, provides an overview of Corda from a developer’s perspective and guidance on how to start building CorDapps. You may want to view [Webinar 1 - Introduction to Corda](https://vimeo.com/192757743/c2ec39c1e1) as preparation for this session. 
**NB. This was recorded for the M5 release.**

## Building on Corda

To build your own CorDapps:

1. Clone the [CorDapp Template repository](https://github.com/corda/cordapp-template)
2. Read the [README](https://github.com/corda/cordapp-template/blob/master/README.md) (**IMPORTANT!**)
3. Read the [Writing a CorDapp](https://docs.corda.net/tutorial-cordapp.html) documentation

To look at the Corda source and run some sample applications:

1. Clone this repository
2. To run some sample CorDapps, read the [running the demos documentation](https://docs.corda.r3cev.com/running-the-demos.html)
3. Start hacking and [contribute](./CONTRIBUTING.md)!

## Useful links

* [Project website](https://corda.net)
* [Documentation](https://docs.corda.net)
* [Slack channel] (https://slack.corda.net/)
* [Forum](https://discourse.corda.net)
* [Meetups](https://www.meetup.com/pro/corda/)
* [Training Courses](https://www.corda.net/corda-training/)


## Development State
 
Corda is currently in very early development and should not be used in production systems. Breaking
changes will happen on minor versions until 1.0. Experimentation with Corda is recommended.

Pull requests, experiments, and contributions are encouraged and welcomed.

## Background

The project is supported by R3, a financial industry consortium, which is why it 
contains some code for financial use cases and why the documentation focuses on finance. The goal is to use it
to construct a global ledger, simplifying finance and reducing the overheads of banking. But it is run as 
an open source project and the basic technology of a peer-to-peer decentralised database may be useful 
for many different projects.

## Contributing

Please read [here](./CONTRIBUTING.md).

## License

[Apache 2.0](./LICENSE)

## Acknowledgements

![YourKit](https://www.yourkit.com/images/yklogo.png)

YourKit supports open source projects with its full-featured Java Profiler.

YourKit, LLC is the creator of [YourKit Java Profiler](https://www.yourkit.com/java/profiler/) and [YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/), innovative and intelligent tools for profiling Java and .NET applications.
