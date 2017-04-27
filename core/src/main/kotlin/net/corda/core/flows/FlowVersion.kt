package net.corda.core.flows

/**
 * Annotation for initiating [FlowLogic]s to specify the version of their flow protocol. The version is a single integer
 * [value] which increments by one whenever a release is made where the flow protocol changes in any manner which is
 * backwards incompatible. This may be a change in the sequence of sends and receives between the client and service flows,
 * or it could be a change in the meaning. The version is used when a flow first initiates communication with a party to
 * inform them what version they are using. For this reason the annotation is not applicable for the initiated flow.
 *
 * This flow version integer is not the same as Corda's platform version, though it follows a similar semantic.
 *
 * Note: Only one version of the same flow can currently be loaded at the same time. Any session request by a client flow for
 * a different version will be rejected.
 *
 * Defaults to a flow version of 1 if not specified.
 */
// TODO Add support for multiple versions once CorDapps are loaded in separate class loaders
annotation class FlowVersion(val value: Int)
