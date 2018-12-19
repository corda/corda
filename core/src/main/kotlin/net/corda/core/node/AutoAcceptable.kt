package net.corda.core.node

/**
 * Tagging a network parameter with this annotation signifies that any update which modifies the parameter could be accepted without
 * the need for the node operator to run a manual accept command. If the update contains changes for any non-auto-acceptable parameter
 * or the behaviour is switched off via configuration then it will not be auto-accepted
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoAcceptable
