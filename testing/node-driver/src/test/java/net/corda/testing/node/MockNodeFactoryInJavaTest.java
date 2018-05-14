/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.testing.node;

import static java.util.Collections.emptyList;

@SuppressWarnings("unused")
public class MockNodeFactoryInJavaTest {
    /**
     * Does not need to run, only compile.
     */
    @SuppressWarnings("unused")
    private static void factoryIsEasyToPassInUsingJava() {
        //noinspection Convert2MethodRef
        new MockNetwork(emptyList());
        new MockNetwork(emptyList(), new MockNetworkParameters().withThreadPerNode(true));
        //noinspection Convert2MethodRef
        new MockNetwork(emptyList()).createNode(new MockNodeParameters());
    }
}
