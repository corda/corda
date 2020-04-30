package net.corda.errorUtilities.resourceGenerator

import net.corda.common.logging.errorReporting.ErrorCodes

// These test errors are not used directly, but their compiled class files are used to verify the resource generator functionality.
enum class TestNamespaces {
    TN1,
    TN2
}

enum class TestCodes1 : ErrorCodes {
    CASE1,
    CASE2;

    override val namespace = TestNamespaces.TN1.toString()
}

enum class TestCodes2 : ErrorCodes {
    CASE1,
    CASE3;

    override val namespace = TestNamespaces.TN2.toString()
}