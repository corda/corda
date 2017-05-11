package net.corda.core.flows

import java.lang.annotation.Inherited
import kotlin.annotation.AnnotationTarget.CLASS

/**
 * This annotation is required by any [FlowLogic] which has been designated to initiate communication with a counterparty
 * and request they start their side of the flow communication. To ensure that this is correctly applied
 * [net.corda.core.node.PluginServiceHub.registerServiceFlow] checks the initiating flow class has this annotation.
 *
 * There is also an optional [version] property, which defaults to 1, to specify the version of the flow protocol. This
 * integer value should be incremented whenever there is a release of this flow which has changes that are not backwards
 * compatible with previous releases. This may be a change in the sends and receives that occur, or it could be a change
 * in what a send or receive means, etc.
 *
 * The version is used when a flow first initiates communication with a party to inform them of the version they are using.
 * If the other side does not have this flow registered with the same version then the initiation request will be rejected.
 * Currently only one version of the same flow can be registered by a node.
 *
 * The flow version number is similar in concept to Corda's platform version but they are not the same. A flow's version
 * number can change independently of the platform version.
 */
// TODO Add support for multiple versions once CorDapps are loaded in separate class loaders
@Target(CLASS)
@Inherited
@MustBeDocumented
annotation class InitiatingFlow(val version: Int = 1)
