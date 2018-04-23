/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.sandbox.costing;

/**
 * This interface is to decouple the actual executable code from the entry point and
 * how vetted deterministic code will be used inside the sandbox
 *
 * @author ben
 */
public interface ContractExecutor {
    /**
     * Executes a smart contract
     *
     * @param contract the contract to be executed
     */
    void execute(Contract contract);

    /**
     * Checks to see if the supplied Contract is suitable
     *
     * @param contract
     * @return true if the contract is suitable for execution
     */
    boolean isSuitable(Contract contract);
}
