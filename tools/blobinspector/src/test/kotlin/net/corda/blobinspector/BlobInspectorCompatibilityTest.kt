package net.corda.blobinspector

import net.corda.testing.CliBackwardsCompatibleTest

class NodeStartupCompatibilityTest : CliBackwardsCompatibleTest(BlobInspector::class.java)