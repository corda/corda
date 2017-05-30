package net.corda.core.serialization

import java.lang.annotation.Inherited

/**
 * This annotation is a marker to indicate that a class should NOT be serialised when a flow checkpoints.
 *
 * As flows are arbitrary code in which it is convenient to do many things,
 * that means we can often end up pulling in a lot of junk that doesn't make sense to put in a checkpoint and thus deserialization is not required.
 * Also, it is critical to identify that a class is not intended to be deserialized by the node, to avoid
 * a security compromise later when a vulnerability is discovered in the deserialisation of a class that just happens to
 * be on the classpath, perhaps from a 3rd party library, as has been witnessed elsewhere.
 *
 * It also makes it possible for a code reviewer to clearly identify the classes that should never be passed on the wire or stored.
 *
 * TODO: As we approach a long term wire format, this annotation will only be permitted on classes that meet certain criteria.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
annotation class CordaNotSerializable