package net.corda.core.messaging

/**
 * If an RPC is tagged with this annotation it may return one or more observables anywhere in its response graph.
 * Calling such a method comes with consequences: it's slower, and consumes server side resources as observations
 * will buffer up on the server until they're consumed by the client.
 */
@Target(AnnotationTarget.FUNCTION)
@MustBeDocumented
annotation class RPCReturnsObservables