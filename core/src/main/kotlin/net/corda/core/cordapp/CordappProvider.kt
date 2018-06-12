/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.cordapp

import net.corda.core.DeleteForDJVM
import net.corda.core.DoNotImplement
import net.corda.core.contracts.ContractClassName
import net.corda.core.node.services.AttachmentId

/**
 * Provides access to what the node knows about loaded applications.
 */
@DoNotImplement
@DeleteForDJVM
interface CordappProvider {
    /**
     * Exposes the current CorDapp context which will contain information and configuration of the CorDapp that
     * is currently running.
     *
     * The calling application is found via stack walking and finding the first class on the stack that matches any class
     * contained within the automatically resolved [Cordapp]s loaded by the [CordappLoader]
     *
     * @throws IllegalStateException When called from a non-app context
     */
    fun getAppContext(): CordappContext

    /**
     * Resolve an attachment ID for a given contract name
     *
     * @param contractClassName The contract to find the attachment for
     * @return An attachment ID if it exists
     */
    fun getContractAttachmentID(contractClassName: ContractClassName): AttachmentId?
}