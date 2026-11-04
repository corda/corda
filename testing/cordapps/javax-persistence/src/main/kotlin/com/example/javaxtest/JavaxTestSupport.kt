package com.example.javaxtest

import net.corda.core.node.ServiceHub

/**
 * Test code built against the `javax.persistence` API, as the test suite of a CorDapp built for Corda 4.14 or earlier has it: a
 * `withEntityManager` block in a test. Not part of any CorDapp; it is converted by the javax persistence test agent like the CorDapp.
 */
fun ServiceHub.findJavaxTestStateLabels(): List<String> = withEntityManager {
    createQuery("select s.label from PersistentJavaxTestState s order by s.label", String::class.java).resultList
}
