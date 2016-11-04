# Corda

Corda is a decentralised database system in which nodes trust each other as little as possible. This reference 
implementation is not yet complete or offering backwards compatibility, but should be suitable for small experiments
and exploration of the underlying ideas.
 
Pull requests are appreciated. You can chat to the team on our forums and on our Slack.
It runs on the JVM and is mostly written in Kotlin.

### **[Project website](https://corda.net)**

### **[Documentation](https://docs.corda.net)**

The project is supported by R3, a financial industry consortium, which is why it 
contains some code for financial use cases and why the documentation focuses on finance. The goal is to use it
to construct a global ledger, thus simplifying finance and reducing the overheads of banking. But it is run as 
a typical open source project and the basic technology of a peer to peer decentralised database may be useful 
for many different projects. We'd love to hear about any interesting experiments you do with it!

# Getting started

Corda consists of node software that takes part in a network, libraries you can use to develop apps for the platform,
a client library that lets you interact with the node via RPC, and some samples showing how it all works.

To install:

1. Make sure you have Oracle JDK 8 available (OpenJDK 8 support is coming).
2. Run `./gradlew install` to download all the dependencies (including the build tools), compile the code and 
   install the core libraries into your local Maven repository (under `~/.m2`)
   
To run some demo nodes that swap cash around between themselves:

1. `./gradlew tools:explorer:runDemoNodes`
2. You can find a node now running on port 20002

To run an explorer app that lets you connect to a node and explore what it's doing, try `./gradlew tools:explorer:run`.
The explorer also lets you send and receive cash.

Now [go read the documentation](https://docs.corda.net).

# Getting involved

* [Forums](https://not.here.yet)
* [Chat](https://cordaledger.slack.com)

# License

Apache 2.0