/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.finance.contracts;

import net.corda.core.contracts.Amount;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.Issued;
import net.corda.core.identity.AbstractParty;

import java.time.Instant;
import java.util.Currency;

/* This is an interface solely created to demonstrate that the same kotlin tests can be run against
 * either a Java implementation of the CommercialPaper or a kotlin implementation.
 * Normally one would not duplicate an implementation in different languages for obvious reasons, but it demonstrates that
 * ultimately either language can be used against a common test framework (and therefore can be used for real).
 */
public interface ICommercialPaperState extends ContractState {
    ICommercialPaperState withOwner(AbstractParty newOwner);

    ICommercialPaperState withFaceValue(Amount<Issued<Currency>> newFaceValue);

    ICommercialPaperState withMaturityDate(Instant newMaturityDate);
}
