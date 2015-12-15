/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.utilities.continuations

import org.apache.commons.javaflow.Continuation
import org.apache.commons.javaflow.ContinuationClassLoader

/**
 * A "continuation" is an object that represents a suspended execution of a function. They allow you to write code
 * that suspends itself half way through, bundles up everything that was on the stack into a (potentially serialisable)
 * object, and then be resumed from the exact same spot later. Continuations are not natively supported by the JVM
 * but we can use the Apache JavaFlow library which implements them using bytecode rewriting.
 *
 * The primary benefit of using continuations is that state machine/protocol code that would otherwise be very
 * convoluted and hard to read becomes very clear and straightforward.
 *
 * TODO: Document classloader interactions and gotchas here.
 */
inline fun <reified T : Runnable> loadContinuationClass(classLoader: ClassLoader): Continuation {
    val klass = T::class.java
    val url = klass.protectionDomain.codeSource.location
    val cl = ContinuationClassLoader(arrayOf(url), classLoader)
    val obj = cl.forceLoadClass(klass.name).newInstance() as Runnable
    return Continuation.startSuspendedWith(obj)
}
