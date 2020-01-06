package net.corda.client.rpc.internal.security

/**
 * Annotation which can be used on `RPCOps` interface to group certain methods for the purposes of granting permissions to all of them.
 * Please see usages of this interface for more details.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class RpcPermissionGroup(vararg val value: String)

const val READ_ONLY = "READ_ONLY"