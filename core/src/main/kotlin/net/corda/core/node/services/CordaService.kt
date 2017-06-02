package net.corda.core.node.services

import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Annotate any class that needs to be a long-lived service within the node, such as an oracle, with this annotation.
 * Such a class needs to have a constructor with a single parameter of type [net.corda.core.node.PluginServiceHub]. This
 * construtor will be invoked during node start to initialise the service. The service hub provided can be used to get
 * information about the node that may be necessary for the service. Corda services are created as singletons within
 * the node and are available to flows via [net.corda.core.node.ServiceHub.cordaService].
 *
 * The service class has to implement [net.corda.core.serialization.SerializeAsToken] to ensure correct usage within flows.
 * (If possible extend [net.corda.core.serialization.SingletonSerializeAsToken] instead as it removes the boilerplate.)
 *
 * The annotated class should expose its [ServiceType] via a public static field named `type`, so that the service is
 * only loaded in nodes that declare the type in their advertisedServices.
 */
// TODO Handle the singleton serialisation of Corda services automatically, removing the need to implement SerializeAsToken
// TODO Currently all nodes which load the plugin will attempt to load the service even if it's not revelant to them. The
// underlying problem is that the entire CorDapp jar is used as a dependency, when in fact it's just the client-facing
// bit of the CorDapp that should be depended on (e.g. the initiating flows).
@Target(CLASS)
annotation class CordaService
